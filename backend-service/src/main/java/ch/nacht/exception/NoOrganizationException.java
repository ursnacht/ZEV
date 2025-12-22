package ch.nacht.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception die geworfen wird, wenn keine Organisation im JWT-Token enthalten ist.
 * Der Benutzer muss ausgeloggt werden und einer Organisation zugewiesen werden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class NoOrganizationException extends RuntimeException {

    public NoOrganizationException(String message) {
        super(message);
    }

    public NoOrganizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
