package ch.nacht.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request-Body für das Setzen einer mandantenspezifischen Feature-Flag-Überschreibung
 * ({@code PUT /api/feature-flags/{key}}).
 */
public class FeatureFlagUpdateDTO {

    @NotNull(message = "enabled is required")
    private Boolean enabled;

    public FeatureFlagUpdateDTO() {
    }

    public FeatureFlagUpdateDTO(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
