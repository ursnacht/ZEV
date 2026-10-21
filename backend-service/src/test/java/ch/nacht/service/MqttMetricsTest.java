package ch.nacht.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für die MQTT-Metriken (Specs/MQTT-Integration.md, FR-8.1): Metrik-Namen, steigende
 * Counter und Zeitstempel-Gauges - gegen eine {@link SimpleMeterRegistry}, ohne Spring-Kontext.
 */
public class MqttMetricsTest {

    private SimpleMeterRegistry registry;
    private MqttMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MqttMetrics(registry);
    }

    private double counter(String name) {
        Counter counter = registry.find(name).counter();
        assertNotNull(counter, "Counter fehlt: " + name);
        return counter.count();
    }

    private double gauge(String name) {
        Gauge gauge = registry.find(name).gauge();
        assertNotNull(gauge, "Gauge fehlt: " + name);
        return gauge.value();
    }

    @Test
    void konstruktor_RegistriertAlleMetrikenGemaessFr81() {
        // Act + Assert - Namen exakt wie in der Spec (Prometheus-Export unter /actuator/prometheus)
        assertEquals(0.0, counter("zev_mqtt_messages_received_total"));
        assertEquals(0.0, counter("zev_mqtt_messages_processed_total"));
        assertEquals(0.0, counter("zev_mqtt_messages_failed_total"));
        assertEquals(0.0, counter("zev_aggregation_runs_total"));
        assertEquals(0.0, gauge("zev_mqtt_last_message_timestamp"));
        assertEquals(0.0, gauge("zev_aggregation_last_run_timestamp"));
    }

    @Test
    void recordReceived_ErhoehtCounterUndSetztZeitstempel() {
        // Arrange
        long vorher = Instant.now().getEpochSecond();

        // Act
        metrics.recordReceived();
        metrics.recordReceived();

        // Assert
        assertEquals(2.0, counter("zev_mqtt_messages_received_total"));
        double zeitstempel = gauge("zev_mqtt_last_message_timestamp");
        assertTrue(zeitstempel >= vorher && zeitstempel <= Instant.now().getEpochSecond(),
                "Unix-Timestamp in Sekunden erwartet, war: " + zeitstempel);
        // Die übrigen Zähler bleiben unberührt
        assertEquals(0.0, counter("zev_mqtt_messages_processed_total"));
        assertEquals(0.0, counter("zev_mqtt_messages_failed_total"));
    }

    @Test
    void recordProcessed_ErhoehtNurProcessedCounter() {
        // Act
        metrics.recordProcessed();

        // Assert
        assertEquals(1.0, counter("zev_mqtt_messages_processed_total"));
        assertEquals(0.0, counter("zev_mqtt_messages_received_total"));
        assertEquals(0.0, gauge("zev_mqtt_last_message_timestamp"));
    }

    @Test
    void recordFailed_ErhoehtNurFailedCounter() {
        // Act
        metrics.recordFailed();
        metrics.recordFailed();
        metrics.recordFailed();

        // Assert
        assertEquals(3.0, counter("zev_mqtt_messages_failed_total"));
        assertEquals(0.0, counter("zev_mqtt_messages_processed_total"));
    }

    @Test
    void recordAggregationRun_ErhoehtCounterUndSetztZeitstempel() {
        // Arrange
        long vorher = Instant.now().getEpochSecond();

        // Act
        metrics.recordAggregationRun();

        // Assert
        assertEquals(1.0, counter("zev_aggregation_runs_total"));
        double zeitstempel = gauge("zev_aggregation_last_run_timestamp");
        assertTrue(zeitstempel >= vorher && zeitstempel <= Instant.now().getEpochSecond(),
                "Unix-Timestamp in Sekunden erwartet, war: " + zeitstempel);
        assertEquals(0.0, gauge("zev_mqtt_last_message_timestamp"));
    }
}
