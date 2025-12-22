package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity representing a tariff with validity period.
 */
@Entity
@Table(name = "tarif", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Tarif {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tarif_seq")
    @SequenceGenerator(name = "tarif_seq", sequenceName = "zev.tarif_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @NotBlank(message = "Bezeichnung is required")
    @Size(max = 30, message = "Bezeichnung must not exceed 30 characters")
    @Column(name = "bezeichnung", length = 30, nullable = false)
    private String bezeichnung;

    @NotNull(message = "Tariftyp is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "tariftyp", length = 10, nullable = false)
    private TarifTyp tariftyp;

    @NotNull(message = "Preis is required")
    @Positive(message = "Preis must be positive")
    @Column(name = "preis", precision = 10, scale = 5, nullable = false)
    private BigDecimal preis;

    @NotNull(message = "Gültig von is required")
    @Column(name = "gueltig_von", nullable = false)
    private LocalDate gueltigVon;

    @NotNull(message = "Gültig bis is required")
    @Column(name = "gueltig_bis", nullable = false)
    private LocalDate gueltigBis;

    public Tarif() {
    }

    public Tarif(String bezeichnung, TarifTyp tariftyp, BigDecimal preis, LocalDate gueltigVon, LocalDate gueltigBis) {
        this.bezeichnung = bezeichnung;
        this.tariftyp = tariftyp;
        this.preis = preis;
        this.gueltigVon = gueltigVon;
        this.gueltigBis = gueltigBis;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public TarifTyp getTariftyp() {
        return tariftyp;
    }

    public void setTariftyp(TarifTyp tariftyp) {
        this.tariftyp = tariftyp;
    }

    public BigDecimal getPreis() {
        return preis;
    }

    public void setPreis(BigDecimal preis) {
        this.preis = preis;
    }

    public LocalDate getGueltigVon() {
        return gueltigVon;
    }

    public void setGueltigVon(LocalDate gueltigVon) {
        this.gueltigVon = gueltigVon;
    }

    public LocalDate getGueltigBis() {
        return gueltigBis;
    }

    public void setGueltigBis(LocalDate gueltigBis) {
        this.gueltigBis = gueltigBis;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    @Override
    public String toString() {
        return "Tarif{id=" + id + ", orgId=" + orgId + ", bezeichnung='" + bezeichnung + "', tariftyp=" + tariftyp +
               ", preis=" + preis + ", gueltigVon=" + gueltigVon + ", gueltigBis=" + gueltigBis + "}";
    }
}
