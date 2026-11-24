package com.monitor.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("conote-api");

    private static Jwt tokenFor(List<String> audience) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("kc-user-1")
                .audience(audience)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void acceptsTokenAddressedToTheApi() {
        assertFalse(validator.validate(tokenFor(List.of("account", "conote-api"))).hasErrors());
    }

    @Test
    void rejectsTokenIssuedForAnotherAudience() {
        assertTrue(validator.validate(tokenFor(List.of("account"))).hasErrors());
    }
}
