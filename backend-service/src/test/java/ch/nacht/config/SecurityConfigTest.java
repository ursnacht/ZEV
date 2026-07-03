package ch.nacht.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests für den {@link JwtAuthenticationConverter} aus {@link SecurityConfig}.
 * <p>
 * Prüft, dass die Keycloak-Rollen aus {@code realm_access.roles} 1:1 (ohne {@code ROLE_}-Präfix)
 * auf Spring-Authorities gemappt werden – Basis für die permission-basierte Autorisierung
 * (z.B. {@code hasAuthority('einstellungen:write')}).
 */
class SecurityConfigTest {

    private final JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        claims.forEach(builder::claim);
        return builder.build();
    }

    /**
     * Von Spring Security automatisch vergebene Authentifizierungs-Faktor-Authorities
     * (z.B. {@code FACTOR_BEARER}) herausfiltern – geprüft werden nur die aus den Rollen
     * gemappten Permissions.
     */
    private Set<String> mappedRoleAuthorities(Jwt jwt) {
        return converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("FACTOR_"))
                .collect(Collectors.toSet());
    }

    @Test
    void convert_realmRoles_mappedOneToOneWithoutRolePrefix() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access",
                Map.of("roles", List.of("einstellungen:write", "featureflags:manage", "zev_user"))));

        assertEquals(Set.of("einstellungen:write", "featureflags:manage", "zev_user"),
                mappedRoleAuthorities(jwt));
    }

    @Test
    void convert_noRealmAccessClaim_returnsNoRoleAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("sub", "user-1"));

        assertTrue(mappedRoleAuthorities(jwt).isEmpty());
    }

    @Test
    void convert_realmAccessWithoutRoles_returnsNoRoleAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("other", "value")));

        assertTrue(mappedRoleAuthorities(jwt).isEmpty());
    }
}
