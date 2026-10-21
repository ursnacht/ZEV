package ch.nacht.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.stereotype.Component;

/**
 * Health-Indicator für den MQTT-Subscriber (FR-8.2): {@code /actuator/health/mqtt}.
 * NUR aktiv mit Spring-Profil {@code mqtt}. Meldet UP, solange der Inbound-Adapter läuft
 * <b>und</b> mit dem Broker verbunden ist, sonst DOWN.
 *
 * <p>{@code isRunning()} allein genügt nicht: Der Adapter läuft auch weiter, während Paho nach
 * einem Verbindungsabbruch im Hintergrund neu verbindet.
 */
@Component
@Profile("mqtt")
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttPahoMessageDrivenChannelAdapter mqttInbound;
    private final BeobachteteMqttClientFactory clientFactory;

    public MqttHealthIndicator(MqttPahoMessageDrivenChannelAdapter mqttInbound,
                               BeobachteteMqttClientFactory clientFactory) {
        this.mqttInbound = mqttInbound;
        this.clientFactory = clientFactory;
    }

    @Override
    public Health health() {
        boolean running = mqttInbound.isRunning();
        boolean verbunden = clientFactory.istVerbunden();
        Health.Builder builder = running && verbunden ? Health.up() : Health.down();
        return builder.withDetail("running", running).withDetail("verbunden", verbunden).build();
    }
}
