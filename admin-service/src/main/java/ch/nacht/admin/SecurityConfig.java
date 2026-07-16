package ch.nacht.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Zwei getrennte Basic-Auth-Bereiche:
 * <ul>
 *   <li>{@code /actuator/**} (Chain 1): health/info/prometheus oeffentlich
 *       (Prometheus-Scrape), alle uebrigen Endpoints nur mit Rolle {@code ACTUATOR} –
 *       so ruft der SBA-Server die eigenen Actuator-Details ab.</li>
 *   <li>SBA-UI und -API (Chain 2, Catch-all): alles nur mit Rolle {@code SBA_ADMIN}.
 *       Der Browser fragt die Credentials beim Oeffnen von :8081 ab; die Clients
 *       (backend/admin/frontend) registrieren sich mit denselben Credentials via
 *       {@code spring.boot.admin.client.username/password} an {@code POST /instances}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Basic-Auth-Zugangsdaten fuer die geschuetzten Actuator-Endpoints (Abruf durch SBA). */
    @Value("${actuator.user:actuator}")
    private String actuatorUser;

    @Value("${actuator.password:actuator}")
    private String actuatorPassword;

    /** Basic-Auth-Zugangsdaten fuer die SBA-UI/-API (Browser + Client-Registrierung). */
    @Value("${sba.user:sba}")
    private String sbaUser;

    @Value("${sba.password:sba}")
    private String sbaPassword;

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                // SBA sendet POSTs (z.B. Loglevel via /actuator/loggers) ohne CSRF-Token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().hasRole("ACTUATOR"))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain sbaFilterChain(HttpSecurity http) throws Exception {
        http
                // Client-Registrierung (POST/DELETE /instances) und SBA-UI-XHRs kommen ohne
                // CSRF-Token; Schutz ist Basic Auth (stateless, kein Session-Cookie).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasRole("SBA_ADMIN"))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /** Nutzer fuer Actuator-Abruf (ACTUATOR) und SBA-UI/Registrierung (SBA_ADMIN). */
    @Bean
    public InMemoryUserDetailsManager userDetailsManager() {
        return new InMemoryUserDetailsManager(
                User.withUsername(actuatorUser)
                        .password("{noop}" + actuatorPassword)
                        .roles("ACTUATOR")
                        .build(),
                User.withUsername(sbaUser)
                        .password("{noop}" + sbaPassword)
                        .roles("SBA_ADMIN")
                        .build());
    }
}
