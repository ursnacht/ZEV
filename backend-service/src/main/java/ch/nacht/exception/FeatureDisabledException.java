package ch.nacht.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception die geworfen wird, wenn eine Funktion über ein deaktiviertes Feature-Flag
 * gesperrt ist. Führt zu HTTP 403. Die Nachricht ist ein Übersetzungs-Key, den das
 * Frontend anzeigen kann.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class FeatureDisabledException extends RuntimeException {

    public FeatureDisabledException(String messageKey) {
        super(messageKey);
    }
}
