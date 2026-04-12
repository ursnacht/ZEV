package ch.nacht.service;

import ch.nacht.dto.DebitorDTO;
import ch.nacht.entity.Debitor;
import ch.nacht.repository.DebitorRepository;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MieterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Service for managing debitor entries (invoice tracking).
 */
@Service
public class DebitorService {

    private static final Logger log = LoggerFactory.getLogger(DebitorService.class);

    private final DebitorRepository debitorRepository;
    private final MieterRepository mieterRepository;
    private final EinheitRepository einheitRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public DebitorService(DebitorRepository debitorRepository,
                          MieterRepository mieterRepository,
                          EinheitRepository einheitRepository,
                          OrganizationContextService organizationContextService,
                          HibernateFilterService hibernateFilterService) {
        this.debitorRepository = debitorRepository;
        this.mieterRepository = mieterRepository;
        this.einheitRepository = einheitRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Get all debitor entries for the given date range (quarter filter).
     *
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return List of debitor DTOs with joined mieter and einheit names
     */
    @Transactional(readOnly = true)
    public List<DebitorDTO> getDebitoren(LocalDate von, LocalDate bis) {
        hibernateFilterService.enableOrgFilter();
        log.info("Loading debitors from {} to {}", von, bis);
        return debitorRepository.findByDatumVonBetween(von, bis).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Get a debitor entry by ID.
     *
     * @param id Debitor ID
     * @return Optional containing the debitor DTO if found
     */
    @Transactional(readOnly = true)
    public Optional<DebitorDTO> getDebitorById(Long id) {
        hibernateFilterService.enableOrgFilter();
        return debitorRepository.findById(id).map(this::toDTO);
    }

    /**
     * Create a new debitor entry manually.
     *
     * @param dto Debitor data
     * @return Created debitor DTO
     */
    @Transactional
    public DebitorDTO create(DebitorDTO dto) {
        hibernateFilterService.enableOrgFilter();
        validate(dto);
        Debitor debitor = new Debitor();
        debitor.setOrgId(organizationContextService.getCurrentOrgId());
        debitor.setMieterId(dto.getMieterId());
        debitor.setBetrag(dto.getBetrag());
        debitor.setDatumVon(dto.getDatumVon());
        debitor.setDatumBis(dto.getDatumBis());
        debitor.setZahldatum(dto.getZahldatum());
        Debitor saved = debitorRepository.save(debitor);
        log.info("Created debitor entry id={} for mieterId={}", saved.getId(), saved.getMieterId());
        return toDTO(saved);
    }

    /**
     * Update an existing debitor entry.
     *
     * @param id  Debitor ID
     * @param dto Updated debitor data
     * @return Updated debitor DTO
     */
    @Transactional
    public DebitorDTO update(Long id, DebitorDTO dto) {
        hibernateFilterService.enableOrgFilter();
        Debitor debitor = debitorRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Debitor not found: " + id));
        validate(dto);
        debitor.setMieterId(dto.getMieterId());
        debitor.setBetrag(dto.getBetrag());
        debitor.setDatumVon(dto.getDatumVon());
        debitor.setDatumBis(dto.getDatumBis());
        debitor.setZahldatum(dto.getZahldatum());
        log.info("Updated debitor entry id={}", id);
        return toDTO(debitorRepository.save(debitor));
    }

    /**
     * Delete a debitor entry by ID.
     *
     * @param id Debitor ID
     * @return true if deleted, false if not found
     */
    @Transactional
    public boolean delete(Long id) {
        hibernateFilterService.enableOrgFilter();
        if (debitorRepository.existsById(id)) {
            debitorRepository.deleteById(id);
            log.info("Deleted debitor entry id={}", id);
            return true;
        }
        log.warn("Debitor not found for deletion: id={}", id);
        return false;
    }

    /**
     * Upsert a debitor entry from invoice generation.
     * Only invoices with a mieter are persisted.
     * Updates betrag/datumBis only if zahldatum is not yet set.
     *
     * @param mieterId FK to mieter
     * @param betrag   Invoice amount in CHF
     * @param datumVon Start of billing period
     * @param datumBis End of billing period
     */
    @Transactional
    public void upsertFromRechnung(Long mieterId, BigDecimal betrag, LocalDate datumVon, LocalDate datumBis) {
        Long orgId = organizationContextService.getCurrentOrgId();
        debitorRepository.upsert(mieterId, betrag, datumVon, datumBis, orgId);
        log.info("Upserted debitor for mieterId={}, datumVon={}, betrag={}", mieterId, datumVon, betrag);
    }

    private void validate(DebitorDTO dto) {
        if (dto.getMieterId() == null) {
            throw new IllegalArgumentException("Mieter ist Pflicht");
        }
        if (dto.getBetrag() == null || dto.getBetrag().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Betrag muss grösser als 0 sein");
        }
        if (dto.getDatumVon() == null || dto.getDatumBis() == null) {
            throw new IllegalArgumentException("Datum von und bis sind Pflicht");
        }
        if (dto.getDatumVon().isAfter(dto.getDatumBis())) {
            throw new IllegalArgumentException("Datum von muss vor oder gleich Datum bis liegen");
        }
        if (dto.getZahldatum() != null && dto.getZahldatum().isBefore(dto.getDatumBis())) {
            throw new IllegalArgumentException("Zahldatum darf nicht vor Datum bis liegen");
        }
    }

    private DebitorDTO toDTO(Debitor d) {
        DebitorDTO dto = new DebitorDTO();
        dto.setId(d.getId());
        dto.setMieterId(d.getMieterId());
        dto.setBetrag(d.getBetrag());
        dto.setDatumVon(d.getDatumVon());
        dto.setDatumBis(d.getDatumBis());
        dto.setZahldatum(d.getZahldatum());
        mieterRepository.findById(d.getMieterId()).ifPresent(m -> {
            dto.setMieterName(m.getName());
            einheitRepository.findById(m.getEinheitId())
                    .ifPresent(e -> dto.setEinheitName(e.getName()));
        });
        return dto;
    }
}
