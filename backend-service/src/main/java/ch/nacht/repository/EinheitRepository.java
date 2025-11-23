package ch.nacht.repository;

import ch.nacht.entity.Einheit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EinheitRepository extends JpaRepository<Einheit, Long> {
}
