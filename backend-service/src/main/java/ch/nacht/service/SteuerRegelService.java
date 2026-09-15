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
     *   <li>{@code EINSPEISEN_LOHNT} — die Vergütung übertrifft den Wert einer gespeicherten kWh.</li>
     *   <li>{@code WARTEN_AUF_TAL} — heute kommt noch etwas <b>Billigeres als jetzt</b>, und es
     *       liegt unter dem Schwellwert; Kapazität dafür freihalten.</li>
     *   <li>{@code KEIN_UEBERSCHUSS} / {@code LADEN} — keine Sperre. Beide ergeben
     *       {@code FREI}/{@code FREI} und unterscheiden sich nur in der Begründung.</li>
     * </ol>
     *
     * <p><b>Der Überschuss steht bewusst am Ende.</b> Stand er vorn, war die Steuerung wirkungslos:
     * Solange die Batterie lädt, gibt der Wechselrichter über den Zähler nur den Hausbedarf ab —
     * die übrige PV-Energie fliesst DC-seitig in den Speicher und passiert den Zähler nie. Der
     * Überschuss erscheint als 0, die Preisregeln wurden nie erreicht, und entschieden wurde erst,
     * wenn die Batterie voll war. Für den Entscheid ist die Menge auch nicht nötig: Eine Sperre
     * ohne Überschuss läuft ins Leere, schadet aber nicht.
     *
     * <p><b>Zum Vorzeichen:</b> {@code eingabe.produktion()} ist ein <b>Betrag</b>. In
     * {@code messwerte.total} steht die Produktion negativ ({@code ΔBezug − ΔEinspeisung}); wer sie
     * ohne Vorzeichenwechsel addiert, erhält einen Überschuss von <b>immer 0</b> und eine
     * Steuerung, die stumm ins Leere läuft. {@code MesswerteRepository
     * .sumBilanzKomponentenPerZeitBetween} liefert die Produktion bereits mit {@code ABS()} — das
     * ist die Quelle der Wahl.
     *
     * <p><b>Ohne Preis wird nicht gesperrt.</b> Fehlt {@code preis}, greifen {@code PREIS_NEGATIV}
     * und {@code EINSPEISEN_LOHNT} nicht; fehlt {@code preisTiefRest}, greift {@code WARTEN_AUF_TAL}
     * nicht. Nichtstun ist der sichere Zustand:
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

        // 2. Einspeisen lohnt mehr als speichern. Mit dem Massstab "vermiedener Netzbezug" loest
        //    das praktisch nie aus - die Regel ist der Waechter fuer Knappheitspreise.
        if (eingabe.preis() != null && eingabe.preis().compareTo(speicherwert) >= 0) {
            return new Entscheid(Steuerregel.EINSPEISEN_LOHNT,
                    Steuerzustand.GESPERRT, Steuerzustand.FREI, ueberschuss);
        }

        // 3. Der Kern: Kommt heute noch ein guenstigeres Intervall, bleibt die knappe
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

        // 4. Keine Sperre. Bleibt noch die Frage, WARUM nicht - beide Faelle ergeben denselben
        //    Entscheid (FREI/FREI) und unterscheiden sich nur in der Begruendung:
        //
        //    a) Kein Ueberschuss gemessen. Fruehere Fassungen prueften das VOR den Preisregeln und
        //       machten die Steuerung damit wirkungslos: Solange die Batterie laedt, gibt der
        //       Wechselrichter ueber den Zaehler nur den Hausbedarf ab - bei Hene am 15.09.2026
        //       drei Stunden lang konstant rund 0.8 kW, dann um 11:30 der Sprung auf das Zwoelffache,
        //       als die Batterie voll war. Der Ueberschuss erscheint bis dahin als 0, die Regeln 1
        //       bis 3 wurden nie erreicht, und entschieden wurde erst, als es nichts mehr zu
        //       entscheiden gab.
        //
        //       Fuer den Entscheid ist der Ueberschuss auch gar nicht noetig: Eine Sperre ohne
        //       Ueberschuss laeuft ins Leere, schadet aber nicht. Der Anlagenregler entscheidet
        //       ohnehin, ob tatsaechlich geladen wird.
        //
        //    b) Ueberschuss ist da und kein Tal mehr in Sicht - laden. Das deckt den bewoelkten Tag
        //       ab: Ein hoher Mittagspreis heisst, dass der ganze Markt wenig Solarstrom erwartet;
        //       dann ist die Gelegenheit knapp, nicht die Kapazitaet.
        if (ueberschuss.signum() <= 0) {
            return new Entscheid(Steuerregel.KEIN_UEBERSCHUSS,
                    Steuerzustand.FREI, Steuerzustand.FREI, ueberschuss);
        }
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
