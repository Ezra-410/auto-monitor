package com.monitor.backend.health;

import com.monitor.backend.security.JwtService;
import com.monitor.backend.service.AgentClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

public final class HealthIndicators {

    private HealthIndicators() {
    }

    @Component("jwks")
    public static class JwksHealthIndicator implements HealthIndicator {
        private final JwtService jwtService;

        public JwksHealthIndicator(JwtService jwtService) {
            this.jwtService = jwtService;
        }

        @Override
        public Health health() {
            try {
                jwtService.jwks();
                return Health.up().build();
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
