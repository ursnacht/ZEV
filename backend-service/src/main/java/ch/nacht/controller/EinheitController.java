package ch.nacht.controller;

import ch.nacht.entity.Einheit;
import ch.nacht.service.EinheitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/einheit")
public class EinheitController {

    private final EinheitService einheitService;

    public EinheitController(EinheitService einheitService) {
        this.einheitService = einheitService;
    }

    @GetMapping
    public List<Einheit> getAllEinheiten() {
        return einheitService.getAllEinheiten();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Einheit> getEinheitById(@PathVariable Long id) {
        return einheitService.getEinheitById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Einheit> createEinheit(@jakarta.validation.Valid @RequestBody Einheit einheit) {
        Einheit saved = einheitService.createEinheit(einheit);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Einheit> updateEinheit(@PathVariable Long id,
            @jakarta.validation.Valid @RequestBody Einheit einheit) {
        return einheitService.updateEinheit(id, einheit)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEinheit(@PathVariable Long id) {
        if (einheitService.deleteEinheit(id)) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
