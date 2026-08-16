package ch.nacht.service;

import ch.nacht.entity.Erfassungsart;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import ch.nacht.repository.MieterRepository;
import ch.nacht.repository.TarifRepository;
import ch.nacht.repository.TarifpositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing manually captured tariff positions (Ladestrom and later further use cases).
 */
@Service
public class TarifpositionService {

    private static final Logger log = LoggerFactory.getLogger(TarifpositionService.class);

    private final TarifpositionRepository tarifpositionRepository;
    private final MieterRepository mieterRepository;
    private final TarifRepository tarifRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public TarifpositionService(TarifpositionRepository tarifpositionRepository,
                                MieterRepository mieterRepository,
                                TarifRepository tarifRepository,
                                OrganizationContextService organizationContextService,
                                HibernateFilterService hibernateFilterService) {
        this.tarifpositionRepository = tarifpositionRepository;
        this.mieterRepository = mieterRepository;
        this.tarifRepository = tarifRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Get all positions of a tenant.
     *
     * @param mieterId Tenant ID
     * @return List of positions, newest quarter first
     */
    @Transactional(readOnly = true)
    public List<Tarifposition> getByMieter(Long mieterId) {
        hibernateFilterService.enableOrgFilter();
        return tarifpositionRepository.findByMieterId(mieterId);
    }

    /**
     * Get the positions of a tenant whose quarter overlaps the given billing period.
     * Only positions with a quantity greater than zero are returned.
     *
     * @param mieterId Tenant ID
     * @param von Period start (inclusive)
     * @param bis Period end (inclusive)
     * @return Positions, oldest quarter first
     */
    @Transactional(readOnly = true)
    public List<Tarifposition> getFuerRechnung(Long mieterId, LocalDate von, LocalDate bis) {
        hibernateFilterService.enableOrgFilter();
        return tarifpositionRepository.findByMieterIdAndQuartalOverlapping(
                mieterId,
                von.getYear(), quartalVon(von),
                bis.getYear(), quartalVon(bis));
    }

    /**
     * Quarter (1-4) containing the given date.
     *
     * @param datum Date
     * @return Quarter number
     */
    public static int quartalVon(LocalDate datum) {
        return (datum.getMonthValue() - 1) / 3 + 1;
    }

    /**
     * First day of the given quarter.
     *
     * @param jahr Year
     * @param quartal Quarter (1-4)
     * @return First day
     */
    public static LocalDate quartalBeginn(int jahr, int quartal) {
        return LocalDate.of(jahr, (quartal - 1) * 3 + 1, 1);
    }

    /**
     * Last day of the given quarter.
     *
     * @param jahr Year
     * @param quartal Quarter (1-4)
     * @return Last day
     */
    public static LocalDate quartalEnde(int jahr, int quartal) {
        return quartalBeginn(jahr, quartal).plusMonths(3).minusDays(1);
    }

    /**
     * Get a position by ID.
     *
     * @param id Position ID
     * @return Optional containing the position if found
     */
    @Transactional(readOnly = true)
    public Optional<Tarifposition> getTarifpositionById(Long id) {
        hibernateFilterService.enableOrgFilter();
        return tarifpositionRepository.findById(id);
    }

    /**
     * Save a new or updated position.
     *
     * <p>Validates that the referenced tariff is of a manually captured type and that no other
     * position for the same tenant, quarter and tariff <b>type</b> exists. The latter rule is
     * stricter than the database constraint (which covers the exact tariff only), because two
     * different LADESTROM tariffs would otherwise bypass it.
     *
     * @param tarifposition Position to save
     * @return Saved position
     * @throws IllegalArgumentException on any validation failure
     */
    @Transactional
    public Tarifposition saveTarifposition(Tarifposition tarifposition) {
        hibernateFilterService.enableOrgFilter();
        log.info("Saving tariff position: {}", tarifposition);

        Mieter mieter = resolveMieter(tarifposition);
        Tarif tarif = resolveTarif(tarifposition);

        if (!TarifTyp.MANUELL_ERFASST.contains(tarif.getTariftyp())) {
            throw new IllegalArgumentException(
                    "Für den Tariftyp " + tarif.getTariftyp() + " können keine Positionen erfasst werden");
        }

        Long excludeId = tarifposition.getId() != null ? tarifposition.getId() : -1L;
        if (tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
                mieter.getId(), tarifposition.getJahr(), tarifposition.getQuartal(),
                TarifTyp.MANUELL_ERFASST, excludeId)) {
            throw new IllegalArgumentException(
                    "Für diesen Mieter und dieses Quartal existiert bereits eine Position");
        }

        tarifposition.setMieter(mieter);
        tarifposition.setTarif(tarif);
        if (tarifposition.getErfassungsart() == null) {
            tarifposition.setErfassungsart(Erfassungsart.MANUELL);
        }
        // Beim Update traegt das DTO keine org_id (anders als bei Entities, die direkt als JSON
        // durchgereicht werden) - sie wird aus dem bestehenden Datensatz uebernommen. `findById`
        // laeuft unter dem orgFilter, ein fremder Mandant kommt hier also gar nicht an.
        if (tarifposition.getId() == null) {
            tarifposition.setOrgId(organizationContextService.getCurrentOrgId());
        } else {
            Tarifposition bestehend = tarifpositionRepository.findById(tarifposition.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Tarifposition nicht gefunden"));
            tarifposition.setOrgId(bestehend.getOrgId());
        }

        Tarifposition saved = tarifpositionRepository.save(tarifposition);
        log.info("Tariff position saved with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Delete a position.
     *
     * @param id Position ID
     * @return true if deleted, false if not found
     */
    @Transactional
    public boolean deleteTarifposition(Long id) {
        hibernateFilterService.enableOrgFilter();
        Optional<Tarifposition> vorhanden = tarifpositionRepository.findById(id);
        if (vorhanden.isEmpty()) {
            log.warn("Tariff position not found for deletion: {}", id);
            return false;
        }
        tarifpositionRepository.delete(vorhanden.get());
        log.info("Tariff position deleted: {}", id);
        return true;
    }

    /**
     * Count positions referencing a tariff — used to reject deletion of a referenced tariff.
     *
     * @param tarifId Tariff ID
     * @return Number of positions
     */
    @Transactional(readOnly = true)
    public long countByTarif(Long tarifId) {
        hibernateFilterService.enableOrgFilter();
        return tarifpositionRepository.countByTarifId(tarifId);
    }

    private Mieter resolveMieter(Tarifposition tarifposition) {
        if (tarifposition.getMieter() == null || tarifposition.getMieter().getId() == null) {
            throw new IllegalArgumentException("Mieter ist erforderlich");
        }
        return mieterRepository.findById(tarifposition.getMieter().getId())
                .orElseThrow(() -> new IllegalArgumentException("Mieter nicht gefunden"));
    }

    private Tarif resolveTarif(Tarifposition tarifposition) {
        if (tarifposition.getTarif() == null || tarifposition.getTarif().getId() == null) {
            throw new IllegalArgumentException("Tarif ist erforderlich");
        }
        return tarifRepository.findById(tarifposition.getTarif().getId())
                .orElseThrow(() -> new IllegalArgumentException("Tarif nicht gefunden"));
    }
}
