package ch.nacht.service;

import ch.nacht.entity.Steuerregel;
import ch.nacht.entity.Steuerzustand;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Die Regel der Einspeisesteuerung (Specs/Einspeisesteuerung.md, FR-2).
 *
 * <p><b>Eine reine Funktion.</b> Kein Repository, kein Mandantenkontext, keine Datenbank: Eingaben
 * rein, Entscheid raus. Zwei Gründe:
 * <ul>
 *   <li>Der <b>Job</b> (ein Intervall, jetzt) und das <b>Nachrechnen</b> (viele Intervalle,
 *       rückblickend, mit anderen Schwellen) verwenden dieselbe Regel. Zwei Rechenwege wären zwei
 *       Wahrheiten — und die Rückrechnung soll ja gerade vorhersagen, was der Job getan hätte.</li>
 *   <li>Die Fachlichkeit ist damit <b>ohne Datenbank prüfbar</b>. Dasselbe Muster wie
 *       {@code NkBerechnungService}.</li>
 * </ul>
 *
 * <p><b>Es wird nichts geschaltet.</b> Der Entscheid sagt, was die Steuerung tun <i>würde</i>.
 */
@Service
public class SteuerRegelService {

    /**
     * Entscheid für ein Intervall — was die Steuerung tun würde.
     *
     * @param regel          die erste zutreffende Regel
     * @param batterieladung Sollzustand der Batterieladung
     * @param einspeisung    Sollzustand der Einspeisung
     * @param ueberschuss    {@code max(0, produktion − verbrauch)} in kWh
     */
    public record Entscheid(Steuerregel regel, Steuerzustand batterieladung,
                            Steuerzustand einspeisung, BigDecimal ueberschuss) {
    }

    /**
     * Eingangsgrössen eines Intervalls.
     *
     * @param preis         Einspeisepreis in CHF/kWh; {@code null}, wenn keiner vorliegt
     * @param preisTiefRest tiefster erwarteter Preis im <b>Rest</b> des Ortstages; {@code null},
     *                      wenn für den Rest des Tages keine Preise vorliegen
     * @param produktion    Summe der {@code PRODUCER} in kWh, <b>als Betrag</b> — siehe
     *                      {@link #entscheide(Eingabe, BigDecimal, BigDecimal)}
     * @param verbrauch     Summe der {@code CONSUMER} in kWh
     */
    public record Eingabe(BigDecimal preis, BigDecimal preisTiefRest,
                          BigDecimal produktion, BigDecimal verbrauch) {
    }

    /**
     * Wendet die fünf Regeln in fester Reihenfolge an; die erste zutreffende bestimmt den
     * Entscheid.
     *
     * <p><b>Die Reihenfolge ist Fachlichkeit, nicht Stil.</b> Wer sie ändert, ändert das Verhalten
     * der Steuerung:
     * <ol>
     *   <li>{@code PREIS_NEGATIV} — Einspeisen kostet Geld. Laden bleibt frei.</li>
     *   <li>{@code KEIN_UEBERSCHUSS} — nichts zu entscheiden, wird aber protokolliert.</li>
     *   <li>{@code EINSPEISEN_LOHNT} — die Vergütung übertrifft den Wert einer gespeicherten kWh.</li>
     *   <li>{@code WARTEN_AUF_TAL} — heute kommt noch etwas <b>Billigeres als jetzt</b>, und es
     *       liegt unter dem Schwellwert; Kapazität dafür freihalten.</li>
     *   <li>{@code LADEN} — kein Tal mehr in Sicht, also jetzt.</li>
     * </ol>
     *
     * <p><b>Zum Vorzeichen:</b> {@code eingabe.produktion()} ist ein <b>Betrag</b>. In
     * {@code messwerte.total} steht die Produktion negativ ({@code ΔBezug − ΔEinspeisung}); wer sie
     * ohne Vorzeichenwechsel addiert, erhält einen Überschuss von <b>immer 0</b> und eine
     * Steuerung, die stumm ins Leere läuft. {@code MesswerteRepository
     * .sumBilanzKomponentenPerZeitBetween} liefert die Produktion bereits mit {@code ABS()} — das
     * ist die Quelle der Wahl.
     *
     * <p><b>Ohne Preis wird nicht gesperrt.</b> Fehlt {@code preis}, greifen die Regeln 1 und 3
     * nicht; fehlt {@code preisTiefRest}, greift Regel 4 nicht. Nichtstun ist der sichere Zustand:
     * Eine Steuerung, die mangels Daten sperrt, richtet mehr Schaden an als eine, die zusieht.
     *
     * @param eingabe      Messwerte und Preise des Intervalls
     * @param schwellwert  Grenze für {@code WARTEN_AUF_TAL} in CHF/kWh; darf negativ sein
     * @param speicherwert Wert einer gespeicherten kWh in CHF/kWh; darf negativ sein
     * @return der Entscheid samt Überschuss
     */
    public Entscheid entscheide(Eingabe eingabe, BigDecimal schwellwert, BigDecimal speicherwert) {
        BigDecimal ueberschuss = ueberschuss(eingabe);

        // 1. Negativer Preis: Einspeisen kostet Geld. Die Batterie darf laden - sie nimmt Energie
        //    auf, die sonst abgeregelt wuerde.
        if (eingabe.preis() != null && eingabe.preis().signum() < 0) {
            return new Entscheid(Steuerregel.PREIS_NEGATIV,
                    Steuerzustand.FREI, Steuerzustand.GESPERRT, ueberschuss);
        }

        // 2. Kein Ueberschuss: nichts zu entscheiden. Trotzdem protokolliert - eine Luecke im
        //    Protokoll liesse offen, ob die Steuerung ueberhaupt lief.
        if (ueberschuss.signum() <= 0) {
            return new Entscheid(Steuerregel.KEIN_UEBERSCHUSS,
                    Steuerzustand.FREI, Steuerzustand.FREI, ueberschuss);
        }

        // 3. Einspeisen lohnt mehr als speichern. Mit dem Massstab "vermiedener Netzbezug" loest
        //    das praktisch nie aus - die Regel ist der Waechter fuer Knappheitspreise.
        if (eingabe.preis() != null && eingabe.preis().compareTo(speicherwert) >= 0) {
            return new Entscheid(Steuerregel.EINSPEISEN_LOHNT,
                    Steuerzustand.GESPERRT, Steuerzustand.FREI, ueberschuss);
        }

        // 4. Der Kern: Kommt heute noch ein guenstigeres Intervall, bleibt die knappe
        //    Batteriekapazitaet dafuer frei, statt sie jetzt mit teurerem Strom zu fuellen.
        //
        //    ZWEI Bedingungen, und die zweite fehlte zuerst:
        //      a) das kommende Tief liegt unter dem Schwellwert - es lohnt sich ueberhaupt
        //      b) es liegt unter dem Preis JETZT - es gibt etwas, worauf sich warten laesst
        //
        //    Ohne (b) sperrte die Regel weiter, sobald das Tal erreicht war: Am 14.09.2026 stand
        //    bei Hene von 13:00 bis 14:30 der aktuelle Preis (0.161) gleich dem Tiefstpreis des
        //    Resttages, und die Steuerung wartete auf sich selbst. 27.9 kWh Ueberschuss - mehr als
        //    die Batterie fasst - gingen im Preistal ins Netz, geladen wurde ab 14:45 zu 0.170.
        //    Die Regel bewirkte damit das Gegenteil ihrer Absicht.
        //
        //    Ohne Preis wird nicht gesperrt: Ein Vergleich ohne die eine Seite ist keiner, und
        //    Nichtstun ist der sichere Zustand.
        if (eingabe.preisTiefRest() != null
                && eingabe.preisTiefRest().compareTo(schwellwert) < 0
                && eingabe.preis() != null
                && eingabe.preisTiefRest().compareTo(eingabe.preis()) < 0) {
            return new Entscheid(Steuerregel.WARTEN_AUF_TAL,
                    Steuerzustand.GESPERRT, Steuerzustand.FREI, ueberschuss);
        }

        // 5. Kein Tal mehr in Sicht - laden, solange Ueberschuss da ist. Das deckt den bewoelkten
        //    Tag ab: Ein hoher Mittagspreis heisst, dass der ganze Markt wenig Solarstrom
        //    erwartet; dann ist die Gelegenheit knapp, nicht die Kapazitaet.
        return new Entscheid(Steuerregel.LADEN,
                Steuerzustand.FREI, Steuerzustand.FREI, ueberschuss);
    }

    /**
     * {@code max(0, produktion − verbrauch)} — siehe Vorzeichen-Hinweis bei
     * {@link #entscheide(Eingabe, BigDecimal, BigDecimal)}.
     */
    private BigDecimal ueberschuss(Eingabe eingabe) {
        BigDecimal produktion = nullSicher(eingabe.produktion());
        BigDecimal verbrauch = nullSicher(eingabe.verbrauch());
        BigDecimal differenz = produktion.subtract(verbrauch);
        return differenz.signum() > 0 ? differenz : BigDecimal.ZERO;
    }

    private BigDecimal nullSicher(BigDecimal wert) {
        return wert != null ? wert : BigDecimal.ZERO;
    }
}
