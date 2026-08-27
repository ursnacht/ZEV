package ch.nacht.exception;

/**
 * Die Quelle der Einspeisepreise hat versagt: nicht erreichbar, Zeitueberschreitung, HTTP-Fehler,
 * unlesbares Format, fremde Einheit oder unplausible Menge (Specs/Preiszeitreihe.md, FR-7).
 *
 * <p>Bewusst <b>ohne</b> {@code @ResponseStatus}: Der Controller bildet sie auf {@code 502 Bad
 * Gateway} mit Klartext-Rumpf ab. {@code 502} statt {@code 400}, weil der Fehler nicht beim
 * Aufrufer liegt — er hat alles richtig gemacht, das fremde System nicht.
 *
 * <p>Die Meldung ist fuer den Benutzer bestimmt und nennt deshalb keine internen Details.
 */
public class PreiszeitreiheQuelleException extends RuntimeException {

    public PreiszeitreiheQuelleException(String message) {
        super(message);
    }

    public PreiszeitreiheQuelleException(String message, Throwable cause) {
        super(message, cause);
    }
}
