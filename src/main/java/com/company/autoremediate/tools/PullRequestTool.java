package com.company.autoremediate.tools;

import com.company.autoremediate.config.RemediationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** GitHub pull request creation. V1 never merges. */
@Component
public class PullRequestTool {
    private static final Pattern GITHUB = Pattern.compile("github\\.com[:/]+([^/]+)/([^/]+?)(?:\\.git)?/?$");

    private final RemediationProperties.Git cfg;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public PullRequestTool(RemediationProperties props, ObjectMapper mapper) {
        this.cfg = props.git();
        this.mapper = mapper;
    }

    /** Returns owner/repo for GitHub remotes, empty for anything else. */
    public static Optional<String> githubSlug(String remoteUrl) {
        if (remoteUrl == null) return Optional.empty();
        Matcher m = GITHUB.matcher(remoteUrl.trim());
        return m.find() ? Optional.of(m.group(1) + "/" + m.group(2)) : Optional.empty();
    }

    public String createPullRequest(String remoteUrl, String head, String base, String title, String body) {
        String slug = githubSlug(remoteUrl)
                .orElseThrow(() -> new UnsupportedOperationException("Only GitHub remotes are supported in V1"));
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("title", title);
            payload.put("head", head);
            payload.put("base", base);
            payload.put("body", body);
            payload.put("draft", true);
            String api = cfg.apiUrl().endsWith("/") ? cfg.apiUrl().substring(0, cfg.apiUrl().length() - 1) : cfg.apiUrl();
            HttpRequest req = HttpRequest.newBuilder(URI.create(api + "/repos/" + slug + "/pulls"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + cfg.token())
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("GitHub API returned " + resp.statusCode());
            }
            JsonNode node = mapper.readTree(resp.body());
            return node.path("html_url").asText();
        } catch (IOException e) {
            throw new IllegalStateException("Pull request creation failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating pull request", e);
        }
    }
}
