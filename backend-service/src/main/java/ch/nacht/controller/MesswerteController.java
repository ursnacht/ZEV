package ch.nacht.controller;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.Messwerte;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messwerte")
public class MesswerteController {

    private final MesswerteRepository messwerteRepository;
    private final EinheitRepository einheitRepository;

    public MesswerteController(MesswerteRepository messwerteRepository, EinheitRepository einheitRepository) {
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadCsv(
            @RequestParam("date") String dateStr,
            @RequestParam("einheitId") Long einheitId,
            @RequestParam("file") MultipartFile file) {

        try {
            // Fetch the Einheit entity
            Einheit einheit = einheitRepository.findById(einheitId)
                .orElseThrow(() -> new RuntimeException("Einheit not found with id: " + einheitId));

            LocalDate date = LocalDate.parse(dateStr);
            LocalDateTime zeit = LocalDateTime.of(date, LocalTime.of(0, 15));

            List<Messwerte> messwerteList = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
                String line;
                reader.readLine(); // Skip header line
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("[,;]");
                    if (parts.length >= 3) {
                        Double total = Double.parseDouble(parts[1].trim());
                        Double zev = Double.parseDouble(parts[2].trim());

                        messwerteList.add(new Messwerte(zeit, total, zev, einheit));
                        zeit = zeit.plusMinutes(15);
                    }
                }
            }

            messwerteRepository.saveAll(messwerteList);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "count", messwerteList.size(),
                "einheitId", einheitId,
                "einheitName", einheit.getName()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
}
