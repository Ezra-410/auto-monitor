package com.monitor.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KeycloakJwtConverterTest {

    private static Jwt.Builder base() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("sub-1");
    }

    private static Set<String> names(AbstractAuthenticationToken a) {
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void userTokenGetsScopesAndRolesAndIsProvisioned() {
        UserProvisioningService prov = mock(UserProvisioningService.class);
        when(prov.provision(any())).thenReturn(new AuthUser(7L, "teacher01", List.of("USER")));
        Jwt jwt = base().claim("preferred_username", "teacher01").claim("scope", "openid notes:read profile:read").build();

        AbstractAuthenticationToken auth = new KeycloakJwtConverter(prov).convert(jwt);

        assertInstanceOf(CoNoteAuthentication.class, auth);
        assertEquals(Set.of("SCOPE_openid", "SCOPE_notes:read", "SCOPE_profile:read", "ROLE_USER"), names(auth));
        verify(prov).provision(jwt);
    }

    @Test
    void serviceAccountTokenIsNeverProvisioned() {
        UserProvisioningService prov = mock(UserProvisioningService.class);
        Jwt jwt = base().claim("client_id", "conote-sync").claim("azp", "conote-sync").claim("scope", "notes:read").build();

        AbstractAuthenticationToken auth = new KeycloakJwtConverter(prov).convert(jwt);

        assertInstanceOf(ServiceAccountAuthentication.class, auth);
        assertEquals("conote-sync", auth.getName());
        assertEquals(Set.of("SCOPE_notes:read", "ROLE_SERVICE"), names(auth));
        assertFalse(names(auth).contains("ROLE_USER"));
        verifyNoInteractions(prov);
    }

    @Test
    void tokenWithoutScopeClaimHasNoScopeAuthorities() {
        assertTrue(KeycloakJwtConverter.scopeAuthorities(base().claim("x", 1).build()).isEmpty());
    }
}
