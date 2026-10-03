package com.company.autoremediate.agent;

import com.company.autoremediate.config.RemediationProperties;
import com.company.autoremediate.security.SecretRedactor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** LlmClient for the Anthropic Messages API (POST /v1/messages). Model and key come from configuration. */
@Component
public class AnthropicLlmClient implements LlmClient {
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 529);

    private final RemediationProperties.Ai cfg;
    private final ObjectMapper mapper;
    private final SecretRedactor redactor;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public AnthropicLlmClient(RemediationProperties props, ObjectMapper mapper, SecretRedactor redactor) {
        this.cfg = props.ai();
        this.mapper = mapper;
        this.redactor = redactor;
    }

    @Override
    public boolean isConfigured() {
        return "anthropic".equalsIgnoreCase(cfg.provider())
                && cfg.apiKey() != null && !cfg.apiKey().isBlank()
                && cfg.model() != null && !cfg.model().isBlank();
    }

    @Override
    public String modelName() {
        return cfg.model();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new LlmException("LLM is not configured (remediation.ai.provider/model/api-key)");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", cfg.model());
            body.put("max_tokens", cfg.maxTokens());
            if (cfg.temperature() != null) body.put("temperature", cfg.temperature());
            body.put("system", systemPrompt);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "user").put("content", userPrompt);
            String payload = mapper.writeValueAsString(body);

            String base = cfg.baseUrl().endsWith("/") ? cfg.baseUrl().substring(0, cfg.baseUrl().length() - 1) : cfg.baseUrl();
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/v1/messages"))
                    .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                    .header("x-api-key", cfg.apiKey())
                    .header("anthropic-version", cfg.anthropicVersion())
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (!RETRYABLE.contains(response.statusCode())) break;
                Thread.sleep(2000L * (attempt + 1));
            }
            if (response.statusCode() / 100 != 2) {
                throw new LlmException("LLM API returned " + response.statusCode() + ": "
                        + redactor.redact(truncate(response.body())));
            }
            JsonNode root = mapper.readTree(response.body());
            StringBuilder text = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText());
            }
            if (text.isEmpty()) throw new LlmException("LLM returned no text content");
            return text.toString();
        } catch (IOException e) {
            throw new LlmException("LLM request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM request interrupted", e);
        }
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
