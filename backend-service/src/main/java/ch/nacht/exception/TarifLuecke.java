package ch.nacht.exception;

/**
 * A single tariff coverage gap, in a language-neutral form so the frontend can
 * render a translated message. Part of the structured payload of a tariff
 * validation error (see {@link TarifLueckenException}).
 *
 * @param tarifTyp Tariff type code ("ZEV" or "VNB"); the frontend maps it to a
 *                 translation key (e.g. {@code TARIF_LUECKE_ZEV}).
 * @param datum    First uncovered date in Swiss format (dd.MM.yyyy).
 * @param weitere  Whether further gaps exist beyond the first date.
 */
public record TarifLuecke(String tarifTyp, String datum, boolean weitere) {
}
