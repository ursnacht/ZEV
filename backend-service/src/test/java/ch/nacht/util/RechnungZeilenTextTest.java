package ch.nacht.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RechnungZeilenTextTest {

    private static final LocalDate Q3_VON = LocalDate.of(2026, 7, 1);
    private static final LocalDate Q3_BIS = LocalDate.of(2026, 9, 30);

    @Test
    void bezeichnung_ZeitraumGleichRechnungszeitraum_OhneZeitraum() {
        assertEquals("vZEV PV Tarif",
                RechnungZeilenText.bezeichnung("vZEV PV Tarif", Q3_VON, Q3_BIS, Q3_VON, Q3_BIS));
    }

    @Test
    void bezeichnung_TarifwechselImQuartal_MitZeitraum() {
        assertEquals("Strombezug EWB (01.07.2026 - 15.08.2026)",
                RechnungZeilenText.bezeichnung("Strombezug EWB",
                        Q3_VON, LocalDate.of(2026, 8, 15), Q3_VON, Q3_BIS));
    }

    @Test
    void bezeichnung_NurBeginnWeichtAb_MitZeitraum() {
        assertEquals("Grundgebühr (01.08.2026 - 30.09.2026)",
                RechnungZeilenText.bezeichnung("Grundgebühr",
                        LocalDate.of(2026, 8, 1), Q3_BIS, Q3_VON, Q3_BIS));
    }

    @Test
    void bezeichnung_OhneZeitraumDerPosition_NurBezeichnung() {
        assertEquals("Ladestation", RechnungZeilenText.bezeichnung("Ladestation", null, null, Q3_VON, Q3_BIS));
    }

    @Test
    void bezeichnung_OhneRechnungszeitraum_MitZeitraum() {
        assertEquals("Ladestation (01.07.2026 - 30.09.2026)",
                RechnungZeilenText.bezeichnung("Ladestation", Q3_VON, Q3_BIS, null, null));
    }

    @Test
    void bezeichnung_Null_LeererText() {
        assertEquals("", RechnungZeilenText.bezeichnung(null, Q3_VON, Q3_BIS, Q3_VON, Q3_BIS));
    }

    @Test
    void bezeichnung_ZuLangFuerEineZeile_ZeitraumInZweiterZeile() {
        String lang = "Strombezug Hochtarif Netz inkl. Abgaben und Gebühr"; // 50 Zeichen
        assertEquals(lang + "\n(16.08.2026 - 30.09.2026)",
                RechnungZeilenText.bezeichnung(lang, LocalDate.of(2026, 8, 16), Q3_BIS, Q3_VON, Q3_BIS));
    }

    @Test
    void bezeichnung_GenauSchwelle_EineZeile() {
        // 34 + 1 + 25 = 60 Zeichen: passt noch auf eine Zeile
        String bezeichnung = "x".repeat(RechnungZeilenText.ZEICHEN_EINE_ZEILE - 26);
        assertEquals(bezeichnung + " (16.08.2026 - 30.09.2026)",
                RechnungZeilenText.bezeichnung(bezeichnung, LocalDate.of(2026, 8, 16), Q3_BIS, Q3_VON, Q3_BIS));
    }

    @Test
    void bezeichnung_EinZeichenUeberSchwelle_ZweiZeilen() {
        String bezeichnung = "x".repeat(RechnungZeilenText.ZEICHEN_EINE_ZEILE - 25);
        assertEquals(bezeichnung + "\n(16.08.2026 - 30.09.2026)",
                RechnungZeilenText.bezeichnung(bezeichnung, LocalDate.of(2026, 8, 16), Q3_BIS, Q3_VON, Q3_BIS));
    }
}
