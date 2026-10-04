package ch.nacht.service;

import ch.nacht.entity.Steuerzustand;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Die Merit-Order über den Resttag (Specs/Ladeplanung.md, FR-1).
 *
 * <p>Die Intervalle des Resttages werden nach Einspeisepreis <b>aufsteigend</b> sortiert und
 * aufgefüllt, bis die freie Batteriekapazität gedeckt ist. Was im Plan liegt, darf laden.
 *
 * <p><b>Warum eine Merit-Order und kein Solver.</b> Eine gespeicherte Kilowattstunde ist immer
 * gleich viel wert — aus welchem Intervall sie stammt, spielt keine Rolle. Was eine Ladung
 * <b>kostet</b>, ist deshalb allein die Einspeisung, auf die man dafür verzichtet. Füllt man die
 * Kapazität mit den billigsten Intervallen, ist dieser Verzicht am kleinsten; jeder Tausch gegen
 * ein teureres Intervall speichert dieselbe Menge und kostet mehr. Also gibt es keinen besseren
 * Plan. Das ist ein Austauschargument, kein Optimierungsverfahren — deshalb genügen zwanzig
 * Zeilen, wo sonst ein Solver stünde.
 *
 * <p><b>Die Bedingung dafür ist nachgemessen, nicht angenommen.</b> Der Beweis trägt nur, solange
 * die Batterie jeden angebotenen Überschuss auch aufnimmt. Am 27.09.2026 um 12:00: Erzeugung
 * 3.737 kWh, Verbrauch 0.353 kWh, Ladung 3.400 kWh — der gesamte Überschuss ging hinein, keine
 * Leistungsgrenze band.
 *
 * <p><b>Kippt diese Bedingung, versagt das Verfahren lautlos:</b> Es plante Intervalle ein, deren
 * Überschuss gar nicht vollständig hineinpasst, und der Plan wäre zu kurz. Erkennbar wäre das
 * daran, dass {@code speicher_ladung} bei starker Sonne auf einem Wert stehen bleibt, während die
 * verrechnete Erzeugung weiter steigt. Dann bräuchte es ein LP oder eine Deckelung des
 * Überschusses auf die Ladeleistung (Specs/Ladeplanung.md, §7).
 *
 * <p><b>Reine Rechnung.</b> Kein Repository, kein Mandantenkontext, keine Uhr — alles kommt als
 * Parameter herein. Damit lässt sich jeder Fall einzeln prüfen, und das ist nötig: Die Regel
 * entscheidet lokal richtig und global falsch, und genau das ist der Grund, warum sie ersetzt wird.
 */
@Service
public class LadeplanService {

    /**
     * Ladewirkungsgrad: Anteil des Überschusses, der im Speicher ankommt.
     *
     * <p><b>Gemessen, nicht geschätzt</b> (Specs/Ladeplanung.md, FR-1): Am 27.09.2026 ergab der
     * Energiezähler über acht Intervalle eine Kapazität von rund 43 kWh, konfiguriert sind 40.8 —
     * Verhältnis 1.054. Der Rest bleibt als Wärme in Wechselrichter und Zellen.
     *
     * <p><b>Warum 5 % nicht vernachlässigt werden</b>, obwohl das Verfahren gegen 30 %
     * Prognosefehler robust sein soll: Dieser Fehler zeigt immer in dieselbe Richtung.
     * Prognosefehler mitteln sich über die Tage heraus, ein systematischer Divisor nicht — die
     * Batterie wäre jeden Abend knapp nicht voll.
     */
    private static final BigDecimal WIRKUNGSGRAD = new BigDecimal("0.95");

    /**
     * Ein Intervall des Resttages.
     *
     * @param zeit        Beginn des Intervalls in Ortszeit
     * @param preis       Einspeisepreis in CHF/kWh; darf negativ sein
     * @param ueberschuss erwarteter PV-Überschuss in kWh, nie negativ
     */
    public record Intervall(LocalDateTime zeit, BigDecimal preis, BigDecimal ueberschuss) {
    }

    /**
     * Das Ergebnis für <b>ein</b> Intervall.
     *
     * @param batterieladung {@code FREI}, wenn das Intervall im Plan liegt, sonst {@code GESPERRT}
     * @param rang           Platz in der Merit-Order, 1-basiert; {@code null} ohne Überschuss
     * @param rangBenoetigt  wie viele Intervalle die freie Kapazität deckt
     * @param kapazitaetFrei die zugrunde gelegte freie Kapazität in kWh
     */
    public record Plan(Steuerzustand batterieladung, Integer rang, int rangBenoetigt,
                       BigDecimal kapazitaetFrei) {
    }

    /**
     * Plant den Resttag und gibt das Ergebnis für das <b>ausgewertete</b> Intervall zurück.
     *
     * <p><b>„Ausgewertet", nicht „laufend".</b> Der Job läuft zur Minute 6 und wertet das eben
     * abgeschlossene Intervall aus. Es gehört in die Merit-Order, denn über seinen Entscheid wird
     * gerade befunden; der Resttag beginnt bei ihm.
     *
     * @param resttag       alle Intervalle ab dem ausgewerteten, einschliesslich
     * @param ausgewertet   Beginn des Intervalls, über das entschieden wird
     * @param kapazitaetFrei freie Batteriekapazität in kWh
     * @return der Plan für das ausgewertete Intervall
     */
    public Plan plane(List<Intervall> resttag, LocalDateTime ausgewertet,
                      BigDecimal kapazitaetFrei) {

        // Nur Intervalle, in denen es ueberhaupt etwas zuzuteilen gibt. Ein Intervall ohne
        // erwarteten Ueberschuss belegt keinen Platz im Plan.
        List<Intervall> mitUeberschuss = resttag.stream()
                .filter(i -> i.ueberschuss() != null && i.ueberschuss().signum() > 0)
                .sorted(Comparator.comparing(Intervall::preis)
                        // Zweites Kriterium, damit die Reihenfolge FEST ist: Gleiche Preise kommen
                        // vor, und ohne diese Ordnung wechselte der Rang zwischen zwei Laeufen -
                        // der Entscheid flatterte, ohne dass sich etwas geaendert haette.
                        .thenComparing(Intervall::zeit))
                .toList();

        // Der Ueberschuss ist die Energie VOR dem Speicher; ein Teil davon kommt dort nie an.
        BigDecimal zuDecken = kapazitaetFrei.divide(WIRKUNGSGRAD, 6, RoundingMode.HALF_UP);

        int rangBenoetigt = 0;
        BigDecimal summe = BigDecimal.ZERO;
        for (Intervall intervall : mitUeberschuss) {
            if (summe.compareTo(zuDecken) >= 0) {
                break;
            }
            summe = summe.add(intervall.ueberschuss());
            rangBenoetigt++;
        }

        Integer rang = null;
        for (int i = 0; i < mitUeberschuss.size(); i++) {
            if (mitUeberschuss.get(i).zeit().equals(ausgewertet)) {
                rang = i + 1;
                break;
            }
        }

        return new Plan(batterieladung(rang, rangBenoetigt, kapazitaetFrei), rang, rangBenoetigt,
                kapazitaetFrei);
    }

    /**
     * {@code FREI}, wenn das Intervall im Plan liegt — <b>oder wenn es nichts zuzuteilen gibt</b>.
     *
     * <p>Die Merit-Order verteilt knappe Kapazität. Wo nichts zu verteilen ist, gibt es nichts zu
     * sperren, und das gilt in <b>zwei</b> Fällen:
     *
     * <ul>
     *   <li><b>Kein erwarteter Überschuss</b> ({@code rang == null}) — nachts und an trüben
     *       Intervallen.</li>
     *   <li><b>Keine freie Kapazität</b> ({@code kapazitaetFrei <= 0}) — die Batterie ist voll.</li>
     * </ul>
     *
     * <p><b>Der zweite Fall war zuerst als {@code GESPERRT} festgelegt</b>, und das war falsch. Am
     * 04.10.2026 stand ab 13:00 das billigste Intervall des Tages (Rang 1) auf {@code GESPERRT},
     * weil die volle Batterie {@code rangBenoetigt = 0} ergab. Eine Sperre, die nichts verhindert:
     * In eine volle Batterie lässt sich ohnehin nichts laden.
     *
     * <p>Schlimmer als unschön war die Wirkung auf die <b>Schattenrechnung</b> (FR-1a): Die
     * Regelkaskade entscheidet in beiden Fällen {@code FREI}, also zählte jedes Intervall nach dem
     * Vollwerden als Abweichung — an diesem einen Tag über vierzig. Die Kennzahl, um derentwillen
     * der Parallelbetrieb gebaut wurde, hätte Scheinunterschiede gemessen.
     */
    private Steuerzustand batterieladung(Integer rang, int rangBenoetigt,
                                         BigDecimal kapazitaetFrei) {
        if (rang == null || kapazitaetFrei.signum() <= 0) {
            return Steuerzustand.FREI;
        }
        return rang <= rangBenoetigt ? Steuerzustand.FREI : Steuerzustand.GESPERRT;
    }
}
