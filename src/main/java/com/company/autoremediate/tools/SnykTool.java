package com.company.autoremediate.tools;

import com.company.autoremediate.config.RemediationProperties;
import com.company.autoremediate.util.JsonExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Runs `snyk test --json`. Exit codes: 0 = no vulnerabilities, 1 = vulnerabilities found,
 * anything else = the scan itself failed (which must never be read as "clean").
 * The token is passed through the SNYK_TOKEN environment variable of that one process only.
 */
@Component
public class SnykTool {
    private final RemediationProperties.Snyk cfg;
    private final CommandRunner runner;
    private final ObjectMapper mapper;

    public SnykTool(RemediationProperties props, CommandRunner runner, ObjectMapper mapper) {
        this.cfg = props.snyk();
        this.runner = runner;
        this.mapper = mapper;
    }

    public boolean isEnabled() {
        return cfg.enabled();
    }

    public SnykScan scan(Path repo) {
        Path jsonFile = repo.getParent().resolve("snyk-" + System.nanoTime() + ".json");
        try {
            List<String> cmd = new ArrayList<>(cfg.command());
            cmd.add("--json-file-output=" + jsonFile);
            CommandResult r = runner.run(cmd, repo, Map.of("SNYK_TOKEN", cfg.token()),
                    Duration.ofSeconds(cfg.timeoutSeconds()));
            if (r.timedOut() || (r.exitCode() != 0 && r.exitCode() != 1)) {
                return SnykScan.failed("Snyk scan failed (exit " + r.exitCode() + "): " + tail(r.output()));
            }
            String json = Files.exists(jsonFile)
                    ? Files.readString(jsonFile, StandardCharsets.UTF_8)
                    : JsonExtractor.extractObject(r.output());
            JsonNode root = mapper.readTree(json);
            if (root.isObject() && root.has("error") && !root.has("vulnerabilities")) {
                return SnykScan.failed("Snyk reported an error: " + root.path("error").asText());
            }
            return SnykScan.ok(SnykJsonParser.parse(root));
        } catch (Exception e) {
            return SnykScan.failed("Snyk scan error: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(jsonFile);
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private static String tail(String s) {
        return s.length() > 1500 ? s.substring(s.length() - 1500) : s;
    }
}
