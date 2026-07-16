package ch.nacht.config;

import ch.nacht.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifiziert die Security-Invariante der Actuator-Endpoints
 * ({@link SecurityConfig#actuatorFilterChain}):
 * <ul>
 *   <li>health/info/prometheus bleiben oeffentlich (Docker-Healthchecks, Prometheus-Scrape)</li>
 *   <li>alle uebrigen Endpoints (env, loggers, ...) erfordern Basic Auth –
 *       so kann der SBA-Server Details abrufen, ohne dass sie oeffentlich sind (A05)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "actuator.user=test-actuator",
        "actuator.password=test-secret"
})
class ActuatorSecurityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_istOeffentlich() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheus_istOeffentlich() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void env_ohneAuth_401() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggers_ohneAuth_401() throws Exception {
        mockMvc.perform(get("/actuator/loggers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void env_mitBasicAuth_200() throws Exception {
        mockMvc.perform(get("/actuator/env").with(httpBasic("test-actuator", "test-secret")))
                .andExpect(status().isOk());
    }

    @Test
    void env_mitFalschemPasswort_401() throws Exception {
        mockMvc.perform(get("/actuator/env").with(httpBasic("test-actuator", "falsch")))
                .andExpect(status().isUnauthorized());
    }
}
