package ch.nacht.config;

import ch.nacht.health.BeobachteteMqttClientFactory;
import ch.nacht.service.MqttIngestService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * MQTT-Subscriber (Spring Integration + Eclipse Paho). NUR aktiv mit Spring-Profil {@code mqtt}
 * (siehe MQTT-Integration.md FR-1). Ohne Profil wird kein Broker-Client gestartet.
 */
@Configuration
@Profile("mqtt")
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.broker.username:}")
    private String username;

    @Value("${mqtt.broker.password:}")
    private String password;

    @Value("${mqtt.client-id:zev-backend-subscriber}")
    private String clientId;

    @Value("${mqtt.topic:zev/+/+/messwert}")
    private String topic;

    @Value("${mqtt.qos:1}")
    private int qos;

    /**
     * {@code false} = dauerhafte Sitzung: Der Broker behält das Abo über einen Neustart des Backends
     * hinweg und sammelt die QoS-1-Nachrichten, bis es wieder verbunden ist. Mit {@code true} verwarf
     * er sie, solange das Backend fehlte - die Energie blieb dank absoluter Zählerstände zwar
     * erhalten, fiel aber gebündelt ins nächste Intervall (MQTT-Integration.md, FR-1.5).
     *
     * <p>Setzt eine feste Client-ID voraus, die nur <b>eine</b> Instanz je Broker verwendet.
     */
    @Value("${mqtt.clean-session:false}")
    private boolean cleanSession;

    /** Merkt sich den Client fuer den Verbindungsstatus in {@code /actuator/health/mqtt} (FR-8.2). */
    @Bean
    public BeobachteteMqttClientFactory mqttClientFactory() {
        BeobachteteMqttClientFactory factory = new BeobachteteMqttClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        options.setAutomaticReconnect(true);
        options.setCleanSession(cleanSession);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound(MqttPahoClientFactory mqttClientFactory,
                                                           MessageChannel mqttInputChannel) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory, topic);
        // Nach einem Neustart mit gepufferten Nachrichten meldet das Abo "Timed out (32000)" - erwartet und
        // harmlos, eine laengere Wartezeit hilft nicht (MQTT-Integration.md, FR-1.6).
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler mqttMessageHandler(MqttIngestService ingestService) {
        return message -> {
            Object topicHeader = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
            String receivedTopic = topicHeader != null ? topicHeader.toString() : null;
            String payload = message.getPayload().toString();
            ingestService.handle(receivedTopic, payload);
        };
    }
}
