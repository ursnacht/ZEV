package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;

import java.util.UUID;

@Entity
@Table(name = "einheit", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Einheit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "einheit_seq")
    @SequenceGenerator(name = "einheit_seq", sequenceName = "zev.einheit_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 30, message = "Name must be between 2 and 30 characters")
    @Column(name = "name", length = 30, nullable = false)
    private String name;

    @NotNull(message = "Typ is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false)
    private EinheitTyp typ = EinheitTyp.CONSUMER;

    @Size(max = 100, message = "Mietername must not exceed 100 characters")
    @Column(name = "mietername", length = 100)
    private String mietername;

    @Size(max = 50, message = "Messpunkt must not exceed 50 characters")
    @Column(name = "messpunkt", length = 50)
    private String messpunkt;

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

    public String getMietername() {
        return mietername;
    }

    public void setMietername(String mietername) {
        this.mietername = mietername;
    }

    public String getMesspunkt() {
        return messpunkt;
    }

    public void setMesspunkt(String messpunkt) {
        this.messpunkt = messpunkt;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    @Override
    public String toString() {
        return "Einheit{id=" + id + ", orgId=" + orgId + ", name='" + name + "', typ=" + typ + ", mietername='" + mietername + "', messpunkt='" + messpunkt + "'}";
    }
}
