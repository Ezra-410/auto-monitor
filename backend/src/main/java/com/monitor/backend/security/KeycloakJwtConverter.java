package com.monitor.backend.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns a VALIDATED Keycloak access token into the Co-Note authentication used by the controllers.
 * Called by Spring Security only after the JwtDecoder has accepted the token.
 */
@Component
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserProvisioningService provisioning;

    public KeycloakJwtConverter(UserProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthUser user = provisioning.provision(jwt);
        List<SimpleGrantedAuthority> authorities = user.roles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new CoNoteAuthentication(user, jwt, authorities);
    }
}
