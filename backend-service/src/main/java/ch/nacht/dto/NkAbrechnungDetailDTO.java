package ch.nacht.dto;

import ch.nacht.entity.NkAbrechnung;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Abrechnung samt allem, was zu ihr gehört — Antwort von {@code GET /{id}} und Rumpf von
 * {@code PUT /{id}} (Specs/Nebenkosten/Abrechnung.md, FR-6).
 *
 * <p><b>Ein Aufruf statt fünf</b> (NFR-1): Die Maske zeigt Kopf, Positionen und alle Mieterblöcke
 * gleichzeitig; sie einzeln zu laden hiesse, die Sofortberechnung auf fünf Antworten warten zu
 * lassen.
 *
 * <p>{@link #berechnung} ist beim Speichern <b>Ausgabe</b> und wird im Rumpf ignoriert: Das
 * Backend rechnet selbst und ist massgebend. Die Maske zeigt nach dem Speichern die hier
 * gelieferten Werte, nicht ihre eigene Vorschau.
 */
public class NkAbrechnungDetailDTO {

    private NkAbrechnung abrechnung;

    private List<NkPositionDTO> positionen = new ArrayList<>();

    private List<NkZusatzDTO> zusaetze = new ArrayList<>();

    private List<NkAkontoDTO> akonto = new ArrayList<>();

    /** Berechnete Mieterblöcke und Kontrollzahlen; nur Ausgabe. */
    private NkBerechnungDTO berechnung;

    /**
     * Vorschlag für die Anzahl Wohnungen: die Zahl der {@code CONSUMER}-Einheiten des Mandanten.
     * {@code null}, wenn es keine gibt — dann bleibt das Feld leer statt auf {@code 0} zu stehen,
     * das gegen den eigenen CHECK-Constraint verstiesse (FR-2).
     */
    private Integer anzahlWohnungenVorschlag;

    /**
     * Vorschlag für die Anzahl Personen: die Anzahl Wohnungen. Zusammen mit der Vorgabe „1 Person
     * je Mieter" rechnet eine Umlage pro Person damit genau wie eine Umlage pro Wohnung.
     */
    private Integer anzahlPersonenVorschlag;

    /** Erfasste Personenzahlen je Mieter; fehlt eine, gilt 1. */
    private List<NkPersonDTO> personen = new ArrayList<>();

    public NkAbrechnungDetailDTO() {
    }

    public NkAbrechnung getAbrechnung() {
        return abrechnung;
    }

    public void setAbrechnung(NkAbrechnung abrechnung) {
        this.abrechnung = abrechnung;
    }

    public List<NkPositionDTO> getPositionen() {
        return positionen;
    }

    public void setPositionen(List<NkPositionDTO> positionen) {
        this.positionen = positionen != null ? positionen : new ArrayList<>();
    }

    public List<NkZusatzDTO> getZusaetze() {
        return zusaetze;
    }

    public void setZusaetze(List<NkZusatzDTO> zusaetze) {
        this.zusaetze = zusaetze != null ? zusaetze : new ArrayList<>();
    }

    public List<NkAkontoDTO> getAkonto() {
        return akonto;
    }

    public void setAkonto(List<NkAkontoDTO> akonto) {
        this.akonto = akonto != null ? akonto : new ArrayList<>();
    }

    public NkBerechnungDTO getBerechnung() {
        return berechnung;
    }

    public void setBerechnung(NkBerechnungDTO berechnung) {
        this.berechnung = berechnung;
    }

    public Integer getAnzahlPersonenVorschlag() {
        return anzahlPersonenVorschlag;
    }

    public void setAnzahlPersonenVorschlag(Integer anzahlPersonenVorschlag) {
        this.anzahlPersonenVorschlag = anzahlPersonenVorschlag;
    }

    public List<NkPersonDTO> getPersonen() {
        return personen;
    }

    public void setPersonen(List<NkPersonDTO> personen) {
        this.personen = personen;
    }

    public Integer getAnzahlWohnungenVorschlag() {
        return anzahlWohnungenVorschlag;
    }

    public void setAnzahlWohnungenVorschlag(Integer anzahlWohnungenVorschlag) {
        this.anzahlWohnungenVorschlag = anzahlWohnungenVorschlag;
    }
}
