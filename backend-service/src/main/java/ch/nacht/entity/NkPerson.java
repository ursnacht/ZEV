package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Filter;

/**
 * Anzahl Personen je Wohnung eines Mieters, bezogen auf eine Abrechnung
 * (Specs/Nebenkosten/Abrechnung.md, FR-2).
 *
 * <p>Zähler der <b>Umlage pro Person</b>: {@code Miettage x Wohnungen x Personen}. Die Zahl gehört
 * zur Abrechnung und nicht zum Mieter — ein Haushalt wächst oder schrumpft, und eine
 * abgeschlossene Abrechnung muss ihre Zahlen behalten. Das ist derselbe Grundsatz, aus dem die
 * Anzahl Wohnungen am Kopf der Abrechnung steht und nicht aus den Einheiten abgeleitet wird.
 *
 * <p><b>Eigene Tabelle und keine Spalte in {@link NkAkonto}:</b> Die Personenzahl hat mit dem
 * Akonto nichts zu tun, auch wenn beide dieselbe Körnung (Abrechnung × Mieter) haben.
 *
 * <p>Fehlt die Zeile, gilt <b>1</b> — so rechnet eine Umlage pro Person ohne jede Erfassung genau
 * wie eine Umlage pro Wohnung.
 */
@Entity
@Table(name = "nk_person", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class NkPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nk_person_seq")
    @SequenceGenerator(name = "nk_person_seq", sequenceName = "zev.nk_person_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotNull(message = "Abrechnung is required")
    @Column(name = "abrechnung_id", nullable = false)
    private Long abrechnungId;

    @NotNull(message = "Mieter is required")
    @Column(name = "mieter_id", nullable = false)
    private Long mieterId;

    /** Personen je Wohnung; mindestens 1 — eine Wohnung ohne Bewohner trägt keine Personenumlage. */
    @NotNull(message = "Anzahl Personen is required")
    @Min(value = 1, message = "Anzahl Personen must be at least 1")
    @Column(name = "anzahl_personen", nullable = false)
    private Integer anzahlPersonen = 1;

    public NkPerson() {
    }

    public NkPerson(Long orgId, Long abrechnungId, Long mieterId, Integer anzahlPersonen) {
        this.orgId = orgId;
        this.abrechnungId = abrechnungId;
        this.mieterId = mieterId;
        this.anzahlPersonen = anzahlPersonen;
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

    public Long getAbrechnungId() {
        return abrechnungId;
    }

    public void setAbrechnungId(Long abrechnungId) {
        this.abrechnungId = abrechnungId;
    }

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public Integer getAnzahlPersonen() {
        return anzahlPersonen;
    }

    public void setAnzahlPersonen(Integer anzahlPersonen) {
        this.anzahlPersonen = anzahlPersonen;
    }

    @Override
    public String toString() {
        return "NkPerson{id=" + id + ", abrechnung=" + abrechnungId
                + ", mieter=" + mieterId + ", personen=" + anzahlPersonen + '}';
    }
}
