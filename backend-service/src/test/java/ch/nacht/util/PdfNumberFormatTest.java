package ch.nacht.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit-Tests für {@link PdfNumberFormat} (Spec Statistik-Kennzahlen.md):
 * locale-unabhängige Formatierung mit Punkt-Dezimal- und Hochkomma-Tausendertrennzeichen,
 * Rundung (3 NKS bei kWh, 1 NKS bei Prozent), Vorzeichen bei {@code kwhSigned} und null-Handling.
 */
public class PdfNumberFormatTest {

    /** en-dash (U+2013), den {@link PdfNumberFormat#percent} bei {@code null} liefert. */
    private static final String DASH = "–";

    // ==================== kwh ====================

    @Test
    void kwh_ValueBelow1000_ThreeDecimalsNoGrouping() {
        assertEquals("999.500", PdfNumberFormat.kwh(999.5));
    }

    @Test
    void kwh_ValueAtLeast1000_UsesApostropheAsThousandsSeparator() {
        assertEquals("1'234.567", PdfNumberFormat.kwh(1234.567));
    }

    @Test
    void kwh_LargeValue_GroupsEveryThreeDigits() {
        assertEquals("1'234'567.000", PdfNumberFormat.kwh(1234567.0));
    }

    @Test
    void kwh_Zero_ReturnsZeroWithThreeDecimals() {
        assertEquals("0.000", PdfNumberFormat.kwh(0.0));
    }

    @Test
    void kwh_RoundsToThreeDecimalsHalfUp() {
        // 4. NKS = 9 → aufrunden
        assertEquals("2.719", PdfNumberFormat.kwh(2.7189));
        // 4. NKS = 4 → abrunden
        assertEquals("2.718", PdfNumberFormat.kwh(2.71844));
    }

    @Test
    void kwh_Null_ReturnsEmptyString() {
        assertEquals("", PdfNumberFormat.kwh(null));
    }

    @Test
    void kwh_NegativeValue_KeepsMinusSign() {
        assertEquals("-1'000.000", PdfNumberFormat.kwh(-1000.0));
    }

    // ==================== kwhSigned ====================

    @Test
    void kwhSigned_PositiveValue_HasPlusSign() {
        assertEquals("+300.000", PdfNumberFormat.kwhSigned(300.0));
    }

    @Test
    void kwhSigned_Zero_HasPlusSign() {
        assertEquals("+0.000", PdfNumberFormat.kwhSigned(0.0));
    }

    @Test
    void kwhSigned_NegativeValue_HasMinusSign() {
        assertEquals("-42.500", PdfNumberFormat.kwhSigned(-42.5));
    }

    @Test
    void kwhSigned_PositiveWithGrouping_HasPlusAndApostrophe() {
        assertEquals("+1'234.500", PdfNumberFormat.kwhSigned(1234.5));
    }

    @Test
    void kwhSigned_Null_ReturnsEmptyString() {
        assertEquals("", PdfNumberFormat.kwhSigned(null));
    }

    // ==================== percent ====================

    @Test
    void percent_Fraction_OneDecimalWithPercentSign() {
        assertEquals("60.0 %", PdfNumberFormat.percent(0.6));
    }

    @Test
    void percent_Zero_ReturnsZeroPercent() {
        assertEquals("0.0 %", PdfNumberFormat.percent(0.0));
    }

    @Test
    void percent_One_ReturnsHundredPercent() {
        assertEquals("100.0 %", PdfNumberFormat.percent(1.0));
    }

    @Test
    void percent_RoundsToOneDecimalHalfUp() {
        // 3/7 = 0.428571… → 42.9 % (Spec-Beispiel Round-Trip-Wirkungsgrad)
        assertEquals("42.9 %", PdfNumberFormat.percent(3.0 / 7.0));
    }

    @Test
    void percent_ValueOverHundred_UsesApostropheGrouping() {
        assertEquals("1'000.0 %", PdfNumberFormat.percent(10.0));
    }

    @Test
    void percent_Null_ReturnsDash() {
        assertEquals(DASH, PdfNumberFormat.percent(null));
    }
}
