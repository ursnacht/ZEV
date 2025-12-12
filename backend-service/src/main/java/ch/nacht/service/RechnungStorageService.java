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
 */
@Service
public class RechnungStorageService {

    private static final Logger log = LoggerFactory.getLogger(RechnungStorageService.class);
    private static final long EXPIRY_MINUTES = 30;

    private final Map<String, StoredPdf> storage = new ConcurrentHashMap<>();

    /**
     * Store a PDF with the given key (unit name).
     *
     * @param key The storage key (sanitized unit name)
     * @param pdf The PDF bytes
     */
    public void store(String key, byte[] pdf) {
        String sanitizedKey = sanitizeKey(key);
        storage.put(sanitizedKey, new StoredPdf(pdf, Instant.now()));
        log.debug("Stored PDF with key: {}, size: {} bytes", sanitizedKey, pdf.length);
    }

    /**
     * Retrieve a PDF by key.
     *
     * @param key The storage key
     * @return The PDF bytes if found and not expired
     */
    public Optional<byte[]> get(String key) {
        String sanitizedKey = sanitizeKey(key);
        StoredPdf stored = storage.get(sanitizedKey);
        if (stored != null && !isExpired(stored)) {
            log.debug("Retrieved PDF with key: {}", sanitizedKey);
            return Optional.of(stored.pdf);
        }
        log.debug("PDF not found or expired for key: {}", sanitizedKey);
        return Optional.empty();
    }

    /**
     * Check if a PDF exists for the given key.
     *
     * @param key The storage key
     * @return true if PDF exists and is not expired
     */
    public boolean exists(String key) {
        String sanitizedKey = sanitizeKey(key);
        StoredPdf stored = storage.get(sanitizedKey);
        return stored != null && !isExpired(stored);
    }

    /**
     * Clear all stored PDFs (e.g., when generating new batch).
     */
    public void clearAll() {
        int count = storage.size();
        storage.clear();
        log.info("Cleared {} stored PDFs", count);
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
     * Internal class to store PDF with timestamp.
     */
    private static class StoredPdf {
        final byte[] pdf;
        final Instant createdAt;

        StoredPdf(byte[] pdf, Instant createdAt) {
            this.pdf = pdf;
            this.createdAt = createdAt;
        }
    }
}
