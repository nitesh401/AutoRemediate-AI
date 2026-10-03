package com.company.autoremediate.tools;

import com.company.autoremediate.model.SonarIssue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Normalizes one page of a SonarQube/SonarCloud /api/issues/search response. */
public final class SonarIssueParser {
    private SonarIssueParser() {}

    public static List<SonarIssue> parse(JsonNode root) {
        Map<String, String> pathByComponent = new HashMap<>();
        for (JsonNode c : root.path("components")) {
            String key = c.path("key").asText("");
            String path = c.path("path").asText("");
            if (!key.isEmpty() && !path.isEmpty()) pathByComponent.put(key, path);
        }
        List<SonarIssue> issues = new ArrayList<>();
        for (JsonNode i : root.path("issues")) {
            String component = i.path("component").asText("");
            String file = pathByComponent.getOrDefault(component, afterLastColon(component));
            Integer line = i.hasNonNull("line") ? i.get("line").asInt() : null;
            issues.add(new SonarIssue(
                    i.path("key").asText(),
                    i.path("rule").asText(),
                    i.path("severity").asText(),
                    i.path("type").asText(),
                    file,
                    line,
                    i.path("message").asText()));
        }
        return issues;
    }

    private static String afterLastColon(String component) {
        int idx = component.lastIndexOf(':');
        return idx >= 0 ? component.substring(idx + 1) : component;
    }
}
