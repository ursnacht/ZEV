package ch.nacht.dto;

/**
 * Response DTO for AI-based unit matching.
 * Contains the matched unit information and confidence level.
 */
public class EinheitMatchResponseDTO {

    private Long einheitId;
    private String einheitName;
    private double confidence;  // 0.0 - 1.0
    private boolean matched;
    private String message;     // Error message if not matched

    public EinheitMatchResponseDTO() {
    }

    private EinheitMatchResponseDTO(Builder builder) {
        this.einheitId = builder.einheitId;
        this.einheitName = builder.einheitName;
        this.confidence = builder.confidence;
        this.matched = builder.matched;
        this.message = builder.message;
    }

    public static Builder builder() {
        return new Builder();
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

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class Builder {
        private Long einheitId;
        private String einheitName;
        private double confidence;
        private boolean matched;
        private String message;

        public Builder einheitId(Long einheitId) {
            this.einheitId = einheitId;
            return this;
        }

        public Builder einheitName(String einheitName) {
            this.einheitName = einheitName;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder matched(boolean matched) {
            this.matched = matched;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public EinheitMatchResponseDTO build() {
            return new EinheitMatchResponseDTO(this);
        }
    }
}
