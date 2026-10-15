package ch.nacht.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZeitraumTest {

    @Test
    void tageInklusive_Gemeinjahr_365() {
        assertEquals(365, Zeitraum.tageInklusive(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void tageInklusive_Schaltjahr_366() {
        assertEquals(366, Zeitraum.tageInklusive(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31)));
    }

    /** Beide Enden zaehlen: ein einzelner Tag ist 1, nicht 0. */
    @Test
    void tageInklusive_EinTag_1() {
        assertEquals(1, Zeitraum.tageInklusive(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)));
    }

    @Test
    void tageInklusive_Quartal_92() {
        assertEquals(92, Zeitraum.tageInklusive(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
    }
}
