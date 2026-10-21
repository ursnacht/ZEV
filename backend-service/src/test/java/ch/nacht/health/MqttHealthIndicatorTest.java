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
 * Unit-Tests für {@code /actuator/health/mqtt} (Specs/MQTT-Integration.md, FR-8.2): UP, solange
 * der Inbound-Adapter läuft, sonst DOWN.
 */
@ExtendWith(MockitoExtension.class)
public class MqttHealthIndicatorTest {

    @Mock
    private MqttPahoMessageDrivenChannelAdapter mqttInbound;

    @InjectMocks
    private MqttHealthIndicator healthIndicator;

    @Test
    void health_AdapterLaeuft_Up() {
        // Arrange
        when(mqttInbound.isRunning()).thenReturn(true);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("running"));
    }

    @Test
    void health_AdapterGestoppt_Down() {
        // Arrange
        when(mqttInbound.isRunning()).thenReturn(false);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(false, health.getDetails().get("running"));
    }
}
