package com.monitor.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * Milestone 4 (D-31): a request made by a MACHINE (Client Credentials token), not a person.
 * Its principal is the validated token, not an AuthUser, so it can never pass as a Co-Note user:
 * ApiSupport.currentUser() refuses it, and SecurityConfig keeps it away from user endpoints.
 * getName() returns the client id (e.g. "conote-sync").
 */
public class ServiceAccountAuthentication extends JwtAuthenticationToken {

    public ServiceAccountAuthentication(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(jwt, authorities, jwt.getClaimAsString("client_id"));
    }
}
