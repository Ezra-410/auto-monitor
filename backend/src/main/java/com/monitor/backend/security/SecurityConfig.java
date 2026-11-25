package com.monitor.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * The backend is an OAuth 2.0 resource server (D-23): every /api/** request must carry a Keycloak
 * access token, which is validated HERE, by this service, on every request.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * SECURITY CHECKS (D-23), applied to every access token:
     *  1. signature: RS256 only, verified with Keycloak's public keys (JWKS, fetched lazily and cached);
     *  2. expiry / not-before: JwtTimestampValidator, 30 s clock skew;
     *  3. issuer: must be exactly OIDC_ISSUER (our conote realm);
     *  4. audience: must contain "conote-api" (AudienceValidator).
     * The JWKS is fetched on first use, not at startup, so the backend can start while Keycloak is
     * down (D-26); requests then fail closed with 401.
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.oidc.issuer:}") String issuer,
                                 @Value("${app.oidc.audience:conote-api}") String audience) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("OIDC_ISSUER is not set");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(issuer + "/protocol/openid-connect/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(issuer),
                new AudienceValidator(audience));
        decoder.setJwtValidator(validators);
        return decoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           InternalTokenFilter internalTokenFilter,
                                           KeycloakJwtConverter keycloakJwtConverter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())   // no cookies here: bearer tokens only
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/internal/**").hasAuthority("INTERNAL")
                        // Milestone 4 (D-28, D-31): scope-based authorization, checked on EVERY request.
                        // Machine endpoint: service accounts only, with notes:read.
                        .requestMatchers("/api/v1/sync/**").access(caller("SERVICE", "notes:read"))
                        // User endpoints: a person (ROLE_USER) whose token carries the right scope.
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").access(caller("USER", "profile:read"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/notes", "/api/v1/notes/**").access(caller("USER", "notes:read"))
                        .requestMatchers("/api/v1/notes", "/api/v1/notes/**").access(caller("USER", "notes:write"))
                        // Everything else under /api: any person; never a service account.
                        .requestMatchers("/api/**").hasRole("USER")
                        .anyRequest().authenticated())
                // service-to-service calls from the agent keep their shared internal token
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter))
                        .authenticationEntryPoint(SecurityConfig::unauthorized)
                        .accessDeniedHandler(SecurityConfig::forbidden))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(SecurityConfig::unauthorized)
                        .accessDeniedHandler(SecurityConfig::forbidden));
        return http.build();
    }

    /** Both conditions must hold: the caller's kind (ROLE_USER / ROLE_SERVICE) AND the OAuth scope. */
    private static AuthorizationManager<RequestAuthorizationContext> caller(String role, String scope) {
        return AuthorizationManagers.allOf(
                AuthorityAuthorizationManager.<RequestAuthorizationContext>hasRole(role),
                AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority("SCOPE_" + scope));
    }

    /** 401: no token, or a token that failed validation (who are you?). */
    private static void unauthorized(HttpServletRequest request, HttpServletResponse response,
                                     AuthenticationException e) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    }

    /** 403: valid token, but not allowed to do this (what may you do?). */
    private static void forbidden(HttpServletRequest request, HttpServletResponse response,
                                  AccessDeniedException e) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"forbidden\"}");
    }
}
