package ch.nacht.repository;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, String> {
    List<Translation> findAllByOrderByKeyAsc();
}
