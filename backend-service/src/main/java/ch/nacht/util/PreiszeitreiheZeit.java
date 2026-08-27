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
     * Gespeicherten UTC-Zeitpunkt als Ortszeit.
     *
     * @param utc Zeitpunkt in UTC, darf {@code null} sein
     * @return derselbe Zeitpunkt in Europe/Zurich, oder {@code null}
     */
    public static LocalDateTime nachOrtszeit(LocalDateTime utc) {
        return utc == null
                ? null
                : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).toLocalDateTime();
    }
}
