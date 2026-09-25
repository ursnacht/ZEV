package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein Intervall der Produktionsprognose (Specs/Ladeplanung.md, FR-3 und FR-7).
 *
 * <p>Enthält <b>beide</b> Grössen: die Einstrahlung, wie sie von Open-Meteo kam, und die daraus
 * erwartete Erzeugung. Zusammen mit dem Faktor lässt sich die Rechnung nachvollziehen — wer der
 * Prognose misstraut, kann sie prüfen, statt sie glauben zu müssen.
 */
public class PrognosepunktDTO {

    /** <b>Beginn</b> des 15-Minuten-Intervalls in <b>Ortszeit</b> — wie {@code zeit_von}. */
    private LocalDateTime zeit;

    /** Einstrahlung auf die geneigte Modulfläche in W/m². */
    private BigDecimal gti;

    /**
     * Erwartete Erzeugung in kWh; {@code null}, solange kein Umrechnungsfaktor gelernt ist.
     *
     * <p>Bewusst leer statt 0: Eine 0 sähe aus wie „nichts erwartet", obwohl der Grund „noch nicht
     * gelernt" ist.
     */
    private BigDecimal erwarteteErzeugung;

    /** Der gelernte Faktor von W/m² auf kWh; {@code null}, wenn zu wenig Historie vorliegt. */
    private BigDecimal faktor;

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

    public BigDecimal getErwarteteErzeugung() {
        return erwarteteErzeugung;
    }

    public void setErwarteteErzeugung(BigDecimal erwarteteErzeugung) {
        this.erwarteteErzeugung = erwarteteErzeugung;
    }

    public BigDecimal getFaktor() {
        return faktor;
    }

    public void setFaktor(BigDecimal faktor) {
        this.faktor = faktor;
    }
}
