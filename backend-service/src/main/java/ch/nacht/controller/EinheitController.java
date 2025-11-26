package ch.nacht.controller;

import ch.nacht.entity.Einheit;
import ch.nacht.repository.EinheitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/einheit")
public class EinheitController {

    private final EinheitRepository einheitRepository;

    public EinheitController(EinheitRepository einheitRepository) {
        this.einheitRepository = einheitRepository;
    }

    @GetMapping
    public List<Einheit> getAllEinheiten() {
        return einheitRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Einheit> getEinheitById(@PathVariable Long id) {
        Optional<Einheit> einheit = einheitRepository.findById(id);
        return einheit.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Einheit> createEinheit(@RequestBody Einheit einheit) {
        Einheit saved = einheitRepository.save(einheit);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Einheit> updateEinheit(@PathVariable Long id, @RequestBody Einheit einheit) {
        if (!einheitRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        einheit.setId(id);
        Einheit updated = einheitRepository.save(einheit);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEinheit(@PathVariable Long id) {
        if (!einheitRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        einheitRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
