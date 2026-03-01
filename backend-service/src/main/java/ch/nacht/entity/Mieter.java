package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

/**
 * Entity representing a tenant with lease period.
 */
@Entity
@Table(name = "mieter", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Mieter {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mieter_seq")
    @SequenceGenerator(name = "mieter_seq", sequenceName = "zev.mieter_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @NotBlank(message = "Strasse is required")
    @Size(max = 150, message = "Strasse must not exceed 150 characters")
    @Column(name = "strasse", length = 150, nullable = false)
    private String strasse;

    @NotBlank(message = "PLZ is required")
    @Size(max = 20, message = "PLZ must not exceed 20 characters")
    @Column(name = "plz", length = 20, nullable = false)
    private String plz;

    @NotBlank(message = "Ort is required")
    @Size(max = 100, message = "Ort must not exceed 100 characters")
    @Column(name = "ort", length = 100, nullable = false)
    private String ort;

    @NotNull(message = "Mietbeginn is required")
    @Column(name = "mietbeginn", nullable = false)
    private LocalDate mietbeginn;

    @Column(name = "mietende")
    private LocalDate mietende;

    @NotNull(message = "Einheit is required")
    @Column(name = "einheit_id", nullable = false)
    private Long einheitId;

    public Mieter() {
    }

    public Mieter(String name, LocalDate mietbeginn, Long einheitId) {
        this.name = name;
        this.mietbeginn = mietbeginn;
        this.einheitId = einheitId;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStrasse() {
        return strasse;
    }

    public void setStrasse(String strasse) {
        this.strasse = strasse;
    }

    public String getPlz() {
        return plz;
    }

    public void setPlz(String plz) {
        this.plz = plz;
    }

    public String getOrt() {
        return ort;
    }

    public void setOrt(String ort) {
        this.ort = ort;
    }

    public LocalDate getMietbeginn() {
        return mietbeginn;
    }

    public void setMietbeginn(LocalDate mietbeginn) {
        this.mietbeginn = mietbeginn;
    }

    public LocalDate getMietende() {
        return mietende;
    }

    public void setMietende(LocalDate mietende) {
        this.mietende = mietende;
    }

    public Long getEinheitId() {
        return einheitId;
    }

    public void setEinheitId(Long einheitId) {
        this.einheitId = einheitId;
    }

    @Override
    public String toString() {
        return "Mieter{id=" + id + ", orgId=" + orgId + ", name='" + name + "', strasse='" + strasse +
               "', plz='" + plz + "', ort='" + ort + "', mietbeginn=" + mietbeginn +
               ", mietende=" + mietende + ", einheitId=" + einheitId + "}";
    }
}
