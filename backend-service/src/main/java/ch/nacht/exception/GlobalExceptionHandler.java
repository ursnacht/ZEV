package ch.nacht.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * Handles missing tariff coverage when generating invoices. Returns HTTP 400 with
     * a translation key plus the structured gaps, so the frontend can render the same
     * translated validation feedback as the tariff management page (instead of a 500).
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(TarifLueckenException.class)
    public ResponseEntity<Map<String, Object>> handleTarifLueckenException(
            TarifLueckenException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "FEHLER_TARIF_LUECKEN");
        body.put("luecken", ex.getLuecken());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles state validation errors (e.g. settings not yet configured). Returns
     * HTTP 400 with the exception message instead of a generic 500.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(
            IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles features disabled via a tenant feature flag. Returns HTTP 403 with a
     * translation key so the frontend can render a translated message.
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<Map<String, Object>> handleFeatureDisabledException(
            FeatureDisabledException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(NoOrganizationException.class)
    public ResponseEntity<Map<String, Object>> handleNoOrganizationException(
            NoOrganizationException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "NO_ORGANIZATION");
        error.put("message", ex.getMessage());
        error.put("action", "LOGOUT");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
}
