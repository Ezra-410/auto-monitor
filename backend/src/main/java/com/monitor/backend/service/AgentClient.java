package com.monitor.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);

    private final RestClient http;
    private final String internalToken;
    private final String agentUrl;

    public AgentClient(@Value("${app.agent-url}") String agentUrl,
                       @Value("${app.internal-token:}") String internalToken) {
        this.agentUrl = agentUrl;
        // HTTP/1.1 forcé : uvicorn ne gère pas l'upgrade h2c tenté par java.net.http
        this.http = RestClient.builder()
                .baseUrl(agentUrl)
                .requestFactory(http11Factory())
                .build();
        this.internalToken = internalToken;
    }

    /** Transmet un message utilisateur à l'agent (fire-and-forget, l'agent répondra via /internal/v1). */
    public void forwardUserMessage(Long projectId, Long conversationId, Long userId, String content) {
        Map<String, Object> body = Map.of(
                "project_id", projectId,
                "conversation_id", conversationId,
                "user_id", userId,
                "content", content);
        CompletableFuture.runAsync(() -> {
            try {
                http.post()
                        .uri("/internal/agent/messages")
                        .header("X-Internal-Token", internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("Transmission à l'agent impossible: {}", e.getMessage());
            }
        });
    }

    static JdkClientHttpRequestFactory http11Factory() {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new JdkClientHttpRequestFactory(client);
    }

    public boolean ping() {
        try {
            return RestClient.builder()
                    .requestFactory(http11Factory())
                    .build()
                    .get()
                    .uri(agentUrl + "/healthz")
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
