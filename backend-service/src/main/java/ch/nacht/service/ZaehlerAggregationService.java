package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import ch.nacht.entity.Quelle;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Scheduled-Aggregations-Job der MQTT-Integration (FR-6). Bildet je Einheit und
 * 15-Minuten-Intervall die Differenz der absoluten Zählerstände (pro Register), schreibt
 * vorzeichenbehaftete {@code total = ΔBezug − ΔEinspeisung} in {@code messwerte}
 * (Consumer: {@code zev = 0}; Producer: {@code zev = total}; {@code quelle = MQTT}) und
 * markiert die Rohdaten als verarbeitet.
 *
 * <p>NUR aktiv mit Spring-Profil {@code mqtt}. Kein Request-Scope: {@code org_id} wird
 * explizit aus den Rohdaten/der Einheit übernommen (kein {@code orgFilter}).
 */
@Service
@Profile("mqtt")
public class ZaehlerAggregationService {

    private static final Logger log = LoggerFactory.getLogger(ZaehlerAggregationService.class);
    private static final int INTERVALL_MINUTEN = 15;
    private static final int MAX_INTERVALLE = 10_000; // Schutz gegen Runaway-Catch-up

    private final ZaehlerRohdatenRepository rohdatenRepository;
    private final MesswerteRepository messwerteRepository;
    private final EinheitRepository einheitRepository;
    private final MqttMetrics metrics;

    public ZaehlerAggregationService(ZaehlerRohdatenRepository rohdatenRepository,
                                     MesswerteRepository messwerteRepository,
                                     EinheitRepository einheitRepository,
                                     MqttMetrics metrics) {
        this.rohdatenRepository = rohdatenRepository;
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
        this.metrics = metrics;
    }

    // Läuft 5 Minuten nach jeder Viertelstunde (:05/:20/:35/:50), damit spät eintreffende
    // MQTT-Nachrichten des gerade abgeschlossenen Quartals noch enthalten sind. Die verarbeiteten
    // Intervallgrenzen bleiben quartalsgenau (:00/:15/:30/:45) – dafür sorgt floorAufQuartal().
    @Scheduled(cron = "0 5,20,35,50 * * * *")
    @Transactional
    public void aggregiere() {
        metrics.recordAggregationRun();
        // Lokale Zeit – konsistent mit den lokal gespeicherten Rohdaten-Zeitstempeln
        // (Pi sendet lokale Zeit mit Offset, verbatim übernommen) und dem messwerte-Raster.
        // Voraussetzung: Backend/Container läuft in der lokalen Zone (TZ=Europe/Zurich).
        LocalDateTime jetzt = LocalDateTime.now();
        log.info("Aggregation start}");
        LocalDateTime letzteGrenze = floorAufQuartal(jetzt); // letztes abgeschlossenes Intervallende
        int erzeugt = 0;

        for (Long einheitId : rohdatenRepository.findEinheitIdsWithUnverarbeitet()) {
            Einheit einheit = einheitRepository.findById(einheitId).orElse(null);
            if (einheit == null) {
                log.warn("Aggregation: Einheit {} nicht gefunden – übersprungen", einheitId);
                continue;
            }

            Optional<ZaehlerRohdaten> earliestOpt =
                    rohdatenRepository.findFirstByEinheitIdAndVerarbeitetFalseOrderByZeitAsc(einheitId);
            if (earliestOpt.isEmpty()) {
                continue;
            }

            LocalDateTime intervallEnde = ceilAufQuartal(earliestOpt.get().getZeit());
            int schutz = 0;
            while (!intervallEnde.isAfter(letzteGrenze) && schutz++ < MAX_INTERVALLE) {
                LocalDateTime intervallStart = intervallEnde.minusMinutes(INTERVALL_MINUTEN);
                log.info("Aggregation. Einheit: {}, Intervall: {} - {}", einheitId, intervallStart, intervallEnde);
                if (rohdatenRepository.existsByEinheitIdAndZeitGreaterThanAndZeitLessThanEqual(
                        einheitId, intervallStart, intervallEnde)) {
                    if (verarbeiteIntervall(einheit, intervallStart, intervallEnde)) {
                        erzeugt++;
                    }
                    rohdatenRepository.markVerarbeitet(einheitId, intervallEnde, jetzt);
                }
                intervallEnde = intervallEnde.plusMinutes(INTERVALL_MINUTEN);
            }
        }

        if (erzeugt > 0) {
            log.info("Aggregation: {} Messwerte erzeugt (bis {})", erzeugt, letzteGrenze);
        }
    }

    /**
     * Bildet die Register-Deltas über die Intervallgrenze und schreibt (Upsert) den Messwert.
     * Reset-Guard pro Register (Δ < 0 → 0). {@code total} ist vorzeichenbehaftet.
     *
     * @return true, wenn ein Messwert erzeugt/aktualisiert wurde
     */
    private boolean verarbeiteIntervall(Einheit einheit, LocalDateTime start, LocalDateTime ende) {
        Long einheitId = einheit.getId();
        ZaehlerRohdaten referenz = rohdatenRepository
                .findFirstByEinheitIdAndZeitLessThanEqualOrderByZeitDesc(einheitId, start).orElse(null);
        ZaehlerRohdaten letzter = rohdatenRepository
                .findFirstByEinheitIdAndZeitLessThanEqualOrderByZeitDesc(einheitId, ende).orElse(null);

        if (referenz == null || letzter == null) {
            // Kein Referenz-/Basiswert (erste Messung) – nur Baseline, kein Messwert
            return false;
        }

        BigDecimal deltaBezug = nichtNegativ(
                letzter.getZaehlerstandBezug().subtract(referenz.getZaehlerstandBezug()),
                einheitId, "Bezug", ende);
        BigDecimal deltaEinspeisung = nichtNegativ(
                letzter.getZaehlerstandEinspeisung().subtract(referenz.getZaehlerstandEinspeisung()),
                einheitId, "Einspeisung", ende);

        double total = deltaBezug.subtract(deltaEinspeisung).doubleValue();
        upsertMesswert(einheit, ende, total);
        return true;
    }

    private void upsertMesswert(Einheit einheit, LocalDateTime zeit, double total) {
        Messwerte messwert = messwerteRepository.findByEinheitAndZeit(einheit, zeit).orElse(null);
        if (messwert == null) {
            messwert = new Messwerte();
            messwert.setEinheit(einheit);
            messwert.setZeit(zeit);
            messwert.setOrgId(einheit.getOrgId());
        } else if (messwert.getQuelle() == Quelle.CSV) {
            log.warn("Aggregation: MQTT überschreibt bestehenden CSV-Messwert (einheit={}, zeit={})",
                    einheit.getId(), zeit);
        }
        messwert.setTotal(total);
        // Produzenten: zev = total, damit die Statistik die Produktion korrekt ausweist
        // (der gesamte Produktions-total zählt als ZEV-relevant). Consumer: zev = 0 (Sentinel);
        // die Solarverteilung setzt dort später zev = zev_calculated (FR-9).
        messwert.setZev(einheit.getTyp() == EinheitTyp.PRODUCER ? total : 0.0);
        messwert.setQuelle(Quelle.MQTT); // zev_calculated bleibt null bis zur Solarverteilung
        messwerteRepository.save(messwert);
    }

    private BigDecimal nichtNegativ(BigDecimal delta, Long einheitId, String register, LocalDateTime ende) {
        if (delta.signum() < 0) {
            log.warn("Aggregation: Rücksprung bei {} (einheit={}, ende={}) – Delta auf 0 gesetzt",
                    register, einheitId, ende);
            return BigDecimal.ZERO;
        }
        return delta;
    }

    /** Grösstes Quartals-Ende {@code <= t} (abgeschlossenes Intervall). */
    private LocalDateTime floorAufQuartal(LocalDateTime t) {
        LocalDateTime m = t.truncatedTo(ChronoUnit.MINUTES);
        return m.minusMinutes(m.getMinute() % INTERVALL_MINUTEN);
    }

    /** Kleinstes Quartals-Ende {@code >= t} (Intervall, das die Messung abschliesst). */
    private LocalDateTime ceilAufQuartal(LocalDateTime t) {
        LocalDateTime unten = floorAufQuartal(t);
        return unten.equals(t) ? unten : unten.plusMinutes(INTERVALL_MINUTEN);
    }
}
