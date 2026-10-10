package ch.nacht.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Text einer Positionszeile auf der Stromrechnung ({@code reports/rechnung.jrxml},
 * Specs/RechnungenGenerieren.md).
 *
 * <p>Der Zeitraum einer Position steht nur, wenn er vom Rechnungszeitraum abweicht — etwa bei einem
 * Tarifwechsel innerhalb des Quartals. Deckt die Position den ganzen Rechnungszeitraum ab, wiederholte
 * er nur, was im Kopf der Rechnung schon steht.
 *
 * <p>In Java statt als Ausdruck im Template, damit die Regel testbar ist: Ein Template-Ausdruck
 * fällt erst beim Füllen auf, und auch dann nur, wenn der Testfall die Bedingung trifft.
 */
public final class RechnungZeilenText {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Zeichen, die in die Bezeichnungsspalte (305 pt, 9 pt Helvetica) auf eine Zeile passen — rund
     * 64, mit Reserve für breite Buchstaben. Ist der Text länger, steht der Zeitraum in der zweiten
     * Zeile. Sonst bricht JasperReports nach dem Bindestrich um und trennt die beiden Daten
     * ("… (16.08.2026 -" / "30.09.2026)"). Ein nicht trennender Bindestrich hilft nicht: Helvetica
     * im PDF kennt ihn nicht.
     */
    static final int ZEICHEN_EINE_ZEILE = 60;

    private RechnungZeilenText() {
    }

    /**
     * Bezeichnung der Position, um den Zeitraum ergänzt, wenn er vom Rechnungszeitraum abweicht.
     * Beispiel: {@code Strombezug EWB (01.07.2026 - 15.08.2026)}; ist das zu lang für eine Zeile,
     * steht der Zeitraum nach einem Zeilenumbruch.
     *
     * @param bezeichnung Bezeichnung der Position (ggf. mit Quell-Referenz)
     * @param von         Beginn der Position
     * @param bis         Ende der Position
     * @param rechnungVon Beginn des Rechnungszeitraums
     * @param rechnungBis Ende des Rechnungszeitraums
     * @return Bezeichnung allein oder mit Zeitraum in Klammern
     */
    public static String bezeichnung(String bezeichnung, LocalDate von, LocalDate bis,
                                     LocalDate rechnungVon, LocalDate rechnungBis) {
        String text = bezeichnung != null ? bezeichnung : "";
        if (von == null || bis == null) {
            return text;
        }
        if (von.equals(rechnungVon) && bis.equals(rechnungBis)) {
            return text;
        }
        String zeitraum = "(" + DATUM.format(von) + " - " + DATUM.format(bis) + ")";
        String trenner = text.length() + 1 + zeitraum.length() > ZEICHEN_EINE_ZEILE ? "\n" : " ";
        return text + trenner + zeitraum;
    }
}
