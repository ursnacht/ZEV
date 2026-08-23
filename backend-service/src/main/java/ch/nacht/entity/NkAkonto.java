package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * Akonto-Zahlungen eines Mieters zu einer Abrechnung
 * (Specs/Nebenkosten/Abrechnung.md, FR-4).
 *
 * <p>{@link #anzahlMonate} wird anteilig gerechnet: Ein angebrochener Monat zählt mit dem Anteil
 * seiner Miettage. Vorgeschlagen wird der Wert vom Backend, überschreibbar bleibt er trotzdem —
 * eine Zahlung, die tatsächlich anders geflossen ist, muss erfassbar sein.
 *
 * <p>{@link #korrektur} darf als einziges Feld negativ sein: Sie zieht eine Zahlung ab, die im
 * Vorjahr bereits berücksichtigt wurde.
 */
@Entity
@Table(name = "nk_akonto", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class NkAkonto {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nk_akonto_seq")
    @SequenceGenerator(name = "nk_akonto_seq", sequenceName = "zev.nk_akonto_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotNull(message = "Abrechnung is required")
    @Column(name = "abrechnung_id", nullable = false)
    private Long abrechnungId;

    @NotNull(message = "Mieter is required")
    @Column(name = "mieter_id", nullable = false)
    private Long mieterId;

    /** Anteilige Anzahl Monate im Zeitraum, zwei Nachkommastellen. */
    @NotNull(message = "Anzahl Monate is required")
    @PositiveOrZero(message = "Anzahl Monate must not be negative")
    @Column(name = "anzahl_monate", precision = 5, scale = 2, nullable = false)
    private BigDecimal anzahlMonate;

    @NotNull(message = "Betrag pro Monat is required")
    @PositiveOrZero(message = "Betrag pro Monat must not be negative")
    @Column(name = "betrag_pro_monat", precision = 10, scale = 2, nullable = false)
    private BigDecimal betragProMonat;

    /** Freier Korrekturbetrag; bewusst ohne {@code @PositiveOrZero} — er darf negativ sein. */
    @NotNull(message = "Korrektur is required")
    @Column(name = "korrektur", precision = 10, scale = 2, nullable = false)
    private BigDecimal korrektur = BigDecimal.ZERO;

    public NkAkonto() {
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

    public BigDecimal getAnzahlMonate() {
        return anzahlMonate;
    }

    public void setAnzahlMonate(BigDecimal anzahlMonate) {
        this.anzahlMonate = anzahlMonate;
    }

    public BigDecimal getBetragProMonat() {
        return betragProMonat;
    }

    public void setBetragProMonat(BigDecimal betragProMonat) {
        this.betragProMonat = betragProMonat;
    }

    public BigDecimal getKorrektur() {
        return korrektur;
    }

    public void setKorrektur(BigDecimal korrektur) {
        this.korrektur = korrektur;
    }

    @Override
    public String toString() {
        return "NkAkonto{id=" + id + ", abrechnungId=" + abrechnungId + ", mieterId=" + mieterId
                + ", anzahlMonate=" + anzahlMonate + ", betragProMonat=" + betragProMonat
                + ", korrektur=" + korrektur + "}";
    }
}
