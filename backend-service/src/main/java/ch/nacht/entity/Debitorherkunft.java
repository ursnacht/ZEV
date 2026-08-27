package ch.nacht.entity;

/**
 * Herkunft einer Forderung in der Debitorenkontrolle
 * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-5).
 *
 * <p>Die Herkunft ist <b>Teil des Unique-Keys</b> von {@code zev.debitor} und nicht bloss eine
 * Anzeigespalte: Eine NK-Jahresabrechnung 01.01.–31.12. und die ZEV-Quartalsrechnung Q1 haben
 * denselben {@code datum_von}. Ohne die Herkunft im Schlüssel würde die eine Buchung die andere
 * beim Upsert stillschweigend überschreiben.
 *
 * <p>Bewusst getrennt von {@code RechnungStorageService.Rechnungsart}, die dieselben zwei Werte
 * trägt: Diese hier ist persistiert und hängt am CHECK-Constraint, jene ist ein Namensraum im
 * flüchtigen PDF-Speicher. Ein gemeinsames Enum liesse eine Änderung am Speicher in die
 * Datenbankschicht reichen.
 */
public enum Debitorherkunft {

    /** Aus der Stromabrechnung (Quartalsrechnung). Der gesamte Bestand vor V126 ist so entstanden. */
    ZEV,

    /** Aus einer Nebenkostenabrechnung. */
    NK
}
