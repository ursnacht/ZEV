package ch.nacht.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit-Tests für die Formatierer des Schweizer QR-Zahlteils in {@link RechnungPdfService}.
 *
 * <p>Beide Methoden sind {@code static} und rein — sie brauchen weder Spring noch das
 * Jasper-Template. Getestet wird genau das, was {@code JasperTemplateCompileTest} <b>nicht</b>
 * abdeckt: Der Kompiliertest prüft, dass das Template übersetzbar ist, nicht was darin steht.
 *
 * <p>Warum das zählt: Ein falsch formatierter Betrag oder eine falsch gruppierte IBAN im
 * QR-Zahlteil ergibt eine Rechnung, die der Zahlungsempfänger nicht einlesen kann — der Fehler
 * fällt erst beim Kunden auf.
 */
public class RechnungPdfServiceTest {

    // ==================== formatIban ====================

    @Test
    void formatIban_SchweizerIban_GruppiertZuViererbloecken() {
        // Beispiel aus dem Javadoc der Methode
        assertThat(RechnungPdfService.formatIban("CH1234567890123456789"),
                is("CH12 3456 7890 1234 5678 9"));
    }

    @Test
    void formatIban_BereitsFormatiert_BleibtUnveraendert() {
        // Vorhandene Leerzeichen werden zuerst entfernt, danach neu gruppiert
        assertThat(RechnungPdfService.formatIban("CH12 3456 7890 1234 5678 9"),
                is("CH12 3456 7890 1234 5678 9"));
    }

    @Test
    void formatIban_UnregelmaessigeLeerzeichen_WerdenNormalisiert() {
        assertThat(RechnungPdfService.formatIban("CH12   34567890 123456789"),
                is("CH12 3456 7890 1234 5678 9"));
    }

    @Test
    void formatIban_Null_ReturnsEmptyString() {
        // Kein NPE im PDF-Lauf: Ohne IBAN bleibt das Feld leer
        assertThat(RechnungPdfService.formatIban(null), is(""));
    }

    @Test
    void formatIban_LeererString_ReturnsEmptyString() {
        assertThat(RechnungPdfService.formatIban(""), is(""));
    }

    @ParameterizedTest
    @CsvSource({
            "'C', 'C'",
            "'CH', 'CH'",
            "'CH12', 'CH12'",
            "'CH123', 'CH12 3'",
            "'CH1234', 'CH12 34'"
    })
    void formatIban_KuerzerAlsEinBlock_GruppiertKorrekt(String eingabe, String erwartet) {
        assertThat(RechnungPdfService.formatIban(eingabe), is(erwartet));
    }

    // ==================== formatBetragQrBill ====================

    @ParameterizedTest
    @CsvSource({
            "0,           '0.00'",
            "5,           '5.00'",
            "12.5,        '12.50'",
            "999.99,      '999.99'",
            "1000,        '1 000.00'",
            "1234.5,      '1 234.50'",
            "12345.67,    '12 345.67'",
            "1234567.89,  '1 234 567.89'"
    })
    void formatBetragQrBill_FormatiertMitLeerzeichenUndZweiNachkommastellen(double betrag, String erwartet) {
        assertThat(RechnungPdfService.formatBetragQrBill(betrag), is(erwartet));
    }

    @Test
    void formatBetragQrBill_TausendertrennerIstEinNormalesLeerzeichen() {
        // Entscheidend für den QR-Zahlteil: ein gewöhnliches Leerzeichen (U+0020). Ein geschütztes
        // Leerzeichen (U+00A0) sieht identisch aus, wird von Lesegeräten aber nicht akzeptiert -
        // deshalb wird hier das Zeichen selbst geprüft und nicht nur die Zeichenkette verglichen.
        String formatiert = RechnungPdfService.formatBetragQrBill(1234.50);

        assertEquals(' ', formatiert.charAt(1),
                "Tausendertrenner muss ein normales Leerzeichen sein, kein geschütztes");
        assertThat(formatiert, is("1 234.50"));
    }

    @Test
    void formatBetragQrBill_DezimaltrennerIstEinPunkt() {
        String formatiert = RechnungPdfService.formatBetragQrBill(7.25);

        assertEquals('.', formatiert.charAt(1),
                "Dezimaltrenner muss ein Punkt sein - ein Komma wäre im QR-Zahlteil unzulässig");
    }

    @Test
    void formatBetragQrBill_MehrAlsZweiNachkommastellen_WirdGerundet() {
        // Rechnungsbetraege sind auf 5 Rappen gerundet; ein laengerer Wert darf den Zahlteil
        // trotzdem nicht sprengen.
        assertThat(RechnungPdfService.formatBetragQrBill(10.999), is("11.00"));
        assertThat(RechnungPdfService.formatBetragQrBill(10.001), is("10.00"));
    }

    @Test
    void formatBetragQrBill_Null_FormatiertAlsNullKommaNull() {
        // Eine Rechnung ueber 0.00 ist fachlich moeglich (alle Mengen null) und darf kein
        // leeres Betragsfeld ergeben
        assertThat(RechnungPdfService.formatBetragQrBill(0.0), is("0.00"));
    }
}
