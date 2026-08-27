package ch.nacht.service;

import ch.nacht.dto.BkwTariffsResponseDTO;
import ch.nacht.dto.PreiszeitreiheDownloadDTO;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Preiszeitreihe;
import ch.nacht.exception.PreiszeitreiheQuelleException;
import ch.nacht.repository.PreiszeitreiheRepository;
import ch.nacht.util.PreiszeitreiheZeit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Beschafft die dynamischen Einspeisepreise bei der Quelle und schreibt sie in die Zeitreihe
 * (Specs/Preiszeitreihe.md, FR-5 und FR-7).
 *
 * <p><b>Ohne Mandantenkontext und ohne Flag-Pruefung.</b> Diese Klasse laeuft auch im geplanten Job,
 * wo es keinen angemeldeten Benutzer und damit keine {@code org_id} gibt. Sie exponiert nichts nach
 * aussen: Der Controller spricht ausschliesslich mit {@link PreiszeitreiheService}, und der prueft
 * das Feature-Flag. Die ArchUnit-Regel fuehrt diese Klasse deshalb namentlich als Ausnahme — wie
 * {@code NkBerechnungService} bei der Nebenkostenabrechnung.
 *
 * <p>Die Systemmeldungen entstehen hier, weil beide Aufrufer (Job und Maske) sie brauchen und die
 * Regeln sonst zweimal geschrieben werden muessten.
 */
@Service
public class PreiszeitreiheAbrufService {

    private static final Logger log = LoggerFactory.getLogger(PreiszeitreiheAbrufService.class);

    /** Die einzige Einheit, in der Preise uebernommen werden. */
    private static final String ERWARTETE_EINHEIT = "CHF_kWh";

    /**
     * Obergrenze plausibler Intervalle je Abruf. Die Quelle liefert 96 (24 Stunden im
     * 15-Minuten-Raster); alles jenseits dieser Groessenordnung ist kein Tagesfenster mehr, sondern
     * ein Hinweis darauf, dass sich die Bedeutung der Antwort geaendert hat.
     */
    private static final int MAX_INTERVALLE = 10_000;

    /** Laenge der Fehlerbeschreibung in der Systemmeldung ({@code systemmeldung.parameter}). */
    private static final int PARAMETER_MAX_LAENGE = 500;

    private final PreiszeitreiheRepository preiszeitreiheRepository;
    private final FeatureFlagService featureFlagService;
    private final SystemmeldungService systemmeldungService;
    private final RestClient restClient;
    private final String quellUrl;

    public PreiszeitreiheAbrufService(PreiszeitreiheRepository preiszeitreiheRepository,
                                      FeatureFlagService featureFlagService,
                                      SystemmeldungService systemmeldungService,
                                      RestClient externerRestClient,
                                      @Value("${preiszeitreihe.url:}") String quellUrl) {
        this.preiszeitreiheRepository = preiszeitreiheRepository;
        this.featureFlagService = featureFlagService;
        this.systemmeldungService = systemmeldungService;
        this.restClient = externerRestClient;
        this.quellUrl = quellUrl;
        log.info("PreiszeitreiheAbrufService initialized (url={})", quellUrl);
    }

    /**
     * Holt das aktuelle Fenster der Quelle und schreibt es per Upsert.
     *
     * <p>Reihenfolge mit Absicht: <b>erst pruefen, dann schreiben</b>. Wuerde die Einheit erst beim
     * Umwandeln je Eintrag geprueft, stuende bei einem Wechsel der Einheit die halbe Reihe in
     * fremder Einheit in der Tabelle — um Faktor 100 falsch und nicht erkennbar.
     *
     * @return Zaehlwerte des Abrufs
     * @throws PreiszeitreiheQuelleException wenn die Quelle versagt (Netz, Format, Einheit, Menge)
     */
    @Transactional
    public PreiszeitreiheDownloadDTO abrufen() {
        if (quellUrl == null || quellUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Die Quelle der Einspeisepreise ist nicht konfiguriert (preiszeitreihe.url)");
        }

        BkwTariffsResponseDTO antwort = hole();
        if (antwort == null || antwort.prices() == null) {
            scheitern("Die Antwort der Quelle enthielt keine Preise (unerwartetes Format)", null);
        }

        List<BkwTariffsResponseDTO.BkwPrice> preise = antwort.prices();
        if (preise.size() > MAX_INTERVALLE) {
            scheitern("Die Quelle lieferte " + preise.size()
                    + " Intervalle - das ist kein Tagesfenster; es wurde nichts gespeichert", null);
        }
        pruefeEinheiten(preise);

        LocalDateTime publikation = nachUtc(antwort.publicationTimestamp());
        List<Preiszeitreihe> gueltige = new ArrayList<>();
        int uebersprungen = 0;
        for (BkwTariffsResponseDTO.BkwPrice preis : preise) {
            Preiszeitreihe wert = umwandeln(preis, publikation);
            if (wert == null) {
                uebersprungen++;
                continue;
            }
            gueltige.add(wert);
        }
        // Aufsteigend schreiben: Job und Schaltflaeche nehmen die Sperren dann in derselben
        // Reihenfolge und koennen sich nicht verklemmen (ein Deadlock kaeme als
        // CannotAcquireLockException durch, die kein Handler lesbar abbildet).
        gueltige.sort(Comparator.comparing(Preiszeitreihe::getZeitVon));

        Set<LocalDateTime> bestand = bestandDerSpanne(gueltige);
        int neu = 0;
        for (Preiszeitreihe wert : gueltige) {
            if (!bestand.contains(wert.getZeitVon())) {
                neu++;
            }
            preiszeitreiheRepository.upsert(wert.getZeitVon(), wert.getZeitBis(), wert.getPreis(),
                    wert.getPublikation());
        }

        int aktualisiert = gueltige.size() - neu;
        log.info("Preiszeitreihe: {} Intervalle abgerufen, {} neu, {} aktualisiert, {} uebersprungen",
                preise.size(), neu, aktualisiert, uebersprungen);
        meldeErgebnis(uebersprungen, gueltige);

        return new PreiszeitreiheDownloadDTO(preise.size(), neu, aktualisiert, uebersprungen,
                PreiszeitreiheZeit.nachOrtszeit(publikation));
    }

    /** Ruft die Quelle ab; jeder Fehlschlag endet als {@link PreiszeitreiheQuelleException}. */
    private BkwTariffsResponseDTO hole() {
        try {
            return restClient.get().uri(quellUrl).retrieve().body(BkwTariffsResponseDTO.class);
        } catch (RuntimeException e) {
            scheitern("Die Quelle der Einspeisepreise ist nicht erreichbar: " + kurz(e), e);
            return null; // unerreichbar - scheitern() wirft immer
        }
    }

    /**
     * Weist den ganzen Abruf ab, wenn irgendein Wert eine andere Einheit als {@code CHF_kWh} traegt.
     *
     * <p>Eine stillschweigende Uebernahme waere der teuerste Fehler dieses Features: Rp./kWh statt
     * CHF/kWh verfaelscht die Reihe um Faktor 100, und niemand sieht es der Zahl an.
     */
    private void pruefeEinheiten(List<BkwTariffsResponseDTO.BkwPrice> preise) {
        for (BkwTariffsResponseDTO.BkwPrice preis : preise) {
            if (preis.feedIn() == null) {
                continue;
            }
            for (BkwTariffsResponseDTO.BkwValue wert : preis.feedIn()) {
                if (wert != null && wert.unit() != null
                        && !ERWARTETE_EINHEIT.equals(wert.unit())) {
                    scheitern("Die Quelle lieferte die Einheit '" + wert.unit() + "' statt '"
                            + ERWARTETE_EINHEIT + "' - es wurde nichts gespeichert", null);
                }
            }
        }
    }

    /**
     * Wandelt ein Intervall um; {@code null}, wenn es unvollstaendig ist.
     *
     * <p>Ein leeres {@code feed_in} oder ein fehlender Wert wird <b>uebersprungen</b> und nicht als
     * {@code 0.00000} gespeichert: Kein Preis ist etwas anderes als Preis null, und die Reihe darf
     * darueber nicht luegen.
     */
    private Preiszeitreihe umwandeln(BkwTariffsResponseDTO.BkwPrice preis,
                                     LocalDateTime publikation) {
        if (preis == null || preis.startTimestamp() == null || preis.endTimestamp() == null) {
            return null;
        }
        if (preis.feedIn() == null || preis.feedIn().isEmpty()) {
            return null;
        }
        BkwTariffsResponseDTO.BkwValue erster = preis.feedIn().get(0);
        if (erster == null || erster.value() == null) {
            return null;
        }
        BigDecimal wert = erster.value();
        if (wert.signum() < 0) {
            return null;
        }
        LocalDateTime von = nachUtc(preis.startTimestamp());
        LocalDateTime bis = nachUtc(preis.endTimestamp());
        if (von == null || bis == null || !von.isBefore(bis)) {
            return null;
        }
        return new Preiszeitreihe(von, bis, wert, publikation);
    }

    /** Bereits vorhandene Intervallbeginne der gelieferten Spanne - Grundlage der Zaehlung. */
    private Set<LocalDateTime> bestandDerSpanne(List<Preiszeitreihe> gueltige) {
        if (gueltige.isEmpty()) {
            return Set.of();
        }
        LocalDateTime von = gueltige.get(0).getZeitVon();
        LocalDateTime bis = gueltige.get(gueltige.size() - 1).getZeitVon().plusNanos(1);
        Set<LocalDateTime> bestand = new HashSet<>();
        for (Preiszeitreihe vorhanden : preiszeitreiheRepository.findByZeitraum(von, bis)) {
            bestand.add(vorhanden.getZeitVon());
        }
        return bestand;
    }

    /**
     * Meldet das Ergebnis: Selbstheilung bei sauberem Lauf, Warnung bei uebersprungenen Werten.
     *
     * <p>Je Mandant mit aktivem Flag, weil {@code /systemmeldungen} mandantenbezogen ist - eine
     * Meldung ohne {@code org_id} saehe niemand.
     */
    private void meldeErgebnis(int uebersprungen, List<Preiszeitreihe> gueltige) {
        List<Long> orgIds = featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE);
        for (Long orgId : orgIds) {
            systemmeldungService.autoResolve(orgId,
                    SystemmeldungService.KEY_PREISZEITREIHE_ABRUF_FEHLER);
            if (uebersprungen == 0) {
                systemmeldungService.autoResolve(orgId,
                        SystemmeldungService.KEY_PREISZEITREIHE_UEBERSPRUNGEN);
            } else {
                systemmeldungService.erfasse(orgId, MeldungLevel.WARN,
                        SystemmeldungService.KATEGORIE_PREISZEITREIHE,
                        SystemmeldungService.KEY_PREISZEITREIHE_UEBERSPRUNGEN,
                        beschreibeLuecke(uebersprungen, gueltige));
            }
        }
    }

    private String beschreibeLuecke(int uebersprungen, List<Preiszeitreihe> gueltige) {
        if (gueltige.isEmpty()) {
            return uebersprungen + " Preisintervall(e) uebersprungen";
        }
        return uebersprungen + " Preisintervall(e) uebersprungen ("
                + PreiszeitreiheZeit.nachOrtszeit(gueltige.get(0).getZeitVon()) + " - "
                + PreiszeitreiheZeit.nachOrtszeit(gueltige.get(gueltige.size() - 1).getZeitBis())
                + ")";
    }

    /**
     * Erfasst die Fehlermeldung und wirft.
     *
     * <p>Der Text wird gekuerzt: {@code systemmeldung.parameter} ist 500 Zeichen breit, und
     * {@code erfasse} kuerzt nicht selbst. Eine HTML-Fehlerseite der Quelle liesse das Melden des
     * Fehlers sonst am Fehler selbst scheitern.
     */
    private void scheitern(String meldung, Throwable ursache) {
        log.warn("Preiszeitreihe: {}", meldung, ursache);
        String parameter = meldung.length() > PARAMETER_MAX_LAENGE
                ? meldung.substring(0, PARAMETER_MAX_LAENGE)
                : meldung;
        for (Long orgId : featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE)) {
            systemmeldungService.erfasse(orgId, MeldungLevel.WARN,
                    SystemmeldungService.KATEGORIE_PREISZEITREIHE,
                    SystemmeldungService.KEY_PREISZEITREIHE_ABRUF_FEHLER, parameter);
        }
        throw new PreiszeitreiheQuelleException(meldung, ursache);
    }

    /** Kurzfassung einer Ausnahme fuer Log und Meldung - ohne Stacktrace, ohne interne Pfade. */
    private String kurz(RuntimeException e) {
        String text = e.getMessage();
        return text == null || text.isBlank() ? e.getClass().getSimpleName() : text;
    }

    /** {@code Instant} der Quelle als {@code LocalDateTime} in UTC - ohne Zonenverschiebung. */
    private LocalDateTime nachUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
