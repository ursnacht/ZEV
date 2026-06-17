package ch.nacht.exception;

import java.util.List;

/**
 * Thrown when tariffs do not cover the requested period without gaps. Carries the
 * gaps in a language-neutral form so {@code GlobalExceptionHandler} can return them
 * as structured data (HTTP 400) for the frontend to translate.
 */
public class TarifLueckenException extends RuntimeException {

    private final transient List<TarifLuecke> luecken;

    public TarifLueckenException(List<TarifLuecke> luecken) {
        super("Tariff coverage gaps: " + luecken.size());
        this.luecken = luecken;
    }

    public List<TarifLuecke> getLuecken() {
        return luecken;
    }
}
