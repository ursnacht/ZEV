package ch.nacht.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication-related events.
 *
 * <p>The backend is a stateless OAuth2 resource server, so logout itself is handled by
 * Keycloak on the client side. This endpoint lets the frontend notify the backend right
 * before redirecting to the Keycloak logout, so the event can be logged server-side.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /**
     * Log that the authenticated user is logging out.
     *
     * @param jwt The current user's JWT (injected from the security context)
     * @return 204 No Content
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt != null ? jwt.getClaimAsString("preferred_username") : "unknown";
        log.info("User logged out: {}", username);
        return ResponseEntity.noContent().build();
    }
}
