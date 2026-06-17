package ch.nacht.service;

import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.exception.TarifLuecke;
import ch.nacht.exception.TarifLueckePeriode;
import ch.nacht.exception.TarifLueckenException;
import ch.nacht.repository.TarifRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing tariffs.
 */
@Service
public class TarifService {

    private static final Logger log = LoggerFactory.getLogger(TarifService.class);

    private final TarifRepository tarifRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public TarifService(TarifRepository tarifRepository,
                        OrganizationContextService organizationContextService,
                        HibernateFilterService hibernateFilterService) {
        this.tarifRepository = tarifRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Get all tariffs ordered by type and validity date.
     *
     * @return List of all tariffs
     */
    @Transactional(readOnly = true)
    public List<Tarif> getAllTarife() {
        hibernateFilterService.enableOrgFilter();
        return tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc();
    }

    /**
     * Get a tariff by ID.
     *
     * @param id Tariff ID
     * @return Optional containing the tariff if found
     */
    @Transactional(readOnly = true)
    public Optional<Tarif> getTarifById(Long id) {
        hibernateFilterService.enableOrgFilter();
        return tarifRepository.findById(id);
    }

    /**
     * Save a new or updated tariff.
     * Validates that no overlapping tariff exists for the same type.
     *
     * @param tarif Tariff to save
     * @return Saved tariff
     * @throws IllegalArgumentException if tariff overlaps with existing one or dates are invalid
     */
    @Transactional
    public Tarif saveTarif(Tarif tarif) {
        hibernateFilterService.enableOrgFilter();
        log.info("Saving tariff: {}", tarif);

        // Validate date range
        if (tarif.getGueltigVon().isAfter(tarif.getGueltigBis())) {
            throw new IllegalArgumentException("Gültig von muss vor oder gleich Gültig bis sein");
        }

        // Check for overlapping tariffs
        Long excludeId = tarif.getId() != null ? tarif.getId() : -1L;
        if (tarifRepository.existsOverlappingTarif(
                tarif.getTariftyp(),
                tarif.getGueltigVon(),
                tarif.getGueltigBis(),
                excludeId)) {
            throw new IllegalArgumentException("Tarif überschneidet sich mit bestehendem Tarif");
        }

        // org_id setzen bei neuem Tarif
        if (tarif.getId() == null) {
            tarif.setOrgId(organizationContextService.getCurrentOrgId());
        }

        Tarif saved = tarifRepository.save(tarif);
        log.info("Tariff saved with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Delete a tariff by ID.
     *
     * @param id Tariff ID
     * @return true if deleted, false if not found
     */
    @Transactional
    public boolean deleteTarif(Long id) {
        hibernateFilterService.enableOrgFilter();
        if (tarifRepository.existsById(id)) {
            tarifRepository.deleteById(id);
            log.info("Deleted tariff with ID: {}", id);
            return true;
        }
        log.warn("Tariff not found for deletion: {}", id);
        return false;
    }

    /**
     * Get all tariffs of a specific type that are valid for the given date range.
     * Used for invoice calculation.
     *
     * @param typ Tariff type
     * @param von Start date
     * @param bis End date
     * @return List of valid tariffs
     */
    @Transactional(readOnly = true)
    public List<Tarif> getTarifeForZeitraum(TarifTyp typ, LocalDate von, LocalDate bis) {
        hibernateFilterService.enableOrgFilter();
        return tarifRepository.findByTariftypAndZeitraumOverlapping(typ, von, bis);
    }

    /**
     * Validate that tariffs cover the entire date range for both ZEV and VNB.
     * Throws an exception if there are gaps in coverage.
     *
     * @param von Start date
     * @param bis End date
     * @throws TarifLueckenException if there are gaps in tariff coverage
     */
    public void validateTarifAbdeckung(LocalDate von, LocalDate bis) {
        log.debug("Validating tariff coverage from {} to {}", von, bis);

        List<TarifLuecke> luecken = findTarifLuecken(von, bis);
        if (!luecken.isEmpty()) {
            log.warn("Tariff coverage gaps from {} to {}: {}", von, bis, luecken);
            throw new TarifLueckenException(luecken);
        }

        log.debug("Tariff coverage validated successfully");
    }

    /**
     * Find tariff coverage gaps (ZEV and VNB) for the given period in a
     * language-neutral form.
     *
     * @param von Start date
     * @param bis End date
     * @return List of gaps (empty if fully covered)
     */
    private List<TarifLuecke> findTarifLuecken(LocalDate von, LocalDate bis) {
        List<TarifLuecke> luecken = new ArrayList<>();

        List<LocalDate> zevGaps = findCoverageGaps(TarifTyp.ZEV, von, bis);
        if (!zevGaps.isEmpty()) {
            luecken.add(new TarifLuecke("ZEV", zevGaps.get(0).format(DATE_FORMATTER), zevGaps.size() > 1));
        }

        List<LocalDate> vnbGaps = findCoverageGaps(TarifTyp.VNB, von, bis);
        if (!vnbGaps.isEmpty()) {
            luecken.add(new TarifLuecke("VNB", vnbGaps.get(0).format(DATE_FORMATTER), vnbGaps.size() > 1));
        }

        return luecken;
    }

    /**
     * Find gaps in tariff coverage for a specific type and date range.
     *
     * @param typ Tariff type
     * @param von Start date
     * @param bis End date
     * @return List of dates without coverage (first date of each gap)
     */
    private List<LocalDate> findCoverageGaps(TarifTyp typ, LocalDate von, LocalDate bis) {
        List<Tarif> tarife = getTarifeForZeitraum(typ, von, bis);
        List<LocalDate> gaps = new ArrayList<>();

        if (tarife.isEmpty()) {
            gaps.add(von);
            return gaps;
        }

        LocalDate currentDate = von;
        for (Tarif tarif : tarife) {
            LocalDate tarifStart = tarif.getGueltigVon().isBefore(von) ? von : tarif.getGueltigVon();
            LocalDate tarifEnd = tarif.getGueltigBis().isAfter(bis) ? bis : tarif.getGueltigBis();

            // Check for gap before this tariff
            if (currentDate.isBefore(tarifStart)) {
                gaps.add(currentDate);
            }

            // Move current date to day after this tariff ends
            if (tarifEnd.plusDays(1).isAfter(currentDate)) {
                currentDate = tarifEnd.plusDays(1);
            }
        }

        // Check for gap after all tariffs
        if (currentDate.isBefore(bis) || currentDate.equals(bis)) {
            if (!bis.isBefore(currentDate)) {
                gaps.add(currentDate);
            }
        }

        return gaps;
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Validate tariff coverage for all quarters that have at least one tariff.
     *
     * @return ValidationResult with status and error messages
     */
    @Transactional(readOnly = true)
    public ValidationResult validateQuartale() {
        hibernateFilterService.enableOrgFilter();
        log.info("Validating tariff coverage for all quarters");

        List<Tarif> alleTarife = tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc();
        if (alleTarife.isEmpty()) {
            return new ValidationResult(true, List.of());
        }

        Set<String> quartalsToCheck = new HashSet<>();
        for (Tarif tarif : alleTarife) {
            addQuartalsForTarif(tarif, quartalsToCheck);
        }

        List<TarifLueckePeriode> luecken = new ArrayList<>();
        for (String quartal : quartalsToCheck.stream().sorted().toList()) {
            String[] parts = quartal.split("/");
            int q = Integer.parseInt(parts[0].substring(1));
            int year = Integer.parseInt(parts[1]);

            List<TarifLuecke> periodLuecken = findTarifLuecken(getQuartalStart(q, year), getQuartalEnd(q, year));
            if (!periodLuecken.isEmpty()) {
                luecken.add(new TarifLueckePeriode(quartal, periodLuecken));
            }
        }

        log.info("Quarter validation found {} periods with gaps", luecken.size());
        return new ValidationResult(luecken.isEmpty(), luecken);
    }

    /**
     * Validate tariff coverage for all years that have at least one tariff.
     *
     * @return ValidationResult with status and error messages
     */
    @Transactional(readOnly = true)
    public ValidationResult validateJahre() {
        hibernateFilterService.enableOrgFilter();
        log.info("Validating tariff coverage for all years");

        List<Integer> years = tarifRepository.findDistinctYears();
        if (years.isEmpty()) {
            return new ValidationResult(true, List.of());
        }

        List<TarifLueckePeriode> luecken = new ArrayList<>();
        for (Integer year : years) {
            List<TarifLuecke> periodLuecken = findTarifLuecken(
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            if (!periodLuecken.isEmpty()) {
                luecken.add(new TarifLueckePeriode(String.valueOf(year), periodLuecken));
            }
        }

        log.info("Year validation found {} periods with gaps", luecken.size());
        return new ValidationResult(luecken.isEmpty(), luecken);
    }

    private void addQuartalsForTarif(Tarif tarif, Set<String> quartals) {
        LocalDate von = tarif.getGueltigVon();
        LocalDate bis = tarif.getGueltigBis();

        LocalDate current = von;
        while (!current.isAfter(bis)) {
            int q = (current.getMonthValue() - 1) / 3 + 1;
            quartals.add("Q" + q + "/" + current.getYear());
            current = getQuartalEnd(q, current.getYear()).plusDays(1);
        }
    }

    private LocalDate getQuartalStart(int q, int year) {
        return switch (q) {
            case 1 -> LocalDate.of(year, 1, 1);
            case 2 -> LocalDate.of(year, 4, 1);
            case 3 -> LocalDate.of(year, 7, 1);
            case 4 -> LocalDate.of(year, 10, 1);
            default -> throw new IllegalArgumentException("Invalid quarter: " + q);
        };
    }

    private LocalDate getQuartalEnd(int q, int year) {
        return switch (q) {
            case 1 -> LocalDate.of(year, 3, 31);
            case 2 -> LocalDate.of(year, 6, 30);
            case 3 -> LocalDate.of(year, 9, 30);
            case 4 -> LocalDate.of(year, 12, 31);
            default -> throw new IllegalArgumentException("Invalid quarter: " + q);
        };
    }

    /**
     * Result of tariff validation.
     */
    public record ValidationResult(boolean valid, List<TarifLueckePeriode> luecken) {}
}
