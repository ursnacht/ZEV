package ch.nacht.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Retention-Cleanup für Systemmeldungen: löscht periodisch <b>erledigte</b> Einträge, deren
 * {@code erledigt_am} älter als die konfigurierte Frist ist (Default 90 Tage). Offene Einträge
 * bleiben unberührt.
 *
 * <p><b>Bewusst ohne {@code @Profile("mqtt")}</b>: Systemmeldungen entstehen auch im manuellen
 * Lauf (ohne MQTT), daher muss die Retention in allen Umgebungen laufen.
 */
@Component
public class SystemmeldungCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SystemmeldungCleanupJob.class);

    private final SystemmeldungService systemmeldungService;
    private final int retentionTage;

    public SystemmeldungCleanupJob(SystemmeldungService systemmeldungService,
                                   @Value("${systemmeldung.retention.tage:90}") int retentionTage) {
        this.systemmeldungService = systemmeldungService;
        this.retentionTage = retentionTage;
    }

    /** Läuft täglich (Default 03:00); Cron über {@code systemmeldung.retention.cron} konfigurierbar. */
    @Scheduled(cron = "${systemmeldung.retention.cron:0 0 3 * * *}")
    public void bereinige() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionTage);
        int geloescht = systemmeldungService.loescheErledigteAelterAls(cutoff);
        log.info("Systemmeldung-Retention: {} erledigte Einträge älter als {} Tage gelöscht (cutoff={})",
                geloescht, retentionTage, cutoff);
    }
}
