package ch.nacht.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP-Client fuer Fremd-APIs (heute: die Einspeisepreise der BKW, Specs/Preiszeitreihe.md).
 *
 * <p>Bewusst ein eigener Bean und kein {@code RestClient.create()} im Service: Zeitgrenzen sind
 * eine Betriebs-, keine Fachentscheidung. Ohne sie wartet ein Aufruf an einer stummen Verbindung
 * unbegrenzt — der geplante Abruf haengt dann bis zum naechsten Lauf, ohne dass jemand etwas merkt.
 *
 * <p>Gebaut wird auf dem von Spring Boot bereitgestellten {@code RestClient.Builder}: Damit gelten
 * die Jackson-Konverter der Anwendung (JavaTime, unbekannte Felder werden ignoriert), und die
 * Fremd-DTOs brauchen keinen eigenen {@code ObjectMapper}.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient externerRestClient(
            RestClient.Builder builder,
            @Value("${preiszeitreihe.timeout.verbindung:5s}") Duration verbindungsTimeout,
            @Value("${preiszeitreihe.timeout.lesen:10s}") Duration leseTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(verbindungsTimeout);
        factory.setReadTimeout(leseTimeout);
        return builder.requestFactory(factory).build();
    }
}
