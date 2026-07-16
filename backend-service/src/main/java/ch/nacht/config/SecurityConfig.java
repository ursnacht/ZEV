package ch.nacht.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Allowed browser origins for the REST API; configurable via app.cors.allowed-origins. */
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    /** Basic-Auth-Zugangsdaten fuer die geschuetzten Actuator-Endpoints (Abruf durch SBA). */
    @Value("${actuator.user:actuator}")
    private String actuatorUser;

    @Value("${actuator.password:actuator}")
    private String actuatorPassword;

    /**
     * Actuator-Endpoints: health/info/prometheus bleiben oeffentlich (Docker-Healthchecks,
     * Prometheus-Scrape), alle uebrigen (env, loggers, heapdump, ...) erfordern Basic Auth.
     * Der SBA-Server authentifiziert sich mit den Credentials aus der Client-Registrierung
     * (metadata user.name/user.password, siehe application.yml).
     */
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
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /** Nutzer fuer die Basic-Auth der Actuator-Endpoints (nicht fuer die REST-API). */
    @Bean
    public InMemoryUserDetailsManager actuatorUserDetailsManager() {
        return new InMemoryUserDetailsManager(User.withUsername(actuatorUser)
                .password("{noop}" + actuatorPassword)
                .roles("ACTUATOR")
                .build());
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**", "/ping").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null || realmAccess.isEmpty()) {
                return List.of();
            }
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles == null) {
                return List.of();
            }
            // Rollen 1:1 als Authorities uebernehmen (ohne ROLE_-Praefix). Fachrollen sind in
            // Keycloak Composite Roles, die feingranulare Permissions (z.B. "einstellungen:write")
            // buendeln; die Anwendung prueft ausschliesslich diese Permissions via hasAuthority(...).
            return roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });
        return converter;
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
