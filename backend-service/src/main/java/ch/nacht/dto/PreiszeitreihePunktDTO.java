package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein Punkt der Preiszeitreihe fuer die Darstellung (Specs/Preiszeitreihe.md, FR-4).
 *
 * @param zeit  Intervallbeginn in <b>Ortszeit</b> (Europe/Zurich) — gespeichert wird UTC, die
 *              Umrechnung passiert bewusst erst hier, damit der Eindeutigkeitsschluessel der
 *              Tabelle an der Zeitumstellung eindeutig bleibt
 * @param preis Einspeisepreis in CHF/kWh
 */
public record PreiszeitreihePunktDTO(
        LocalDateTime zeit,
        BigDecimal preis) {
}
