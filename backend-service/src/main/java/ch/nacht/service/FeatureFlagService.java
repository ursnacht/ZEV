package ch.nacht.service;

import ch.nacht.dto.FeatureFlagDTO;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.repository.OrganisationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service für die Auflösung und Verwaltung von Feature-Flags pro Mandant.
 * <p>
 * Effektiver Wert = mandantenspezifische Überschreibung (falls vorhanden), sonst globaler
 * Default aus {@link FeatureFlag}, sonst {@code false}. Überschreibungen liegen in der
 * JSONB-Spalte {@code feature_flags} der {@code organisation}-Tabelle. Die effektiven Flags
 * werden pro {@code orgId} in Caffeine gecacht; Schreiboperationen invalidieren den Eintrag.
 */
@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    private final OrganisationRepository organisationRepository;
    private final OrganizationContextService organizationContextService;

    public FeatureFlagService(OrganisationRepository organisationRepository,
                              OrganizationContextService organizationContextService) {
        this.organisationRepository = organisationRepository;
        this.organizationContextService = organizationContextService;
    }

    /**
     * Liefert alle deklarierten Flags mit ihrem effektiven Wert für den angegebenen Mandanten.
     * Ergebnis wird pro {@code orgId} gecacht (Cache {@code featureFlags}).
     *
     * @param orgId interne Organisations-ID
     * @return Map technischer Key → effektiver Boolean-Wert
     */
    @Cacheable(value = "featureFlags", key = "#orgId")
    @Transactional(readOnly = true)
    public Map<String, Boolean> getEffectiveFlags(Long orgId) {
        Map<String, Boolean> overrides = loadOverrides(orgId);
        Map<String, Boolean> effective = new HashMap<>();
        for (FeatureFlag flag : FeatureFlag.values()) {
            Boolean override = overrides.get(flag.name());
            effective.put(flag.name(), override != null ? override : flag.isDefaultEnabled());
        }
        return effective;
    }

    /**
     * Liefert die Admin-Sicht aller deklarierten Flags (Default, effektiver Wert, Quelle)
     * für den angegebenen Mandanten.
     */
    @Transactional(readOnly = true)
    public List<FeatureFlagDTO> getAdminFlags(Long orgId) {
        Map<String, Boolean> overrides = loadOverrides(orgId);
        List<FeatureFlagDTO> result = new ArrayList<>();
        for (FeatureFlag flag : FeatureFlag.values()) {
            Boolean override = overrides.get(flag.name());
            boolean effektiv = override != null ? override : flag.isDefaultEnabled();
            FeatureFlagDTO.Quelle quelle = override != null
                    ? FeatureFlagDTO.Quelle.OVERRIDE
                    : FeatureFlagDTO.Quelle.DEFAULT;
            result.add(new FeatureFlagDTO(flag.name(), flag.getBeschreibungKey(),
                    flag.isDefaultEnabled(), effektiv, quelle));
        }
        return result;
    }

    /**
     * Prüft, ob ein Flag für den angegebenen Mandanten effektiv aktiv ist.
     *
     * @param orgId interne Organisations-ID
     * @param flag  zu prüfendes Flag
     * @return {@code true}, wenn aktiv (Überschreibung oder Default)
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(Long orgId, FeatureFlag flag) {
        Boolean override = loadOverrides(orgId).get(flag.name());
        return override != null ? override : flag.isDefaultEnabled();
    }

    /**
     * Setzt eine mandantenspezifische Überschreibung und invalidiert den Cache.
     *
     * @param orgId   interne Organisations-ID
     * @param key     technischer Flag-Key
     * @param enabled neuer Wert
     * @throws IllegalArgumentException wenn der Key unbekannt ist
     */
    @CacheEvict(value = "featureFlags", key = "#orgId")
    @Transactional
    public void setOverride(Long orgId, String key, boolean enabled) {
        FeatureFlag flag = FeatureFlag.fromKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unbekanntes Feature-Flag: " + key));
        var org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalStateException("Organisation nicht gefunden: " + orgId));

        Map<String, Boolean> overrides = org.getFeatureFlags() != null
                ? new HashMap<>(org.getFeatureFlags())
                : new HashMap<>();
        overrides.put(flag.name(), enabled);
        org.setFeatureFlags(overrides);
        organisationRepository.save(org);
        log.info("Feature-Flag-Überschreibung gesetzt: org={}, flag={}, enabled={}", orgId, flag.name(), enabled);
    }

    /**
     * Entfernt eine mandantenspezifische Überschreibung (Flag fällt auf den Default zurück)
     * und invalidiert den Cache.
     *
     * @param orgId interne Organisations-ID
     * @param key   technischer Flag-Key
     * @throws IllegalArgumentException wenn der Key unbekannt ist
     */
    @CacheEvict(value = "featureFlags", key = "#orgId")
    @Transactional
    public void removeOverride(Long orgId, String key) {
        FeatureFlag flag = FeatureFlag.fromKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unbekanntes Feature-Flag: " + key));
        var org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalStateException("Organisation nicht gefunden: " + orgId));

        if (org.getFeatureFlags() != null && org.getFeatureFlags().containsKey(flag.name())) {
            Map<String, Boolean> overrides = new HashMap<>(org.getFeatureFlags());
            overrides.remove(flag.name());
            org.setFeatureFlags(overrides.isEmpty() ? null : overrides);
            organisationRepository.save(org);
            log.info("Feature-Flag-Überschreibung entfernt: org={}, flag={}", orgId, flag.name());
        } else {
            log.warn("Keine Überschreibung zum Entfernen gefunden: org={}, flag={}", orgId, flag.name());
        }
    }

    /**
     * Liefert die aktuelle Organisations-ID aus dem Request-Kontext (JWT).
     */
    public Long getCurrentOrgId() {
        return organizationContextService.getCurrentOrgId();
    }

    /**
     * Lädt die Überschreibungs-Map eines Mandanten robust.
     * Bei fehlender Organisation, NULL-Spalte oder korruptem JSON wird eine leere Map
     * zurückgegeben (es gelten die globalen Defaults) – die Anwendung stürzt nicht ab.
     */
    private Map<String, Boolean> loadOverrides(Long orgId) {
        try {
            return organisationRepository.findById(orgId)
                    .map(org -> org.getFeatureFlags())
                    .filter(flags -> flags != null)
                    .orElseGet(Collections::emptyMap);
        } catch (Exception e) {
            log.error("Feature-Flags konnten nicht gelesen werden (org={}), Rückfall auf Defaults", orgId, e);
            return Collections.emptyMap();
        }
    }
}
