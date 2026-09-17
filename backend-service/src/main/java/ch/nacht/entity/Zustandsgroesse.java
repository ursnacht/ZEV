package ch.nacht.entity;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/**
 * Registry der Zustandsgrössen, die ein Gerät melden darf
 * (Specs/Gerätezustand.md, FR-3).
 *
 * <p><b>Diese Aufzählung ist die einzige Prüfung, welche Grössen es gibt.</b> Die Tabelle
 * {@code zev.geraetezustand} ist bewusst schmal ({@code groesse}, {@code wert}) und trägt
 * <b>keinen</b> CHECK über die Namen — er verlangte für jede neue Grösse eine Migration, und genau
 * das soll die schmale Form vermeiden. Eine neue Grösse ist deshalb ein Eintrag hier plus einer in
 * der Pi-Konfiguration: keine Migration, kein DTO-Feld, kein Rollout auf die Geräte.
 *
 * <p><b>Die Einheit steht nur hier</b> und nicht als Spalte daneben. Zwei Orte könnten einander
 * widersprechen; die Einheit folgt aus der Grösse und nicht aus dem Datensatz.
 *
 * <p><b>Was nicht umgesetzt ist, steht auch nicht drin.</b> Kandidaten für die nächste Stufe wären
 * {@code SOH} (Register 33001 des Solinteg MHT, {@code U16}, Skalierung 0.01, 0–100 %, nur
 * {@code SPEICHER}) und {@code TEMPERATUR} (Register 11032–11035, {@code I16}, Skalierung 0.1,
 * −40–100 °C, an allen Typen). Sie hier aufzuführen liesse einen Leser annehmen, sie seien
 * verfügbar.
 */
public enum Zustandsgroesse {

    /**
     * Ladezustand des Batteriespeichers in Prozent.
     *
     * <p>Quelle: Register {@code 33000} des Solinteg MHT ({@code U16}), Rohwert × {@code 0.01} =
     * Prozent. Die Protokolldokumentation notiert dieselbe Grösse umgekehrt als „Faktor 100" — wer
     * sie abschreibt, erhält 8750 statt 87.5.
     */
    SOC("%", BigDecimal.ZERO, new BigDecimal("100"), Set.of(EinheitTyp.SPEICHER));

    private final String einheit;
    private final BigDecimal minimum;
    private final BigDecimal maximum;
    private final Set<EinheitTyp> zulaessigeTypen;

    Zustandsgroesse(String einheit, BigDecimal minimum, BigDecimal maximum,
                    Set<EinheitTyp> zulaessigeTypen) {
        this.einheit = einheit;
        this.minimum = minimum;
        this.maximum = maximum;
        this.zulaessigeTypen = zulaessigeTypen;
    }

    /** Einheit für Anzeige und Meldungstexte (z.B. {@code %}). */
    public String getEinheit() {
        return einheit;
    }

    public BigDecimal getMinimum() {
        return minimum;
    }

    public BigDecimal getMaximum() {
        return maximum;
    }

    /** Erlaubter Bereich als Text, für Systemmeldungen (z.B. {@code 0–100 %}). */
    public String getBereich() {
        return minimum.toPlainString() + "–" + maximum.toPlainString() + " " + einheit;
    }

    /** Liegt der Wert im erlaubten Bereich (Grenzen eingeschlossen)? */
    public boolean istImBereich(BigDecimal wert) {
        return wert != null
                && wert.compareTo(minimum) >= 0
                && wert.compareTo(maximum) <= 0;
    }

    /**
     * Darf diese Grösse an einer Einheit dieses Typs gemeldet werden?
     *
     * <p>Ein Ladezustand an einer {@code CONSUMER}-Einheit ist ein Konfigurationsfehler — dieselbe
     * Überlegung wie bei der Register-Projektion des Ingest, wo {@code BEZUG} nur den Bezug erhält.
     */
    public boolean giltFuer(EinheitTyp typ) {
        return typ != null && zulaessigeTypen.contains(typ);
    }

    /**
     * Findet eine Grösse anhand des Schlüssels aus dem MQTT-Payload.
     *
     * <p><b>Unabhängig von Gross- und Kleinschreibung:</b> Im Payload stehen die Schlüssel klein
     * ({@code soc}), in der Tabelle der Enum-Name ({@code SOC}). Eine Nachricht soll nicht daran
     * scheitern, wie ein Gerät seine Felder schreibt.
     *
     * @param key Schlüssel aus dem Payload; {@code null} oder leer ergibt {@link Optional#empty()}
     * @return die Grösse, oder leer bei unbekanntem Schlüssel
     */
    public static Optional<Zustandsgroesse> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String gesucht = key.trim();
        for (Zustandsgroesse groesse : values()) {
            if (groesse.name().equalsIgnoreCase(gesucht)) {
                return Optional.of(groesse);
            }
        }
        return Optional.empty();
    }
}
