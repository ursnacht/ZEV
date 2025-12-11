package ch.nacht.dto;

import ch.nacht.entity.EinheitTyp;

public class EinheitSummenDTO {
    private Long einheitId;
    private String einheitName;
    private EinheitTyp einheitTyp;
    private Double summeTotal;
    private Double summeZev;
    private Double summeZevCalculated;

    public EinheitSummenDTO() {
    }

    public EinheitSummenDTO(Long einheitId, String einheitName, EinheitTyp einheitTyp,
                           Double summeTotal, Double summeZev, Double summeZevCalculated) {
        this.einheitId = einheitId;
        this.einheitName = einheitName;
        this.einheitTyp = einheitTyp;
        this.summeTotal = summeTotal;
        this.summeZev = summeZev;
        this.summeZevCalculated = summeZevCalculated;
    }

    public Long getEinheitId() {
        return einheitId;
    }

    public void setEinheitId(Long einheitId) {
        this.einheitId = einheitId;
    }

    public String getEinheitName() {
        return einheitName;
    }

    public void setEinheitName(String einheitName) {
        this.einheitName = einheitName;
    }

    public EinheitTyp getEinheitTyp() {
        return einheitTyp;
    }

    public void setEinheitTyp(EinheitTyp einheitTyp) {
        this.einheitTyp = einheitTyp;
    }

    public Double getSummeTotal() {
        return summeTotal;
    }

    public void setSummeTotal(Double summeTotal) {
        this.summeTotal = summeTotal;
    }

    public Double getSummeZev() {
        return summeZev;
    }

    public void setSummeZev(Double summeZev) {
        this.summeZev = summeZev;
    }

    public Double getSummeZevCalculated() {
        return summeZevCalculated;
    }

    public void setSummeZevCalculated(Double summeZevCalculated) {
        this.summeZevCalculated = summeZevCalculated;
    }
}
