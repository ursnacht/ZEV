package ch.nacht.frontend.config;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert die Laufzeit-Konfiguration des Angular-Frontends als {@code /assets/config.json}.
 *
 * <p>Diese Zuordnung hat Vorrang vor der (ins JAR gepackten) statischen Datei gleichen Namens,
 * sodass die Werte zur Laufzeit aus {@link FrontendConfigProperties} (application.yml / Env)
 * stammen. Dadurch lässt sich dasselbe Image ohne Rebuild in jeder Umgebung betreiben.
 */
@RestController
public class FrontendConfigController {

    private final FrontendConfigProperties properties;

    public FrontendConfigController(FrontendConfigProperties properties) {
        this.properties = properties;
    }

    @GetMapping(value = "/assets/config.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public FrontendConfigDto config() {
        FrontendConfigProperties.Keycloak kc = properties.getKeycloak();
        return new FrontendConfigDto(
                properties.getApiBaseUrl(),
                new FrontendConfigDto.KeycloakDto(kc.getUrl(), kc.getRealm(), kc.getClientId()));
    }

    /** JSON-Struktur passend zum Angular-{@code RuntimeConfig}-Interface. */
    public record FrontendConfigDto(String apiBaseUrl, KeycloakDto keycloak) {
        public record KeycloakDto(String url, String realm, String clientId) {
        }
    }
}
