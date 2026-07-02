package ch.nacht.entity;

import java.util.Optional;

/**
 * Zentrale Registry aller im Code bekannten Feature-Flags.
 * <p>
 * Jedes Flag hat einen technischen Key (Enum-Name), einen globalen Default und einen
 * Übersetzungs-Key für die Beschreibung (angezeigt in der Verwaltung unter /einstellungen).
 * Zur Laufzeit können keine beliebigen neuen Keys angelegt werden – die Menge der Flags
 * ist durch dieses Enum fest definiert.
 */
public enum FeatureFlag {

    /** Steuert die Sichtbarkeit/Verfügbarkeit des Messdatenuploads (CSV-Upload). */
    MESSWERTE_UPLOAD(true, "FEATURE_FLAG_MESSWERTE_UPLOAD");

    private final boolean defaultEnabled;
    private final String beschreibungKey;

    FeatureFlag(boolean defaultEnabled, String beschreibungKey) {
        this.defaultEnabled = defaultEnabled;
        this.beschreibungKey = beschreibungKey;
    }

    /** Globaler Default, falls keine mandantenspezifische Überschreibung existiert. */
    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    /** Übersetzungs-Key der Flag-Beschreibung (DE/EN). */
    public String getBeschreibungKey() {
        return beschreibungKey;
    }

    /**
     * Findet ein Flag anhand seines technischen Keys.
     *
     * @param key technischer Key (Enum-Name)
     * @return das Flag oder {@link Optional#empty()}, falls unbekannt
     */
    public static Optional<FeatureFlag> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (FeatureFlag flag : values()) {
            if (flag.name().equals(key)) {
                return Optional.of(flag);
            }
        }
        return Optional.empty();
    }
}
