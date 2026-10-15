package ch.nacht.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrtszeitTest {

    @Test
    void zone_IstSchweizerOrtszeit() {
        assertEquals(ZoneId.of("Europe/Zurich"), Ortszeit.ZONE);
    }

    /** Die Preiszeitreihe rechnet in derselben Zone - eine Zone fuer die ganze Anwendung. */
    @Test
    void zone_IstDieselbeWieDieDerPreiszeitreihe() {
        assertSame(Ortszeit.ZONE, PreiszeitreiheZeit.ZONE);
    }

    /**
     * {@code heute()} ist das Datum in Schweizer Ortszeit, nicht in der Zone der JVM. Verglichen
     * wird vor und nach dem Aufruf, damit ein Datumswechsel genau dazwischen den Test nicht kippt.
     */
    @Test
    void heute_IstDasDatumInOrtszeit() {
        LocalDate vorher = LocalDate.now(ZoneId.of("Europe/Zurich"));
        LocalDate heute = Ortszeit.heute();
        LocalDate nachher = LocalDate.now(ZoneId.of("Europe/Zurich"));

        assertTrue(!heute.isBefore(vorher) && !heute.isAfter(nachher));
    }

    /** {@code jetzt()} liegt zwischen zwei Messungen in Ortszeit - nicht in der Zone der JVM. */
    @Test
    void jetzt_IstDerZeitpunktInOrtszeit() {
        LocalDateTime vorher = LocalDateTime.now(ZoneId.of("Europe/Zurich"));
        LocalDateTime jetzt = Ortszeit.jetzt();
        LocalDateTime nachher = LocalDateTime.now(ZoneId.of("Europe/Zurich"));

        assertTrue(!jetzt.isBefore(vorher) && !jetzt.isAfter(nachher));
    }
}
