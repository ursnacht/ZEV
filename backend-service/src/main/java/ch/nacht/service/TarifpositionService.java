package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Erfassungsart;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.TarifRepository;
import ch.nacht.repository.TarifpositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing manually captured tariff positions (Ladestrom and later further use cases).
 */
@Service
public class TarifpositionService {

    private static final Logger log = LoggerFactory.getLogger(TarifpositionService.class);

    private final TarifpositionRepository tarifpositionRepository;
    private final EinheitRepository einheitRepository;
    private final TarifRepository tarifRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public TarifpositionService(TarifpositionRepository tarifpositionRepository,
                                EinheitRepository einheitRepository,
                                TarifRepository tarifRepository,
                                OrganizationContextService organizationContextService,
                                HibernateFilterService hibernateFilterService) {
        this.tarifpositionRepository = tarifpositionRepository;
        this.einheitRepository = einheitRepository;
        this.tarifRepository = tarifRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Get all positions of a unit.
     *
     * @param einheitId Unit ID
     * @return List of positions, newest quarter first
     */
    @Transactional(readOnly = true)
    public List<Tarifposition> getByEinheit(Long einheitId) {
        hibernateFilterService.enableOrgFilter();
        return tarifpositionRepository.findByEinheitId(einheitId);
    }

    /**
     * Get the positions of the given units whose quarter overlaps the billing period.
     * Only positions with a quantity greater than zero are returned.
     *
     * @param einheitIds Unit IDs of the tenant
     * @param von Period start (inclusive)
     * @param bis Period end (inclusive)
     * @return Positions, oldest quarter first
     */
    @Transactional(readOnly = true)
    public List<Tarifposition> getFuerRechnung(Collection<Long> einheitIds, LocalDate von, LocalDate bis) {
        hibernateFilterService.enableOrgFilter();
        if (einheitIds.isEmpty()) {
            return List.of();
        }
        return tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(
                einheitIds,
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
        return tarifpositionRepository.findFirstById(id);
    }

    /**
     * Save a new or updated position.
     *
     * <p>Validates that the referenced unit is a charging station, that the tariff is of a
     * manually captured type and that no other position for the same unit, quarter and tariff
     * <b>type</b> exists. The latter rule is stricter than the database constraint (which covers
     * the exact tariff only), because two different LADESTROM tariffs would otherwise bypass it.
     * Geprüft wird gegen den Typ <b>dieser</b> Position, nicht gegen alle manuell erfassbaren
     * Typen: Positionen verschiedener Typen sind je Quartal unabhängig voneinander erfassbar.
     *
     * @param tarifposition Position to save
     * @return Saved position
     * @throws IllegalArgumentException on any validation failure
     */
    @Transactional
    public Tarifposition saveTarifposition(Tarifposition tarifposition) {
        hibernateFilterService.enableOrgFilter();
        log.info("Saving tariff position: {}", tarifposition);

        Einheit einheit = resolveEinheit(tarifposition);
        Tarif tarif = resolveTarif(tarifposition);

        if (!TarifTyp.MANUELL_ERFASST.contains(tarif.getTariftyp())) {
            throw new IllegalArgumentException(
                    "Für den Tariftyp " + tarif.getTariftyp() + " können keine Positionen erfasst werden");
        }
        pruefeTariftypZuEinheit(einheit, tarif);

        Long excludeId = tarifposition.getId() != null ? tarifposition.getId() : -1L;
        pruefeEindeutigkeit(einheit, tarif, tarifposition, excludeId);

        tarifposition.setEinheit(einheit);
        tarifposition.setTarif(tarif);
        if (tarifposition.getErfassungsart() == null) {
            tarifposition.setErfassungsart(Erfassungsart.MANUELL);
        }
        // Beim Update traegt das DTO keine org_id (anders als bei Entities, die direkt als JSON
        // durchgereicht werden) - sie wird aus dem bestehenden Datensatz uebernommen.
        // `findFirstById` ist gefiltert, ein fremder Mandant kommt hier also gar nicht an.
        // Frueher stand hier `findById` mit derselben Begruendung - die war falsch: Der Filter
        // greift nicht auf Primaerschluessel-Loads, womit ein fremder Datensatz seine org_id an
        // den eingehenden weitergab.
        if (tarifposition.getId() == null) {
            tarifposition.setOrgId(organizationContextService.getCurrentOrgId());
        } else {
            Tarifposition bestehend = tarifpositionRepository.findFirstById(tarifposition.getId())
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
        Optional<Tarifposition> vorhanden = tarifpositionRepository.findFirstById(id);
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

    /**
     * Einheitentypen, an denen Positionen hängen dürfen. Produzenten fehlen bewusst: Ihre
     * Rechnung enthält ausschliesslich Grundgebühr-Zeilen und ruft die Positionen gar nicht ab
     * (Specs/Tarifpositionen.md, Abgrenzung). Bilanz-Typen werden nie verrechnet.
     */
    private static final Set<EinheitTyp> ERFASSBARE_EINHEITEN =
            EnumSet.of(EinheitTyp.LADESTATION, EinheitTyp.CONSUMER);

    private Einheit resolveEinheit(Tarifposition tarifposition) {
        if (tarifposition.getEinheit() == null || tarifposition.getEinheit().getId() == null) {
            throw new IllegalArgumentException("Einheit ist erforderlich");
        }
        Einheit einheit = einheitRepository.findFirstById(tarifposition.getEinheit().getId())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
        if (!ERFASSBARE_EINHEITEN.contains(einheit.getTyp())) {
            throw new IllegalArgumentException(
                    "Positionen sind nur für Einheiten vom Typ Ladestation oder Konsument zulässig");
        }
        return einheit;
    }

    /**
     * Prüft, ob der Tariftyp zum Einheitentyp passt.
     *
     * <p>An einer Wohnung ist ausschliesslich {@link TarifTyp#ZUSATZ} zulässig: Ladestrom gehört
     * fachlich an eine Ladestation. Die Checkbox in der Oberfläche ist nur eine Anzeigehilfe —
     * verbindlich ist diese Prüfung.
     *
     * @param einheit Einheit der Position
     * @param tarif Gewählter Tarif
     * @throws IllegalArgumentException wenn die Kombination unzulässig ist
     */
    private void pruefeTariftypZuEinheit(Einheit einheit, Tarif tarif) {
        if (einheit.getTyp() == EinheitTyp.CONSUMER
                && !TarifTyp.EIGENE_MENGENEINHEIT.contains(tarif.getTariftyp())) {
            throw new IllegalArgumentException(
                    "Für Konsumenten sind nur Tarife vom Typ " + TarifTyp.ZUSATZ + " zulässig");
        }
    }

    /**
     * Höchstens eine Position je Einheit und Quartal — die Bezugsgrösse hängt am Typ:
     *
     * <ul>
     *   <li>{@link TarifTyp#LADESTROM}: je <b>Tariftyp</b>. Strenger als der DB-Constraint, weil
     *       zwei verschiedene Ladestrom-Tarife die Regel sonst unterliefen.</li>
     *   <li>{@link TarifTyp#ZUSATZ}: je <b>Tarif</b>. Je Typ zu prüfen wäre sinnlos — man könnte
     *       dann pro Quartal nur eine einzige Zusatzposition erfassen, also nicht Sauna
     *       <i>und</i> Waschküche.</li>
     * </ul>
     *
     * @throws IllegalArgumentException wenn bereits eine passende Position existiert
     */
    private void pruefeEindeutigkeit(Einheit einheit, Tarif tarif, Tarifposition position, Long excludeId) {
        if (TarifTyp.EINDEUTIG_JE_TARIF.contains(tarif.getTariftyp())) {
            if (tarifpositionRepository.existsByEinheitAndQuartalAndTarif(
                    einheit.getId(), position.getJahr(), position.getQuartal(),
                    tarif.getId(), excludeId)) {
                throw new IllegalArgumentException(
                        "Für diese Einheit, dieses Quartal und diesen Tarif existiert bereits eine Position");
            }
            return;
        }
        if (tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                einheit.getId(), position.getJahr(), position.getQuartal(),
                EnumSet.of(tarif.getTariftyp()), excludeId)) {
            throw new IllegalArgumentException(
                    "Für diese Einheit und dieses Quartal existiert bereits eine Position dieses Tariftyps");
        }
    }

    private Tarif resolveTarif(Tarifposition tarifposition) {
        if (tarifposition.getTarif() == null || tarifposition.getTarif().getId() == null) {
            throw new IllegalArgumentException("Tarif ist erforderlich");
        }
        return tarifRepository.findFirstById(tarifposition.getTarif().getId())
                .orElseThrow(() -> new IllegalArgumentException("Tarif nicht gefunden"));
    }
}
