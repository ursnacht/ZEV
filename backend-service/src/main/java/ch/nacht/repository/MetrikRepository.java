package ch.nacht.repository;

import ch.nacht.entity.Metrik;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetrikRepository extends JpaRepository<Metrik, Long> {
    Optional<Metrik> findByNameAndOrgId(String name, UUID orgId);
}
