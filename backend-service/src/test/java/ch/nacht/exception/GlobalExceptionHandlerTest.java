package ch.nacht.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests des {@link GlobalExceptionHandler}.
 *
 * <p>Der Fokus liegt auf der Form der Antwort: Die Angular-Masken zeigen {@code error.error} an,
 * ein Objekt-Rumpf erscheint dort als {@code [object Object]}. Deshalb ist der Klartext-Rumpf
 * keine Geschmacksfrage, sondern die Bedingung dafuer, dass der Benutzer den Grund erfaehrt.
 */
public class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDataIntegrityViolation_ReturnsConflict() {
        ResponseEntity<String> antwort = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("doppelter Schluessel"));

        assertEquals(HttpStatus.CONFLICT, antwort.getStatusCode());
    }

    @Test
    void handleDataIntegrityViolation_BodyIsReadableText() {
        ResponseEntity<String> antwort = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("doppelter Schluessel"));

        assertNotNull(antwort.getBody());
        assertFalse(antwort.getBody().isBlank());
    }

    @Test
    void handleDataIntegrityViolation_BodyHidesDatabaseInternals() {
        ResponseEntity<String> antwort = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException(
                        "ERROR: duplicate key value violates unique constraint "
                                + "\"uq_debitor_mieter_von_herkunft_org\""));

        assertNotNull(antwort.getBody());
        assertFalse(antwort.getBody().contains("uq_debitor_mieter_von_herkunft_org"));
        assertFalse(antwort.getBody().contains("constraint"));
    }
}
