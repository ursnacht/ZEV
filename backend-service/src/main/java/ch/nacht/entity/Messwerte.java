package ch.nacht.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Entity
@Table(name = "messwerte", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Messwerte {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "messwerte_seq")
    @SequenceGenerator(name = "messwerte_seq", sequenceName = "zev.messwerte_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "zeit", nullable = false)
    private LocalDateTime zeit;

    @Column(name = "total", nullable = false)
    private Double total;

    @Column(name = "zev", nullable = false)
    private Double zev;

    @Column(name = "zev_calculated")
    private Double zevCalculated;

    @ManyToOne
    @JoinColumn(name = "einheit_id")
    private Einheit einheit;

    public Messwerte() {
    }

    public Messwerte(LocalDateTime zeit, Double total, Double zev, Einheit einheit) {
        this.zeit = zeit;
        this.total = total;
        this.zev = zev;
        this.einheit = einheit;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getZeit() {
        return zeit;
    }

    public void setZeit(LocalDateTime zeit) {
        this.zeit = zeit;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public Double getZev() {
        return zev;
    }

    public void setZev(Double zev) {
        this.zev = zev;
    }

    public Double getZevCalculated() {
        return zevCalculated;
    }

    public void setZevCalculated(Double zevCalculated) {
        this.zevCalculated = zevCalculated;
    }

    public Einheit getEinheit() {
        return einheit;
    }

    public void setEinheit(Einheit einheit) {
        this.einheit = einheit;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    @Override
    public String toString() {
        return "Messwerte{id=" + id + ", orgId=" + orgId + ", zeit=" + zeit + ", total=" + total + ", zev=" + zev + ", zevCalculated=" + zevCalculated + ", einheit=" + (einheit != null ? einheit.getId() : null) + "}";
    }
}
