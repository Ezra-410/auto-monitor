package com.monitor.backend.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a VALIDATED Keycloak access token into the Co-Note authentication used by the controllers.
 * Called by Spring Security only after the JwtDecoder has accepted the token (signature, expiry,
 * issuer, audience).
 *
 * Authorities produced (Milestone 4):
 *   SCOPE_<scope>  one per OAuth scope in the "scope" claim, e.g. SCOPE_notes:read (D-28)
 *   ROLE_SERVICE   for a machine (Client Credentials token): no Co-Note profile is created (D-31)
 *   ROLE_USER / ROLE_ADMIN  for a person, from the LDAP groups (D-24)
 */
@Component
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserProvisioningService provisioning;

    public KeycloakJwtConverter(UserProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>(scopeAuthorities(jwt));
        if (isServiceAccount(jwt)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SERVICE"));
            return new ServiceAccountAuthentication(jwt, authorities);
        }
        AuthUser user = provisioning.provision(jwt);
        user.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        return new CoNoteAuthentication(user, jwt, authorities);
    }

    /**
     * Keycloak puts a "client_id" claim only in Client Credentials (service account) tokens.
     * The claim is inside the signed token, so a caller cannot add or remove it.
     */
    static boolean isServiceAccount(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");
        return clientId != null && !clientId.isBlank();
    }

    /** "openid profile notes:read" -> [SCOPE_openid, SCOPE_profile, SCOPE_notes:read] */
    static List<GrantedAuthority> scopeAuthorities(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        List<GrantedAuthority> result = new ArrayList<>();
        for (String s : scope.trim().split("\\s+")) {
            result.add(new SimpleGrantedAuthority("SCOPE_" + s));
        }
        return result;
    }
}
