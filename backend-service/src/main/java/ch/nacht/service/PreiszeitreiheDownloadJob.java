package ch.nacht.service;

import ch.nacht.entity.FeatureFlag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Holt die dynamischen Einspeisepreise taeglich (Specs/Preiszeitreihe.md, FR-1).
 *
 * <p>Der taegliche Lauf ist keine Bequemlichkeit: Die Quelle liefert nur das laufende Fenster von
 * rund 24 Stunden und <b>keine Historie</b>. Jeder Tag ohne Abruf ist eine dauerhafte Luecke in der
 * Reihe.
 *
 * <p><b>{@code @Component}, nicht {@code @Service}:</b> Die ArchUnit-Regel
 * {@code servicesShouldEndWithService} prueft {@code @Service}-Klassen auf das Namenssuffix. Vorbild
 * ist {@link SystemmeldungCleanupJob}.
 *
 * <p>Der Job prueft das Feature-Flag nicht ueber einen Mandantenkontext — den hat er nicht. Er
 * ermittelt die Mandanten mit aktivem Flag und laeuft nur, wenn es mindestens einen gibt; damit ruft
 * eine Installation, die das Feature nicht nutzt, auch keine Fremd-API auf.
 */
@Component
public class PreiszeitreiheDownloadJob {

    private static final Logger log = LoggerFactory.getLogger(PreiszeitreiheDownloadJob.class);

    private final PreiszeitreiheAbrufService abrufService;
    private final FeatureFlagService featureFlagService;

    public PreiszeitreiheDownloadJob(PreiszeitreiheAbrufService abrufService,
                                     FeatureFlagService featureFlagService) {
        this.abrufService = abrufService;
        this.featureFlagService = featureFlagService;
    }

    /**
     * Taeglicher Abruf, per Default um 02:00 (konfigurierbar).
     *
     * <p>Faengt jede Ausnahme: Ein geworfener Fehler wuerde den Scheduler-Thread nicht toeten, aber
     * als Stacktrace ohne Zusammenhang im Log landen. Die Meldung an den Benutzer hat
     * {@link PreiszeitreiheAbrufService} bereits erfasst (Systemmeldung, FR-7).
     */
    @Scheduled(cron = "${preiszeitreihe.download.cron:0 0 2 * * *}")
    public void hole() {
        List<Long> orgIds = featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE);
        if (orgIds.isEmpty()) {
            log.debug("Preiszeitreihe: kein Mandant hat das Feature aktiv - kein Abruf");
            return;
        }
        try {
            abrufService.abrufen();
        } catch (RuntimeException e) {
            log.warn("Preiszeitreihe: geplanter Abruf fehlgeschlagen - {}", e.getMessage());
        }
    }
}
