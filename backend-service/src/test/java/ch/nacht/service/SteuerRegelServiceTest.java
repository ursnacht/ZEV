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
 * Überschuss. Solange {@code KEIN_UEBERSCHUSS} vor den Preisregeln stand, liess sich dort keine
 * einzige von ihnen durch Zusehen prüfen — beide bis heute gefundenen Fehler der Regel fielen erst
 * im Produktivbetrieb auf, an einer Tabelle mit echten Preisen.
 *
 * <p>Die Abschnitte sind nach <b>Regelnamen</b> benannt, nicht nach Nummern: Die
 * Auswertungsreihenfolge hat sich schon einmal geändert, und Nummern in Kommentaren veralten
 * stillschweigend.
 */
public class SteuerRegelServiceTest {

    /** Vorgabe des Mandanten (SteuerKonfigurationDTO). */
    private static final BigDecimal SCHWELLWERT = new BigDecimal("0.165");
    private static final BigDecimal SPEICHERWERT = new BigDecimal("0.31");

    /** Mindest-Ladezustand; die meisten Faelle liegen mit SOC_HOCH bewusst darueber. */
    private static final BigDecimal SOC_MINIMUM = new BigDecimal("20.0");
    private static final BigDecimal SOC_HOCH = new BigDecimal("80.0");

    /** Hysterese: Freigabe endet erst 5 Punkte ueber dem Mindestwert. */
    private static final BigDecimal SOC_HYSTERESE = new BigDecimal("5.0");

    /**
     * Mindest-Preisabstand. Die meisten Faelle setzen ihn auf 0 und pruefen damit die Regel ohne
     * diese Bedingung; die eigenen Tests weiter unten drehen daran.
     */
    private static final BigDecimal KEIN_ABSTAND = BigDecimal.ZERO;
    private static final BigDecimal ABSTAND = new BigDecimal("0.02000");

    /** Produktion deutlich über Verbrauch — für die Fälle, in denen ein Überschuss gebraucht wird. */
    private static final BigDecimal PRODUKTION = new BigDecimal("4.000");
    private static final BigDecimal VERBRAUCH = new BigDecimal("0.300");

    private SteuerRegelService steuerRegelService;

    @BeforeEach
    void setUp() {
        steuerRegelService = new SteuerRegelService();
    }

    // ==================== PREIS_NEGATIV ====================

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

    // ==================== KEIN_UEBERSCHUSS und der Ueberschuss selbst ====================

    @Test
    void entscheide_ProduktionUnterVerbrauch_KeinUeberschussUndBeideFrei() {
        // Damit KEIN_UEBERSCHUSS ueberhaupt greift, darf KEINE Preisregel zutreffen: Der Preis liegt
        // im Normalbereich und der Rest des Tages bringt nichts Guenstigeres.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.100"), new BigDecimal("0.200"),
                        new BigDecimal("1.000"), new BigDecimal("2.000"), SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.KEIN_UEBERSCHUSS, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
        assertEquals(0, BigDecimal.ZERO.compareTo(entscheid.ueberschuss()));
    }

    /**
     * <b>Der Regressionstest zur umgestellten Reihenfolge (15.09.2026).</b>
     *
     * <p>Ohne gemessenen Überschuss greift die Preisregel <b>trotzdem</b>. Das ist der Fall, der die
     * Steuerung vorher wirkungslos machte: Solange die Batterie lädt, gibt der Wechselrichter über
     * den Zähler nur den Hausbedarf ab, der Überschuss erscheint als 0 — und
     * {@code KEIN_UEBERSCHUSS} blockierte alle Preisregeln. Entschieden wurde erst, wenn die
     * Batterie voll war und es nichts mehr zu entscheiden gab.
     */
    @Test
    void entscheide_OhneUeberschussAberTalInSicht_SperrtLadungTrotzdem() {
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.190"), new BigDecimal("0.100"),
                        new BigDecimal("1.000"), new BigDecimal("2.000"), SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
        assertEquals(Steuerzustand.GESPERRT, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
        // Der Ueberschuss bleibt 0 - er wird protokolliert, steuert den Entscheid aber nicht mehr.
        assertEquals(0, BigDecimal.ZERO.compareTo(entscheid.ueberschuss()));
    }

    @Test
    void entscheide_OhneUeberschussUndEinspeisenLohnt_SperrtLadungTrotzdem() {
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.320"), new BigDecimal("0.300"),
                        BigDecimal.ZERO, new BigDecimal("2.000"), SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.EINSPEISEN_LOHNT, entscheid.regel());
        assertEquals(Steuerzustand.GESPERRT, entscheid.batterieladung());
    }

    @Test
    void entscheide_NegativeProducerWerteAlsBetrag_ErgebenUeberschuss() {
        // Spec AK: Bei Producer-Betrag 10 und Consumer 4 sind es 6 - nicht 0 und nicht -14.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.100"), null,
                        new BigDecimal("10"), new BigDecimal("4"), SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(0, new BigDecimal("6").compareTo(entscheid.ueberschuss()));
    }

    // ==================== EINSPEISEN_LOHNT ====================

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

    // ==================== WARTEN_AUF_TAL ====================

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
                        PRODUKTION, VERBRAUCH, SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.LADEN, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
    }

    @Test
    void entscheide_OhneTiefstpreisRest_SperrtNicht() {
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.100"), null,
                        PRODUKTION, VERBRAUCH, SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.LADEN, entscheid.regel());
    }

    @Test
    void entscheide_NegativerSchwellwert_IstErlaubt() {
        // Negative Preise sind genau die Stunden, fuer die sich eine Steuerung lohnt.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.050"), new BigDecimal("-0.020"),
                        PRODUKTION, VERBRAUCH, SOC_HOCH, false),
                new BigDecimal("-0.010"), SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
    }

    // ==================== LADEN ====================

    @Test
    void entscheide_KeinTalInSicht_LaedtUndLaesstBeidesFrei() {
        SteuerRegelService.Entscheid entscheid = entscheide("0.200", "0.190");

        assertEquals(Steuerregel.LADEN, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
    }

    // ==================== Reihenfolge der Regeln ====================

    @Test
    void entscheide_NegativerPreisOhneUeberschuss_BleibtPreisNegativ() {
        // Seit der Umstellung greift JEDE Preisregel auch ohne Ueberschuss - also auch nachts.
        // Genau darum zaehlt die Rueckrechnung ausschliesslich Intervalle mit Ueberschuss: Sonst
        // staenden die Preisregeln vielfach ueber den Faellen, in denen wirklich etwas zu
        // entscheiden war.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("-0.010"), new BigDecimal("0.010"),
                        BigDecimal.ZERO, new BigDecimal("2.000"), SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.PREIS_NEGATIV, entscheid.regel());
        assertEquals(0, BigDecimal.ZERO.compareTo(entscheid.ueberschuss()));
    }

    // ==================== SOC_TIEF ====================

    @Test
    void entscheide_SocUnterMinimum_HebtLadesperreAuf() {
        // Dieselbe Lage wie entscheide_SpaeterGuenstigerUndUnterSchwellwert_SperrtLadung - nur ist
        // der Speicher fast leer. Warten wuerde bedeuten, den Hausbedarf aus dem Netz zu decken.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.200"), new BigDecimal("0.100"),
                        PRODUKTION, VERBRAUCH, new BigDecimal("15.0"), false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.SOC_TIEF, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
        assertEquals(Steuerzustand.FREI, entscheid.einspeisung());
    }

    @Test
    void entscheide_SocGenauAufMinimum_SperrtWeiter() {
        // Die Grenze ist ein ECHTES Kleiner-als: Auf dem Minimum ist die Reserve noch da.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.200"), new BigDecimal("0.100"),
                        PRODUKTION, VERBRAUCH, SOC_MINIMUM, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
    }

    @Test
    void entscheide_OhneSoc_GreiftSocTiefNicht() {
        // Kein Speicher erfasst oder kein Messwert: Eine Freigabe auf Verdacht machte die
        // Preisregeln wirkungslos, und zwar unbemerkt.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.200"), new BigDecimal("0.100"),
                        PRODUKTION, VERBRAUCH, null, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
    }

    @Test
    void entscheide_SocTiefAberPreisNegativ_BleibtPreisNegativ() {
        // PREIS_NEGATIV steht VOR SOC_TIEF und laesst die Ladung ohnehin frei - die Reihenfolge
        // aendert am Zustand nichts, wohl aber an der Begruendung im Protokoll.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("-0.010"), new BigDecimal("0.100"),
                        PRODUKTION, VERBRAUCH, new BigDecimal("5.0"), false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.PREIS_NEGATIV, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
    }

    @Test
    void entscheide_SocTiefUndEinspeisenLohnt_HebtSperreAuf() {
        // Auch die zweite sperrende Preisregel wird ueberstimmt: Ein leerer Speicher zaehlt mehr
        // als ein guter Einspeisepreis.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.400"), null,
                        PRODUKTION, VERBRAUCH, new BigDecimal("5.0"), false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.SOC_TIEF, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
    }

    @Test
    void entscheide_OhneSocMinimum_GreiftSocTiefNicht() {
        // socMinimum null schaltet die Regel ab - der Vergleich haette sonst keine zweite Seite.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.200"), new BigDecimal("0.100"),
                        PRODUKTION, VERBRAUCH, new BigDecimal("1.0"), false),
                SCHWELLWERT, SPEICHERWERT, null, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
    }

    @Test
    void entscheide_SocUeberMinimumAberFreigabeLaeuft_BleibtFrei() {
        // 22 % liegt UEBER dem Mindestwert von 20 - ohne Hysterese waere hier wieder gesperrt.
        // Weil die Freigabe schon galt, reicht die Grenze bis 25.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.200"), new BigDecimal("0.100"),
                        PRODUKTION, VERBRAUCH, new BigDecimal("22.0"), true),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.SOC_TIEF, entscheid.regel());
    }

    @Test
    void entscheide_SocUeberHysteresegrenze_SperrtWiederZu() {
        // 25 % ist die obere Grenze - erreicht, also endet die Freigabe. Sonst liefe sie ewig.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.200"), new BigDecimal("0.100"),
                        PRODUKTION, VERBRAUCH, new BigDecimal("25.0"), true),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
    }

    @Test
    void entscheide_SocUeberMinimumUndKeineFreigabeLief_SperrtSofort() {
        // Dieselben 22 %, aber die Freigabe lief NICHT: Die Hysterese gilt nur nach unten heraus,
        // sie senkt die Einstiegsschwelle nicht.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.200"), new BigDecimal("0.100"),
                        PRODUKTION, VERBRAUCH, new BigDecimal("22.0"), false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
    }

    @Test
    void entscheide_OhneHysterese_EndetFreigabeAmMindestwert() {
        // Hysterese null: Die Grenze bleibt der Mindestwert, auch wenn die Freigabe lief.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.200"), new BigDecimal("0.100"),
                        PRODUKTION, VERBRAUCH, new BigDecimal("22.0"), true),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, null, KEIN_ABSTAND);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
    }

    // ==================== Mindest-Preisabstand ====================

    @Test
    void entscheide_TalNurKnappTiefer_SperrtNicht() {
        // Der Fall vom 19.09.2026 bei Hene: Preis 0.010, Tal 0.005. Die halbe Rappe Unterschied
        // rechtfertigt keine vier Stunden Sperre in der besten Sonne.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.010"), new BigDecimal("0.005"),
                        PRODUKTION, VERBRAUCH, SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, ABSTAND);

        assertEquals(Steuerregel.LADEN, entscheid.regel());
        assertEquals(Steuerzustand.FREI, entscheid.batterieladung());
    }

    @Test
    void entscheide_TalDeutlichTiefer_SperrtWeiterhin() {
        // Der Fall vom 18.09.2026: Preis 0.150, Tal 0.050 - hier lohnt das Warten, und die Regel
        // soll unveraendert sperren. Waere das nicht so, kostete der Abstand genau den Ertrag,
        // fuer den die ganze Steuerung da ist.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.150"), new BigDecimal("0.050"),
                        PRODUKTION, VERBRAUCH, SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, ABSTAND);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
        assertEquals(Steuerzustand.GESPERRT, entscheid.batterieladung());
    }

    @Test
    void entscheide_TalGenauUmDenAbstandTiefer_SperrtNicht() {
        // Grenzfall: Preis 0.100, Tal 0.080, Abstand 0.020. Die Bedingung ist ein echtes
        // Kleiner-als (tal < preis - abstand); Gleichstand genuegt nicht.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.100"), new BigDecimal("0.080"),
                        PRODUKTION, VERBRAUCH, SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, ABSTAND);

        assertEquals(Steuerregel.LADEN, entscheid.regel());
    }

    @Test
    void entscheide_OhneAbstand_VerhaeltSichWieVorV164() {
        // Abstand null schaltet die Bedingung ab - dieselbe Lage wie oben ergibt dann eine Sperre.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.010"), new BigDecimal("0.005"),
                        PRODUKTION, VERBRAUCH, SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, null);

        assertEquals(Steuerregel.WARTEN_AUF_TAL, entscheid.regel());
    }

    @Test
    void entscheide_AbstandGreiftNichtInDieSchwellwertBedingung() {
        // Der Abstand ist eine ZUSAETZLICHE Bedingung, keine Ersetzung: Liegt das Tal ueber dem
        // Schwellwert, wird auch bei grossem Abstand nicht gesperrt.
        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal("0.300"), new BigDecimal("0.200"),
                        PRODUKTION, VERBRAUCH, SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, ABSTAND);

        assertEquals(Steuerregel.LADEN, entscheid.regel());
    }

    /** Kurzform mit Überschuss; nur Preis und Tiefstpreis des Resttages unterscheiden die Fälle. */
    private SteuerRegelService.Entscheid entscheide(String preis, String preisTiefRest) {
        return steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(new BigDecimal(preis), new BigDecimal(preisTiefRest),
                        PRODUKTION, VERBRAUCH, SOC_HOCH, false),
                SCHWELLWERT, SPEICHERWERT, SOC_MINIMUM, SOC_HYSTERESE, KEIN_ABSTAND);
    }
}
