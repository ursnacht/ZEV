package ch.nacht.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for invoice generation.
 * Maps to 'rechnung' prefix in application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "rechnung")
public class RechnungConfig {

    private String zahlungsfrist;
    private String iban;
    private Steller steller = new Steller();
    private Adresse adresse = new Adresse();

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

    public Steller getSteller() {
        return steller;
    }

    public void setSteller(Steller steller) {
        this.steller = steller;
    }

    public Adresse getAdresse() {
        return adresse;
    }

    public void setAdresse(Adresse adresse) {
        this.adresse = adresse;
    }

    /**
     * Invoice issuer (Rechnungssteller) configuration
     */
    public static class Steller {
        private String name;
        private String strasse;
        private String plz;
        private String ort;

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

        public String getFullAddress() {
            return strasse + "\n" + plz + " " + ort;
        }
    }

    /**
     * Invoice recipient address configuration (building address)
     */
    public static class Adresse {
        private String strasse;
        private String plz;
        private String ort;

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

        public String getFullAddress() {
            return strasse + "\n" + plz + " " + ort;
        }
    }
}
