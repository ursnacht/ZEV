package ch.nacht.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sichert ausschliesslich die eigenen Actuator-Endpoints mit Basic Auth:
 * health/info/prometheus bleiben oeffentlich (Prometheus-Scrape), alle uebrigen
 * (env, loggers, heapdump, ...) erfordern Basic Auth.
 * <p>
 * Alle anderen Pfade (SBA-UI, /instances-Registrierung der Clients) haben bewusst
 * keine SecurityFilterChain und laufen wie bisher ohne Authentifizierung.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Basic-Auth-Zugangsdaten fuer die geschuetzten Actuator-Endpoints (Abruf durch SBA). */
    @Value("${actuator.user:actuator}")
    private String actuatorUser;

    @Value("${actuator.password:actuator}")
    private String actuatorPassword;

    @Bean
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                // SBA sendet POSTs (z.B. Loglevel via /actuator/loggers) ohne CSRF-Token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /** Nutzer fuer die Basic-Auth der Actuator-Endpoints. */
    @Bean
    public InMemoryUserDetailsManager actuatorUserDetailsManager() {
        return new InMemoryUserDetailsManager(User.withUsername(actuatorUser)
                .password("{noop}" + actuatorPassword)
                .roles("ACTUATOR")
                .build());
    }
}
