package ch.nacht.exception;

import java.util.List;

/**
 * Tariff coverage gaps for a single period (quarter or year), used by the tariff
 * validation in the tariff management page.
 *
 * @param periode Language-neutral period label, e.g. "Q1/2024" or "2024".
 * @param luecken The coverage gaps found within this period.
 */
public record TarifLueckePeriode(String periode, List<TarifLuecke> luecken) {
}
