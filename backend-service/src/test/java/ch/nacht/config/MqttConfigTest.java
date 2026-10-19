package ch.nacht.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests für die Sitzungsart in {@link MqttConfig} (Specs/MQTT-Integration.md, FR-1.5).
 *
 * <p>Mit {@code cleanSession = true} verwarf der Broker alle Nachrichten, die während eines Updates
 * des Backends eintrafen. Geprüft wird, dass die Einstellung bei der Verbindung ankommt und dass der
 * Standard aus {@code application-mqtt.yml} die dauerhafte Sitzung ist - ohne Broker.
 */
class MqttConfigTest {

    private DefaultMqttPahoClientFactory factoryMit(boolean cleanSession) {
        MqttConfig config = new MqttConfig();
        ReflectionTestUtils.setField(config, "brokerUrl", "tcp://localhost:1883");
        ReflectionTestUtils.setField(config, "username", "");
        ReflectionTestUtils.setField(config, "password", "");
        ReflectionTestUtils.setField(config, "cleanSession", cleanSession);
        return (DefaultMqttPahoClientFactory) config.mqttClientFactory();
    }

    @Test
    void mqttClientFactory_CleanSessionFalse_DauerhafteSitzung() {
        assertFalse(factoryMit(false).getConnectionOptions().isCleanSession());
    }

    @Test
    void mqttClientFactory_CleanSessionTrue_SitzungOhnePuffer() {
        assertTrue(factoryMit(true).getConnectionOptions().isCleanSession());
    }

    /**
     * Specs/MQTT-Integration.md, FR-1.6: Nach einem Neustart stellt der Broker zuerst die
     * gepufferten Nachrichten zu; 5 s reichten fuer das SUBACK nicht.
     */
    @Test
    void mqttInbound_WartetDreissigSekundenAufDasAbo() {
        MqttConfig config = new MqttConfig();
        ReflectionTestUtils.setField(config, "clientId", "test-client");
        ReflectionTestUtils.setField(config, "topic", "zev/+/+/messwert");
        ReflectionTestUtils.setField(config, "qos", 1);

        MqttPahoMessageDrivenChannelAdapter adapter =
                config.mqttInbound(factoryMit(false), new DirectChannel());

        assertEquals(30_000L, ((Number) ReflectionTestUtils.getField(adapter, "completionTimeout")).longValue());
    }

    /** Auf Hene ist {@code MQTT_CLEAN_SESSION} nicht gesetzt - dort gilt der Standard der Datei. */
    @Test
    void applicationMqttYml_OhneUmgebungsvariable_DauerhafteSitzung() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader()
                .load("application-mqtt", new ClassPathResource("application-mqtt.yml"))
                .forEach(env.getPropertySources()::addLast);

        assertEquals("false", env.getProperty("mqtt.clean-session"));
    }
}
