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
     * Auflösung der Einheiten über (org_id, messpunkt) – explizite Mandantenprüfung
     * für den MQTT-Ingest (ohne Request-Scope / orgFilter). Mehrere Treffer sind zulässig:
     * die Bilanz-Typen BEZUG/RUECKLIEFERUNG dürfen denselben Messpunkt teilen, die Meldung
     * wird dann beim Ingest auf die Einheiten aufgeteilt (Register-Projektion).
     */
    List<Einheit> findAllByOrgIdAndMesspunkt(Long orgId, String messpunkt);

    /** Erste Einheit eines Typs, z.B. die Bilanz-Einheit (max. eine je Mandant; orgFilter muss aktiv sein). */
    Optional<Einheit> findFirstByTyp(EinheitTyp typ);

    /** Eindeutigkeit der Bilanz-Typen je Mandant (orgFilter muss aktiv sein). */
    boolean existsByTyp(EinheitTyp typ);

    /** Eindeutigkeit der Bilanz-Typen je Mandant beim Update (orgFilter muss aktiv sein). */
    boolean existsByTypAndIdNot(EinheitTyp typ, Long id);
}
