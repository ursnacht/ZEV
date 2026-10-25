package ch.nacht.config;

import ch.nacht.util.Ortszeit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Die Uhr als Bean — für Code, der mit <b>absoluten</b> Zeitpunkten rechnet und das in Tests
 * fixieren muss (z.B. das Alter des letzten Empfangs, Specs/Messpunkt-Empfang.md, FR-2.5).
 *
 * <p>Für Ortszeit-Werte (Messwerte, Intervalle) bleibt {@link Ortszeit} der Weg.
 */
@Configuration
public class ZeitConfig {

    @Bean
    public Clock clock() {
        return Clock.system(Ortszeit.ZONE);
    }
}
