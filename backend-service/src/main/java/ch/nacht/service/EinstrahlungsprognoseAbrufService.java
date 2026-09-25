package ch.nacht.service;

import ch.nacht.dto.OpenMeteoResponseDTO;
import ch.nacht.dto.SteuerKonfigurationDTO;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.repository.EinstrahlungsprognoseRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Holt die Einstrahlungsprognose bei Open-Meteo und schreibt sie in
 * {@code zev.einstrahlungsprognose} (Specs/Ladeplanung.md, FR-2).
 *
 * <p><b>Warum überhaupt gespeichert wird.</b> Der Steuerungs-Job darf nicht an einer fremden
 * Schnittstelle hängen: Eine Störung bei Open-Meteo nähme sonst die Steuerung mit. Diese Klasse
 * ist der einzige Ort, der nach aussen spricht; alles Weitere liest aus der Datenbank. Dasselbe
 * Muster wie bei {@link PreiszeitreiheAbrufService}.
 *
 * <p><b>Ohne Mandantenkontext.</b> Sie läuft im geplanten Job, wo es keinen angemeldeten Benutzer
 * gibt, und bekommt die {@code orgId} deshalb als Parameter.
 *
 * <p><b>Nach aussen gehen nur Koordinaten und Ausrichtung</b> — keine Verbrauchs-, Erzeugungs-
 * oder Mieterdaten. Der Aufruf ist ein {@code GET} ohne Authentifizierung und ohne Nutzlast.
 */
@Service
public class EinstrahlungsprognoseAbrufService {

    private static final Logger log =
            LoggerFactory.getLogger(EinstrahlungsprognoseAbrufService.class);

    /**
     * Obergrenze plausibler Intervalle je Abruf.
     *
     * <p>Zwei Tage im 15-Minuten-Raster sind 192. Alles jenseits dieser Grössenordnung ist kein
     * Tagesfenster mehr, sondern ein Hinweis darauf, dass sich die Bedeutung der Antwort geändert
     * hat — dann wird nichts geschrieben, statt die Tabelle zu fluten.
     */
    private static final int MAX_INTERVALLE = 1_000;

    /** Länge der Fehlerbeschreibung in der Systemmeldung ({@code systemmeldung.parameter}). */
    private static final int PARAMETER_MAX_LAENGE = 500;

    private final EinstrahlungsprognoseRepository prognoseRepository;
    private final EinstellungenService einstellungenService;
    private final SystemmeldungService systemmeldungService;
    private final HibernateFilterService hibernateFilterService;
    private final RestClient restClient;
    private final String basisUrl;
    private final String modell;
    private final int tage;

    public EinstrahlungsprognoseAbrufService(
            EinstrahlungsprognoseRepository prognoseRepository,
            EinstellungenService einstellungenService,
            SystemmeldungService systemmeldungService,
            HibernateFilterService hibernateFilterService,
            RestClient externerRestClient,
            @Value("${ladeplanung.prognose.url:https://api.open-meteo.com/v1/forecast}")
                    String basisUrl,
            @Value("${ladeplanung.prognose.modell:meteoswiss_icon_ch1}") String modell,
            @Value("${ladeplanung.prognose.tage:2}") int tage) {
        this.prognoseRepository = prognoseRepository;
        this.einstellungenService = einstellungenService;
        this.systemmeldungService = systemmeldungService;
        this.hibernateFilterService = hibernateFilterService;
        this.restClient = externerRestClient;
        this.basisUrl = basisUrl;
        this.modell = modell;
        this.tage = tage;
        log.info("EinstrahlungsprognoseAbrufService initialized (url={}, modell={}, tage={})",
                basisUrl, modell, tage);
    }

    /**
     * Holt die Prognose für <b>einen</b> Mandanten und schreibt sie per Upsert.
     *
     * <p>Ohne vollständigen Standort und Ausrichtung geschieht nichts — und zwar still: Ein
     * Mandant, der die Ladeplanung nicht nutzt, soll keine Meldung dafür bekommen.
     *
     * @param orgId Mandant
     * @return Anzahl geschriebener Intervalle; 0, wenn nichts zu tun war
     */
    @Transactional
    public int abrufen(Long orgId) {
        hibernateFilterService.enableOrgFilter(orgId);
        SteuerKonfigurationDTO konfiguration = einstellungenService.getSteuerKonfiguration(orgId);

        if (!konfiguration.hatStandortUndAusrichtung()) {
            log.debug("Einstrahlungsprognose: org={} ohne Standort/Ausrichtung - kein Abruf", orgId);
            return 0;
        }

        try {
            OpenMeteoResponseDTO antwort = hole(konfiguration);
            int geschrieben = schreibe(orgId, antwort);
            log.info("Einstrahlungsprognose: org={} {} Intervalle geschrieben", orgId, geschrieben);
            return geschrieben;
        } catch (RuntimeException e) {
            // Die vorhandene Prognose bleibt stehen - eine veraltete ist brauchbarer als keine.
            log.warn("Einstrahlungsprognose: Abruf fuer org={} fehlgeschlagen - {}",
                    orgId, e.getMessage());
            systemmeldungService.erfasse(orgId, MeldungLevel.WARN,
                    SystemmeldungService.KATEGORIE_PREISZEITREIHE,
                    "LADEPLANUNG_PROGNOSE_FEHLER", kuerze(e.getMessage()));
            return 0;
        }
    }

    /** Ruft die API auf. Wirft, wenn sie nicht erreichbar ist oder Unerwartetes liefert. */
    private OpenMeteoResponseDTO hole(SteuerKonfigurationDTO k) {
        String url = basisUrl
                + "?latitude=" + k.getBreitengrad()
                + "&longitude=" + k.getLaengengrad()
                + "&tilt=" + k.getNeigung()
                + "&azimuth=" + k.getAzimut()
                + "&minutely_15=global_tilted_irradiance"
                + "&timezone=Europe%2FZurich"
                + "&models=" + modell
                + "&forecast_days=" + tage;

        OpenMeteoResponseDTO antwort = restClient.get().uri(url)
                .retrieve()
                .body(OpenMeteoResponseDTO.class);

        if (antwort == null || antwort.minutely15() == null
                || antwort.minutely15().time() == null
                || antwort.minutely15().globalTiltedIrradiance() == null) {
            throw new IllegalStateException("Antwort ohne minutely_15-Daten");
        }
        return antwort;
    }

    /**
     * Schreibt die Antwort per Upsert.
     *
     * <p><b>Erst prüfen, dann schreiben.</b> Passen die beiden Listen nicht zusammen oder ist die
     * Antwort unplausibel gross, wird <b>nichts</b> geschrieben: Eine halb ersetzte Prognose wäre
     * schlimmer als eine veraltete, weil niemand sähe, welcher Teil von wann stammt.
     */
    private int schreibe(Long orgId, OpenMeteoResponseDTO antwort) {
        List<String> zeiten = antwort.minutely15().time();
        List<Double> werte = antwort.minutely15().globalTiltedIrradiance();

        if (zeiten.size() != werte.size()) {
            throw new IllegalStateException("time und global_tilted_irradiance verschieden lang: "
                    + zeiten.size() + " / " + werte.size());
        }
        if (zeiten.size() > MAX_INTERVALLE) {
            throw new IllegalStateException("Antwort mit " + zeiten.size()
                    + " Intervallen - erwartet werden hoechstens " + MAX_INTERVALLE);
        }

        LocalDateTime abgerufenAm = LocalDateTime.now();
        int geschrieben = 0;
        for (int i = 0; i < zeiten.size(); i++) {
            Double wert = werte.get(i);
            if (wert == null) {
                // Kein Wert heisst NICHT BEKANNT. Eine 0 hiesse "kein Licht" und verdorbe den
                // gelernten Umrechnungsfaktor.
                continue;
            }
            LocalDateTime zeit = parse(zeiten.get(i));
            prognoseRepository.upsert(orgId, zeit, BigDecimal.valueOf(wert), abgerufenAm);
            geschrieben++;
        }
        return geschrieben;
    }

    /**
     * Zeitstempel der API zu {@link LocalDateTime}.
     *
     * <p>Die API liefert bei {@code timezone=Europe/Zurich} Ortszeit ohne Zonenangabe
     * ({@code "2026-09-25T13:15"}) — genau die Form, die {@code LocalDateTime.parse} erwartet.
     * Das ist derselbe Zeitbezug wie {@code steuerentscheid.zeit_von}.
     */
    private LocalDateTime parse(String zeit) {
        try {
            return LocalDateTime.parse(zeit);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Zeitstempel nicht lesbar: " + zeit, e);
        }
    }

    private String kuerze(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= PARAMETER_MAX_LAENGE
                ? text
                : text.substring(0, PARAMETER_MAX_LAENGE);
    }
}
