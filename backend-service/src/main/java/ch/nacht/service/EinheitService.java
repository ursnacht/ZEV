package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.repository.EinheitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EinheitService {

    private final EinheitRepository einheitRepository;

    public EinheitService(EinheitRepository einheitRepository) {
        this.einheitRepository = einheitRepository;
    }

    public List<Einheit> getAllEinheiten() {
        return einheitRepository.findAllByOrderByNameAsc();
    }

    public Optional<Einheit> getEinheitById(Long id) {
        return einheitRepository.findById(id);
    }

    @Transactional
    public Einheit createEinheit(Einheit einheit) {
        return einheitRepository.save(einheit);
    }

    @Transactional
    public Optional<Einheit> updateEinheit(Long id, Einheit einheit) {
        if (!einheitRepository.existsById(id)) {
            return Optional.empty();
        }
        einheit.setId(id);
        return Optional.of(einheitRepository.save(einheit));
    }

    @Transactional
    public boolean deleteEinheit(Long id) {
        if (!einheitRepository.existsById(id)) {
            return false;
        }
        einheitRepository.deleteById(id);
        return true;
    }
}
