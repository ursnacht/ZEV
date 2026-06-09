package ch.nacht.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthControllerTest {

    private final AuthController controller = new AuthController();

    @Test
    void logout_withAuthenticatedUser_returnsNoContent() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "testuser")
                .build();

        ResponseEntity<Void> response = controller.logout(jwt);

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void logout_withoutJwt_returnsNoContent() {
        ResponseEntity<Void> response = controller.logout(null);

        assertEquals(204, response.getStatusCode().value());
    }
}
