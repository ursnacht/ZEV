package ch.nacht.dto;

import ch.nacht.entity.Erfassungsart;
import ch.nacht.entity.Tarifposition;

import java.math.BigDecimal;

/**
 * Transportobjekt für eine {@link Tarifposition}.
 *
 * <p>Bewusst mit flachen IDs statt der Entity: {@code mieter} und {@code tarif} sind
 * {@code LAZY}-Beziehungen, deren direkte Serialisierung Proxy-Fehler auslöst und mehr Daten
 * preisgäbe als nötig. Bezeichnung und Preis des Tarifs werden mitgeliefert, damit die Liste
 * ohne Zusatzabfrage darstellbar ist.
 */
public class TarifpositionDTO {

    private Long id;
    private Long mieterId;
    private String mieterName;
    private Long tarifId;
    private String tarifBezeichnung;
    private BigDecimal tarifPreis;
    private Integer jahr;
    private Integer quartal;
    private BigDecimal menge;
    private Erfassungsart erfassungsart;
    private String quellReferenz;
    private String bemerkung;

    public TarifpositionDTO() {
    }

    /**
     * Build a DTO from an entity.
     *
     * @param position Entity
     * @return DTO with resolved tenant and tariff data
     */
    public static TarifpositionDTO von(Tarifposition position) {
        TarifpositionDTO dto = new TarifpositionDTO();
        dto.setId(position.getId());
        if (position.getMieter() != null) {
            dto.setMieterId(position.getMieter().getId());
            dto.setMieterName(position.getMieter().getName());
        }
        if (position.getTarif() != null) {
            dto.setTarifId(position.getTarif().getId());
            dto.setTarifBezeichnung(position.getTarif().getBezeichnung());
            dto.setTarifPreis(position.getTarif().getPreis());
        }
        dto.setJahr(position.getJahr());
        dto.setQuartal(position.getQuartal());
        dto.setMenge(position.getMenge());
        dto.setErfassungsart(position.getErfassungsart());
        dto.setQuellReferenz(position.getQuellReferenz());
        dto.setBemerkung(position.getBemerkung());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public String getMieterName() {
        return mieterName;
    }

    public void setMieterName(String mieterName) {
        this.mieterName = mieterName;
    }

    public Long getTarifId() {
        return tarifId;
    }

    public void setTarifId(Long tarifId) {
        this.tarifId = tarifId;
    }

    public String getTarifBezeichnung() {
        return tarifBezeichnung;
    }

    public void setTarifBezeichnung(String tarifBezeichnung) {
        this.tarifBezeichnung = tarifBezeichnung;
    }

    public BigDecimal getTarifPreis() {
        return tarifPreis;
    }

    public void setTarifPreis(BigDecimal tarifPreis) {
        this.tarifPreis = tarifPreis;
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
}
