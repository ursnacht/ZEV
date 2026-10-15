package ch.nacht.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uebersetzungs-Keys der Mengeneinheit — die eine Zuordnung fuer ZEV- und NK-Rechnungen.
 *
 * <p>Vorher stand sie in {@code RechnungService} (switch) und {@code NkRechnungService} (Map)
 * doppelt; {@code TAG} musste an beiden Stellen nachgetragen werden.
 */
public class MengeneinheitTest {

    @Test
    void keyFuer_EinzahlUndMehrzahl() {
        assertEquals("MONAT", Mengeneinheit.keyFuer("MONAT", BigDecimal.ONE));
        assertEquals("MONATE", Mengeneinheit.keyFuer("MONAT", new BigDecimal("3")));
        assertEquals("TAG", Mengeneinheit.keyFuer("TAG", BigDecimal.ONE));
        assertEquals("TAGE", Mengeneinheit.keyFuer("TAG", new BigDecimal("92")));
        assertEquals("KWH", Mengeneinheit.keyFuer("KWH", new BigDecimal("1")));
        assertEquals("STUECK", Mengeneinheit.keyFuer("STUECK", new BigDecimal("4")));
    }

    @Test
    void keyFuer_OhneEinheit_ReturnsNull() {
        assertNull(Mengeneinheit.keyFuer(null, BigDecimal.TEN));
    }

    /** Kleinschreibung aus aelteren Daten wird wie die Konstante behandelt. */
    @Test
    void keyFuer_Kleinschreibung_WieKonstante() {
        assertEquals("MONATE", Mengeneinheit.keyFuer("monat", new BigDecimal("2")));
    }

    /** Ein unbekannter Wert bleibt, wie er ist - der Text ist dann selbst der Key. */
    @Test
    void keyFuer_UnbekannterWert_BleibtUnveraendert() {
        assertEquals("PAUSCHALE", Mengeneinheit.keyFuer("Pauschale", BigDecimal.ONE));
    }

    /** NK-Rechnungen beschriften die Spalte ohne Menge - immer in der Mehrzahl. */
    @Test
    void mehrzahlKey_JeEinheit() {
        assertEquals("KWH", Mengeneinheit.KWH.mehrzahlKey());
        assertEquals("MONATE", Mengeneinheit.MONAT.mehrzahlKey());
        assertEquals("TAGE", Mengeneinheit.TAG.mehrzahlKey());
        assertEquals("STUECK", Mengeneinheit.STUECK.mehrzahlKey());
        assertEquals("M3", Mengeneinheit.M3.mehrzahlKey());
        assertEquals("M2", Mengeneinheit.M2.mehrzahlKey());
        assertEquals("CHF", Mengeneinheit.CHF.mehrzahlKey());
    }

    /** Jeder Wert hat beide Keys - ein neuer Wert ohne Zuordnung faellt hier auf. */
    @Test
    void jedeEinheit_HatBeideKeys() {
        for (Mengeneinheit einheit : Mengeneinheit.values()) {
            assertNotNull(einheit.mehrzahlKey(), einheit.name());
            assertNotNull(einheit.key(BigDecimal.ONE), einheit.name());
        }
    }
}
