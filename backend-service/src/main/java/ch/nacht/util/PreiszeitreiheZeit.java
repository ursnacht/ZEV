package ch.nacht.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Umrechnung zwischen der Speicherung der Preiszeitreihe (UTC) und der Anzeige (Europe/Zurich).
 *
 * <p><b>Warum ueberhaupt zwei Zeiten:</b> Gespeichert wird UTC, weil lokale Zeit kein tauglicher
 * Eindeutigkeitsschluessel ist — in der Nacht der Umstellung auf Winterzeit tritt die Stunde
 * 02:00–03:00 zweimal auf, vier Viertelstundenwerte wuerden vier andere ueberschreiben. Angezeigt
 * wird Ortszeit, weil ein Einspeisepreis fuer den Benutzer an der Uhr an der Wand haengt.
 *
 * <p>Die Umrechnung steht bewusst an <b>einer</b> Stelle: Verteilt auf Service, Controller und
 * Diagramm waere sie dreimal zu pflegen, und ein Fehler faellt genau an zwei Tagen im Jahr auf.
 */
public final class PreiszeitreiheZeit {

    /** Zone der Anzeige. Bewusst fest: Die Anwendung rechnet durchgaengig in Schweizer Ortszeit. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private PreiszeitreiheZeit() {
    }

    /**
     * Tagesbeginn in UTC — untere, <b>einschliessliche</b> Grenze einer Abfrage.
     *
     * @param tag Datum in Ortszeit
     * @return {@code tag 00:00} Ortszeit, ausgedrueckt in UTC
     */
    public static LocalDateTime tagesbeginnUtc(LocalDate tag) {
        return tag.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Beginn des Folgetags in UTC — obere, <b>ausschliessliche</b> Grenze einer Abfrage.
     *
     * <p>Ausschliessend und nicht {@code 23:59:59}: Sonst faellt der Wert an der Tagesgrenze
     * entweder in zwei Abfragen oder, bei einer Aenderung der Aufloesung, in keine.
     *
     * @param tag letzter gewuenschter Tag in Ortszeit (einschliesslich)
     * @return {@code tag+1 00:00} Ortszeit, ausgedrueckt in UTC
     */
    public static LocalDateTime tagesendeUtc(LocalDate tag) {
        return tagesbeginnUtc(tag.plusDays(1));
    }

    /**
     * Ortszeit (Europe/Zurich) nach <b>UTC</b> — die Gegenrichtung zu
     * {@link #nachOrtszeit(LocalDateTime)}.
     *
     * <p>Gebraucht, sobald Daten in Ortszeit auf die Preiszeitreihe treffen: {@code messwerte.zeit}
     * und {@code steuerentscheid.zeit_von} sind Ortszeit, {@code preiszeitreihe.zeit_von} dagegen
     * UTC. Ohne ausdrückliche Umrechnung entsteht ein stiller Versatz von ein bis zwei Stunden —
     * die Zahlen sehen plausibel aus, gehören aber zu verschiedenen Zeitpunkten.
     *
     * <p><b>An der Zeitumstellung nicht eindeutig:</b> In der Nacht der Rückstellung tritt eine
     * Ortszeit zweimal auf; {@code atZone} wählt dann den <b>früheren</b> Zeitpunkt. Das System
     * nimmt das in Kauf — {@code messwerte} und {@code zaehler_rohdaten} führen ihre Zeitstempel
     * seit jeher so, und eine abweichende Konvention für eine einzelne Tabelle hat schon einmal
     * mehr gekostet, als sie wert war (Specs/Einspeisesteuerung.md, §5).
     *
     * @param ortszeit Zeitpunkt in Europe/Zurich; {@code null} ergibt {@code null}
     * @return derselbe Zeitpunkt in UTC
     */
    public static LocalDateTime nachUtc(LocalDateTime ortszeit) {
        return ortszeit == null
                ? null
                : ortszeit.atZone(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Gespeicherten UTC-Zeitpunkt als Ortszeit — die Gegenrichtung zu
     * {@link #nachUtc(LocalDateTime)}. Diese Richtung ist immer eindeutig.
     *
     * @param utc Zeitpunkt in UTC; {@code null} ergibt {@code null}
     * @return derselbe Zeitpunkt in Europe/Zurich
     */
    public static LocalDateTime nachOrtszeit(LocalDateTime utc) {
        return utc == null
                ? null
                : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).toLocalDateTime();
    }
}
