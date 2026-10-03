package com.company.autoremediate.tools;

import com.company.autoremediate.model.SnykIssue;
import com.company.autoremediate.util.VersionUtil;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes `snyk test --json` output (single project object or an array of projects). */
public final class SnykJsonParser {
    private SnykJsonParser() {}

    public static List<SnykIssue> parse(JsonNode root) {
        Map<String, SnykIssue> unique = new LinkedHashMap<>();
        if (root.isArray()) {
            for (JsonNode project : root) collect(project, unique);
        } else {
            collect(root, unique);
        }
        return new ArrayList<>(unique.values());
    }

    private static void collect(JsonNode project, Map<String, SnykIssue> out) {
        for (JsonNode v : project.path("vulnerabilities")) {
            String id = v.path("id").asText();
            String pkg = v.path("packageName").asText(v.path("name").asText());
            String current = v.path("version").asText();
            List<String> from = new ArrayList<>();
            for (JsonNode f : v.path("from")) from.add(f.asText());
            // from = [project, directDependency, ... , vulnerablePackage]
            boolean direct = from.size() == 2;
            List<String> fixedIn = new ArrayList<>();
            for (JsonNode f : v.path("fixedIn")) fixedIn.add(f.asText());
            SnykIssue issue = new SnykIssue(id, pkg, current, v.path("severity").asText(),
                    v.path("title").asText(), pickFix(current, fixedIn), direct, from);
            out.putIfAbsent(issue.fingerprint(), issue);
        }
    }

    /** Smallest fixed version above the current one, preferring the same major version. */
    static String pickFix(String current, List<String> fixedIn) {
        String bestSameMajor = null;
        String bestAny = null;
        for (String candidate : fixedIn) {
            if (!VersionUtil.isUpgrade(current, candidate)) continue;
            if (bestAny == null || VersionUtil.compare(candidate, bestAny) < 0) bestAny = candidate;
            if (VersionUtil.sameMajor(current, candidate)
                    && (bestSameMajor == null || VersionUtil.compare(candidate, bestSameMajor) < 0)) {
                bestSameMajor = candidate;
            }
        }
        return bestSameMajor != null ? bestSameMajor : bestAny;
    }
}
