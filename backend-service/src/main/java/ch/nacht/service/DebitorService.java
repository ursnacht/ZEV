package ch.nacht.service;

import ch.nacht.dto.DebitorDTO;
import ch.nacht.entity.Debitor;
import ch.nacht.entity.Debitorherkunft;
import ch.nacht.repository.DebitorRepository;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MieterEinheitRepository;
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
import java.util.stream.Collectors;

/**
 * Service for managing debitor entries (invoice tracking).
 */
@Service
public class DebitorService {

    private static final Logger log = LoggerFactory.getLogger(DebitorService.class);

    private final DebitorRepository debitorRepository;
    private final MieterRepository mieterRepository;
    private final EinheitRepository einheitRepository;
    private final MieterEinheitRepository mieterEinheitRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public DebitorService(DebitorRepository debitorRepository,
                          MieterRepository mieterRepository,
                          EinheitRepository einheitRepository,
                          MieterEinheitRepository mieterEinheitRepository,
                          OrganizationContextService organizationContextService,
                          HibernateFilterService hibernateFilterService) {
        this.debitorRepository = debitorRepository;
        this.mieterRepository = mieterRepository;
        this.einheitRepository = einheitRepository;
        this.mieterEinheitRepository = mieterEinheitRepository;
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
        return debitorRepository.findFirstById(id).map(this::toDTO);
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
        debitor.setHerkunft(herkunftOderZev(dto));
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
        Debitor debitor = debitorRepository.findFirstById(id)
                .orElseThrow(() -> new NoSuchElementException("Debitor not found: " + id));
        validate(dto);
        debitor.setMieterId(dto.getMieterId());
        debitor.setBetrag(dto.getBetrag());
        debitor.setDatumVon(dto.getDatumVon());
        debitor.setDatumBis(dto.getDatumBis());
        debitor.setZahldatum(dto.getZahldatum());
        debitor.setHerkunft(herkunftOderZev(dto));
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
     * <p>Das Upsert ist <b>je Herkunft</b> idempotent: Ein wiederholter Lauf aktualisiert seine
     * eigene Forderung und laesst die der anderen Herkunft unberuehrt
     * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-5).
     *
     * @param mieterId FK to mieter
     * @param betrag   Invoice amount in CHF
     * @param datumVon Start of billing period
     * @param datumBis End of billing period
     * @param herkunft Herkunft der Forderung
     */
    @Transactional
    public void upsertFromRechnung(Long mieterId, BigDecimal betrag, LocalDate datumVon, LocalDate datumBis,
                                   Debitorherkunft herkunft) {
        Long orgId = organizationContextService.getCurrentOrgId();
        debitorRepository.upsert(mieterId, betrag, datumVon, datumBis, orgId, herkunft.name());
        log.info("Upserted debitor for mieterId={}, datumVon={}, betrag={}, herkunft={}",
                mieterId, datumVon, betrag, herkunft);
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

    /**
     * Herkunft aus dem Request oder {@code ZEV}
     * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-6).
     *
     * <p>Fehlt das Feld, gilt {@code ZEV}: Bestehende Aufrufer bleiben gueltig, und der Bestand ist
     * ohnehin aus der Stromabrechnung entstanden. Ein <b>unbekannter</b> Wert kommt hier nicht an —
     * {@code DebitorDTO.herkunft} ist als Enum typisiert, Jackson weist ihn mit {@code 400} ab,
     * bevor der Service laeuft. Der Rueckfall deckt also nur das Fehlen und macht aus einem
     * Tippfehler nicht stillschweigend eine ZEV-Forderung.
     */
    private Debitorherkunft herkunftOderZev(DebitorDTO dto) {
        return dto.getHerkunft() != null ? dto.getHerkunft() : Debitorherkunft.ZEV;
    }

    private DebitorDTO toDTO(Debitor d) {
        DebitorDTO dto = new DebitorDTO();
        dto.setId(d.getId());
        dto.setMieterId(d.getMieterId());
        dto.setBetrag(d.getBetrag());
        dto.setDatumVon(d.getDatumVon());
        dto.setDatumBis(d.getDatumBis());
        dto.setZahldatum(d.getZahldatum());
        dto.setHerkunft(d.getHerkunft());
        mieterRepository.findFirstById(d.getMieterId()).ifPresent(m -> {
            dto.setMieterName(m.getName());
            // Ein Mieter kann mehreren Einheiten zugeordnet sein (Wohnung + Ladestation(en)) -
            // fuer die Debitorenanzeige werden alle Namen genannt.
            String einheiten = mieterEinheitRepository.findEinheitIdsByMieterId(m.getId()).stream()
                    .map(einheitRepository::findFirstById)
                    .filter(Optional::isPresent)
                    .map(e -> e.get().getName())
                    .collect(Collectors.joining(", "));
            dto.setEinheitName(einheiten.isEmpty() ? null : einheiten);
        });
        return dto;
    }
}
