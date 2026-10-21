package ch.nacht.health;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für den Verbindungsstatus, den {@code /actuator/health/mqtt} meldet
 * (Specs/MQTT-Integration.md, FR-8.2).
 */
public class BeobachteteMqttClientFactoryTest {

    @Test
    void istVerbunden_NochKeinClient_False() {
        // Arrange
        BeobachteteMqttClientFactory factory = new BeobachteteMqttClientFactory();

        // Act + Assert - vor dem Start des Adapters gibt es keine Verbindung
        assertFalse(factory.istVerbunden());
    }

    @Test
    void istVerbunden_ClientVerbunden_True() throws Exception {
        // Arrange - der echte Client wuerde eine Verbindung brauchen; sein Zustand wird gestellt
        try (MockedConstruction<MqttAsyncClient> clients = mockConstruction(MqttAsyncClient.class,
                (client, context) -> when(client.isConnected()).thenReturn(true))) {
            BeobachteteMqttClientFactory factory = new BeobachteteMqttClientFactory();

            // Act
            IMqttAsyncClient client = factory.getAsyncClientInstance("tcp://localhost:1883", "test");

            // Assert - die Factory gibt den Client unveraendert weiter und merkt ihn sich
            assertSame(clients.constructed().get(0), client);
            assertTrue(factory.istVerbunden());
        }
    }

    @Test
    void istVerbunden_ClientGetrennt_False() throws Exception {
        // Arrange - z.B. waehrend Paho nach einem Verbindungsabbruch neu verbindet
        try (MockedConstruction<MqttAsyncClient> clients = mockConstruction(MqttAsyncClient.class,
                (client, context) -> when(client.isConnected()).thenReturn(false))) {
            BeobachteteMqttClientFactory factory = new BeobachteteMqttClientFactory();

            // Act
            factory.getAsyncClientInstance("tcp://localhost:1883", "test");

            // Assert
            assertFalse(factory.istVerbunden());
        }
    }
}
