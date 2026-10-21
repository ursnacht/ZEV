package ch.nacht.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für {@code /actuator/health/mqtt} (Specs/MQTT-Integration.md, FR-8.2): UP nur, wenn
 * der Inbound-Adapter läuft <b>und</b> der Client mit dem Broker verbunden ist.
 */
@ExtendWith(MockitoExtension.class)
public class MqttHealthIndicatorTest {

    @Mock
    private MqttPahoMessageDrivenChannelAdapter mqttInbound;

    @Mock
    private BeobachteteMqttClientFactory clientFactory;

    @InjectMocks
    private MqttHealthIndicator healthIndicator;

    @Test
    void health_AdapterLaeuftUndVerbunden_Up() {
        // Arrange
        when(mqttInbound.isRunning()).thenReturn(true);
        when(clientFactory.istVerbunden()).thenReturn(true);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("running"));
        assertEquals(true, health.getDetails().get("verbunden"));
    }

    /** Der eigentliche Fall von FR-8.2: Broker weg, Paho verbindet neu, der Adapter läuft weiter. */
    @Test
    void health_AdapterLaeuftAberBrokerGetrennt_Down() {
        // Arrange
        when(mqttInbound.isRunning()).thenReturn(true);
        when(clientFactory.istVerbunden()).thenReturn(false);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(true, health.getDetails().get("running"));
        assertEquals(false, health.getDetails().get("verbunden"));
    }

    @Test
    void health_AdapterGestoppt_Down() {
        // Arrange
        when(mqttInbound.isRunning()).thenReturn(false);
        when(clientFactory.istVerbunden()).thenReturn(false);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(false, health.getDetails().get("running"));
    }
}
