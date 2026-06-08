package ch.nacht.config;

import ch.nacht.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifiziert, dass der Spring-Context vollstaendig startet, ohne dass der
 * Keycloak-Issuer (issuer-uri = localhost:9000) erreichbar sein muss.
 * Reproduziert das frühere Startup-Problem (eager OIDC-Discovery beim
 * JwtDecoder-Bean) und stellt sicher, dass der Decoder lazy bleibt.
 */
@SpringBootTest
class SecurityConfigContextLoadIT extends AbstractIntegrationTest {

    @Test
    void contextLoadsWithoutKeycloak() {
        // Laedt der Context (inkl. SecurityConfig + JwtDecoder), ohne dass
        // localhost:9000 erreichbar ist, ist der Decoder lazy -> Test gruen.
    }
}
