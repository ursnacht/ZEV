package ch.nacht.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.stereotype.Component;

/**
 * Health-Indicator für den MQTT-Subscriber (FR-8): {@code /actuator/health/mqtt}.
 * NUR aktiv mit Spring-Profil {@code mqtt}. Meldet UP, solange der Inbound-Adapter läuft
 * (Broker-Reconnect erfolgt automatisch), sonst DOWN.
 */
@Component
@Profile("mqtt")
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttPahoMessageDrivenChannelAdapter mqttInbound;

    public MqttHealthIndicator(MqttPahoMessageDrivenChannelAdapter mqttInbound) {
        this.mqttInbound = mqttInbound;
    }

    @Override
    public Health health() {
        boolean running = mqttInbound.isRunning();
        Health.Builder builder = running ? Health.up() : Health.down();
        return builder.withDetail("running", running).build();
    }
}
