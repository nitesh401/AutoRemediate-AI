package com.company.autoremediate.remediation;

import com.company.autoremediate.config.RemediationProperties;
import com.company.autoremediate.model.ClassifiedFinding;
import com.company.autoremediate.model.Confidence;
import com.company.autoremediate.model.FileEdit;
import com.company.autoremediate.model.FindingSource;
import com.company.autoremediate.tools.PatchStats;
import com.company.autoremediate.util.Severity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Deterministic rules about what may be automated and how big a patch may be. */
@Component
public class RemediationPolicy {
    private static final List<String> FORBIDDEN_PREFIXES = List.of(".git/", ".github/", ".mvn/", "target/");

    private final RemediationProperties props;

    public RemediationPolicy(RemediationProperties props) {
        this.props = props;
    }

    /** Only HIGH_CONFIDENCE findings are ever modified automatically in V1. */
    public List<ClassifiedFinding> select(List<ClassifiedFinding> classified, int maxIssues) {
        Set<String> seen = new HashSet<>();
        return classified.stream()
                .filter(c -> c.confidence() == Confidence.HIGH_CONFIDENCE)
                .sorted(Comparator
                        .comparingInt((ClassifiedFinding c) -> -Severity.rank(c.finding().severity()))
                        .thenComparing(c -> c.finding().id()))
                .filter(c -> seen.add(c.finding().fingerprint()))
                .limit(maxIssues)
                .toList();
    }

    /** Returns an error message, or null when the edit list is acceptable. */
    public String validateEdits(List<FileEdit> edits) {
        if (edits == null || edits.isEmpty()) return "Plan contains no edits";
        if (edits.size() > props.policy().maxEditsPerPlan()) {
            return "Too many edits (" + edits.size() + " > " + props.policy().maxEditsPerPlan() + ")";
        }
        for (FileEdit e : edits) {
            if (e == null || e.file() == null || e.search() == null || e.replace() == null || e.search().isEmpty()) {
                return "Malformed edit";
            }
            String f = e.file().replace('\\', '/');
            if (f.startsWith("/") || f.contains("..")) return "Illegal path in edit: " + e.file();
            if (!f.endsWith(".java")) return "Only .java files may be edited for Sonar fixes: " + e.file();
            for (String prefix : FORBIDDEN_PREFIXES) {
                if (f.startsWith(prefix)) return "Edit targets a protected location: " + e.file();
            }
        }
        return null;
    }

    /** Returns an error message, or null when the applied patch is within policy. */
    public String validatePatch(FindingSource source, PatchStats stats) {
        if (stats.files().isEmpty()) return "Patch changed nothing";
        if (stats.files().size() > props.policy().maxChangedFilesPerFix()) {
            return "Patch touches too many files (" + stats.files().size() + ")";
        }
        if (stats.changedLines() > props.policy().maxChangedLinesPerFix()) {
            return "Patch is not minimal (" + stats.changedLines() + " changed lines)";
        }
        for (String f : stats.files()) {
            boolean ok = source == FindingSource.SONAR ? f.endsWith(".java") : f.endsWith("pom.xml");
            if (!ok) return "Unexpected file changed for " + source + " fix: " + f;
        }
        return null;
    }
}
