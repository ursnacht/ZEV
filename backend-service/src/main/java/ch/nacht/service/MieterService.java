package ch.nacht.service;

import ch.nacht.entity.Mieter;
import ch.nacht.repository.MieterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing tenants.
 */
@Service
public class MieterService {

    private static final Logger log = LoggerFactory.getLogger(MieterService.class);

    private final MieterRepository mieterRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public MieterService(MieterRepository mieterRepository,
                         OrganizationContextService organizationContextService,
                         HibernateFilterService hibernateFilterService) {
        this.mieterRepository = mieterRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Get all tenants ordered by unit ID and lease start date.
     *
     * @return List of all tenants
     */
    @Transactional(readOnly = true)
    public List<Mieter> getAllMieter() {
        hibernateFilterService.enableOrgFilter();
        return mieterRepository.findAllByOrderByEinheitIdAscMietbeginnDesc();
    }

    /**
     * Get a tenant by ID.
     *
     * @param id Tenant ID
     * @return Optional containing the tenant if found
     */
    @Transactional(readOnly = true)
    public Optional<Mieter> getMieterById(Long id) {
        hibernateFilterService.enableOrgFilter();
        return mieterRepository.findById(id);
    }

    /**
     * Save a new or updated tenant.
     * Validates lease dates and checks for overlapping tenants.
     *
     * @param mieter Tenant to save
     * @return Saved tenant
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Mieter saveMieter(Mieter mieter) {
        hibernateFilterService.enableOrgFilter();
        log.info("Saving tenant: {}", mieter);

        // Validate: mietende must be after mietbeginn
        if (mieter.getMietende() != null && !mieter.getMietende().isAfter(mieter.getMietbeginn())) {
            throw new IllegalArgumentException("Mietende muss nach Mietbeginn liegen");
        }

        Long excludeId = mieter.getId() != null ? mieter.getId() : -1L;

        // Validate: no overlapping lease periods for the same unit
        boolean hasOverlap;
        if (mieter.getMietende() == null) {
            hasOverlap = mieterRepository.existsOverlappingMieterOpenEnded(
                    mieter.getEinheitId(),
                    mieter.getMietbeginn(),
                    excludeId);
        } else {
            hasOverlap = mieterRepository.existsOverlappingMieterBounded(
                    mieter.getEinheitId(),
                    mieter.getMietbeginn(),
                    mieter.getMietende(),
                    excludeId);
        }
        if (hasOverlap) {
            throw new IllegalArgumentException("Mietzeit überschneidet sich mit bestehendem Mieter");
        }

        // Validate: only the most recent tenant can have no mietende
        if (mieter.getMietende() == null) {
            if (mieterRepository.existsOtherMieterWithoutMietende(mieter.getEinheitId(), excludeId)) {
                throw new IllegalArgumentException("Es existiert bereits ein aktueller Mieter ohne Mietende");
            }
        }

        // Set org_id for new tenant
        if (mieter.getId() == null) {
            mieter.setOrgId(organizationContextService.getCurrentOrgId());
        }

        Mieter saved = mieterRepository.save(mieter);
        log.info("Tenant saved with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Delete a tenant by ID.
     *
     * @param id Tenant ID
     * @return true if deleted, false if not found
     */
    @Transactional
    public boolean deleteMieter(Long id) {
        hibernateFilterService.enableOrgFilter();
        if (mieterRepository.existsById(id)) {
            mieterRepository.deleteById(id);
            log.info("Deleted tenant with ID: {}", id);
            return true;
        }
        log.warn("Tenant not found for deletion: {}", id);
        return false;
    }

    /**
     * Get all tenants for a unit within a quarter (for invoice generation).
     *
     * @param einheitId Unit ID
     * @param quartalBeginn Quarter start date
     * @param quartalEnde Quarter end date
     * @return List of tenants active during the quarter
     */
    @Transactional(readOnly = true)
    public List<Mieter> getMieterForQuartal(Long einheitId, LocalDate quartalBeginn, LocalDate quartalEnde) {
        hibernateFilterService.enableOrgFilter();
        return mieterRepository.findByEinheitIdAndQuartal(einheitId, quartalBeginn, quartalEnde);
    }
}
