package com.monitor.backend.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * The authenticated request: the principal is the Co-Note user (AuthUser, used by every controller
 * through ApiSupport.currentUser()) and the credentials are the validated Keycloak access token,
 * so claims such as groups, studentId and department stay available.
 */
public class CoNoteAuthentication extends AbstractAuthenticationToken {

    private final AuthUser principal;
    private final Jwt token;

    public CoNoteAuthentication(AuthUser principal, Jwt token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public Jwt getToken() {
        return token;
    }

    @Override
    public String getName() {
        return principal.username();
    }
}
