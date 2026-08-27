package ch.nacht.service;

import ch.nacht.dto.PreiszeitreiheDownloadDTO;
import ch.nacht.dto.PreiszeitreihePunktDTO;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Preiszeitreihe;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.PreiszeitreiheRepository;
import ch.nacht.util.PreiszeitreiheZeit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Die Preiszeitreihe fuer die Maske (Specs/Preiszeitreihe.md).
 *
 * <p>Jede oeffentliche Methode prueft zuerst das Feature-Flag {@code PREISZEITREIHE}. Ohne diese
 * Pruefung waere der Flag reine Kosmetik: Das Panel bliebe verborgen, die API aber ueber jeden
 * HTTP-Client erreichbar. Eine ArchUnit-Regel haelt das fest.
 *
 * <p><b>Kein Mandantenfilter:</b> Die Zeitreihe traegt bewusst kein {@code org_id} — die Preise sind
 * fuer alle Mandanten identisch (FR-2). Deshalb steht hier auch kein
 * {@code hibernateFilterService.enableOrgFilter()}: Es gaebe nichts zu filtern.
 */
@Service
public class PreiszeitreiheService {

    private static final Logger log = LoggerFactory.getLogger(PreiszeitreiheService.class);

    /** Obergrenze der abfragbaren Spanne. Ohne sie zieht ein getippter Bereich die ganze Historie. */
    private static final int MAX_TAGE = 366;

    private final PreiszeitreiheRepository preiszeitreiheRepository;
    private final PreiszeitreiheAbrufService abrufService;
    private final FeatureFlagService featureFlagService;
    private final OrganizationContextService organizationContextService;

    public PreiszeitreiheService(PreiszeitreiheRepository preiszeitreiheRepository,
                                 PreiszeitreiheAbrufService abrufService,
                                 FeatureFlagService featureFlagService,
                                 OrganizationContextService organizationContextService) {
        this.preiszeitreiheRepository = preiszeitreiheRepository;
        this.abrufService = abrufService;
        this.featureFlagService = featureFlagService;
        this.organizationContextService = organizationContextService;
        log.info("PreiszeitreiheService initialized");
    }

    /**
     * Werte einer Spanne, aufsteigend, in Ortszeit.
     *
     * @param von erster Tag (einschliesslich, Ortszeit)
     * @param bis letzter Tag (einschliesslich, Ortszeit)
     * @return Punkte der Spanne; leere Liste, wenn nichts vorliegt
     * @throws IllegalArgumentException bei fehlenden, vertauschten oder zu weit gespannten Daten
     */
    @Transactional(readOnly = true)
    public List<PreiszeitreihePunktDTO> getPunkte(LocalDate von, LocalDate bis) {
        pruefeFeatureFlag();
        pruefeSpanne(von, bis);

        LocalDateTime vonUtc = PreiszeitreiheZeit.tagesbeginnUtc(von);
        LocalDateTime bisUtc = PreiszeitreiheZeit.tagesendeUtc(bis);
        List<Preiszeitreihe> werte = preiszeitreiheRepository.findByZeitraum(vonUtc, bisUtc);
        log.info("Preiszeitreihe: {} Punkte fuer {} bis {}", werte.size(), von, bis);

        return werte.stream()
                .map(w -> new PreiszeitreihePunktDTO(
                        PreiszeitreiheZeit.nachOrtszeit(w.getZeitVon()), w.getPreis()))
                .toList();
    }

    /**
     * Holt die Preise jetzt (Schaltflaeche „Herunterladen").
     *
     * <p>Derselbe Weg wie der geplante Job — die Beschaffung steht in
     * {@link PreiszeitreiheAbrufService}, damit sie nicht zweimal existiert.
     *
     * @return Zaehlwerte des Abrufs
     */
    public PreiszeitreiheDownloadDTO download() {
        pruefeFeatureFlag();
        return abrufService.abrufen();
    }

    /**
     * Wirft, wenn der Feature-Flag {@code PREISZEITREIHE} fuer den Mandanten aus ist.
     *
     * <p>Ohne diese Pruefung wuerde der Flag nur das Panel verbergen; die API bliebe offen.
     */
    private void pruefeFeatureFlag() {
        Long orgId = organizationContextService.getCurrentOrgId();
        if (!featureFlagService.isEnabled(orgId, FeatureFlag.PREISZEITREIHE)) {
            log.warn("Preiszeitreihe rejected - feature disabled for org: {}", orgId);
            throw new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT");
        }
    }

    private void pruefeSpanne(LocalDate von, LocalDate bis) {
        if (von == null || bis == null) {
            throw new IllegalArgumentException("Datum von und bis sind Pflicht");
        }
        if (von.isAfter(bis)) {
            throw new IllegalArgumentException("Datum von muss vor oder gleich Datum bis liegen");
        }
        if (von.plusDays(MAX_TAGE).isBefore(bis)) {
            throw new IllegalArgumentException(
                    "Der Zeitraum darf höchstens " + MAX_TAGE + " Tage umfassen");
        }
    }
}
