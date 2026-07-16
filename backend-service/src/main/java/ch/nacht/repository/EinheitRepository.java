package ch.nacht.repository;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EinheitRepository extends JpaRepository<Einheit, Long> {
    List<Einheit> findAllByOrderByNameAsc();

    /**
     * Auflösung einer Einheit über (org_id, messpunkt) – explizite Mandantenprüfung
     * für den MQTT-Ingest (ohne Request-Scope / orgFilter).
     */
    Optional<Einheit> findByOrgIdAndMesspunkt(Long orgId, String messpunkt);

    /** Eindeutigkeit der Bilanz-Typen je Mandant (orgFilter muss aktiv sein). */
    boolean existsByTyp(EinheitTyp typ);

    /** Eindeutigkeit der Bilanz-Typen je Mandant beim Update (orgFilter muss aktiv sein). */
    boolean existsByTypAndIdNot(EinheitTyp typ, Long id);
}
