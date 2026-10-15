package ch.nacht.util;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * „Heute" in Schweizer Ortszeit.
 *
 * <p><b>Warum nicht {@code LocalDate.now()}:</b> Das haengt an der Zeitzone der JVM. Laeuft der
 * Container in UTC, ist es zwischen 00:00 und 02:00 Ortszeit dort noch der Vortag — eine Rechnung
 * oder Forderung aus dieser Zeit truege das falsche Datum.
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
}
