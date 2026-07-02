package ch.nacht.dto;

/**
 * Admin-Sicht eines Feature-Flags für die Verwaltung unter /einstellungen.
 * Enthält den deklarierten Default, den effektiven Wert des Mandanten und die Quelle.
 */
public class FeatureFlagDTO {

    /** Quelle des effektiven Werts. */
    public enum Quelle {
        /** Wert stammt aus dem globalen Default (keine Überschreibung). */
        DEFAULT,
        /** Wert ist mandantenspezifisch überschrieben. */
        OVERRIDE
    }

    private String key;
    private String beschreibungKey;
    private boolean defaultWert;
    private boolean effektiv;
    private Quelle quelle;

    public FeatureFlagDTO() {
    }

    public FeatureFlagDTO(String key, String beschreibungKey, boolean defaultWert, boolean effektiv, Quelle quelle) {
        this.key = key;
        this.beschreibungKey = beschreibungKey;
        this.defaultWert = defaultWert;
        this.effektiv = effektiv;
        this.quelle = quelle;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getBeschreibungKey() {
        return beschreibungKey;
    }

    public void setBeschreibungKey(String beschreibungKey) {
        this.beschreibungKey = beschreibungKey;
    }

    public boolean isDefaultWert() {
        return defaultWert;
    }

    public void setDefaultWert(boolean defaultWert) {
        this.defaultWert = defaultWert;
    }

    public boolean isEffektiv() {
        return effektiv;
    }

    public void setEffektiv(boolean effektiv) {
        this.effektiv = effektiv;
    }

    public Quelle getQuelle() {
        return quelle;
    }

    public void setQuelle(Quelle quelle) {
        this.quelle = quelle;
    }
}
