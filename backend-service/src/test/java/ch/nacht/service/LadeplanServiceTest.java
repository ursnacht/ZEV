package ch.nacht.service;

import ch.nacht.entity.Steuerzustand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit-Tests der Merit-Order (Specs/Ladeplanung.md, FR-1).
 *
 * <p><b>Reine Rechnung, keine Mocks.</b> Der Service hat kein Repository und keine Uhr — alles
 * kommt als Parameter herein. Genau deshalb lässt sich jeder Fall einzeln festnageln.
 *
 * <p><b>Worauf es ankommt.</b> Die Merit-Order ersetzt eine Regelkaskade, die lokal richtig und
 * global falsch entschied. Ein Fehler hier erzeugt keine Ausnahme: Er verschiebt nur, welche
 * Viertelstunde geladen wird — und das fällt frühestens am Abend auf, wenn die Batterie nicht
 * voll ist.
 */
public class LadeplanServiceTest {

    private final LadeplanService ladeplanService = new LadeplanService();

    private static final LocalDateTime ZEHN_UHR = LocalDateTime.of(2026, 10, 3, 10, 0);

    /** Ein Intervall ab 10:00, je {@code schritt} Viertelstunden später. */
    private static LadeplanService.Intervall intervall(int schritt, String preis,
                                                       String ueberschuss) {
        return new LadeplanService.Intervall(ZEHN_UHR.plusMinutes(15L * schritt),
                new BigDecimal(preis), new BigDecimal(ueberschuss));
    }

    private static LocalDateTime zeit(int schritt) {
        return ZEHN_UHR.plusMinutes(15L * schritt);
    }

    // ==================== Die Reihenfolge ====================

    /**
     * <b>Der Kern.</b> Sortiert wird nach Preis, nicht nach Zeit: Das günstigste Intervall des
     * Resttages bekommt Rang 1, auch wenn es am Abend liegt.
     *
     * <p>Hier ist das <b>letzte</b> Intervall das billigste. Eine Regel, die nur das laufende
     * Intervall ansieht, könnte das nicht wissen — und genau darum geht es bei der Umstellung.
     */
    @Test
    void plane_SortiertNachPreisNichtNachZeit() {
        List<LadeplanService.Intervall> resttag = List.of(
                intervall(0, "0.15", "1.0"),
                intervall(1, "0.05", "1.0"),
                intervall(2, "0.10", "1.0"));

        // Kapazitaet fuer genau ein Intervall: 0.95 kWh / 0.95 = 1.0 kWh zu decken.
        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(1), new BigDecimal("0.95"));

        assertEquals(1, plan.rang(), "Das billigste Intervall hat Rang 1");
        assertEquals(1, plan.rangBenoetigt());
        assertEquals(Steuerzustand.FREI, plan.batterieladung());
    }

    /** Das teuerste Intervall fällt aus dem Plan, obwohl es zeitlich zuerst kommt. */
    @Test
    void plane_TeuerstesIntervallWirdGesperrt() {
        List<LadeplanService.Intervall> resttag = List.of(
                intervall(0, "0.15", "1.0"),
                intervall(1, "0.05", "1.0"),
                intervall(2, "0.10", "1.0"));

        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(0), new BigDecimal("0.95"));

        assertEquals(3, plan.rang());
        assertEquals(1, plan.rangBenoetigt());
        assertEquals(Steuerzustand.GESPERRT, plan.batterieladung());
    }

    /**
     * <b>Bei gleichem Preis entscheidet die Zeit</b> — und zwar fest.
     *
     * <p>Ohne zweites Sortierkriterium hinge die Reihenfolge an der Eingabereihenfolge. Der Rang
     * wechselte dann zwischen zwei Läufen, und der Entscheid flatterte, ohne dass sich etwas
     * geändert hätte. Dieser Test gibt die Intervalle absichtlich in <b>umgekehrter</b> Zeitfolge
     * herein.
     */
    @Test
    void plane_BeiGleichemPreisEntscheidetDieZeit() {
        List<LadeplanService.Intervall> resttag = List.of(
                intervall(2, "0.10", "1.0"),
                intervall(0, "0.10", "1.0"),
                intervall(1, "0.10", "1.0"));

        assertEquals(1, ladeplanService.plane(resttag, zeit(0), BigDecimal.ONE).rang());
        assertEquals(2, ladeplanService.plane(resttag, zeit(1), BigDecimal.ONE).rang());
        assertEquals(3, ladeplanService.plane(resttag, zeit(2), BigDecimal.ONE).rang());
    }

    /** Negative Preise stehen ganz vorn — dort kostet Einspeisen Geld. */
    @Test
    void plane_NegativerPreisStehtVorn() {
        List<LadeplanService.Intervall> resttag = List.of(
                intervall(0, "0.05", "1.0"),
                intervall(1, "-0.02", "1.0"));

        assertEquals(1, ladeplanService.plane(resttag, zeit(1), BigDecimal.ONE).rang());
    }

    // ==================== Das Auffüllen ====================

    /**
     * <b>Aufgefüllt wird bis {@code kapazitaetFrei / 0.95}</b>, nicht bis {@code kapazitaetFrei}.
     *
     * <p>Der erwartete Überschuss ist die Energie <b>vor</b> dem Speicher; rund 5 % kommen dort
     * nie an. Hier sind 9.5 kWh frei, zu decken sind also 10.0 — bei 2 kWh je Intervall sind das
     * fünf Intervalle. Ohne den Divisor wären es nur fünf... deshalb ist die Zahl so gewählt, dass
     * sich beide Fälle <b>unterscheiden</b>: 9.5 / 2 = 4.75 → 5 Intervalle; 10.0 / 2 = 5.0 → 5.
     * Mit 1.9 kWh je Intervall dagegen: 9.5 / 1.9 = 5 gegen 10.0 / 1.9 = 5.26 → 6.
     */
    @Test
    void plane_BeruecksichtigtDenLadewirkungsgrad() {
        List<LadeplanService.Intervall> resttag = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            resttag.add(intervall(i, "0.0" + i, "1.9"));
        }

        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(0), new BigDecimal("9.5"));

        // Ohne Wirkungsgrad waeren 9.5 / 1.9 = genau 5 Intervalle genug.
        assertEquals(6, plan.rangBenoetigt(),
                "9.5 kWh frei brauchen 10.0 kWh Ueberschuss, also sechs Intervalle zu 1.9");
    }

    /** Reicht der Überschuss des Resttages nicht, sind **alle** Intervalle im Plan. */
    @Test
    void plane_UeberschussReichtNicht_AllesImPlan() {
        List<LadeplanService.Intervall> resttag = List.of(
                intervall(0, "0.20", "1.0"),
                intervall(1, "0.05", "1.0"));

        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(0), new BigDecimal("40.0"));

        assertEquals(2, plan.rangBenoetigt());
        assertEquals(Steuerzustand.FREI, plan.batterieladung(),
                "Auch das teure Intervall wird geladen - die Kapazitaet wird sonst nicht voll");
    }

    /** Ist die Batterie voll, ist der Plan leer und jedes Intervall gesperrt. */
    @Test
    void plane_BatterieVoll_PlanLeer() {
        List<LadeplanService.Intervall> resttag = List.of(intervall(0, "0.05", "1.0"));

        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(0), BigDecimal.ZERO);

        assertEquals(0, plan.rangBenoetigt());
        assertEquals(1, plan.rang());
        assertEquals(Steuerzustand.GESPERRT, plan.batterieladung());
    }

    // ==================== Intervalle ohne Überschuss ====================

    /**
     * <b>Ohne erwarteten Überschuss ist die Frage gegenstandslos — {@code FREI}, nicht
     * {@code GESPERRT}.</b>
     *
     * <p>Die Merit-Order teilt knappe Kapazität zu; wo nichts zuzuteilen ist, gibt es nichts zu
     * sperren. Andernfalls stünde jede Nachtstunde auf {@code GESPERRT}, obwohl nichts zu laden
     * war — und in der Schattenrechnung zählte jede Nacht als Abweichung zur Regelkaskade, die
     * im selben Fall {@code FREI} entscheidet. Der Vergleich wäre wertlos.
     */
    @Test
    void plane_OhneUeberschuss_FreiUndOhneRang() {
        List<LadeplanService.Intervall> resttag = List.of(
                intervall(0, "0.05", "0.0"),
                intervall(1, "0.20", "1.0"));

        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(0), new BigDecimal("0.95"));

        assertNull(plan.rang(), "Ein Intervall ohne Ueberschuss belegt keinen Platz");
        assertEquals(Steuerzustand.FREI, plan.batterieladung());
    }

    /** Ein Intervall ohne Überschuss verschiebt die Ränge der anderen nicht. */
    @Test
    void plane_IntervallOhneUeberschussZaehltNichtMit() {
        List<LadeplanService.Intervall> resttag = List.of(
                intervall(0, "0.01", "0.0"),
                intervall(1, "0.05", "1.0"),
                intervall(2, "0.10", "1.0"));

        assertEquals(1, ladeplanService.plane(resttag, zeit(1), BigDecimal.ONE).rang(),
                "Trotz des billigeren, leeren Intervalls davor");
    }

    /** Ein {@code null}-Überschuss wird wie 0 behandelt und reisst nichts mit. */
    @Test
    void plane_NullUeberschuss_WirdUebersprungen() {
        List<LadeplanService.Intervall> resttag = new ArrayList<>();
        resttag.add(new LadeplanService.Intervall(zeit(0), new BigDecimal("0.05"), null));
        resttag.add(intervall(1, "0.10", "1.0"));

        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(1), BigDecimal.ONE);

        assertEquals(1, plan.rang());
    }

    // ==================== Randfälle ====================

    /** Letztes Intervall des Tages: Der Resttag besteht nur aus ihm selbst. */
    @Test
    void plane_LetztesIntervallDesTages() {
        List<LadeplanService.Intervall> resttag = List.of(intervall(0, "0.08", "1.0"));

        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(0), new BigDecimal("0.95"));

        assertEquals(1, plan.rang());
        assertEquals(1, plan.rangBenoetigt());
        assertEquals(Steuerzustand.FREI, plan.batterieladung());
    }

    /** Ein leerer Resttag ergibt einen leeren Plan, keine Ausnahme. */
    @Test
    void plane_LeererResttag() {
        LadeplanService.Plan plan = ladeplanService.plane(List.of(), zeit(0), BigDecimal.TEN);

        assertNull(plan.rang());
        assertEquals(0, plan.rangBenoetigt());
        assertEquals(Steuerzustand.FREI, plan.batterieladung());
    }

    /**
     * Das ausgewertete Intervall kommt im Resttag gar nicht vor.
     *
     * <p>Kann auftreten, wenn für dieses Intervall kein Preis vorliegt und es deshalb beim
     * Aufbauen übersprungen wurde. Dann gibt es keinen Rang — und keine Sperre.
     */
    @Test
    void plane_AusgewertetesIntervallNichtImResttag() {
        List<LadeplanService.Intervall> resttag = List.of(intervall(1, "0.05", "1.0"));

        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(0), BigDecimal.TEN);

        assertNull(plan.rang());
        assertEquals(Steuerzustand.FREI, plan.batterieladung());
    }

    /** Die freie Kapazität wird unverändert zurückgegeben — sie geht so ins Protokoll. */
    @Test
    void plane_GibtDieFreieKapazitaetZurueck() {
        List<LadeplanService.Intervall> resttag = List.of(intervall(0, "0.05", "1.0"));

        LadeplanService.Plan plan = ladeplanService.plane(resttag, zeit(0), new BigDecimal("12.345"));

        assertEquals(new BigDecimal("12.345"), plan.kapazitaetFrei());
    }
}
