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

    // ==================== decimals ====================

    @Test
    void decimals_ZeroDecimals_RoundsToWholeNumber() {
        // Mengenspalte der Rechnung (rechnung.jrxml): 0 Nachkommastellen
        assertEquals("121", PdfNumberFormat.decimals(120.567, 0));
        assertEquals("120", PdfNumberFormat.decimals(120.4, 0));
    }

    @Test
    void decimals_FiveDecimals_KeepsTariffPrecision() {
        // Preisspalte der Rechnung: 5 Nachkommastellen wie am Tarif erfasst
        assertEquals("0.35000", PdfNumberFormat.decimals(0.35, 5));
    }

    @Test
    void decimals_LargeValue_UsesApostropheAsThousandsSeparator() {
        assertEquals("1'234.50", PdfNumberFormat.decimals(1234.5, 2));
        assertEquals("1'234'568", PdfNumberFormat.decimals(1234567.8, 0));
    }

    @Test
    void decimals_NegativeValue_KeepsMinusSign() {
        assertEquals("-42.50", PdfNumberFormat.decimals(-42.5, 2));
    }

    @Test
    void decimals_Null_ReturnsEmptyString() {
        assertEquals("", PdfNumberFormat.decimals(null, 2));
    }

    @Test
    void decimals_BigDecimalInput_FormattedLikeDouble() {
        assertEquals("120.500", PdfNumberFormat.decimals(new java.math.BigDecimal("120.500"), 3));
    }

    // ==================== chf ====================

    @Test
    void chf_Value_TwoDecimals() {
        assertEquals("42.35", PdfNumberFormat.chf(42.35));
    }

    @Test
    void chf_Zero_ReturnsZeroWithTwoDecimals() {
        assertEquals("0.00", PdfNumberFormat.chf(0.0));
    }

    @Test
    void chf_LargeValue_UsesApostropheGrouping() {
        assertEquals("1'234.50", PdfNumberFormat.chf(1234.5));
    }

    @Test
    void chf_NegativeRounding_KeepsMinusSign() {
        // Rundungszeile der Rechnung kann negativ sein
        assertEquals("-0.01", PdfNumberFormat.chf(-0.01));
    }

    @Test
    void chf_Null_ReturnsEmptyString() {
        assertEquals("", PdfNumberFormat.chf(null));
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
