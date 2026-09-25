package ch.nacht.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vorhergesagte Sonneneinstrahlung auf die Modulfläche je 15-Minuten-Intervall
 * (Specs/Ladeplanung.md, FR-2).
 *
 * <p><b>Warum gespeichert und nicht bei Bedarf geholt.</b> Der Steuerungs-Job darf nicht an einer
 * fremden Schnittstelle hängen — eine Störung bei Open-Meteo nähme sonst die Steuerung mit. Ein
 * eigener Job holt die Prognose und legt sie hier ab; alles Weitere liest nur aus der Datenbank.
 * Dasselbe Muster wie bei {@link Preiszeitreihe}.
 *
 * <p><b>Mit {@code org_id}, anders als die Preiszeitreihe.</b> Preise gelten für den ganzen Markt;
 * die Einstrahlung hängt an Standort und Ausrichtung der Anlage.
 *
 * <p><b>Zeitbezug:</b> {@link #zeit} ist der <b>Beginn</b> des Intervalls in <b>Ortszeit</b> —
 * derselbe Bezug wie {@code steuerentscheid.zeit_von}. <b>Anders</b> als {@code messwerte.zeit}
 * (Intervall<i>ende</i>) und {@code preiszeitreihe.zeit_von} (UTC). In diesem Umfeld sind drei
 * Zeitkonventionen nebeneinander schon einmal zum Fehler geworden; deshalb steht es hier
 * ausgeschrieben.
 */
@Entity
@Table(name = "einstrahlungsprognose", schema = "zev",
        // Deckungsgleich mit V166. Der Upsert stuetzt sich auf ON CONFLICT (org_id, zeit);
        // hier deklariert, damit ein aus den Entities erzeugtes Schema ihn mitbringt.
        uniqueConstraints = @UniqueConstraint(name = "uq_einstrahlungsprognose_org_zeit",
                columnNames = {"org_id", "zeit"}))
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Einstrahlungsprognose {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "einstrahlungsprognose_seq")
    @SequenceGenerator(name = "einstrahlungsprognose_seq",
            sequenceName = "zev.einstrahlungsprognose_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /** <b>Beginn</b> des 15-Minuten-Intervalls in <b>Ortszeit</b> — siehe Klassenkommentar. */
    @NotNull
    @Column(name = "zeit", nullable = false)
    private LocalDateTime zeit;

    /**
     * Global Tilted Irradiance in W/m² — Einstrahlung auf die <b>geneigte</b> Modulfläche.
     *
     * <p>Nicht die horizontale Einstrahlung: Open-Meteo rechnet Sonnenstand, Einfallswinkel und
     * diffusen Anteil bereits gegen die konfigurierte Neigung und Ausrichtung.
     */
    @NotNull
    @Column(name = "gti", precision = 8, scale = 2, nullable = false)
    private BigDecimal gti;

    /**
     * Wann diese Prognose geholt wurde.
     *
     * <p>Ohne diesen Wert wäre nicht unterscheidbar, ob ein Eintrag von heute früh stammt oder
     * von vorgestern — und damit nicht, ob die Prognose noch brauchbar ist.
     */
    @NotNull
    @Column(name = "abgerufen_am", nullable = false)
    private LocalDateTime abgerufenAm;

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

    public LocalDateTime getZeit() {
        return zeit;
    }

    public void setZeit(LocalDateTime zeit) {
        this.zeit = zeit;
    }

    public BigDecimal getGti() {
        return gti;
    }

    public void setGti(BigDecimal gti) {
        this.gti = gti;
    }

    public LocalDateTime getAbgerufenAm() {
        return abgerufenAm;
    }

    public void setAbgerufenAm(LocalDateTime abgerufenAm) {
        this.abgerufenAm = abgerufenAm;
    }

    @Override
    public String toString() {
        return "Einstrahlungsprognose{zeit=" + zeit + ", gti=" + gti + "}";
    }
}
