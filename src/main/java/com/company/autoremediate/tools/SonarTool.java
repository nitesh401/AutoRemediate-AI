package com.company.autoremediate.tools;

import com.company.autoremediate.config.RemediationProperties;
import com.company.autoremediate.model.SonarIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * Runs the Maven Sonar scanner against a scratch project (projectKey + suffix, so the team's real
 * Sonar project is never overwritten) and reads the findings from the SonarQube Web API.
 * The server URL always comes from configuration, never from files inside the analysed repository.
 */
@Component
public class SonarTool {
    private final RemediationProperties.Sonar cfg;
    private final CommandRunner runner;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public SonarTool(RemediationProperties props, CommandRunner runner, ObjectMapper mapper) {
        this.cfg = props.sonar();
        this.runner = runner;
        this.mapper = mapper;
    }

    public boolean isEnabled() {
        return cfg.enabled();
    }

    public SonarScan scan(Path repo) {
        try {
            String key = projectKey(repo);
            List<String> cmd = new ArrayList<>(cfg.command());
            cmd.add("-Dsonar.host.url=" + cfg.hostUrl());
            cmd.add("-Dsonar.projectKey=" + key);
            CommandResult r = runner.run(cmd, repo, Map.of("SONAR_TOKEN", cfg.token()),
                    Duration.ofSeconds(cfg.timeoutSeconds()));
            if (!r.success()) {
                return SonarScan.failed("Sonar analysis failed (exit " + r.exitCode() + "): " + tail(r.output()));
            }
            waitForAnalysis(repo);
            return SonarScan.ok(fetchIssues(key));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SonarScan.failed("Interrupted");
        } catch (Exception e) {
            return SonarScan.failed("Sonar scan error: " + e.getMessage());
        }
    }

    String projectKey(Path repo) {
        String base = cfg.projectKey();
        if (base == null || base.isBlank()) {
            Path pom = repo.resolve("pom.xml");
            if (Files.exists(pom)) {
                PomReader.PomInfo info = PomReader.read(pom);
                base = info.groupId() + ":" + info.artifactId();
            } else {
                base = repo.getFileName().toString();
            }
        }
        return base + cfg.projectKeySuffix();
    }

    private void waitForAnalysis(Path repo) throws IOException, InterruptedException {
        Path report = repo.resolve("target/sonar/report-task.txt");
        if (!Files.exists(report)) return; // nothing to wait for; issues API will reflect latest state
        Properties p = new Properties();
        try (var in = Files.newInputStream(report)) {
            p.load(in);
        }
        String taskId = p.getProperty("ceTaskId");
        if (taskId == null || taskId.isBlank()) return;
        long deadline = System.currentTimeMillis() + cfg.taskWaitSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            JsonNode node = get("/api/ce/task?id=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8));
            String status = node.path("task").path("status").asText();
            if ("SUCCESS".equals(status)) return;
            if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                throw new IOException("Sonar background task " + status);
            }
            Thread.sleep(3000);
        }
        throw new IOException("Timed out waiting for Sonar background task");
    }

    private List<SonarIssue> fetchIssues(String projectKey) throws IOException, InterruptedException {
        List<SonarIssue> all = new ArrayList<>();
        int page = 1;
        while (true) {
            JsonNode root = get("/api/issues/search?componentKeys="
                    + URLEncoder.encode(projectKey, StandardCharsets.UTF_8)
                    + "&resolved=false&ps=500&p=" + page);
            List<SonarIssue> batch = SonarIssueParser.parse(root);
            all.addAll(batch);
            int total = root.path("paging").path("total").asInt(all.size());
            if (batch.isEmpty() || all.size() >= total || all.size() >= 10_000) break;
            page++;
        }
        return all;
    }

    private JsonNode get(String pathAndQuery) throws IOException, InterruptedException {
        String basic = Base64.getEncoder().encodeToString((cfg.token() + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(stripTrailingSlash(cfg.hostUrl()) + pathAndQuery))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Basic " + basic)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Sonar API " + resp.statusCode() + " for " + pathAndQuery);
        }
        return mapper.readTree(resp.body());
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String tail(String s) {
        return s.length() > 1500 ? s.substring(s.length() - 1500) : s;
    }
}
