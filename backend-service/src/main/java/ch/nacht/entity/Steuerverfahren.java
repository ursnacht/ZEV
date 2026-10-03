package ch.nacht.entity;

/**
 * Das Verfahren, das einen Steuerentscheid gefällt hat (Specs/Ladeplanung.md, FR-5).
 *
 * <p><b>Warum diese Angabe nötig ist.</b> Ohne sie trüge die Spalte {@code regel} zwei
 * verschiedene Bedeutungen: bei der Kaskade die zutreffende Regel, bei der Merit-Order den
 * Platzhalter {@code LADEPLAN}. Ein Protokoll über beide Zeiträume wäre nicht mehr lesbar — und
 * genau dafür ist es da.
 */
public enum Steuerverfahren {

    /**
     * Die Regelkaskade hat entschieden (Specs/Einspeisesteuerung.md, FR-2).
     *
     * <p>Gilt in der <b>Schattenrechnung</b> (FR-1a) durchgehend, und später bei jedem Rückfall:
     * fehlender Prognose, fehlendem Faktor, fehlendem Lastprofil, fehlender Kapazität oder
     * fehlenden Preisen für den Resttag.
     */
    REGEL,

    /**
     * Die Merit-Order über den Resttag hat entschieden (Specs/Ladeplanung.md, FR-1).
     *
     * <p>Wird erst nach dem Umschalten vergeben. In der Schattenrechnung steht das Ergebnis der
     * Merit-Order in {@code ladeplan_batterieladung}, ohne den Entscheid zu bestimmen.
     */
    MERIT_ORDER
}
