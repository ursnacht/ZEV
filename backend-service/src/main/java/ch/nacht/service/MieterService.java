package ch.nacht.service;

import ch.nacht.entity.Mieter;
import ch.nacht.entity.MieterEinheit;
import ch.nacht.repository.MieterEinheitRepository;
import ch.nacht.repository.MieterRepository;
import ch.nacht.repository.NkAkontoRepository;
import ch.nacht.repository.NkVerbrauchRepository;
import ch.nacht.repository.NkZusatzRepository;
import ch.nacht.repository.TarifpositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing tenants.
 *
 * <p>Ein Mieter kann seit {@code V105} mehreren Einheiten zugeordnet sein (Wohnung und
 * Ladestation(en), siehe {@code Specs/Ladestationen.md}). Die Zuordnung liegt in
 * {@link MieterEinheit}; {@code Mieter.einheitIds} ist nur der Transport zum Client und wird
 * ausschliesslich hier gefüllt und ausgewertet.
 */
@Service
public class MieterService {

    private static final Logger log = LoggerFactory.getLogger(MieterService.class);

    private final MieterRepository mieterRepository;
    private final MieterEinheitRepository mieterEinheitRepository;
    private final TarifpositionRepository tarifpositionRepository;
    private final NkVerbrauchRepository nkVerbrauchRepository;
    private final NkZusatzRepository nkZusatzRepository;
    private final NkAkontoRepository nkAkontoRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public MieterService(MieterRepository mieterRepository,
                         MieterEinheitRepository mieterEinheitRepository,
                         TarifpositionRepository tarifpositionRepository,
                         NkVerbrauchRepository nkVerbrauchRepository,
                         NkZusatzRepository nkZusatzRepository,
                         NkAkontoRepository nkAkontoRepository,
                         OrganizationContextService organizationContextService,
                         HibernateFilterService hibernateFilterService) {
        this.mieterRepository = mieterRepository;
        this.mieterEinheitRepository = mieterEinheitRepository;
        this.tarifpositionRepository = tarifpositionRepository;
        this.nkVerbrauchRepository = nkVerbrauchRepository;
        this.nkZusatzRepository = nkZusatzRepository;
        this.nkAkontoRepository = nkAkontoRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Get all tenants ordered by name and lease start date.
     *
     * @return List of all tenants
     */
    @Transactional(readOnly = true)
    public List<Mieter> getAllMieter() {
        hibernateFilterService.enableOrgFilter();
        return ladeEinheiten(mieterRepository.findAllByOrderByNameAscMietbeginnDesc());
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
        return mieterRepository.findFirstById(id).map(this::ladeEinheiten);
    }

    /**
     * Save a new or updated tenant.
     * Validates lease dates and checks for overlapping tenants per assigned unit.
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

        // Validate: at least one unit — ohne Einheit gaebe es weder Messwerte noch eine Rechnung
        List<Long> einheitIds = new ArrayList<>(new LinkedHashSet<>(mieter.getEinheitIds()));
        if (einheitIds.isEmpty()) {
            throw new IllegalArgumentException("Mindestens eine Einheit ist erforderlich");
        }

        Long excludeId = mieter.getId() != null ? mieter.getId() : -1L;

        // Validate je zugeordneter Einheit: Der Mietzeitraum haengt am Mieter, die Belegung aber
        // an der einzelnen Einheit — eine Ueberschneidung in EINER Einheit reicht zur Ablehnung.
        for (Long einheitId : einheitIds) {
            boolean hasOverlap;
            if (mieter.getMietende() == null) {
                hasOverlap = mieterRepository.existsOverlappingMieterOpenEnded(
                        einheitId,
                        mieter.getMietbeginn(),
                        excludeId);
            } else {
                hasOverlap = mieterRepository.existsOverlappingMieterBounded(
                        einheitId,
                        mieter.getMietbeginn(),
                        mieter.getMietende(),
                        excludeId);
            }
            if (hasOverlap) {
                throw new IllegalArgumentException("Mietzeit überschneidet sich mit bestehendem Mieter");
            }

            // Validate: only the most recent tenant can have no mietende
            if (mieter.getMietende() == null
                    && mieterRepository.existsOtherMieterWithoutMietende(einheitId, excludeId)) {
                throw new IllegalArgumentException("Es existiert bereits ein aktueller Mieter ohne Mietende");
            }
        }

        // Set org_id for new tenant
        if (mieter.getId() == null) {
            mieter.setOrgId(organizationContextService.getCurrentOrgId());
        }

        Mieter saved = mieterRepository.save(mieter);
        speichereZuordnungen(saved, einheitIds);
        saved.setEinheitIds(einheitIds);
        log.info("Tenant saved with ID: {} (Einheiten: {})", saved.getId(), einheitIds);
        return saved;
    }

    /**
     * Delete a tenant by ID.
     *
     * <p>Abgewiesen, solange an einer zugeordneten Einheit Tarifpositionen haengen: Die Positionen
     * gehoeren zwar der Einheit, verlieren mit dem Mieter aber ihren Rechnungsempfaenger und
     * blieben unbemerkt liegen (Specs/Ladestationen.md, §5).
     *
     * <p>Ebenso abgewiesen, wenn der Mieter in einer Nebenkostenabrechnung vorkommt
     * (Specs/Nebenkosten/Abrechnung.md, FR-5). Die Fremdschluessel stehen dort auf
     * {@code ON DELETE RESTRICT}; ohne diese Pruefung erschiene statt einer verstaendlichen
     * Meldung ein Datenbankfehler.
     *
     * @param id Tenant ID
     * @return true if deleted, false if not found
     * @throws IllegalArgumentException if positions or a billing period still reference the tenant
     */
    @Transactional
    public boolean deleteMieter(Long id) {
        hibernateFilterService.enableOrgFilter();
        if (!mieterRepository.existsById(id)) {
            log.warn("Tenant not found for deletion: {}", id);
            return false;
        }

        long positionen = mieterEinheitRepository.findEinheitIdsByMieterId(id).stream()
                .mapToLong(tarifpositionRepository::countByEinheitId)
                .sum();
        if (positionen > 0) {
            throw new IllegalArgumentException(
                    "Mieter kann nicht gelöscht werden: " + positionen + " Tarifposition(en) vorhanden");
        }

        long nebenkosten = nkVerbrauchRepository.countByMieterId(id)
                + nkZusatzRepository.countByMieterId(id)
                + nkAkontoRepository.countByMieterId(id);
        if (nebenkosten > 0) {
            throw new IllegalArgumentException(
                    "Mieter kann nicht gelöscht werden: er kommt in einer Nebenkostenabrechnung vor");
        }

        mieterRepository.deleteById(id);
        log.info("Deleted tenant with ID: {}", id);
        return true;
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
        return ladeEinheiten(mieterRepository.findByEinheitIdAndQuartal(einheitId, quartalBeginn, quartalEnde));
    }

    /**
     * Unit IDs assigned to a tenant — used by the invoice calculation to collect the positions of
     * all units of a tenant.
     *
     * @param mieterId Tenant ID
     * @return Unit IDs
     */
    @Transactional(readOnly = true)
    public List<Long> getEinheitIds(Long mieterId) {
        hibernateFilterService.enableOrgFilter();
        return mieterEinheitRepository.findEinheitIdsByMieterId(mieterId);
    }

    /** Fuellt die Einheiten-IDs eines einzelnen Mieters. */
    private Mieter ladeEinheiten(Mieter mieter) {
        mieter.setEinheitIds(mieterEinheitRepository.findEinheitIdsByMieterId(mieter.getId()));
        return mieter;
    }

    /** Fuellt die Einheiten-IDs einer ganzen Liste mit einer einzigen Abfrage. */
    private List<Mieter> ladeEinheiten(List<Mieter> mieterListe) {
        if (mieterListe.isEmpty()) {
            return mieterListe;
        }
        Map<Long, List<Long>> jeMieter = mieterEinheitRepository
                .findByMieterIdIn(mieterListe.stream().map(Mieter::getId).toList()).stream()
                .collect(Collectors.groupingBy(MieterEinheit::getMieterId,
                        Collectors.mapping(MieterEinheit::getEinheitId, Collectors.toList())));
        mieterListe.forEach(m -> m.setEinheitIds(
                new ArrayList<>(jeMieter.getOrDefault(m.getId(), List.of()))));
        return mieterListe;
    }

    /** Schreibt die Zuordnungen neu — einfacher und sicherer als ein Abgleich Zeile fuer Zeile. */
    private void speichereZuordnungen(Mieter mieter, List<Long> einheitIds) {
        mieterEinheitRepository.deleteByMieterId(mieter.getId());
        mieterEinheitRepository.flush();
        einheitIds.forEach(einheitId -> mieterEinheitRepository.save(
                new MieterEinheit(mieter.getOrgId(), mieter.getId(), einheitId)));
    }
}
