package com.company.autoremediate.remediation;

import com.company.autoremediate.config.RemediationProperties;
import com.company.autoremediate.model.ClassifiedFinding;
import com.company.autoremediate.model.Confidence;
import com.company.autoremediate.model.Finding;
import com.company.autoremediate.model.SnykIssue;
import com.company.autoremediate.model.SonarIssue;
import com.company.autoremediate.util.VersionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Rule-based classification. The LLM may only downgrade a finding later, never upgrade it. */
@Component
public class IssueClassifier {
    private final RemediationProperties.Policy policy;

    public IssueClassifier(RemediationProperties props) {
        this.policy = props.policy();
    }

    public List<ClassifiedFinding> classify(List<SonarIssue> sonar, List<SnykIssue> snyk) {
        List<ClassifiedFinding> out = new ArrayList<>();
        sonar.forEach(i -> out.add(classifySonar(i)));
        snyk.forEach(i -> out.add(classifySnyk(i)));
        return out;
    }

    public ClassifiedFinding classifySonar(SonarIssue i) {
        Finding f = i;
        String type = i.type() == null ? "" : i.type().toUpperCase(Locale.ROOT);
        if (type.equals("VULNERABILITY") || type.equals("SECURITY_HOTSPOT")) {
            return new ClassifiedFinding(f, Confidence.HUMAN_REVIEW_REQUIRED, "Security finding requires human review");
        }
        String path = i.file() == null ? "" : i.file().toLowerCase(Locale.ROOT);
        for (String keyword : policy.humanReviewPathKeywords()) {
            if (path.contains(keyword.toLowerCase(Locale.ROOT))) {
                return new ClassifiedFinding(f, Confidence.HUMAN_REVIEW_REQUIRED,
                        "Sensitive area (path contains '" + keyword + "')");
            }
        }
        if (i.line() == null) {
            return new ClassifiedFinding(f, Confidence.LOW_CONFIDENCE, "Finding has no line number");
        }
        if (policy.highConfidenceSonarRules().contains(i.rule())) {
            return new ClassifiedFinding(f, Confidence.HIGH_CONFIDENCE, "Allow-listed deterministic rule " + i.rule());
        }
        if (policy.mediumConfidenceSonarRules().contains(i.rule())) {
            return new ClassifiedFinding(f, Confidence.MEDIUM_CONFIDENCE, "Rule " + i.rule() + " needs human judgement in V1");
        }
        return new ClassifiedFinding(f, Confidence.LOW_CONFIDENCE, "Rule " + i.rule() + " is not on the V1 allow-list");
    }

    public ClassifiedFinding classifySnyk(SnykIssue i) {
        Finding f = i;
        if (!i.directDependency()) {
            return new ClassifiedFinding(f, Confidence.HUMAN_REVIEW_REQUIRED,
                    "Transitive dependency: needs dependencyManagement or parent change");
        }
        if (i.recommendedVersion() == null || i.recommendedVersion().isBlank()) {
            return new ClassifiedFinding(f, Confidence.LOW_CONFIDENCE, "No known fixed version");
        }
        if (!VersionUtil.sameMajor(i.currentVersion(), i.recommendedVersion())) {
            return new ClassifiedFinding(f, Confidence.MEDIUM_CONFIDENCE,
                    "Major version upgrade " + i.currentVersion() + " -> " + i.recommendedVersion());
        }
        return new ClassifiedFinding(f, Confidence.HIGH_CONFIDENCE,
                "Direct dependency, same-major upgrade to known fixed version " + i.recommendedVersion());
    }
}
