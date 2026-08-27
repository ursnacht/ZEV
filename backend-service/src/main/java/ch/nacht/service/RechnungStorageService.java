package ch.nacht.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for temporary in-memory storage of generated invoice PDFs.
 * PDFs are automatically cleaned up after 30 minutes.
 *
 * <p>Die Ablage ist nach {@link Rechnungsart} <b>getrennt</b>
 * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-5). Die Art ist ein eigenes Segment des internen
 * Schluessels und kein Praefix im uebergebenen Schluessel: Ein Praefix waere nicht disjunkt, weil
 * ZEV-Schluessel aus dem Einheitennamen entstehen und {@link #sanitizeKey(String)} jedes
 * Leerzeichen zu {@code _} macht — eine Einheit „nk 12" mit {@code mieterId 45} ergaebe genau
 * {@code nk_12_45}.
 */
@Service
public class RechnungStorageService {

    private static final Logger log = LoggerFactory.getLogger(RechnungStorageService.class);
    private static final long EXPIRY_MINUTES = 30;

    /**
     * Art der abgelegten Rechnung — trennt die Namensraeume der beiden Rechnungswege.
     *
     * <p>Bewusst nicht {@code Debitorherkunft}, die dieselben Werte traegt: Diese hier ist ein
     * Laufzeit-Namensraum, jene ist persistiert und haengt an einem CHECK-Constraint.
     */
    public enum Rechnungsart {
        /** Quartalsrechnung aus der Stromabrechnung. */
        ZEV,
        /** Rechnung aus einer Nebenkostenabrechnung. */
        NK
    }

    private final Map<String, StoredPdf> storage = new ConcurrentHashMap<>();

    private final OrganizationContextService organizationContextService;

    public RechnungStorageService(OrganizationContextService organizationContextService) {
        this.organizationContextService = organizationContextService;
    }

    /**
     * Build the tenant- and art-scoped internal storage key.
     * The PDFs are isolated per organisation, so a {@code zev_admin} of one tenant
     * can never retrieve another tenant's invoice via a guessed unit-name key.
     */
    private String orgScopedKey(Rechnungsart art, String key) {
        return organizationContextService.getCurrentOrgId() + ":" + art.name() + ":" + sanitizeKey(key);
    }

    /** Praefix aller Eintraege einer Art im aktuellen Mandanten. */
    private String artPrefix(Rechnungsart art) {
        return organizationContextService.getCurrentOrgId() + ":" + art.name() + ":";
    }

    /**
     * Store a PDF with the given key, scoped to the current organisation and invoice art.
     *
     * @param art      Art der Rechnung — bestimmt den Namensraum
     * @param key      The storage key (sanitized before use)
     * @param pdf      The PDF bytes
     * @param filename Dateiname fuer den Download; wird mitgespeichert, damit der Schluessel frei
     *                 waehlbar bleibt und der Benutzer trotzdem einen lesbaren Namen erhaelt
     */
    public void store(Rechnungsart art, String key, byte[] pdf, String filename) {
        storage.put(orgScopedKey(art, key), new StoredPdf(pdf, filename, Instant.now()));
        log.debug("Stored PDF art={} key={}, size: {} bytes", art, sanitizeKey(key), pdf.length);
    }

    /**
     * Retrieve a PDF by key within the current organisation and art.
     *
     * @param art Art der Rechnung
     * @param key The storage key
     * @return The PDF bytes if found and not expired
     */
    public Optional<byte[]> get(Rechnungsart art, String key) {
        StoredPdf stored = storage.get(orgScopedKey(art, key));
        if (stored != null && !isExpired(stored)) {
            log.debug("Retrieved PDF art={} key={}", art, sanitizeKey(key));
            return Optional.of(stored.pdf);
        }
        log.debug("PDF not found or expired for art={} key={}", art, sanitizeKey(key));
        return Optional.empty();
    }

    /**
     * Check if a PDF exists for the given key within the current organisation and art.
     *
     * @param art Art der Rechnung
     * @param key The storage key
     * @return true if PDF exists and is not expired
     */
    public boolean exists(Rechnungsart art, String key) {
        StoredPdf stored = storage.get(orgScopedKey(art, key));
        return stored != null && !isExpired(stored);
    }

    /**
     * Clear the stored PDFs of <b>one art</b> within the current organisation.
     *
     * <p>Ersetzt das frueher mandantenweite {@code clearAll()}: Weil die beiden Rechnungswege von
     * zwei Seiten aus laufen, nahm ein ZEV-Lauf die offenen NK-Downloads mit — der Download
     * antwortete danach stumm mit {@code 404}. Andere Mandanten und die andere Art bleiben
     * unberuehrt.
     *
     * @param art Art, deren Ablagen geloescht werden
     */
    public void clearArt(Rechnungsart art) {
        String prefix = artPrefix(art);
        int before = storage.size();
        storage.keySet().removeIf(k -> k.startsWith(prefix));
        log.info("Cleared {} stored PDFs art={} for org {}", before - storage.size(), art,
                organizationContextService.getCurrentOrgId());
    }

    /**
     * Sanitize the key for safe storage and URL usage.
     * Replaces spaces with underscores and removes special characters.
     *
     * @param key The raw key (unit name)
     * @return Sanitized key
     */
    public String sanitizeKey(String key) {
        if (key == null) return "";
        return key.trim()
                  .replace(" ", "_")
                  .replaceAll("[^a-zA-Z0-9_äöüÄÖÜ-]", "");
    }

    /**
     * Generate a filename from the key.
     *
     * @param key The storage key
     * @return Filename with .pdf extension
     */
    public String getFilename(String key) {
        return sanitizeKey(key) + ".pdf";
    }

    /**
     * Der <b>gespeicherte</b> Dateiname eines Eintrags.
     *
     * @param art Art der Rechnung
     * @param key The storage key
     * @return der beim Ablegen mitgegebene Name; sonst der aus dem Schluessel abgeleitete
     */
    public String getFilename(Rechnungsart art, String key) {
        StoredPdf stored = storage.get(orgScopedKey(art, key));
        return stored != null && stored.filename != null ? stored.filename : getFilename(key);
    }

    /**
     * Scheduled task to clean up expired PDFs.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupExpired() {
        int beforeCount = storage.size();
        storage.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        int removedCount = beforeCount - storage.size();
        if (removedCount > 0) {
            log.info("Cleaned up {} expired PDFs", removedCount);
        }
    }

    private boolean isExpired(StoredPdf stored) {
        return stored.createdAt.plusSeconds(EXPIRY_MINUTES * 60).isBefore(Instant.now());
    }

    /**
     * Internal class to store PDF with filename and timestamp.
     */
    private static class StoredPdf {
        final byte[] pdf;
        final String filename;
        final Instant createdAt;

        StoredPdf(byte[] pdf, String filename, Instant createdAt) {
            this.pdf = pdf;
            this.filename = filename;
            this.createdAt = createdAt;
        }
    }
}
