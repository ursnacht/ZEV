package ch.nacht.repository;

import ch.nacht.entity.Messwerte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MesswerteRepository extends JpaRepository<Messwerte, Long> {
}
