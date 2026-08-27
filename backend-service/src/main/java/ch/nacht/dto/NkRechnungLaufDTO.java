package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ergebnis eines Rechnungslaufs ueber eine Nebenkostenabrechnung
 * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-6).
 *
 * <p>Die Antwortform ist <b>fest</b> und haengt nicht von einer Rechnungsart ab — das war der Grund
 * fuer einen eigenen Endpunkt statt einer Erweiterung von {@code POST /api/rechnungen/generate}.
 *
 * <p>{@link #anzahlRechnungen} und {@link #anzahlForderungen} stehen getrennt, damit
 * „0 Forderungen" nicht wie ein Fehlschlag aussieht: Bei durchweg Guthaben entstehen Rechnungen,
 * aber keine Forderungen (FR-4).
 */
public class NkRechnungLaufDTO {

    private Long abrechnungId;
    private String bezeichnung;
    private LocalDate von;
    private LocalDate bis;

    private int anzahlRechnungen;
    private int anzahlForderungen;
    private BigDecimal summeForderungen = BigDecimal.ZERO;

    private List<NkRechnungErgebnisDTO> rechnungen = new ArrayList<>();

    public NkRechnungLaufDTO() {
    }

    /**
     * Eine Zeile des Ergebnisses — ein Mieter.
     *
     * <p>Kein {@code downloadKey}: Das PDF wird ueber {@code abrechnungId} und {@link #mieterId}
     * geholt. Ein Schluessel im Nutzdatenteil waere ein zweiter Namensraum neben dem der Ablage.
     */
    public static class NkRechnungErgebnisDTO {

        private Long mieterId;
        private String mieterName;

        /** Zahlbarer Betrag, auf 5 Rappen gerundet; negativ bei einem Guthaben. */
        private BigDecimal saldo = BigDecimal.ZERO;

        /** {@code false} bei Saldo ≤ 0 — dann entsteht ein PDF, aber keine Forderung (FR-4). */
        private boolean forderungGebucht;

        /** Dateiname fuer den Download; {@code null}, wenn kein PDF entstanden ist. */
        private String filename;

        /** Uebersetzungsschluessel bei einem gescheiterten Mieter, sonst {@code null}. */
        private String fehler;

        public NkRechnungErgebnisDTO() {
        }

        public Long getMieterId() {
            return mieterId;
        }

        public void setMieterId(Long mieterId) {
            this.mieterId = mieterId;
        }

        public String getMieterName() {
            return mieterName;
        }

        public void setMieterName(String mieterName) {
            this.mieterName = mieterName;
        }

        public BigDecimal getSaldo() {
            return saldo;
        }

        public void setSaldo(BigDecimal saldo) {
            this.saldo = saldo;
        }

        public boolean isForderungGebucht() {
            return forderungGebucht;
        }

        public void setForderungGebucht(boolean forderungGebucht) {
            this.forderungGebucht = forderungGebucht;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getFehler() {
            return fehler;
        }

        public void setFehler(String fehler) {
            this.fehler = fehler;
        }
    }

    public Long getAbrechnungId() {
        return abrechnungId;
    }

    public void setAbrechnungId(Long abrechnungId) {
        this.abrechnungId = abrechnungId;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public LocalDate getVon() {
        return von;
    }

    public void setVon(LocalDate von) {
        this.von = von;
    }

    public LocalDate getBis() {
        return bis;
    }

    public void setBis(LocalDate bis) {
        this.bis = bis;
    }

    public int getAnzahlRechnungen() {
        return anzahlRechnungen;
    }

    public void setAnzahlRechnungen(int anzahlRechnungen) {
        this.anzahlRechnungen = anzahlRechnungen;
    }

    public int getAnzahlForderungen() {
        return anzahlForderungen;
    }

    public void setAnzahlForderungen(int anzahlForderungen) {
        this.anzahlForderungen = anzahlForderungen;
    }

    public BigDecimal getSummeForderungen() {
        return summeForderungen;
    }

    public void setSummeForderungen(BigDecimal summeForderungen) {
        this.summeForderungen = summeForderungen;
    }

    public List<NkRechnungErgebnisDTO> getRechnungen() {
        return rechnungen;
    }

    public void setRechnungen(List<NkRechnungErgebnisDTO> rechnungen) {
        this.rechnungen = rechnungen;
    }
}
