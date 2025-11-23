package ch.nacht.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messwerte", schema = "zev")
public class Messwerte {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "messwerte_seq")
    @SequenceGenerator(name = "messwerte_seq", sequenceName = "zev.messwerte_seq", allocationSize = 1)
    private Long id;

    @Column(name = "zeit", nullable = false)
    private LocalDateTime zeit;

    @Column(name = "total", nullable = false)
    private Double total;

    @Column(name = "zev", nullable = false)
    private Double zev;

    public Messwerte() {
    }

    public Messwerte(LocalDateTime zeit, Double total, Double zev) {
        this.zeit = zeit;
        this.total = total;
        this.zev = zev;
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

    @Override
    public String toString() {
        return "Messwerte{id=" + id + ", zeit=" + zeit + ", total=" + total + ", zev=" + zev + "}";
    }
}
