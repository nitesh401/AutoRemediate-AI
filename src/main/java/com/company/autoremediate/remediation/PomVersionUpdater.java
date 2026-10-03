package com.company.autoremediate.remediation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Deterministic, formatting-preserving change of ONE direct dependency version in the repo's poms.
 * Refuses (returns an error) rather than guessing when the version is managed elsewhere or when a
 * shared property would upgrade unrelated dependencies.
 */
@Component
public class PomVersionUpdater {
    private static final Pattern DEPENDENCY = Pattern.compile("(?s)<dependency>(.*?)</dependency>");
    private static final Pattern EXCLUSIONS = Pattern.compile("(?s)<exclusions>.*?</exclusions>");
    private static final Pattern PROPERTY_REF = Pattern.compile("^\\$\\{([^}]+)}$");

    public record Result(boolean ok, List<String> files, String error) {}

    public Result updateDirectDependency(Path repoRoot, String groupId, String artifactId, String newVersion) {
        try {
            Map<Path, String> contents = new LinkedHashMap<>();
            for (Path pom : findPoms(repoRoot)) contents.put(pom, Files.readString(pom, StandardCharsets.UTF_8));

            Map<Path, String> updated = new LinkedHashMap<>();
            Map<String, Integer> propertyRefs = new LinkedHashMap<>();
            boolean sawDependency = false;
            boolean changedLiteral = false;

            for (Map.Entry<Path, String> e : contents.entrySet()) {
                String content = e.getValue();
                Matcher m = DEPENDENCY.matcher(content);
                StringBuilder sb = new StringBuilder();
                while (m.find()) {
                    String block = m.group(1);
                    String head = EXCLUSIONS.matcher(block).replaceAll("");
                    if (!groupId.equals(tag(head, "groupId")) || !artifactId.equals(tag(head, "artifactId"))) {
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                        continue;
                    }
                    sawDependency = true;
                    String version = tag(head, "version");
                    if (version == null) {
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                        continue;
                    }
                    Matcher ref = PROPERTY_REF.matcher(version);
                    if (ref.matches()) {
                        propertyRefs.merge(ref.group(1), 1, Integer::sum);
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                    } else {
                        String newBlock = replaceTag(block, "version", newVersion);
                        m.appendReplacement(sb, Matcher.quoteReplacement("<dependency>" + newBlock + "</dependency>"));
                        changedLiteral = true;
                    }
                }
                m.appendTail(sb);
                if (!sb.toString().equals(content)) updated.put(e.getKey(), sb.toString());
            }

            if (!sawDependency) {
                return new Result(false, List.of(), groupId + ":" + artifactId + " is not declared directly in any pom.xml");
            }

            for (Map.Entry<String, Integer> prop : propertyRefs.entrySet()) {
                String ref = "${" + prop.getKey() + "}";
                int total = 0;
                for (String c : contents.values()) total += countOccurrences(c, ref);
                if (total != prop.getValue()) {
                    return new Result(false, List.of(), "Property " + ref
                            + " is shared with other dependencies; refusing to upgrade unrelated artifacts");
                }
                int definitions = 0;
                for (Map.Entry<Path, String> e : contents.entrySet()) {
                    String current = updated.getOrDefault(e.getKey(), e.getValue());
                    if (tag(current, prop.getKey()) != null) {
                        definitions++;
                        updated.put(e.getKey(), replaceTag(current, prop.getKey(), newVersion));
                    }
                }
                if (definitions != 1) {
                    return new Result(false, List.of(), "Property " + prop.getKey()
                            + " is not defined exactly once in this repository (found " + definitions + ")");
                }
                changedLiteral = true;
            }

            if (!changedLiteral || updated.isEmpty()) {
                return new Result(false, List.of(), groupId + ":" + artifactId
                        + " has no explicit version here (managed by a parent/BOM); needs human review");
            }

            List<String> files = new ArrayList<>();
            for (Map.Entry<Path, String> e : updated.entrySet()) {
                Files.writeString(e.getKey(), e.getValue(), StandardCharsets.UTF_8);
                files.add(repoRoot.relativize(e.getKey()).toString().replace('\\', '/'));
            }
            return new Result(true, files, null);
        } catch (IOException e) {
            return new Result(false, List.of(), "I/O error updating pom: " + e.getMessage());
        }
    }

    private static List<Path> findPoms(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .filter(p -> {
                        for (Path part : root.relativize(p)) {
                            String n = part.toString();
                            if (n.equals(".git") || n.equals("target") || n.equals("node_modules")) return false;
                        }
                        return true;
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<" + Pattern.quote(name) + ">\\s*([^<]*?)\\s*</" + Pattern.quote(name) + ">").matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private static String replaceTag(String xml, String name, String value) {
        Matcher m = Pattern.compile("(<" + Pattern.quote(name) + ">\\s*)[^<]*?(\\s*</" + Pattern.quote(name) + ">)").matcher(xml);
        if (!m.find()) return xml;
        return xml.substring(0, m.start()) + m.group(1) + value + m.group(2) + xml.substring(m.end());
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) count++;
        return count;
    }
}
