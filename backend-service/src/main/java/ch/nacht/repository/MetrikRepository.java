package ch.nacht.repository;

import ch.nacht.entity.Metrik;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MetrikRepository extends JpaRepository<Metrik, Long> {
    Optional<Metrik> findByName(String name);
}
