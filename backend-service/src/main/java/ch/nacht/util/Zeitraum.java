package ch.nacht.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Rechnungen mit Zeitraeumen, die an mehreren Stellen gebraucht werden.
 *
 * <p>Die Tage eines Zeitraums <b>einschliesslich beider Enden</b> standen in RechnungService,
 * NkBerechnungService und SteuerungController an sechs Stellen als
 * {@code DAYS.between(von, bis) + 1}. Das {@code + 1}
 * ist genau die Stelle, an der eine Kopie einmal fehlt.
 */
public final class Zeitraum {

    private Zeitraum() {
    }

    /**
     * Anzahl Kalendertage von {@code von} bis {@code bis}, beide eingeschlossen — 01.01.–31.12.
     * eines Gemeinjahres ergibt 365, ein einzelner Tag 1.
     */
    public static long tageInklusive(LocalDate von, LocalDate bis) {
        return ChronoUnit.DAYS.between(von, bis) + 1;
    }
}
