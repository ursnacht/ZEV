package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Nebenkostenabrechnung für einen Zeitraum (Specs/Nebenkosten/Abrechnung.md).
 *
 * <p>Die Beziehungen zu Positionen, Verbrauchsmengen, Zusatzpositionen und Akonto sind bewusst
 * <b>nicht</b> als JPA-Relationen abgebildet, sondern über IDs — wie bei {@link Debitor}. Der
 * Service setzt die Antwort ohnehin zu einem DTO zusammen und rechnet dabei; Relationen brächten
 * hier nur Lazy-Loading-Fallen ohne Gewinn.
 *
 * <p>Berechnete Beträge werden nirgends gespeichert: Sie ergeben sich jederzeit aus den erfassten
 * Daten. Damit die Zahlen einer abgeschlossenen Abrechnung trotzdem stabil bleiben, ist ein
 * Mieter, der darin vorkommt, nicht mehr löschbar (ON DELETE RESTRICT).
 */
@Entity
@Table(name = "nk_abrechnung", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class NkAbrechnung {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nk_abrechnung_seq")
    @SequenceGenerator(name = "nk_abrechnung_seq", sequenceName = "zev.nk_abrechnung_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotBlank(message = "Bezeichnung is required")
    @Size(max = 150)
    @Column(name = "bezeichnung", length = 150, nullable = false)
    private String bezeichnung;

    @NotNull(message = "Datum von is required")
    @Column(name = "datum_von", nullable = false)
    private LocalDate datumVon;

    @NotNull(message = "Datum bis is required")
    @Column(name = "datum_bis", nullable = false)
    private LocalDate datumBis;

    /**
     * Nenner der Umlage: {@code anzahlWohnungen * Tage im Zeitraum}.
     *
     * <p>Bewusst erfasst und nicht aus den Einheiten abgeleitet — so lässt sich eine Wohnung
     * abbilden, die nicht an der Nebenkostenabrechnung teilnimmt. Vorbelegt wird sie im Frontend
     * mit der Zahl der CONSUMER-Einheiten.
     */
    @NotNull(message = "Anzahl Wohnungen is required")
    @Min(value = 1, message = "Anzahl Wohnungen must be at least 1")
    @Column(name = "anzahl_wohnungen", nullable = false)
    private Integer anzahlWohnungen;

    /** Gesetzt = abgeschlossen; die Abrechnung ist dann schreibgeschützt. */
    @Column(name = "abgerechnet", nullable = false)
    private boolean abgerechnet = false;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm = LocalDateTime.now();

    public NkAbrechnung() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public LocalDate getDatumVon() {
        return datumVon;
    }

    public void setDatumVon(LocalDate datumVon) {
        this.datumVon = datumVon;
    }

    public LocalDate getDatumBis() {
        return datumBis;
    }

    public void setDatumBis(LocalDate datumBis) {
        this.datumBis = datumBis;
    }

    public Integer getAnzahlWohnungen() {
        return anzahlWohnungen;
    }

    public void setAnzahlWohnungen(Integer anzahlWohnungen) {
        this.anzahlWohnungen = anzahlWohnungen;
    }

    public boolean isAbgerechnet() {
        return abgerechnet;
    }

    public void setAbgerechnet(boolean abgerechnet) {
        this.abgerechnet = abgerechnet;
    }

    public LocalDateTime getErstelltAm() {
        return erstelltAm;
    }

    public void setErstelltAm(LocalDateTime erstelltAm) {
        this.erstelltAm = erstelltAm;
    }

    @Override
    public String toString() {
        return "NkAbrechnung{id=" + id + ", bezeichnung='" + bezeichnung + "', von=" + datumVon
                + ", bis=" + datumBis + ", wohnungen=" + anzahlWohnungen
                + ", abgerechnet=" + abgerechnet + "}";
    }
}
