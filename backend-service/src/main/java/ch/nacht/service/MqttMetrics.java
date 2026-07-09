package ch.nacht.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Prometheus-Metriken für die MQTT-Integration (FR-8). Registriert globale Zähler/Gauges
 * direkt an der {@link MeterRegistry} – bewusst OHNE {@code MetricsService}, da dieser den
 * request-scoped Organisationskontext nutzt, der im MQTT-Ingest/Job nicht verfügbar ist.
 */
@Component
@Profile("mqtt")
public class MqttMetrics {

    private final Counter messagesReceived;
    private final Counter messagesProcessed;
    private final Counter messagesFailed;
    private final Counter aggregationRuns;

    private final AtomicReference<Instant> lastMessage = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastAggregationRun = new AtomicReference<>(null);

    public MqttMetrics(MeterRegistry registry) {
        this.messagesReceived = Counter.builder("zev_mqtt_messages_received_total")
                .description("Anzahl empfangener MQTT-Nachrichten").register(registry);
        this.messagesProcessed = Counter.builder("zev_mqtt_messages_processed_total")
                .description("Anzahl erfolgreich verarbeiteter MQTT-Nachrichten").register(registry);
        this.messagesFailed = Counter.builder("zev_mqtt_messages_failed_total")
                .description("Anzahl verworfener/fehlgeschlagener MQTT-Nachrichten").register(registry);
        this.aggregationRuns = Counter.builder("zev_aggregation_runs_total")
                .description("Anzahl der Aggregations-Job-Läufe").register(registry);

        Gauge.builder("zev_mqtt_last_message_timestamp", lastMessage,
                        ref -> ref.get() != null ? ref.get().getEpochSecond() : 0)
                .description("Unix-Timestamp der letzten empfangenen MQTT-Nachricht").register(registry);
        Gauge.builder("zev_aggregation_last_run_timestamp", lastAggregationRun,
                        ref -> ref.get() != null ? ref.get().getEpochSecond() : 0)
                .description("Unix-Timestamp des letzten Aggregations-Laufs").register(registry);
    }

    public void recordReceived() {
        messagesReceived.increment();
        lastMessage.set(Instant.now());
    }

    public void recordProcessed() {
        messagesProcessed.increment();
    }

    public void recordFailed() {
        messagesFailed.increment();
    }

    public void recordAggregationRun() {
        aggregationRuns.increment();
        lastAggregationRun.set(Instant.now());
    }
}
