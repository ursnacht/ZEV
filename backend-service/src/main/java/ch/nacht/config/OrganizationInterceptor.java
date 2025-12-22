package ch.nacht.config;

import ch.nacht.exception.NoOrganizationException;
import ch.nacht.service.OrganizationContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interceptor für die Extraktion der Organisations-ID aus dem JWT-Token.
 * Aktiviert bei authentifizierten Requests den Organisationskontext.
 */
@Component
public class OrganizationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OrganizationInterceptor.class);

    private final OrganizationContextService organizationContextService;

    public OrganizationInterceptor(OrganizationContextService organizationContextService) {
        this.organizationContextService = organizationContextService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            extractOrganizations(jwtAuth);

            // Prüfen ob eine Organisation gesetzt wurde
            if (!organizationContextService.hasOrganization()) {
                log.error("Benutzer {} hat keine Organisation - Zugriff verweigert",
                        jwtAuth.getToken().getSubject());
                throw new NoOrganizationException(
                        "Keine Organisation zugewiesen. Bitte kontaktieren Sie den Administrator.");
            }
        }

        return true;
    }

    /**
     * Extrahiert die Organisationen aus dem JWT-Token.
     * JWT-Struktur (Keycloak Organizations):
     * {
     *   "organizations": {
     *     "org-alias-1": { "id": "uuid-1" },
     *     "org-alias-2": { "id": "uuid-2" }
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private void extractOrganizations(JwtAuthenticationToken jwtAuth) {
        try {
            Map<String, Object> organizations = jwtAuth.getToken().getClaim("organizations");

            if (organizations == null || organizations.isEmpty()) {
                log.warn("Keine Organisationen im JWT-Token für Benutzer: {}",
                        jwtAuth.getToken().getSubject());
                return;
            }

            List<UUID> orgIds = new ArrayList<>();
            String firstOrgAlias = null;
            UUID firstOrgId = null;

            // JWT-Struktur: {"alias": {"id": "uuid"}}
            for (Map.Entry<String, Object> entry : organizations.entrySet()) {
                String alias = entry.getKey();

                if (entry.getValue() instanceof Map) {
                    Map<String, Object> orgDetails = (Map<String, Object>) entry.getValue();
                    String idString = (String) orgDetails.get("id");

                    if (idString != null) {
                        try {
                            UUID orgId = UUID.fromString(idString);
                            orgIds.add(orgId);

                            // Erste Organisation als aktuelle setzen
                            if (firstOrgId == null) {
                                firstOrgId = orgId;
                                firstOrgAlias = alias;
                            }

                            log.debug("Organisation gefunden: {} (Alias: {})", orgId, alias);
                        } catch (IllegalArgumentException e) {
                            log.warn("Ungültige Organisations-UUID im Token: {}", idString);
                        }
                    }
                }
            }

            if (!orgIds.isEmpty()) {
                organizationContextService.setAvailableOrgIds(orgIds);
                organizationContextService.setCurrentOrgId(firstOrgId);
                organizationContextService.setCurrentOrgName(firstOrgAlias);

                log.info("Organisationskontext gesetzt - Benutzer: {}, aktuelle Org: {} ({}), verfügbare Orgs: {}",
                        jwtAuth.getToken().getSubject(),
                        firstOrgId,
                        firstOrgAlias,
                        orgIds.size());
            }

        } catch (Exception e) {
            log.error("Fehler beim Extrahieren der Organisationen aus JWT: {}", e.getMessage(), e);
        }
    }
}
