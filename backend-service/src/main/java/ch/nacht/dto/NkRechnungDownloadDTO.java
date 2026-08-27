package ch.nacht.dto;

/**
 * Ein erzeugtes NK-Rechnungs-PDF samt Dateiname
 * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-6).
 *
 * <p>Beides kommt aus derselben Ablage und gehoert zusammen: Der Ablageschluessel besteht aus zwei
 * IDs und taugt nicht als Dateiname, also wird der lesbare Name mitgefuehrt.
 *
 * <p>Bewusst hier und nicht als verschachtelter Typ in {@code NkRechnungService}: Dort haette die
 * ArchUnit-Regel {@code nebenkostenServicesMustCheckFeatureFlag} die Record-Zugriffsmethoden als
 * unpruefte oeffentliche Methoden eines {@code Nk}-Services gemeldet — zu Recht, denn die Regel
 * nimmt bewusst keine verschachtelten Typen aus.
 *
 * @param pdf      das PDF
 * @param filename Dateiname fuer den Download
 */
public record NkRechnungDownloadDTO(byte[] pdf, String filename) {
}
