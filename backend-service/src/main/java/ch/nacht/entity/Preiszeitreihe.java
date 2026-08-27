package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein Viertelstundenwert der dynamischen Einspeisepreise (Specs/Preiszeitreihe.md).
 *
 * <p><b>Ohne {@code orgId} und ohne {@code @Filter}</b> — begruendete Ausnahme von der
 * Mandantenregel: Die Preise der BKW sind fuer alle Mandanten identisch, eine Kopie je Mandant
 * waere redundant, und der taegliche Abruf-Job hat keinen Mandantenkontext (anders als etwa die
 * Aggregation, die ihre {@code org_id} aus den verarbeiteten Einheiten zieht). Geschuetzt ist der
 * Zugriff ueber die Permission {@code tarife:manage} am Controller (FR-2, NFR-2). Die ArchUnit-Regel
 * {@code everyEntityMustHaveOrgId} fuehrt diese Klasse deshalb namentlich als Ausnahme.
 *
 * <p><b>Zeitstempel in UTC</b>, verbatim aus der Quelle. Lokale Zeit waere kein tauglicher
 * Eindeutigkeitsschluessel: In der Nacht der Zeitumstellung auf Winterzeit tritt die Stunde
 * 02:00–03:00 zweimal auf, vier Viertelstundenwerte wuerden vier andere ueberschreiben. Die
 * Umrechnung nach Europe/Zurich passiert erst bei der Ausgabe.
 */
@Entity
@Table(name = "preiszeitreihe", schema = "zev")
public class Preiszeitreihe {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "preiszeitreihe_seq")
    @SequenceGenerator(name = "preiszeitreihe_seq", sequenceName = "zev.preiszeitreihe_seq",
            allocationSize = 1)
    private Long id;

    @NotNull(message = "Zeit von is required")
    @Column(name = "zeit_von", nullable = false)
    private LocalDateTime zeitVon;

    @NotNull(message = "Zeit bis is required")
    @Column(name = "zeit_bis", nullable = false)
    private LocalDateTime zeitBis;

    @NotNull(message = "Preis is required")
    @PositiveOrZero(message = "Preis must not be negative")
    @Column(name = "preis", precision = 10, scale = 5, nullable = false)
    private BigDecimal preis;

    /**
     * {@code publication_timestamp} der Quelle (UTC) — <b>optional</b>. Fehlt der Wert in der
     * Antwort, bleibt das Feld leer und die Preise werden trotzdem gespeichert: Ein Pflichtfeld
     * haette 96 fehlerfreie Preise verworfen, weil ein Metadatum fehlt, und ein ersatzweise
     * eingesetzter Abrufzeitpunkt waere eine erfundene Herkunftsangabe.
     */
    @Column(name = "publikation")
    private LocalDateTime publikation;

    @Column(name = "aktualisiert_am", nullable = false)
    private LocalDateTime aktualisiertAm;

    public Preiszeitreihe() {
    }

    public Preiszeitreihe(LocalDateTime zeitVon, LocalDateTime zeitBis, BigDecimal preis,
                          LocalDateTime publikation) {
        this.zeitVon = zeitVon;
        this.zeitBis = zeitBis;
        this.preis = preis;
        this.publikation = publikation;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getZeitVon() {
        return zeitVon;
    }

    public void setZeitVon(LocalDateTime zeitVon) {
        this.zeitVon = zeitVon;
    }

    public LocalDateTime getZeitBis() {
        return zeitBis;
    }

    public void setZeitBis(LocalDateTime zeitBis) {
        this.zeitBis = zeitBis;
    }

    public BigDecimal getPreis() {
        return preis;
    }

    public void setPreis(BigDecimal preis) {
        this.preis = preis;
    }

    public LocalDateTime getPublikation() {
        return publikation;
    }

    public void setPublikation(LocalDateTime publikation) {
        this.publikation = publikation;
    }

    public LocalDateTime getAktualisiertAm() {
        return aktualisiertAm;
    }

    public void setAktualisiertAm(LocalDateTime aktualisiertAm) {
        this.aktualisiertAm = aktualisiertAm;
    }
}
