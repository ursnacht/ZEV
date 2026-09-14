package ch.nacht.service;

import ch.nacht.entity.FeatureFlag;
import ch.nacht.util.PreiszeitreiheZeit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Viertelstündlicher Lauf der Einspeisesteuerung (Specs/Einspeisesteuerung.md, FR-1).
 *
 * <p><b>Eine Minute nach der Aggregierung</b> (Vorgabe {@code 0 6,21,36,51 * * * *}). Der
 * Zeitpunkt hängt nicht am Intervallende, sondern an
 * {@code ZaehlerAggregationService.aggregiere()}, das um {@code :05/:20/:35/:50} läuft und die
 * Messwerte des gerade abgeschlossenen Quartals erst dort schreibt.
 *
 * <p><b>Warum das zählt:</b> Eine frühere Fassung lief um {@code :01} — vier Minuten <i>vor</i> der
 * Aggregierung. Sie las damit Messwerte, die es noch nicht gab, und der Entscheid bezog sich
 * faktisch auf ein altes Intervall. Von aussen sah es aus, als hinke die Steuerung um eine
 * Viertelstunde nach.
 *
 * <p><b>Es wird nichts geschaltet.</b> Der Lauf schreibt Entscheide; einen Schreibpfad zur Anlage
 * gibt es nicht.
 */
@Component
public class SteuerungJob {

    private static final Logger log = LoggerFactory.getLogger(SteuerungJob.class);

    private static final int INTERVALL_MINUTEN = 15;

    private final SteuerungService steuerungService;
    private final FeatureFlagService featureFlagService;

    public SteuerungJob(SteuerungService steuerungService, FeatureFlagService featureFlagService) {
        this.steuerungService = steuerungService;
        this.featureFlagService = featureFlagService;
    }

    /**
     * Wertet das zuletzt abgeschlossene Intervall aus — je Mandant mit aktivem Flag.
     *
     * <p><b>Fängt jede Ausnahme je Mandant.</b> Ein Fehler bei einem Mandanten darf die übrigen
     * nicht mitnehmen: Sie teilen sich denselben Lauf, aber nicht ihre Daten. Eine geworfene
     * Ausnahme landete ausserdem als Stacktrace ohne Zusammenhang im Log.
     *
     * <p><b>Keine Systemmeldung je Fehlschlag.</b> Bei 96 Läufen am Tag wäre das eine Flut; der
     * nächste Lauf versucht es ohnehin erneut, und ein dauerhaft gestörter Zustand fällt in der
     * Tagesansicht als Lücke auf.
     */
    @Scheduled(cron = "${einspeisesteuerung.job.cron:0 6,21,36,51 * * * *}")
    public void werteAus() {
        log.info("Steuerung start");
        List<Long> orgIds = featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.EINSPEISESTEUERUNG);
        if (orgIds.isEmpty()) {
            log.info("Steuerung: kein Mandant hat das Feature aktiv - kein Lauf");
            return;
        }

        LocalDateTime intervall = letztesAbgeschlossenesIntervall();
        int erzeugt = 0;
        for (Long orgId : orgIds) {
            log.info("Steuerung. Org: {}, Intervall (Ortszeit): {} - {}",
                    orgId, intervall, intervall.plusMinutes(INTERVALL_MINUTEN));
            try {
                steuerungService.werteIntervallAus(orgId, intervall);
                erzeugt++;
            } catch (Exception e) {
                log.error("Steuerung: Lauf fuer org={} zeit={} fehlgeschlagen: {}",
                        orgId, intervall, e.getMessage(), e);
            }
        }

        if (erzeugt > 0) {
            log.info("Steuerung: {} Entscheide erzeugt (Intervall {} Ortszeit)", erzeugt, intervall);
        }
    }

    /**
     * Beginn des zuletzt <b>aggregierten</b> Intervalls, in Ortszeit.
     *
     * <p>Läuft der Job um 12:06, hat die Aggregierung um 12:05 das Intervall 11:45–12:00
     * geschrieben. Ausgewertet wird also dieses — sein Beginn ist 11:45.
     *
     * <p><b>Durchgehend Ortszeit</b> (seit V147): Die Viertelstundengrenzen sind Grenzen der
     * Ortszeit — dort laufen Aggregierung und Messraster —, und der Entscheid wird ebenso
     * gespeichert. Die frühere Rückrechnung nach UTC entfällt; damit auch die Gelegenheit, sie zu
     * vergessen.
     *
     * <p>Ausdrücklich {@code now(ZONE)} und nicht {@code now()}: Letzteres hinge an der Zeitzone
     * der JVM und ginge still daneben, sobald der Container anders konfiguriert ist.
     */
    private LocalDateTime letztesAbgeschlossenesIntervall() {
        LocalDateTime jetzt = LocalDateTime.now(PreiszeitreiheZeit.ZONE)
                .truncatedTo(ChronoUnit.MINUTES);
        int minute = jetzt.getMinute();
        LocalDateTime beginnDesLaufenden = jetzt.withMinute(minute - minute % INTERVALL_MINUTEN);
        return beginnDesLaufenden.minusMinutes(INTERVALL_MINUTEN);
    }
}
