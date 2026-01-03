package ch.nacht.service;

import ch.nacht.dto.EinheitMatchResponseDTO;
import ch.nacht.entity.Einheit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for AI-based unit matching by filename.
 * Uses Claude to analyze filenames and match them against available units.
 */
@Service
public class EinheitMatchingService {

    private static final Logger log = LoggerFactory.getLogger(EinheitMatchingService.class);
    private static final String NO_MATCH = "KEINE";

    private final ChatClient chatClient;
    private final EinheitService einheitService;

    public EinheitMatchingService(ChatClient.Builder chatClientBuilder,
                                   EinheitService einheitService) {
        this.chatClient = chatClientBuilder.build();
        this.einheitService = einheitService;
    }

    /**
     * Match a filename against available units using AI.
     *
     * @param filename The filename to analyze
     * @return Match result with unit info and confidence
     */
    public EinheitMatchResponseDTO matchEinheitByFilename(String filename) {
        log.info("Matching filename: {}", filename);

        // 1. Load all available units
        List<Einheit> einheiten = einheitService.getAllEinheiten();

        if (einheiten.isEmpty()) {
            log.warn("No units available for matching");
            return EinheitMatchResponseDTO.builder()
                    .matched(false)
                    .message("Keine Einheiten verfügbar")
                    .confidence(0.0)
                    .build();
        }

        // 2. Build prompt for Claude
        String prompt = buildPrompt(filename, einheiten);
        log.debug("Generated prompt: {}", prompt);

        // 3. Call Claude API
        String response;
        try {
            response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.debug("Claude response: {}", response);
        } catch (Exception e) {
            log.error("Error calling Claude API: {}", e.getMessage(), e);
            String errorMessage = extractErrorMessage(e);
            return EinheitMatchResponseDTO.builder()
                    .matched(false)
                    .message(errorMessage)
                    .confidence(0.0)
                    .build();
        }

        // 4. Parse response and return result
        return parseResponse(response, einheiten);
    }

    /**
     * Extract a user-friendly error message from the exception.
     */
    private String extractErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "KI-Service nicht verfügbar";
        }

        // Check for common error patterns
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("invalid api key") || lowerMessage.contains("authentication")) {
            return "Ungültiger API-Key für KI-Service";
        }
        if (lowerMessage.contains("rate limit") || lowerMessage.contains("too many requests")) {
            return "KI-Service überlastet, bitte später versuchen";
        }
        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            return "KI-Service antwortet nicht (Timeout)";
        }
        if (lowerMessage.contains("connection") || lowerMessage.contains("unreachable")) {
            return "KI-Service nicht erreichbar";
        }

        return "KI-Service nicht verfügbar: " + message;
    }

    // Pattern to extract unit identifier from filename (after YYYY-MM-)
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^\\d{2,4}.?\\d{1,2}.?(.+?)(?:\\.csv)?$", Pattern.CASE_INSENSITIVE);

    /**
     * Extract the unit identifier part from a filename.
     * Filename format: YYYY-MM-<einheit>.csv
     * Example: "2025-07-1-li.csv" -> "1-li"
     */
    private String extractUnitIdentifier(String filename) {
        Matcher matcher = FILENAME_PATTERN.matcher(filename);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return filename; // Fallback to full filename
    }

    /**
     * Build the prompt for Claude to analyze the filename.
     */
    private String buildPrompt(String filename, List<Einheit> einheiten) {
        String unitIdentifier = extractUnitIdentifier(filename);

        StringBuilder sb = new StringBuilder();
        sb.append("Finde die passende Einheit anhand des Kürzels: '").append(unitIdentifier).append("'\n\n");
        sb.append("Typische Abkürzungen sind:\n");
        sb.append("- 'a' oder 'allg' oder 'allgemein' -> Allgemein\n");
        sb.append("- 'pv' oder 'pv-anlage' -> PV Produktion\n");
        sb.append("- 'pv-steuerung' oder 'pv-verbrauch' -> PV Verbrauch\n");
        sb.append("- '1-li' oder '1l' oder '1-l' oder '1-links' -> 1. Stock links\n");
        sb.append("- '1-re' oder '1r' oder '1-r' oder '1-rechts' -> 1. Stock rechts\n");
        sb.append("- 'p-li' oder 'pl' -> Parterre links\n");
        sb.append("- 'p-re' oder 'pr' oder 'p-r' -> Parterre rechts\n");
        sb.append("- 'e' oder 'eg' -> Erdgeschoss\n");
        sb.append("- 'd' oder 'dg' -> Dachgeschoss\n\n");

        sb.append("Verfügbare Einheiten:\n");
        for (Einheit e : einheiten) {
            sb.append("- ID ").append(e.getId()).append(": ").append(e.getName()).append("\n");
        }

        sb.append("\nWelche Einheit passt am besten zu '").append(unitIdentifier).append("'?\n");
        sb.append("Antworte NUR mit der ID-Nummer oder 'KEINE'.");

        return sb.toString();
    }

    /**
     * Parse Claude's response and build the result DTO.
     */
    private EinheitMatchResponseDTO parseResponse(String response, List<Einheit> einheiten) {
        String trimmedResponse = response.trim().toUpperCase();

        // Check for no match
        if (trimmedResponse.equals(NO_MATCH)) {
            log.info("No match found by Claude");
            return EinheitMatchResponseDTO.builder()
                    .matched(false)
                    .message("Keine passende Einheit gefunden")
                    .confidence(0.0)
                    .build();
        }

        // Try to parse ID
        try {
            Long matchedId = Long.parseLong(response.trim());

            // Find the matching unit
            Optional<Einheit> matchedEinheit = einheiten.stream()
                    .filter(e -> e.getId().equals(matchedId))
                    .findFirst();

            if (matchedEinheit.isPresent()) {
                Einheit einheit = matchedEinheit.get();
                log.info("Matched unit: {} (ID: {})", einheit.getName(), einheit.getId());

                return EinheitMatchResponseDTO.builder()
                        .matched(true)
                        .einheitId(einheit.getId())
                        .einheitName(einheit.getName())
                        .confidence(0.9) // High confidence when Claude returns a specific ID
                        .build();
            } else {
                log.warn("Claude returned ID {} which is not in the list", matchedId);
                return EinheitMatchResponseDTO.builder()
                        .matched(false)
                        .message("Einheit-ID nicht gefunden")
                        .confidence(0.0)
                        .build();
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse Claude response as ID: {}", response);
            return EinheitMatchResponseDTO.builder()
                    .matched(false)
                    .message("Ungültige Antwort vom KI-Service")
                    .confidence(0.0)
                    .build();
        }
    }
}
