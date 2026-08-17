package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * Manuell erfasste Menge zu einem Tarif, je Einheit und Quartal.
 *
 * <p>Bewusst <b>generisch</b>: Die fachliche Bedeutung steckt ausschliesslich im referenzierten
 * {@link Tarif} — erster Anwendungsfall ist Ladestrom ({@link TarifTyp#LADESTROM}), weitere
 * (Sauna, Waschküche, …) kommen ohne Schema-Änderung dazu.
 *
 * <p>Anker ist die <b>Einheit</b> (Typ {@code LADESTATION}), nicht der Mieter: Die Menge gehört
 * zur Ladestation. Ein Mieterwechsel innerhalb eines Quartals bleibt trotzdem eindeutig, weil
 * dabei die RFID invalidiert und eine neue Ladestations-Einheit angelegt wird — jede Einheit
 * gehört über ihre ganze Lebensdauer genau einem Nutzer (Specs/Ladestationen.md).
 */
@Entity
@Table(name = "tarifposition", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Tarifposition {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tarifposition_seq")
    @SequenceGenerator(name = "tarifposition_seq", sequenceName = "zev.tarifposition_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotNull(message = "Einheit is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "einheit_id", nullable = false)
    private Einheit einheit;

    @NotNull(message = "Tarif is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarif_id", nullable = false)
    private Tarif tarif;

    @NotNull(message = "Jahr is required")
    @Min(value = 2000, message = "Jahr must be 2000 or later")
    @Max(value = 2100, message = "Jahr must be 2100 or earlier")
    @Column(name = "jahr", nullable = false)
    private Integer jahr;

    @NotNull(message = "Quartal is required")
    @Min(value = 1, message = "Quartal must be between 1 and 4")
    @Max(value = 4, message = "Quartal must be between 1 and 4")
    @Column(name = "quartal", nullable = false)
    private Integer quartal;

    /** Menge; die Mengeneinheit ergibt sich aus dem Tarif (aktuell durchgehend kWh). */
    @NotNull(message = "Menge is required")
    @PositiveOrZero(message = "Menge must not be negative")
    @Column(name = "menge", precision = 12, scale = 3, nullable = false)
    private BigDecimal menge;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "erfassungsart", length = 20, nullable = false)
    private Erfassungsart erfassungsart = Erfassungsart.MANUELL;

    @Size(max = 64, message = "Quell-Referenz must not exceed 64 characters")
    @Column(name = "quell_referenz", length = 64)
    private String quellReferenz;

    @Size(max = 200, message = "Bemerkung must not exceed 200 characters")
    @Column(name = "bemerkung", length = 200)
    private String bemerkung;

    public Tarifposition() {
    }

    public Tarifposition(Einheit einheit, Tarif tarif, Integer jahr, Integer quartal, BigDecimal menge) {
        this.einheit = einheit;
        this.tarif = tarif;
        this.jahr = jahr;
        this.quartal = quartal;
        this.menge = menge;
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

    public Einheit getEinheit() {
        return einheit;
    }

    public void setEinheit(Einheit einheit) {
        this.einheit = einheit;
    }

    public Tarif getTarif() {
        return tarif;
    }

    public void setTarif(Tarif tarif) {
        this.tarif = tarif;
    }

    public Integer getJahr() {
        return jahr;
    }

    public void setJahr(Integer jahr) {
        this.jahr = jahr;
    }

    public Integer getQuartal() {
        return quartal;
    }

    public void setQuartal(Integer quartal) {
        this.quartal = quartal;
    }

    public BigDecimal getMenge() {
        return menge;
    }

    public void setMenge(BigDecimal menge) {
        this.menge = menge;
    }

    public Erfassungsart getErfassungsart() {
        return erfassungsart;
    }

    public void setErfassungsart(Erfassungsart erfassungsart) {
        this.erfassungsart = erfassungsart;
    }

    public String getQuellReferenz() {
        return quellReferenz;
    }

    public void setQuellReferenz(String quellReferenz) {
        this.quellReferenz = quellReferenz;
    }

    public String getBemerkung() {
        return bemerkung;
    }

    public void setBemerkung(String bemerkung) {
        this.bemerkung = bemerkung;
    }

    @Override
    public String toString() {
        return "Tarifposition{id=" + id + ", orgId=" + orgId
                + ", einheit=" + (einheit != null ? einheit.getId() : null)
                + ", tarif=" + (tarif != null ? tarif.getId() : null)
                + ", jahr=" + jahr + ", quartal=" + quartal + ", menge=" + menge
                + ", erfassungsart=" + erfassungsart + "}";
    }
}
