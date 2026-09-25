package ch.nacht.service;

import ch.nacht.dto.PrognosepunktDTO;
import ch.nacht.dto.SteuerKonfigurationDTO;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Einstrahlungsprognose;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.EinstrahlungsprognoseRepository;
import ch.nacht.repository.MesswerteRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rechnet die Einstrahlungsprognose in eine erwartete Erzeugung um
 * (Specs/Ladeplanung.md, FR-3).
 *
 * <p><b>Der Umrechnungsfaktor wird gelernt, nicht konfiguriert:</b>
 *
 * <pre>
 *   faktor = Σ erzeugung_ist / Σ gti      über die letzten n Tage, nur Intervalle mit gti &gt; 0
 *   prognose(i) = gti(i) * faktor
 * </pre>
 *
 * <p>Das Verhältnis der Summen ist eine Regression durch den Ursprung, gewichtet nach Einstrahlung
 * — helle Intervalle zählen mehr, und genau auf die kommt es an.
 *
 * <p><b>Gelernt wird die VERRECHNETE Erzeugung</b>, nicht der blosse Zählerwert:
 * {@code produktion + speicher_ladung − speicher_entladung}. Der Erzeugungszähler sieht nur, was
 * der Hybrid-Wechselrichter wechselstromseitig abgibt; was gleichstromseitig in die Batterie
 * fliesst, passiert ihn nie (Specs/Einspeisesteuerung.md, FR-5b). An zwei gemessenen Tagen fehlten
 * so 12 % und 8 % der Erzeugung.
 *
 * <p><b>Warum das den Faktor unbrauchbar machte.</b> Ein zu tiefer Faktor ergibt eine zu tiefe
 * erwartete Erzeugung, daraus einen zu tiefen erwarteten Überschuss — und damit stünde für die
 * spätere Merit-Order immer die Antwort „die Restsonne reicht nicht" im Raum. Das Verfahren
 * lüde dann grundsätzlich und wirkte wie abgeschaltet: ein Fehler, der nicht als Fehler auffällt.
 *
 * <p><b>Warum gelernt und nicht aus Nennleistung gerechnet.</b> Ein Faktor aus kWp und Performance
 * Ratio wäre eine Annahme; der gelernte Faktor enthält Verschattung, Verschmutzung, Modulalterung
 * und Ausrichtungsfehler, ohne dass sie jemand erfassen muss — und er korrigiert sich von selbst,
 * wenn sich an der Anlage etwas ändert.
 *
 * <p><b>In dieser Ausbaustufe steuert er nichts.</b> Die Prognose wird berechnet und angezeigt,
 * damit sich über einige Wochen beurteilen lässt, ob sie taugt. Die Ladeplanung darauf aufzusetzen
 * ist der nächste Schritt (Specs/Ladeplanung.md, FR-1).
 */
@Service
public class ProduktionsprognoseService {

    private static final Logger log = LoggerFactory.getLogger(ProduktionsprognoseService.class);

    /** Länge eines Messintervalls — dasselbe Raster wie Messwerte und Prognose. */
    private static final int INTERVALL_MINUTEN = 15;

    /**
     * Mindestzahl an Intervallen mit Einstrahlung, damit ein Faktor gebildet wird.
     *
     * <p>Drei Tage mit Sonne entsprechen grob 150 hellen Intervallen. Darunter wäre der Faktor aus
     * zu wenigen Punkten geschätzt — und ein zu kleiner Faktor liesse die Steuerung später
     * dauerhaft zu früh laden.
     */
    private static final int MIN_PUNKTE = 150;

    /** Nachkommastellen einer Energiemenge, wie {@code NUMERIC(12,3)}. */
    private static final int MENGE_SCALE = 3;

    private final EinstrahlungsprognoseRepository prognoseRepository;
    private final MesswerteRepository messwerteRepository;
    private final EinheitRepository einheitRepository;
    private final EinstellungenService einstellungenService;
    private final FeatureFlagService featureFlagService;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public ProduktionsprognoseService(EinstrahlungsprognoseRepository prognoseRepository,
                                      MesswerteRepository messwerteRepository,
                                      EinheitRepository einheitRepository,
                                      EinstellungenService einstellungenService,
                                      FeatureFlagService featureFlagService,
                                      OrganizationContextService organizationContextService,
                                      HibernateFilterService hibernateFilterService) {
        this.prognoseRepository = prognoseRepository;
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
        this.einstellungenService = einstellungenService;
        this.featureFlagService = featureFlagService;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
        log.info("ProduktionsprognoseService initialized");
    }

    /**
     * Prognose eines <b>Ortstages</b>, je 15-Minuten-Intervall.
     *
     * <p>Enthält auch Intervalle, die noch in der Zukunft liegen — anders als die Entscheide, die
     * es nur für abgeschlossene Intervalle gibt. Genau dafür ist die Prognose da.
     *
     * @param datum Tag in Ortszeit
     * @return Punkte mit Einstrahlung und erwarteter Erzeugung; leer, wenn keine Prognose vorliegt
     */
    @Transactional(readOnly = true)
    public List<PrognosepunktDTO> getPrognose(LocalDate datum) {
        Long orgId = organizationContextService.getCurrentOrgId();
        pruefeFeatureFlag(orgId);
        hibernateFilterService.enableOrgFilter();

        List<Einstrahlungsprognose> werte = prognoseRepository.findByZeitBetween(
                datum.atStartOfDay(), datum.plusDays(1).atStartOfDay());
        if (werte.isEmpty()) {
            return List.of();
        }

        BigDecimal faktor = umrechnungsfaktor(orgId, datum);

        List<PrognosepunktDTO> punkte = new ArrayList<>();
        for (Einstrahlungsprognose wert : werte) {
            PrognosepunktDTO punkt = new PrognosepunktDTO();
            punkt.setZeit(wert.getZeit());
            punkt.setGti(wert.getGti());
            punkt.setFaktor(faktor);
            // Ohne Faktor bleibt die erwartete Erzeugung LEER statt 0: Eine 0 saehe aus wie
            // "nichts erwartet", obwohl der Grund "noch nicht gelernt" ist.
            punkt.setErwarteteErzeugung(faktor == null ? null
                    : wert.getGti().multiply(faktor).setScale(MENGE_SCALE, RoundingMode.HALF_UP));
            punkte.add(punkt);
        }
        return punkte;
    }

    /**
     * Der gelernte Faktor von W/m² auf kWh je Intervall; {@code null}, wenn zu wenig Historie
     * vorliegt.
     *
     * <p>Gerechnet wird über die {@code n} Tage <b>vor</b> dem gefragten Tag. Den Tag selbst
     * auszulassen ist kein Zufall: In der Rückrechnung dürfte er nicht einfliessen, weil sein
     * Verlauf zum Entscheidungszeitpunkt noch nicht bekannt war. Für die Anzeige heute macht es
     * kaum einen Unterschied, für die spätere Kalibrierung den ganzen.
     */
    private BigDecimal umrechnungsfaktor(Long orgId, LocalDate datum) {
        SteuerKonfigurationDTO konfiguration = einstellungenService.getSteuerKonfiguration(orgId);
        LocalDateTime von = datum.minusDays(konfiguration.historieTageOderVorgabe()).atStartOfDay();
        LocalDateTime bis = datum.atStartOfDay();

        // Einstrahlung je Intervall (nur helle), als Nachschlagewerk nach Intervall-BEGINN.
        Map<LocalDateTime, BigDecimal> gtiJeZeit = new HashMap<>();
        for (Object[] zeile : prognoseRepository.findGtiJeIntervall(von, bis)) {
            gtiJeZeit.put((LocalDateTime) zeile[0], (BigDecimal) zeile[1]);
        }
        if (gtiJeZeit.size() < MIN_PUNKTE) {
            log.debug("Umrechnungsfaktor: nur {} helle Intervalle fuer org={} - kein Faktor",
                    gtiJeZeit.size(), orgId);
            return null;
        }

        // Der Speicherfluss je Intervall - er fehlt im Erzeugungszaehler und muss dazu.
        Map<LocalDateTime, BigDecimal> speicherJeZeit = speicherflussJeIntervall(
                von.plusMinutes(INTERVALL_MINUTEN), bis.plusMinutes(INTERVALL_MINUTEN));

        // Gemessene Erzeugung je Intervall. messwerte.zeit traegt das Intervall-ENDE, die Prognose
        // den BEGINN - deshalb die Verschiebung. Ohne sie waere der Faktor um eine Viertelstunde
        // versetzt gelernt, und niemand saehe es der Zahl an.
        BigDecimal summeErzeugung = BigDecimal.ZERO;
        BigDecimal summeGti = BigDecimal.ZERO;
        for (Object[] zeile : messwerteRepository.sumBilanzKomponentenPerZeitBetween(
                von.plusMinutes(INTERVALL_MINUTEN), bis.plusMinutes(INTERVALL_MINUTEN))) {
            LocalDateTime ende = (LocalDateTime) zeile[0];
            LocalDateTime beginn = ende.minusMinutes(INTERVALL_MINUTEN);
            BigDecimal gti = gtiJeZeit.get(beginn);
            if (gti == null) {
                continue;
            }
            // Zaehlerwert PLUS Netto-Speicherfluss: Was in die Batterie ging, hat die Anlage
            // erzeugt, auch wenn es am Erzeugungszaehler vorbeilief. Ohne Speicher ist der
            // Zuschlag 0 und es bleibt beim Zaehlerwert.
            BigDecimal erzeugung = alsBigDecimal(zeile[1])
                    .add(speicherJeZeit.getOrDefault(ende, BigDecimal.ZERO));
            // Eine negative Summe waere keine Erzeugung. Sie kann entstehen, wenn die Batterie
            // entlaedt, waehrend die Sonne schon scheint und der Zaehler wenig sieht.
            if (erzeugung.signum() < 0) {
                erzeugung = BigDecimal.ZERO;
            }
            summeErzeugung = summeErzeugung.add(erzeugung);
            summeGti = summeGti.add(gti);
        }

        if (summeGti.signum() <= 0 || summeErzeugung.signum() <= 0) {
            return null;
        }
        return summeErzeugung.divide(summeGti, 8, RoundingMode.HALF_UP);
    }

    /**
     * Netto-Speicherfluss je Intervall: {@code ladung − entladung}, geschlüsselt nach
     * Intervall<b>ende</b> (wie {@code messwerte.zeit}).
     *
     * <p>Leer, wenn keine {@code SPEICHER}-Einheit erfasst ist — dann bleibt es beim blossen
     * Zählerwert, was ohne Speicher auch richtig ist.
     */
    private Map<LocalDateTime, BigDecimal> speicherflussJeIntervall(LocalDateTime von,
                                                                    LocalDateTime bis) {
        Map<LocalDateTime, BigDecimal> fluss = new HashMap<>();
        if (!einheitRepository.existsByTyp(EinheitTyp.SPEICHER)) {
            return fluss;
        }
        for (Object[] zeile : messwerteRepository.sumLadungEntladungPerZeitBetween(
                EinheitTyp.SPEICHER, von, bis)) {
            LocalDateTime zeit = (LocalDateTime) zeile[0];
            fluss.put(zeit, alsBigDecimal(zeile[1]).subtract(alsBigDecimal(zeile[2])));
        }
        return fluss;
    }

    private BigDecimal alsBigDecimal(Object wert) {
        if (wert == null) {
            return BigDecimal.ZERO;
        }
        return wert instanceof BigDecimal b ? b : BigDecimal.valueOf(((Number) wert).doubleValue());
    }

    private void pruefeFeatureFlag(Long orgId) {
        if (!featureFlagService.isEnabled(orgId, FeatureFlag.EINSPEISESTEUERUNG)) {
            log.warn("Prognose rejected - feature disabled for org: {}", orgId);
            throw new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT");
        }
    }
}
