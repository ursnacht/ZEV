package ch.nacht.service;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.entity.Einstellungen;
import ch.nacht.repository.EinstellungenRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing tenant-specific settings.
 */
@Service
public class EinstellungenService {

    private static final Logger log = LoggerFactory.getLogger(EinstellungenService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final EinstellungenRepository einstellungenRepository;
    private final OrganizationContextService organizationContextService;

    public EinstellungenService(EinstellungenRepository einstellungenRepository,
                                 OrganizationContextService organizationContextService) {
        this.einstellungenRepository = einstellungenRepository;
        this.organizationContextService = organizationContextService;
    }

    /**
     * Get settings for the current tenant.
     * Returns null if no settings exist yet.
     *
     * @return EinstellungenDTO or null if not configured
     */
    @Transactional(readOnly = true)
    public EinstellungenDTO getEinstellungen() {
        Long orgId = organizationContextService.getCurrentOrgId();
        log.debug("Getting settings for org: {}", orgId);

        Optional<Einstellungen> einstellungen = einstellungenRepository.findByOrgId(orgId);
        return einstellungen.map(this::toDTO).orElse(null);
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

        String konfigurationJson = toJson(dto.getRechnung());
        Optional<Einstellungen> existing = einstellungenRepository.findByOrgId(orgId);

        Einstellungen einstellungen;
        if (existing.isPresent()) {
            einstellungen = existing.get();
            einstellungen.setKonfiguration(konfigurationJson);
            log.debug("Updating existing settings");
        } else {
            einstellungen = new Einstellungen(orgId, konfigurationJson);
            log.debug("Creating new settings");
        }

        Einstellungen saved = einstellungenRepository.save(einstellungen);
        log.info("Settings saved with ID: {}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Convert entity to DTO.
     */
    private EinstellungenDTO toDTO(Einstellungen entity) {
        RechnungKonfigurationDTO konfiguration = fromJson(entity.getKonfiguration());
        return new EinstellungenDTO(entity.getId(), konfiguration);
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
