package ch.nacht.util;

import java.util.Locale;

/**
 * Locale-unabhängige Zahlenformatierung für die PDF-Reports (Spec Statistik-Kennzahlen.md):
 * <b>Punkt</b> als Dezimal-, <b>Hochkomma (')</b> als Tausendertrennzeichen (Schweizer Konvention).
 * Basiert auf {@link String#format} mit {@link Locale#ROOT} (Dezimal '.', Gruppierung ',') und
 * ersetzt die Gruppierung durch das Hochkomma – damit unabhängig von der Server-/Report-Locale.
 */
public final class PdfNumberFormat {

    private PdfNumberFormat() {
    }

    /**
     * Zahl mit frei waehlbarer Nachkommastellenzahl; {@code null} → leerer String.
     * Beispiel: {@code decimals(1234.5, 2)} → {@code 1'234.50}.
     *
     * @param value Wert
     * @param nachkommastellen Anzahl Nachkommastellen
     * @return Formatierter Wert im Schweizer Format
     */
    public static String decimals(Number value, int nachkommastellen) {
        if (value == null) {
            return "";
        }
        return String.format(Locale.ROOT, "%,." + nachkommastellen + "f", value.doubleValue())
                .replace(',', '\'');
    }

    /** Zahl mit 3 Nachkommastellen; {@code null} → leerer String. Beispiel: {@code 1'234.567}. */
    public static String kwh(Number value) {
        return decimals(value, 3);
    }

    /** Geldbetrag mit 2 Nachkommastellen; {@code null} → leerer String. Beispiel: {@code 1'234.50}. */
    public static String chf(Number value) {
        return decimals(value, 2);
    }

    /** Wie {@link #kwh(Number)}, aber mit Vorzeichen (auch bei ≥ 0). {@code null} → leerer String. */
    public static String kwhSigned(Number value) {
        if (value == null) {
            return "";
        }
        return String.format(Locale.ROOT, "%+,.3f", value.doubleValue()).replace(',', '\'');
    }

    /** Anteil (0..1) als Prozentwert mit 1 Nachkommastelle; {@code null} → {@code "–"}. */
    public static String percent(Double fraction) {
        if (fraction == null) {
            return "–";
        }
        return String.format(Locale.ROOT, "%,.1f", fraction * 100).replace(',', '\'') + " %";
    }
}
