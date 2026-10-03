package com.company.autoremediate.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.autoremediate.model.SnykIssue;
import com.company.autoremediate.model.Snapshot;
import com.company.autoremediate.model.SonarIssue;
import com.company.autoremediate.model.TestSummary;
import com.company.autoremediate.model.VerificationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationEngineTest {
    private final VerificationEngine engine = new VerificationEngine(
            new SonarComparator(), new SnykComparator(), new RegressionChecker());

    private static SonarIssue sonar(String id, String rule, int line) {
        return new SonarIssue(id, rule, "MINOR", "CODE_SMELL", "src/A.java", line, "Remove unused thing");
    }

    private static SnykIssue snyk(String id, String version) {
        return new SnykIssue(id, "g:a", version, "high", "v", "1.10", true, List.of("p", "g:a@" + version));
    }

    private static Snapshot snap(boolean build, int run, int fail, List<SonarIssue> s, List<SnykIssue> y) {
        return new Snapshot(build, new TestSummary(run, fail, 0, 0, List.of()), s, true, y, true);
    }

    @Test
    void passesWhenTargetResolvedAndNothingElseChanges() {
        SonarIssue target = sonar("1", "java:S1128", 3);
        Snapshot base = snap(true, 10, 0, List.of(target, sonar("2", "java:S1481", 9)), List.of());
        Snapshot post = snap(true, 10, 0, List.of(sonar("2", "java:S1481", 8)), List.of()); // line shifted
        VerificationResult r = engine.verify(base, post, target);
        assertThat(r.safe()).isTrue();
        assertThat(r.targetResolved()).isTrue();
        assertThat(r.sonarDelta()).isEqualTo(-1);
        assertThat(r.decision()).isEqualTo("SAFE_FOR_PR");
    }

    @Test
    void failsWhenTargetStillPresent() {
        SonarIssue target = sonar("1", "java:S1128", 3);
        Snapshot base = snap(true, 10, 0, List.of(target), List.of());
        assertThat(engine.verify(base, base, target).safe()).isFalse();
    }

    @Test
    void failsOnNewSonarIssue() {
        SonarIssue target = sonar("1", "java:S1128", 3);
        Snapshot base = snap(true, 10, 0, List.of(target), List.of());
        Snapshot post = snap(true, 10, 0, List.of(sonar("9", "java:S3655", 4)), List.of());
        VerificationResult r = engine.verify(base, post, target);
        assertThat(r.safe()).isFalse();
        assertThat(r.newSonarIssues()).hasSize(1);
    }

    @Test
    void failsOnBuildFailureTestFailureAndTestCountDrop() {
        SonarIssue target = sonar("1", "java:S1128", 3);
        Snapshot base = snap(true, 10, 0, List.of(target), List.of());
        assertThat(engine.verify(base, Snapshot.failedBuild(TestSummary.empty()), target).safe()).isFalse();
        assertThat(engine.verify(base, snap(true, 10, 1, List.of(), List.of()), target).safe()).isFalse();
        assertThat(engine.verify(base, snap(true, 9, 0, List.of(), List.of()), target).safe()).isFalse();
    }

    @Test
    void failsWhenScannerWorkedBeforeButNotAfter() {
        SonarIssue target = sonar("1", "java:S1128", 3);
        Snapshot base = snap(true, 10, 0, List.of(target), List.of());
        Snapshot post = new Snapshot(true, new TestSummary(10, 0, 0, 0, List.of()), List.of(), false, List.of(), false);
        assertThat(engine.verify(base, post, target).safe()).isFalse();
    }

    @Test
    void snykTargetResolvedIgnoringVersionChange() {
        SnykIssue target = snyk("SNYK-1", "1.9");
        Snapshot base = new Snapshot(true, new TestSummary(5, 0, 0, 0, List.of()), List.of(), false,
                List.of(target, snyk("SNYK-2", "1.9")), true);
        Snapshot post = new Snapshot(true, new TestSummary(5, 0, 0, 0, List.of()), List.of(), false,
                List.of(snyk("SNYK-2", "1.10")), true);
        VerificationResult r = engine.verify(base, post, target);
        assertThat(r.safe()).isTrue();
        assertThat(r.snykDelta()).isEqualTo(-1);
    }

    @Test
    void failsOnNewSnykVulnerability() {
        SnykIssue target = snyk("SNYK-1", "1.9");
        Snapshot base = new Snapshot(true, new TestSummary(5, 0, 0, 0, List.of()), List.of(), false, List.of(target), true);
        Snapshot post = new Snapshot(true, new TestSummary(5, 0, 0, 0, List.of()), List.of(), false,
                List.of(snyk("SNYK-NEW", "1.10")), true);
        assertThat(engine.verify(base, post, target).safe()).isFalse();
    }
}
