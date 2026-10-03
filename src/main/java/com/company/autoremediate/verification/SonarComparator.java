package com.company.autoremediate.verification;

import com.company.autoremediate.model.SonarIssue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Compares Sonar findings as a multiset of fingerprints (rule + file + normalized message), so
 * line-number shifts caused by a patch are not mistaken for new issues.
 */
@Component
public class SonarComparator {

    public Comparison<SonarIssue> compare(List<SonarIssue> baseline, List<SonarIssue> post) {
        Map<String, List<SonarIssue>> base = group(baseline);
        Map<String, List<SonarIssue>> after = group(post);
        List<SonarIssue> added = new ArrayList<>();
        List<SonarIssue> resolved = new ArrayList<>();
        Set<String> keys = new HashSet<>(base.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            List<SonarIssue> b = base.getOrDefault(key, List.of());
            List<SonarIssue> a = after.getOrDefault(key, List.of());
            if (a.size() > b.size()) added.addAll(a.subList(b.size(), a.size()));
            if (b.size() > a.size()) resolved.addAll(b.subList(a.size(), b.size()));
        }
        return new Comparison<>(added, resolved, baseline.size(), post.size());
    }

    /** True when fewer instances of the target's fingerprint exist after the change than before. */
    public boolean isResolved(SonarIssue target, List<SonarIssue> baseline, List<SonarIssue> post) {
        String fp = target.fingerprint();
        return count(post, fp) < count(baseline, fp);
    }

    private static long count(List<SonarIssue> issues, String fingerprint) {
        return issues.stream().filter(i -> i.fingerprint().equals(fingerprint)).count();
    }

    private static Map<String, List<SonarIssue>> group(List<SonarIssue> issues) {
        Map<String, List<SonarIssue>> map = new LinkedHashMap<>();
        for (SonarIssue i : issues) map.computeIfAbsent(i.fingerprint(), k -> new ArrayList<>()).add(i);
        return map;
    }
}
