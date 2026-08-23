package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "einheit", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Einheit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "einheit_seq")
    @SequenceGenerator(name = "einheit_seq", sequenceName = "zev.einheit_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 30, message = "Name must be between 2 and 30 characters")
    @Column(name = "name", length = 30, nullable = false)
    private String name;

    @NotNull(message = "Typ is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false)
    private EinheitTyp typ = EinheitTyp.CONSUMER;

    @Size(max = 50, message = "Messpunkt must not exceed 50 characters")
    @Column(name = "messpunkt", length = 50)
    private String messpunkt;

    /**
     * Zählt diese Einheit als Wohnung in der Nebenkostenabrechnung?
     * (Specs/Nebenkosten/Abrechnung.md, FR-2)
     *
     * <p>Nur bei {@link EinheitTyp#CONSUMER} ausgewertet. Unter den Verbrauchern stehen auch
     * Messpunkte, die keine Wohnung sind — Allgemeinstrom, Eigenverbrauch der PV-Anlage. Sie
     * zählten sonst in den Nenner der Umlage, und bei <b>jeder</b> Position bliebe ein Anteil
     * unverteilt, als stünde eine Wohnung leer.
     *
     * <p>Bewusst ein eigenes Feld und nicht aus der Mieterzuordnung abgeleitet: Ein solcher
     * Messpunkt kann durchaus einem Mieter zugeordnet sein — dem Eigentümer.
     */
    @Column(name = "nebenkosten_relevant", nullable = false)
    private boolean nebenkostenRelevant = true;

    public Einheit() {
    }

    public Einheit(String name, EinheitTyp typ) {
        this.name = name;
        this.typ = typ;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EinheitTyp getTyp() {
        return typ;
    }

    public void setTyp(EinheitTyp typ) {
        this.typ = typ;
    }

    public String getMesspunkt() {
        return messpunkt;
    }

    public void setMesspunkt(String messpunkt) {
        this.messpunkt = messpunkt;
    }

    public boolean isNebenkostenRelevant() {
        return nebenkostenRelevant;
    }

    public void setNebenkostenRelevant(boolean nebenkostenRelevant) {
        this.nebenkostenRelevant = nebenkostenRelevant;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    @Override
    public String toString() {
        return "Einheit{id=" + id + ", orgId=" + orgId + ", name='" + name + "', typ=" + typ
                + ", messpunkt='" + messpunkt + "', nebenkostenRelevant=" + nebenkostenRelevant + "}";
    }
}
