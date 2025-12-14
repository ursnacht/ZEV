package ch.nacht.service;

import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.repository.TarifRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing tariffs.
 */
@Service
public class TarifService {

    private static final Logger log = LoggerFactory.getLogger(TarifService.class);

    private final TarifRepository tarifRepository;

    public TarifService(TarifRepository tarifRepository) {
        this.tarifRepository = tarifRepository;
    }

    /**
     * Get all tariffs ordered by type and validity date.
     *
     * @return List of all tariffs
     */
    public List<Tarif> getAllTarife() {
        return tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc();
    }

    /**
     * Get a tariff by ID.
     *
     * @param id Tariff ID
     * @return Optional containing the tariff if found
     */
    public Optional<Tarif> getTarifById(Long id) {
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
    public List<Tarif> getTarifeForZeitraum(TarifTyp typ, LocalDate von, LocalDate bis) {
        return tarifRepository.findByTariftypAndZeitraumOverlapping(typ, von, bis);
    }

    /**
     * Validate that tariffs cover the entire date range for both ZEV and VNB.
     * Throws an exception if there are gaps in coverage.
     *
     * @param von Start date
     * @param bis End date
     * @throws IllegalStateException if there are gaps in tariff coverage
     */
    public void validateTarifAbdeckung(LocalDate von, LocalDate bis) {
        log.debug("Validating tariff coverage from {} to {}", von, bis);

        List<String> errors = new ArrayList<>();

        // Check ZEV tariffs
        List<LocalDate> zevGaps = findCoverageGaps(TarifTyp.ZEV, von, bis);
        if (!zevGaps.isEmpty()) {
            errors.add("ZEV-Tarif fehlt für: " + formatDateGaps(zevGaps));
        }

        // Check VNB tariffs
        List<LocalDate> vnbGaps = findCoverageGaps(TarifTyp.VNB, von, bis);
        if (!vnbGaps.isEmpty()) {
            errors.add("VNB-Tarif fehlt für: " + formatDateGaps(vnbGaps));
        }

        if (!errors.isEmpty()) {
            String message = "Für den Zeitraum fehlen gültige Tarife: " + String.join("; ", errors);
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.debug("Tariff coverage validated successfully");
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

    /**
     * Format date gaps for error message.
     */
    private String formatDateGaps(List<LocalDate> gaps) {
        if (gaps.size() == 1) {
            return gaps.get(0).toString();
        }
        return gaps.get(0) + " (und weitere)";
    }
}
