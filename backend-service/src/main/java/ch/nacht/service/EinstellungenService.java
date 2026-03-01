package ch.nacht.service;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.entity.Organisation;
import ch.nacht.repository.OrganisationRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing tenant-specific settings.
 * Settings are stored as JSONB in the organisation table.
 */
@Service
public class EinstellungenService {

    private static final Logger log = LoggerFactory.getLogger(EinstellungenService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OrganisationRepository organisationRepository;
    private final OrganizationContextService organizationContextService;

    public EinstellungenService(OrganisationRepository organisationRepository,
                                OrganizationContextService organizationContextService) {
        this.organisationRepository = organisationRepository;
        this.organizationContextService = organizationContextService;
    }

    /**
     * Get settings for the current tenant.
     * Returns null if no settings exist yet (konfiguration is null).
     *
     * @return EinstellungenDTO or null if not configured
     */
    @Transactional(readOnly = true)
    public EinstellungenDTO getEinstellungen() {
        Long orgId = organizationContextService.getCurrentOrgId();
        log.debug("Getting settings for org: {}", orgId);

        return organisationRepository.findById(orgId)
                .filter(org -> org.getKonfiguration() != null)
                .map(this::toDTO)
                .orElse(null);
    }

    /**
     * Get settings for the current tenant.
     * Throws an exception if settings are not configured.
     *
     * @return EinstellungenDTO
     * @throws IllegalStateException if settings are not configured
     */
    @Transactional(readOnly = true)
    public EinstellungenDTO getEinstellungenOrThrow() {
        EinstellungenDTO dto = getEinstellungen();
        if (dto == null) {
            throw new IllegalStateException("Einstellungen sind noch nicht konfiguriert. Bitte zuerst die Einstellungen erfassen.");
        }
        return dto;
    }

    /**
     * Save or update settings for the current tenant.
     *
     * @param dto Settings to save
     * @return Saved settings
     */
    @Transactional
    public EinstellungenDTO saveEinstellungen(EinstellungenDTO dto) {
        Long orgId = organizationContextService.getCurrentOrgId();
        log.info("Saving settings for org: {}", orgId);

        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalStateException("Organisation nicht gefunden: " + orgId));

        org.setKonfiguration(toJson(dto.getRechnung()));
        Organisation saved = organisationRepository.save(org);
        log.info("Settings saved for organisation ID: {}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Convert entity to DTO.
     */
    private EinstellungenDTO toDTO(Organisation org) {
        RechnungKonfigurationDTO konfiguration = fromJson(org.getKonfiguration());
        return new EinstellungenDTO(org.getId(), konfiguration);
    }

    /**
     * Convert RechnungKonfigurationDTO to JSON string.
     */
    private String toJson(RechnungKonfigurationDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("Error converting RechnungKonfigurationDTO to JSON", e);
            throw new IllegalArgumentException("Error converting settings to JSON", e);
        }
    }

    /**
     * Convert JSON string to RechnungKonfigurationDTO.
     */
    private RechnungKonfigurationDTO fromJson(String json) {
        try {
            return objectMapper.readValue(json, RechnungKonfigurationDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to RechnungKonfigurationDTO", e);
            throw new IllegalArgumentException("Error reading settings from JSON", e);
        }
    }
}
