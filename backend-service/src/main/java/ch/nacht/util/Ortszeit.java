package ch.nacht.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Datum und Zeit in Schweizer Ortszeit: {@link #heute()}, {@link #jetzt()} und die Zone selbst.
 *
 * <p><b>Warum nicht {@code LocalDate.now()} bzw. {@code LocalDateTime.now()}:</b> Beide haengen
 * an der Zeitzone der JVM. Laeuft der Container in UTC, ist es zwischen 00:00 und 02:00 Ortszeit
 * dort noch der Vortag — eine Rechnung oder Forderung aus dieser Zeit truege das falsche Datum.
 *
 * <p>Die Zone der ganzen Anwendung steht hier; {@link PreiszeitreiheZeit#ZONE} uebernimmt sie.
 */
public final class Ortszeit {

    /** Zone der Anwendung: Schweizer Ortszeit. Bewusst fest, die Anwendung rechnet durchgaengig darin. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private Ortszeit() {
    }

    /** Das heutige Datum in Schweizer Ortszeit. */
    public static LocalDate heute() {
        return LocalDate.now(ZONE);
    }

    /**
     * Der aktuelle Zeitpunkt in Schweizer Ortszeit — fuer Zeitstempel, die als Ortszeit gespeichert
     * oder angezeigt werden (z.B. „generiert am" im PDF, Zeitpunkt einer Systemmeldung).
     */
    public static LocalDateTime jetzt() {
        return LocalDateTime.now(ZONE);
    }

    /**
     * Dieselbe Ortszeit in <b>Normalzeit</b> (MEZ) — im Winter unveraendert, im Sommer eine Stunde
     * frueher.
     *
     * <p>Fuer alles, was am Sonnenstand haengt: Der Schatten eines Nachbarhauses faellt nach der
     * Sonne, nicht nach der Uhr. Ein nach Ortszeit geschluesseltes Tagesprofil laege nach jeder
     * Zeitumstellung um eine Stunde daneben, bis das Lernfenster durchgelaufen ist
     * (Specs/Ladeplanung.md, FR-3).
     *
     * <p>In der doppelten Stunde der Herbstumstellung (02:00–03:00) gilt die erste, die
     * Sommerzeit-Lesart. Sie liegt in der Nacht und spielt fuer die Einstrahlung keine Rolle.
     */
    public static LocalDateTime normalzeit(LocalDateTime ortszeit) {
        Instant zeitpunkt = ortszeit.atZone(ZONE).toInstant();
        return LocalDateTime.ofInstant(zeitpunkt, ZONE.getRules().getStandardOffset(zeitpunkt));
    }
}
