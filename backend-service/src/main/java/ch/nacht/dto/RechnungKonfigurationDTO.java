package ch.nacht.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for invoice configuration (Rechnungskonfiguration).
 * This structure is stored as JSON in the database.
 */
public class RechnungKonfigurationDTO {

    @NotBlank(message = "Zahlungsfrist is required")
    private String zahlungsfrist;

    @NotBlank(message = "IBAN is required")
    @Pattern(regexp = "^CH[0-9]{2}\\s?([0-9]{4}\\s?){4}[0-9]{1}$", message = "Invalid Swiss IBAN format")
    private String iban;

    @Valid
    @NotNull(message = "Steller is required")
    private StellerDTO steller;

    public RechnungKonfigurationDTO() {
    }

    public RechnungKonfigurationDTO(String zahlungsfrist, String iban, StellerDTO steller) {
        this.zahlungsfrist = zahlungsfrist;
        this.iban = iban;
        this.steller = steller;
    }

    public String getZahlungsfrist() {
        return zahlungsfrist;
    }

    public void setZahlungsfrist(String zahlungsfrist) {
        this.zahlungsfrist = zahlungsfrist;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public StellerDTO getSteller() {
        return steller;
    }

    public void setSteller(StellerDTO steller) {
        this.steller = steller;
    }

    /**
     * Invoice issuer (Rechnungssteller) information.
     */
    public static class StellerDTO {

        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Strasse is required")
        private String strasse;

        @NotBlank(message = "PLZ is required")
        private String plz;

        @NotBlank(message = "Ort is required")
        private String ort;

        public StellerDTO() {
        }

        public StellerDTO(String name, String strasse, String plz, String ort) {
            this.name = name;
            this.strasse = strasse;
            this.plz = plz;
            this.ort = ort;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStrasse() {
            return strasse;
        }

        public void setStrasse(String strasse) {
            this.strasse = strasse;
        }

        public String getPlz() {
            return plz;
        }

        public void setPlz(String plz) {
            this.plz = plz;
        }

        public String getOrt() {
            return ort;
        }

        public void setOrt(String ort) {
            this.ort = ort;
        }

        @JsonIgnore
        public String getFullAddress() {
            return strasse + "\n" + plz + " " + ort;
        }
    }
}
