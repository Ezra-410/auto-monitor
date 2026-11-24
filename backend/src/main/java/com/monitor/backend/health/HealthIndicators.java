package com.monitor.backend.health;

import com.monitor.backend.service.AgentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HealthIndicators {

    private HealthIndicators() {
    }

    /**
     * Keycloak's public keys are reachable. Reported in /actuator/health for visibility but
     * deliberately NOT part of the readiness group (D-26): a Keycloak outage must not stop the
     * application stack from starting. Requests fail closed (401) instead.
     */
    @Component("oidc")
    public static class OidcHealthIndicator implements HealthIndicator {
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        private final URI jwksUri;

        public OidcHealthIndicator(@Value("${app.oidc.issuer:}") String issuer) {
            this.jwksUri = (issuer == null || issuer.isBlank())
                    ? null : URI.create(issuer + "/protocol/openid-connect/certs");
        }

        @Override
        public Health health() {
            if (jwksUri == null) {
                return Health.down().withDetail("reason", "OIDC_ISSUER not set").build();
            }
            try {
                HttpResponse<Void> res = http.send(
                        HttpRequest.newBuilder(jwksUri).timeout(Duration.ofSeconds(3)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                return res.statusCode() == 200
                        ? Health.up().build()
                        : Health.down().withDetail("status", res.statusCode()).build();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Health.down(e).build();
            } catch (Exception e) {
                return Health.down(e).build();
            }
        }
    }

    @Component("agent")
    public static class AgentHealthIndicator implements HealthIndicator {
        private final AgentClient agentClient;

        public AgentHealthIndicator(AgentClient agentClient) {
            this.agentClient = agentClient;
        }

        @Override
        public Health health() {
            return agentClient.ping() ? Health.up().build() : Health.down().build();
        }
    }
}
