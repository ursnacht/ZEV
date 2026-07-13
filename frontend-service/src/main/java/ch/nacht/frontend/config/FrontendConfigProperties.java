package ch.nacht.frontend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Laufzeit-Konfiguration des Angular-Frontends (Prefix {@code frontend.config}).
 * Die Werte stammen aus {@code application.yml} bzw. den entsprechenden Environment-Variablen
 * ({@code FRONTEND_API_BASE_URL}, {@code FRONTEND_KEYCLOAK_URL}, ...) und werden vom
 * {@link FrontendConfigController} als {@code /assets/config.json} ausgeliefert.
 */
@ConfigurationProperties(prefix = "frontend.config")
public class FrontendConfigProperties {

    private String apiBaseUrl;
    private final Keycloak keycloak = new Keycloak();

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public static class Keycloak {
        private String url;
        private String realm;
        private String clientId;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }
}
