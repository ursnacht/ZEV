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
    private Tarif tarif = new Tarif();

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

    public Tarif getTarif() {
        return tarif;
    }

    public void setTarif(Tarif tarif) {
        this.tarif = tarif;
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

    /**
     * Tariff configuration container
     */
    public static class Tarif {
        private TarifDetails zev = new TarifDetails();
        private TarifDetails ewb = new TarifDetails();

        public TarifDetails getZev() {
            return zev;
        }

        public void setZev(TarifDetails zev) {
            this.zev = zev;
        }

        public TarifDetails getEwb() {
            return ewb;
        }

        public void setEwb(TarifDetails ewb) {
            this.ewb = ewb;
        }
    }

    /**
     * Individual tariff details (name and price)
     */
    public static class TarifDetails {
        private String bezeichnung;
        private double preis;

        public String getBezeichnung() {
            return bezeichnung;
        }

        public void setBezeichnung(String bezeichnung) {
            this.bezeichnung = bezeichnung;
        }

        public double getPreis() {
            return preis;
        }

        public void setPreis(double preis) {
            this.preis = preis;
        }
    }
}
