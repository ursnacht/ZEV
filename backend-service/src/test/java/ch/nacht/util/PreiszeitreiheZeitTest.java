package ch.nacht.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit-Tests für {@link PreiszeitreiheZeit}.
 *
 * <p>Diese Klasse ist der einzige Ort, an dem die Preiszeitreihe zwischen Speicherung (UTC) und
 * Anzeige (Europe/Zurich) umrechnet. Ihre Fehler fallen an genau <b>zwei Tagen im Jahr</b> auf —
 * den Umstellungstagen —, und genau die prüfen diese Tests. Ohne sie bliebe die Begründung für die
 * UTC-Speicherung eine Behauptung.
 */
class PreiszeitreiheZeitTest {

    // ==================== tagesbeginnUtc ====================

    @Test
    void tagesbeginnUtc_Winterzeit_ZiehtEineStundeAb() {
        // Januar: Europe/Zurich = UTC+1, Mitternacht lokal ist 23:00 UTC des Vortags.
        LocalDateTime utc = PreiszeitreiheZeit.tagesbeginnUtc(LocalDate.of(2026, 1, 15));

        assertEquals(LocalDateTime.of(2026, 1, 14, 23, 0), utc);
    }

    @Test
    void tagesbeginnUtc_Sommerzeit_ZiehtZweiStundenAb() {
        // Juli: Europe/Zurich = UTC+2.
        LocalDateTime utc = PreiszeitreiheZeit.tagesbeginnUtc(LocalDate.of(2026, 7, 15));

        assertEquals(LocalDateTime.of(2026, 7, 14, 22, 0), utc);
    }

    // ==================== tagesendeUtc ====================

    @Test
    void tagesendeUtc_IstBeginnDesFolgetags() {
        LocalDateTime utc = PreiszeitreiheZeit.tagesendeUtc(LocalDate.of(2026, 1, 15));

        // Ausschliessende Obergrenze: 16.01. 00:00 lokal = 15.01. 23:00 UTC.
        assertEquals(LocalDateTime.of(2026, 1, 15, 23, 0), utc);
    }

    @Test
    void tagesendeUtc_UeberMonatsgrenze_RechnetKorrekt() {
        LocalDateTime utc = PreiszeitreiheZeit.tagesendeUtc(LocalDate.of(2026, 1, 31));

        assertEquals(LocalDateTime.of(2026, 1, 31, 23, 0), utc);
    }

    // ==================== Spanne eines Tages ====================

    @Test
    void spanne_NormalerTag_Umfasst24Stunden() {
        LocalDate tag = LocalDate.of(2026, 5, 20);

        long stunden = java.time.Duration.between(
                PreiszeitreiheZeit.tagesbeginnUtc(tag), PreiszeitreiheZeit.tagesendeUtc(tag)).toHours();

        assertEquals(24, stunden);
    }

    /**
     * Umstellung auf Sommerzeit (letzter Sonntag im März): Der Tag hat <b>23</b> Stunden, also 92
     * Viertelstundenwerte. Genau deshalb speichert die Reihe UTC — in Ortszeit fehlte die Stunde
     * 02:00–03:00 im Schlüssel.
     */
    @Test
    void spanne_UmstellungAufSommerzeit_Umfasst23Stunden() {
        LocalDate tag = LocalDate.of(2026, 3, 29);

        long stunden = java.time.Duration.between(
                PreiszeitreiheZeit.tagesbeginnUtc(tag), PreiszeitreiheZeit.tagesendeUtc(tag)).toHours();

        assertEquals(23, stunden);
        assertEquals(92, stunden * 4);
    }

    /**
     * Umstellung auf Winterzeit (letzter Sonntag im Oktober): Der Tag hat <b>25</b> Stunden, also
     * 100 Viertelstundenwerte. In Ortszeit gäbe es die Stunde 02:00–03:00 zweimal — vier Werte
     * würden vier andere überschreiben.
     */
    @Test
    void spanne_UmstellungAufWinterzeit_Umfasst25Stunden() {
        LocalDate tag = LocalDate.of(2026, 10, 25);

        long stunden = java.time.Duration.between(
                PreiszeitreiheZeit.tagesbeginnUtc(tag), PreiszeitreiheZeit.tagesendeUtc(tag)).toHours();

        assertEquals(25, stunden);
        assertEquals(100, stunden * 4);
    }

    // ==================== nachOrtszeit ====================

    @Test
    void nachOrtszeit_Winterzeit_AddiertEineStunde() {
        LocalDateTime ortszeit =
                PreiszeitreiheZeit.nachOrtszeit(LocalDateTime.of(2026, 1, 15, 12, 30));

        assertEquals(LocalDateTime.of(2026, 1, 15, 13, 30), ortszeit);
    }

    @Test
    void nachOrtszeit_Sommerzeit_AddiertZweiStunden() {
        LocalDateTime ortszeit =
                PreiszeitreiheZeit.nachOrtszeit(LocalDateTime.of(2026, 7, 15, 12, 30));

        assertEquals(LocalDateTime.of(2026, 7, 15, 14, 30), ortszeit);
    }

    @Test
    void nachOrtszeit_UeberTagesgrenze_VerschiebtDatum() {
        LocalDateTime ortszeit =
                PreiszeitreiheZeit.nachOrtszeit(LocalDateTime.of(2026, 7, 15, 22, 15));

        assertEquals(LocalDateTime.of(2026, 7, 16, 0, 15), ortszeit);
    }

    @Test
    void nachOrtszeit_Null_GibtNullZurueck() {
        assertNull(PreiszeitreiheZeit.nachOrtszeit(null));
    }

    @Test
    void nachOrtszeit_IstUmkehrungVonTagesbeginn() {
        LocalDate tag = LocalDate.of(2026, 7, 15);

        LocalDateTime zurueck = PreiszeitreiheZeit.nachOrtszeit(PreiszeitreiheZeit.tagesbeginnUtc(tag));

        assertEquals(tag.atStartOfDay(), zurueck);
    }
}
