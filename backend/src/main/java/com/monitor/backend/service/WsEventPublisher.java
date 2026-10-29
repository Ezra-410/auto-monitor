package com.monitor.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class WsEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WsEventPublisher.class);

    private final RestClient http;
    private final String internalToken;

    public WsEventPublisher(@Value("${app.ws-url}") String wsUrl,
                            @Value("${app.internal-token:}") String internalToken) {
        this.http = RestClient.builder()
                .baseUrl(wsUrl)
                .requestFactory(AgentClient.http11Factory())
                .build();
        this.internalToken = internalToken;
    }

    /** Publie un événement vers le ws-gateway (best-effort, non bloquant). */
    public void publish(String type, Long projectId, Long userId, Object payload) {
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("type", type);
        if (projectId != null) event.put("project_id", projectId);
        if (userId != null) event.put("user_id", userId);
        if (payload != null) event.put("payload", payload);
        CompletableFuture.runAsync(() -> {
            try {
                http.post()
                        .uri("/internal/events")
                        .header("X-Internal-Token", internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(event)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("Publication WS '{}' impossible: {}", type, e.getMessage());
            }
        });
    }
}
