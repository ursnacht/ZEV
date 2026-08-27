package ch.nacht.dto;

import java.time.LocalDateTime;

/**
 * Ergebnis eines Abrufs der Einspeisepreise (Specs/Preiszeitreihe.md, FR-4).
 *
 * @param abgerufen     Zahl der von der Quelle gelieferten Intervalle
 * @param neu           davon neu gespeichert
 * @param aktualisiert  davon ueberschrieben (Preiskorrektur oder erneuter Abruf)
 * @param uebersprungen unvollstaendige Intervalle, die nicht gespeichert wurden — bewusst
 *                      ausgewiesen: ein fehlender Preis darf nicht als {@code 0.00000} in der Reihe
 *                      landen, und die Luecke soll sichtbar sein
 * @param publikation   Publikationszeitpunkt der Quelle in Ortszeit; {@code null}, wenn die Quelle
 *                      keinen nennt
 */
public record PreiszeitreiheDownloadDTO(
        int abgerufen,
        int neu,
        int aktualisiert,
        int uebersprungen,
        LocalDateTime publikation) {
}
