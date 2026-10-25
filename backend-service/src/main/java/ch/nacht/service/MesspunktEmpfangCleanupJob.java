package ch.nacht.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Räumt verwaiste Empfangszeilen auf: ohne Einheit und seit mehr als 30 Tagen kein Empfang
 * (Specs/Messpunkt-Empfang.md, FR-3.2). Sonst sammelten sich Tippfehler aus der Inbetriebnahme
 * ({@code Wohnung-1} statt {@code Wohnung1}) für immer an.
 *
 * <p><b>Bewusst ohne {@code @Profile("mqtt")}</b> (wie {@code SystemmeldungCleanupJob}): Auch ohne
 * MQTT können noch Zeilen von früher liegen.
 */
@Component
public class MesspunktEmpfangCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MesspunktEmpfangCleanupJob.class);

    private final MesspunktEmpfangService messpunktEmpfangService;

    public MesspunktEmpfangCleanupJob(MesspunktEmpfangService messpunktEmpfangService) {
        this.messpunktEmpfangService = messpunktEmpfangService;
    }

    /** Läuft täglich (Default 03:30 Ortszeit); Cron über {@code messpunkt-empfang.retention.cron}. */
    @Scheduled(cron = "${messpunkt-empfang.retention.cron:0 30 3 * * *}", zone = "Europe/Zurich")
    public void bereinige() {
        int geloescht = messpunktEmpfangService.raeumeAuf();
        log.info("Messpunkt-Empfang: {} Zeilen ohne Einheit und ohne Empfang seit 30 Tagen gelöscht",
                geloescht);
    }
}
