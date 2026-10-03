package com.company.autoremediate.tools;

import com.company.autoremediate.model.SonarIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Retrieves only the context relevant to one finding (file window, matching tests, versions). */
@Component
public class ContextRetriever {
    private static final int FULL_FILE_MAX_LINES = 400;
    private static final int WINDOW = 120;

    public record SourceContext(String resolvedFile, String repositoryContext, String source,
                                String lineText, String tests) {}

    public Optional<SourceContext> forSonar(RepositoryTool repo, SonarIssue issue, int maxChars) {
        Optional<String> resolved = repo.resolveFile(issue.file());
        if (resolved.isEmpty()) return Optional.empty();
        String file = resolved.get();
        String content = repo.readFile(file);
        String[] lines = content.split("\n", -1);

        String source;
        if (lines.length <= FULL_FILE_MAX_LINES || issue.line() == null) {
            source = content;
        } else {
            int from = Math.max(1, issue.line() - WINDOW);
            int to = Math.min(lines.length, issue.line() + WINDOW);
            StringBuilder sb = new StringBuilder("(excerpt: lines " + from + "-" + to + " of " + lines.length + ")\n");
            for (int i = from; i <= to; i++) sb.append(lines[i - 1]).append('\n');
            source = sb.toString();
        }
        String lineText = issue.line() != null && issue.line() >= 1 && issue.line() <= lines.length
                ? lines[issue.line() - 1] : "";

        RepositoryTool.RepositoryMetadata meta = repo.inspectRepository();
        String repoContext = "Java " + nullToUnknown(meta.javaVersion())
                + ", Spring Boot " + nullToUnknown(meta.springBootVersion())
                + ", modules: " + meta.modules().size();

        return Optional.of(new SourceContext(file, repoContext,
                truncate(source, (int) (maxChars * 0.6)),
                lineText,
                truncate(findTests(repo, file), (int) (maxChars * 0.3))));
    }

    private String findTests(RepositoryTool repo, String file) {
        if (!file.endsWith(".java")) return "(no tests located)";
        String base = file.substring(file.lastIndexOf('/') + 1, file.length() - ".java".length());
        List<String> wanted = List.of(base + "Test.java", base + "Tests.java", base + "IT.java");
        List<String> found = new ArrayList<>();
        for (String f : repo.listFiles("")) {
            String name = f.substring(f.lastIndexOf('/') + 1);
            if (f.contains("src/test/") && wanted.contains(name)) found.add(f);
            if (found.size() >= 2) break;
        }
        if (found.isEmpty()) return "(no matching test class found)";
        StringBuilder sb = new StringBuilder();
        for (String f : found) sb.append("// ").append(f).append('\n').append(repo.readFile(f)).append('\n');
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n... [truncated]";
    }

    private static String nullToUnknown(String s) {
        return s == null ? "unknown" : s;
    }
}
