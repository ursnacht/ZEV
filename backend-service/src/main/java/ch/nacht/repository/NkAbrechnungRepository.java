package ch.nacht.repository;

import ch.nacht.entity.NkAbrechnung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for NkAbrechnung entities.
 */
@Repository
public interface NkAbrechnungRepository extends JpaRepository<NkAbrechnung, Long> {

    /**
     * Find all billing periods, newest first.
     *
     * @return List of billing periods
     */
    List<NkAbrechnung> findAllByOrderByDatumVonDesc();
}
