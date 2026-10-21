package ch.nacht.health;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;

/**
 * Merkt sich den Paho-Client, den der Inbound-Adapter beim Start erzeugt, damit der
 * Health-Indicator die <b>Broker-Verbindung</b> melden kann (MQTT-Integration.md, FR-8.2).
 *
 * <p>Der Adapter gibt seinen Client nicht heraus, und {@code isRunning()} bleibt auch dann wahr,
 * wenn die Verbindung abgerissen ist und Paho im Hintergrund neu verbindet - ein abgerissener
 * Broker erschiene sonst als UP.
 */
public class BeobachteteMqttClientFactory extends DefaultMqttPahoClientFactory {

    private volatile IMqttAsyncClient asyncClient;

    @Override
    public IMqttAsyncClient getAsyncClientInstance(String url, String clientId) throws MqttException {
        IMqttAsyncClient client = super.getAsyncClientInstance(url, clientId);
        this.asyncClient = client;
        return client;
    }

    /** {@code true}, solange der zuletzt erzeugte Client mit dem Broker verbunden ist. */
    public boolean istVerbunden() {
        IMqttAsyncClient client = asyncClient;
        return client != null && client.isConnected();
    }
}
