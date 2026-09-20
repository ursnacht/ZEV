package ch.nacht.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Konfiguration der Einspeisesteuerung <b>je Mandant</b>
 * (Specs/Einspeisesteuerung.md, FR-2 und FR-7).
 *
 * <p>Liegt als Block {@code steuerung} in {@code organisation.konfiguration} und wird über
 * {@link RechnungKonfigurationDTO} transportiert.
 *
 * <p><b>Warum je Mandant und nicht in {@code .env}:</b> Die Schwellen hängen an der Anlage — an
 * Batteriegrösse, PV-Leistung und Verbrauchsprofil. Zwei Mandanten haben verschiedene Werte, und
 * eine Umgebungsvariable kann das nicht abbilden.
 *
 * <p><b>Fehlt der Block ganz, gelten die Vorgaben</b> ({@link #VORGABE_SCHWELLWERT},
 * {@link #VORGABE_SPEICHERWERT}, {@link #VORGABE_SOC_MINIMUM}) — ein Mandant, der den Flag einschaltet, muss nicht zuerst
 * konfigurieren. Die Auflösung passiert im Service, nicht hier: Ein DTO mit vorbelegten Feldern
 * liesse sich nicht mehr von einem unterscheiden, in dem jemand genau diese Werte eingetragen hat.
 */
public class SteuerKonfigurationDTO {

    /**
     * Vorgabe des Schwellwerts in CHF/kWh.
     *
     * <p>Ein <b>Startwert, keine Aussage</b>: Über neun ausgewertete Tage hätte er zweimal
     * ausgelöst. Der brauchbare Wert wird über das Nachrechnen (FR-6) empirisch bestimmt.
     */
    public static final BigDecimal VORGABE_SCHWELLWERT = new BigDecimal("0.05000");

    /**
     * Vorgabe des Werts einer gespeicherten Kilowattstunde in CHF/kWh.
     *
     * <p>Hergeleitet aus dem <b>vermiedenen Netzbezug</b> (0.34936) abzüglich Lade- und
     * Entladeverlusten. Damit schlägt Speichern das Einspeisen immer, solange die Preise in der
     * bisherigen Spanne bleiben (höchster gemessener Preis: 0.238) — die Regel
     * {@code EINSPEISEN_LOHNT} ist ein Wächter für Knappheitspreise, kein Normalfall.
     */
    public static final BigDecimal VORGABE_SPEICHERWERT = new BigDecimal("0.31000");

    /**
     * Vorgabe des Mindest-Ladezustands in Prozent für {@code SOC_TIEF}.
     *
     * <p>20 % liegt über dem üblichen Tiefentladeschutz und lässt dem Speicher Reserve für den
     * Abend. Ein <b>Startwert</b>: Der brauchbare Wert hängt am Verbrauchsprofil und wird sich
     * über die Auswertung zeigen.
     */
    public static final BigDecimal VORGABE_SOC_MINIMUM = new BigDecimal("20.0");

    /**
     * Vorgabe der Hysterese in Prozentpunkten für {@code SOC_TIEF}.
     *
     * <p>5 Punkte entsprechen bei einem 20-kWh-Speicher rund einer Kilowattstunde — genug, dass
     * eine Freigabe spürbar nachlädt, statt beim ersten Messwert darüber wieder zu enden.
     */
    public static final BigDecimal VORGABE_SOC_HYSTERESE = new BigDecimal("5.0");

    /**
     * Vorgabe des Mindest-Preisabstands in CHF/kWh für {@code WARTEN_AUF_TAL}.
     *
     * <p>2 Rappen. Darunter lohnt das Warten die verstreichende Sonnenstunde nicht: Bei einem
     * 20-kWh-Speicher bringt ein Abstand von 0.005 über eine volle Ladung rund fünf Rappen, das
     * Risiko einer am Abend leeren Batterie steht dagegen bei einem Vielfachen davon.
     */
    public static final BigDecimal VORGABE_MINDEST_ABSTAND = new BigDecimal("0.02000");

    /**
     * Schwellwert für {@code WARTEN_AUF_TAL} in CHF/kWh.
     *
     * <p><b>Darf negativ sein</b> — es gibt keinen Vorzeichen-Wächter, dieselbe Begründung wie bei
     * {@code preiszeitreihe.preis}: Negative Preise sind genau die Stunden, für die sich eine
     * Steuerung lohnt.
     */
    private BigDecimal schwellwert;

    /** Wert einer gespeicherten kWh in CHF/kWh für {@code EINSPEISEN_LOHNT}; darf negativ sein. */
    private BigDecimal speicherwert;

    /**
     * Nutzbare Batteriekapazität in kWh.
     *
     * <p><b>Rein dokumentierend</b> in dieser Ausbaustufe: Keine Regel wertet sie aus. Sie steht
     * hier, weil jede spätere Ertragsrechnung sie braucht — und weil die Frage, ob die Kapazität
     * überhaupt knapp ist, über den Nutzen der ganzen Steuerung entscheidet.
     */
    @PositiveOrZero(message = "Batteriekapazitaet darf nicht negativ sein")
    private BigDecimal batteriekapazitaet;

    /**
     * Mindest-Ladezustand in Prozent für {@code SOC_TIEF}.
     *
     * <p>Anders als Schwellwert und Speicherwert <b>nicht</b> negativ erlaubt: Ein Ladezustand ist
     * ein Anteil, kein Preis. 0 schaltet die Regel faktisch ab — unter 0 fällt kein Messwert.
     */
    @PositiveOrZero(message = "Mindest-Ladezustand darf nicht negativ sein")
    private BigDecimal socMinimum;

    /**
     * Hysterese in Prozentpunkten über {@link #socMinimum}, bis zu denen weiter geladen wird.
     *
     * <p><b>Warum es sie braucht:</b> Ohne Abstand würde eine Freigabe beim ersten Messwert über
     * der Grenze wieder enden, und der Entscheid wechselte im Viertelstundentakt, sobald der
     * Ladezustand um den Mindestwert pendelt.
     *
     * <p>0 schaltet die Hysterese ab — dann gilt allein {@link #socMinimum}.
     */
    @PositiveOrZero(message = "Hysterese darf nicht negativ sein")
    private BigDecimal socHysterese;

    /**
     * Mindestabstand in CHF/kWh, um den das erwartete Tal unter dem aktuellen Preis liegen muss,
     * damit {@code WARTEN_AUF_TAL} greift.
     *
     * <p><b>Nicht negativ</b>, anders als Schwellwert und Speicherwert: Ein negativer Abstand
     * hiesse, auch auf ein <i>teureres</i> Intervall zu warten. 0 schaltet die Prüfung ab und
     * stellt das Verhalten vor V164 her.
     */
    @PositiveOrZero(message = "Mindest-Preisabstand darf nicht negativ sein")
    private BigDecimal mindestAbstand;

    public SteuerKonfigurationDTO() {
    }

    public SteuerKonfigurationDTO(BigDecimal schwellwert, BigDecimal speicherwert,
                                  BigDecimal batteriekapazitaet) {
        this.schwellwert = schwellwert;
        this.speicherwert = speicherwert;
        this.batteriekapazitaet = batteriekapazitaet;
    }

    /** Der Schwellwert oder die Vorgabe, wenn keiner erfasst ist. */
    public BigDecimal schwellwertOderVorgabe() {
        return schwellwert != null ? schwellwert : VORGABE_SCHWELLWERT;
    }

    /** Der Speicherwert oder die Vorgabe, wenn keiner erfasst ist. */
    public BigDecimal speicherwertOderVorgabe() {
        return speicherwert != null ? speicherwert : VORGABE_SPEICHERWERT;
    }

    /** Der Mindest-Ladezustand oder die Vorgabe, wenn keiner erfasst ist. */
    public BigDecimal socMinimumOderVorgabe() {
        return socMinimum != null ? socMinimum : VORGABE_SOC_MINIMUM;
    }

    /** Die Hysterese oder die Vorgabe, wenn keine erfasst ist. */
    public BigDecimal socHystereseOderVorgabe() {
        return socHysterese != null ? socHysterese : VORGABE_SOC_HYSTERESE;
    }

    /** Der Mindest-Preisabstand oder die Vorgabe, wenn keiner erfasst ist. */
    public BigDecimal mindestAbstandOderVorgabe() {
        return mindestAbstand != null ? mindestAbstand : VORGABE_MINDEST_ABSTAND;
    }

    public BigDecimal getSchwellwert() {
        return schwellwert;
    }

    public void setSchwellwert(BigDecimal schwellwert) {
        this.schwellwert = schwellwert;
    }

    public BigDecimal getSpeicherwert() {
        return speicherwert;
    }

    public void setSpeicherwert(BigDecimal speicherwert) {
        this.speicherwert = speicherwert;
    }

    public BigDecimal getBatteriekapazitaet() {
        return batteriekapazitaet;
    }

    public void setBatteriekapazitaet(BigDecimal batteriekapazitaet) {
        this.batteriekapazitaet = batteriekapazitaet;
    }

    public BigDecimal getSocMinimum() {
        return socMinimum;
    }

    public void setSocMinimum(BigDecimal socMinimum) {
        this.socMinimum = socMinimum;
    }

    public BigDecimal getSocHysterese() {
        return socHysterese;
    }

    public void setSocHysterese(BigDecimal socHysterese) {
        this.socHysterese = socHysterese;
    }

    public BigDecimal getMindestAbstand() {
        return mindestAbstand;
    }

    public void setMindestAbstand(BigDecimal mindestAbstand) {
        this.mindestAbstand = mindestAbstand;
    }

    @Override
    public String toString() {
        return "SteuerKonfigurationDTO{schwellwert=" + schwellwert +
               ", speicherwert=" + speicherwert +
               ", batteriekapazitaet=" + batteriekapazitaet +
               ", socMinimum=" + socMinimum +
               ", socHysterese=" + socHysterese +
               ", mindestAbstand=" + mindestAbstand + "}";
    }
}
