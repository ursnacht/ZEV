package ch.nacht.service;

import ch.nacht.entity.Steuerregel;
import ch.nacht.entity.Steuerzustand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Die Regel der Einspeisesteuerung (Specs/Einspeisesteuerung.md, FR-2).
 *
 * <p><b>Ohne Mocks und ohne Spring:</b> {@link SteuerRegelService} ist eine reine Funktion — genau
 * dafür wurde sie von der Datenbeschaffung getrennt. Die Fachlichkeit lässt sich damit vollständig
 * prüfen, ohne dass ein einziger Messwert existieren muss.
 *
 * <p><b>Warum diese Klasse dringend war:</b> Auf der Entwicklungsumgebung entsteht nie ein
 * Überschuss — dort greift immer Regel 2, und die Regeln 1, 3, 4 und 5 lassen sich durch Zusehen
 * nicht prüfen. Der Fehler in Regel 4 (siehe unten) fiel erst im Produktivbetrieb auf, an einer
 * Tabelle mit echten Preisen.
 */
public class SteuerRegelServiceTest {

    /** Vorgabe des Mandanten (SteuerKonfigurationDTO). */
    private static final BigDecimal SCHWELLWERT = new BigDecimal("0.165");
    private static final BigDecimal SPEICHERWERT = new BigDecimal("0.31");

    /** Produktion deutlich über Verbrauch — damit die Regeln 3 bis 5 überhaupt erreicht werden. */
    private static final BigDecimal PRODUKTION = new BigDecimal("4.000");
    private static final BigDecimal VERBRAUCH = new BigDecimal("0.300");

    private SteuerRegelService steuerRegelService;

    @BeforeEach
    void setUp() {
        steuerRegelService = new SteuerRegelService();
    }

    // ==================== Regel 1: PREIS_NEGATIV ====================

    @Test
    void entscheide_NegativerPreis_SperrtEinspeisungUndLaesstLadungFrei() {
        SteuerRegelService.Entscheid entscheid = entscheide("-0.001", "0.100");

        assertEquals(Steuerregel.PREIS_NEGATIV, entscheid.regel());
        // Die Batterie darf laden - sie nimmt Energie auf, die sonst abgeregelt wuerde.
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
        assertEquals(Steuerzustand.GESPERRT, entscheid.einspeisung());
    }

    @Test
    void entscheide_PreisGenauNull_IstNichtNegativ() {
        // Grenzfall: signum() == 0 ist keine Sperre. Ein Preis von 0 kostet nichts.
        // Der Tiefstpreis des Resttages liegt DARUNTER, damit Regel 4 greift und sichtbar wird,
        // dass Regel 1 eben nicht ausgeloest hat - sonst liefe der Test auch bei falschem
        // Vorzeichenvergleich durch.
        SteuerRegelService.Entscheid entscheid = entscheide("0.000", "-0.010");

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
    }

    // ==================== Regel 2: KEIN_UEBERSCHUSS ====================

    @Test
    void entscheide_ProduktionUnterVerbrauch_KeinUeberschussUndBeideFrei() {
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.100"), new BigDecimal("0.010"),
                        new BigDecimal("1.000"), new BigDecimal("2.000")),
                SCHWELLWERT, SPEICHERWERT);

        assertEquals(Steuerregel.KEIN_UEBERSCHUSS, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
        assertEquals(0, BigDecimal.ZERO.compareTo(entscheid.ueberschuss()));
    }

    @Test
    void entscheide_NegativeProducerWerteAlsBetrag_ErgebenUeberschuss() {
        // Spec AK: Bei Producer-Betrag 10 und Consumer 4 sind es 6 - nicht 0 und nicht -14.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.100"), null,
                        new BigDecimal("10"), new BigDecimal("4")),
                SCHWELLWERT, SPEICHERWERT);

        assertEquals(0, new BigDecimal("6").compareTo(entscheid.ueberschuss()));
    }

    // ==================== Regel 3: EINSPEISEN_LOHNT ====================

    @Test
    void entscheide_PreisUeberSpeicherwert_SperrtLadung() {
        SteuerRegelService.Entscheid entscheid = entscheide("0.320", "0.100");

        assertEquals(Steuerregel.EINSPEISEN_LOHNT, entscheid.regel());
        assertEquals(Steuerzustand.GESPERRT, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
    }

    @Test
    void entscheide_PreisGenauAufSpeicherwert_LoestAus() {
        // compareTo >= 0: Gleichstand zaehlt als "lohnt".
        SteuerRegelService.Entscheid entscheid = entscheide("0.31", "0.100");

        assertEquals(Steuerregel.EINSPEISEN_LOHNT, entscheid.regel());
    }

    // ==================== Regel 4: WARTEN_AUF_TAL ====================

    @Test
    void entscheide_SpaeterGuenstigerUndUnterSchwellwert_SperrtLadung() {
        // 12:45 bei Hene am 14.09.2026: jetzt 0.175, heute noch 0.161.
        SteuerRegelService.Entscheid entscheid = entscheide("0.175", "0.161");

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
        assertEquals(Steuerzustand.GESPERRT, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
    }

    /**
     * <b>Der Regressionstest zum Fehler vom 14.09.2026.</b>
     *
     * <p>Steht der aktuelle Preis bereits auf dem Tiefstpreis des Resttages, gibt es nichts, worauf
     * sich warten liesse — die Steuerung wartete auf sich selbst. Bei Hene sperrte sie so von 13:00
     * bis 14:30 das Laden im Preistal (27.9 kWh Überschuss, mehr als die Batterie fasst) und gab es
     * ab 14:45 frei, als der Preis wieder gestiegen war.
     *
     * <p>Die Spec beschrieb es immer richtig („statt sie jetzt mit <b>teurerem</b> Strom zu
     * füllen"); nur die Regeltabelle und der Code verglichen ausschliesslich mit dem Schwellwert.
     */
    @Test
    void entscheide_PreisBereitsAufTagestief_LaedtStattZuWarten() {
        SteuerRegelService.Entscheid entscheid = entscheide("0.161", "0.161");

        assertEquals(Steuerregel.LADEN, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
    }

    @Test
    void entscheide_SpaeterTeurerAberUnterSchwellwert_LaedtJetzt() {
        // Der Rest des Tages liegt unter dem Schwellwert, aber ueber dem aktuellen Preis:
        // Warten wuerde teurer.
        SteuerRegelService.Entscheid entscheid = entscheide("0.150", "0.160");

        assertEquals(Steuerregel.LADEN, entscheid.regel());
    }

    @Test
    void entscheide_SpaeterGuenstigerAberUeberSchwellwert_LaedtJetzt() {
        // Das kommende Tief lohnt das Warten nicht - es liegt ueber dem Schwellwert.
        SteuerRegelService.Entscheid entscheid = entscheide("0.250", "0.200");

        assertEquals(Steuerregel.LADEN, entscheid.regel());
    }

    @Test
    void entscheide_OhnePreisJetzt_SperrtNicht() {
        // Ein Vergleich ohne die eine Seite ist keiner; Nichtstun ist der sichere Zustand.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(null, new BigDecimal("0.010"),
                        PRODUKTION, VERBRAUCH),
                SCHWELLWERT, SPEICHERWERT);

        assertEquals(Steuerregel.LADEN, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
    }

    @Test
    void entscheide_OhneTiefstpreisRest_SperrtNicht() {
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.100"), null,
                        PRODUKTION, VERBRAUCH),
                SCHWELLWERT, SPEICHERWERT);

        assertEquals(Steuerregel.LADEN, entscheid.regel());
    }

    @Test
    void entscheide_NegativerSchwellwert_IstErlaubt() {
        // Negative Preise sind genau die Stunden, fuer die sich eine Steuerung lohnt.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.050"), new BigDecimal("-0.020"),
                        PRODUKTION, VERBRAUCH),
                new BigDecimal("-0.010"), SPEICHERWERT);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
    }

    // ==================== Regel 5: LADEN ====================

    @Test
    void entscheide_KeinTalInSicht_LaedtUndLaesstBeidesFrei() {
        SteuerRegelService.Entscheid entscheid = entscheide("0.200", "0.190");

        assertEquals(Steuerregel.LADEN, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
    }

    // ==================== Reihenfolge der Regeln ====================

    @Test
    void entscheide_NegativerPreisOhneUeberschuss_SchlaegtRegelZwei() {
        // Die Reihenfolge ist Fachlichkeit: Regel 1 greift VOR der Ueberschusspruefung und
        // deshalb auch nachts. Genau darum zaehlt die Rueckrechnung nur Intervalle mit Ueberschuss.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("-0.010"), new BigDecimal("0.010"),
                        BigDecimal.ZERO, new BigDecimal("2.000")),
                SCHWELLWERT, SPEICHERWERT);

        assertEquals(Steuerregel.PREIS_NEGATIV, entscheid.regel());
        assertEquals(0, BigDecimal.ZERO.compareTo(entscheid.ueberschuss()));
    }

    /** Kurzform mit Überschuss; nur Preis und Tiefstpreis des Resttages unterscheiden die Fälle. */
    private SteuerRegelService.Entscheid entscheide(String preis, String preisTiefRest) {
        return steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal(preis), new BigDecimal(preisTiefRest),
                        PRODUKTION, VERBRAUCH),
                SCHWELLWERT, SPEICHERWERT);
    }
}
