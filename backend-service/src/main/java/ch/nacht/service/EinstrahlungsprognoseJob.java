package ch.nacht.service;

import ch.nacht.entity.FeatureFlag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Holt die Einstrahlungsprognose stündlich (Specs/Ladeplanung.md, FR-2).
 *
 * <p><b>Stündlich, obwohl das Modell nur alle drei Stunden neu gerechnet wird.</b> Der Abruf ist
 * billig (24 je Tag und Mandant gegen 10'000 erlaubte), und ein fester Takt ist einfacher richtig
 * zu bekommen als eine Abstimmung auf fremde Modellläufe, die sich ändern kann.
 *
 * <p><b>{@code @Component}, nicht {@code @Service}:</b> Die ArchUnit-Regel
 * {@code servicesShouldEndWithService} prüft {@code @Service}-Klassen auf das Namenssuffix.
 * Vorbild ist {@link PreiszeitreiheDownloadJob}.
 *
 * <p>Der Job läuft nur für Mandanten mit aktivem Feature-Flag. Eine Installation, die die
 * Einspeisesteuerung nicht nutzt, ruft damit auch keine Fremd-API auf.
 */
@Component
public class EinstrahlungsprognoseJob {

    private static final Logger log = LoggerFactory.getLogger(EinstrahlungsprognoseJob.class);

    private final EinstrahlungsprognoseAbrufService abrufService;
    private final FeatureFlagService featureFlagService;

    public EinstrahlungsprognoseJob(EinstrahlungsprognoseAbrufService abrufService,
                                    FeatureFlagService featureFlagService) {
        this.abrufService = abrufService;
        this.featureFlagService = featureFlagService;
    }

    /**
     * Stündlicher Abruf, je Mandant mit aktivem Flag.
     *
     * <p><b>Zur Minute 40</b>, also bewusst neben dem Steuerungs-Job (Minuten 6, 21, 36, 51) und
     * neben der Aggregation. Zwei Jobs, die gleichzeitig auf dieselben Tabellen greifen, erzeugen
     * Sperren, die keiner erwartet.
     *
     * <p>Fängt jede Ausnahme <b>je Mandant</b>: Ein Fehler bei einem darf die übrigen nicht
     * mitnehmen. Die Meldung an den Benutzer hat {@link EinstrahlungsprognoseAbrufService} bereits
     * erfasst.
     */
    @Scheduled(cron = "${ladeplanung.prognose.cron:0 40 * * * *}")
    public void hole() {
        List<Long> orgIds = featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.EINSPEISESTEUERUNG);
        if (orgIds.isEmpty()) {
            log.debug("Einstrahlungsprognose: kein Mandant hat das Feature aktiv - kein Abruf");
            return;
        }
        for (Long orgId : orgIds) {
            try {
                abrufService.abrufen(orgId);
            } catch (RuntimeException e) {
                log.warn("Einstrahlungsprognose: Abruf fuer org={} fehlgeschlagen - {}",
                        orgId, e.getMessage());
            }
        }
    }
}
