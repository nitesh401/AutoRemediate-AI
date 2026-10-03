package com.company.autoremediate.report;

import com.company.autoremediate.model.FindingSource;
import com.company.autoremediate.model.Snapshot;
import com.company.autoremediate.orchestration.RemediationJob;
import com.company.autoremediate.orchestration.Workspace;
import com.company.autoremediate.report.RemediationReport.SnapshotSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Builds the auditable report (Markdown + JSON) from the job state and writes it next to audit.log. */
@Component
public class ReportGenerator {
    private final MarkdownReportGenerator markdown;
    private final ObjectMapper mapper;
    private final Workspace workspace;

    public ReportGenerator(MarkdownReportGenerator markdown, ObjectMapper mapper, Workspace workspace) {
        this.markdown = markdown;
        this.mapper = mapper;
        this.workspace = workspace;
    }

    public RemediationReport build(RemediationJob job) {
        boolean anyFixed = job.fixed(FindingSource.SONAR) + job.fixed(FindingSource.SNYK) > 0;
        Snapshot base = job.baseline();
        Snapshot fin = job.finalSnapshot() != null ? job.finalSnapshot() : base;
        String decision = anyFixed ? "SAFE_FOR_PR" : "NO_CHANGES_ACCEPTED";
        String statement = anyFixed
                ? "The automated verification suite passed and no regression was detected within the available checks. "
                  + "This is not proof that business behavior is unchanged; human review is required."
                : "No change was accepted by the safety gate. Nothing is claimed about the safety of any change.";
        return new RemediationReport(job.id(), job.request().repositoryUrl(), job.baseBranch(),
                job.remediationBranch(), job.status().name(), job.result(), job.createdAt(), job.finishedAt(),
                job.model(), job.promptVersions(), summarize(base), summarize(fin), job.newIssues(),
                job.outcomes(), decision, statement, job.pullRequestStatus(), job.pullRequestUrl(),
                job.repoPath() == null ? null : job.repoPath().toString(), job.warnings(), job.error());
    }

    public Path write(RemediationJob job) {
        Path dir = workspace.reportDir(job.id());
        try {
            Files.createDirectories(dir);
            RemediationReport report = build(job);
            Files.writeString(dir.resolve("report.md"), markdown.generate(report), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("report.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            job.warn("Could not write report: " + e.getMessage());
        }
        return dir;
    }

    public Optional<String> read(String jobId, String format) {
        Path file = workspace.reportDir(jobId).resolve("json".equalsIgnoreCase(format) ? "report.json" : "report.md");
        try {
            return Files.exists(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static SnapshotSummary summarize(Snapshot s) {
        if (s == null) return new SnapshotSummary(false, 0, 0, List.of(), false, 0, false, 0);
        int run = s.tests() == null ? 0 : s.tests().run();
        int failed = s.tests() == null ? 0 : s.tests().failures() + s.tests().errors();
        List<String> names = s.tests() == null ? List.of() : s.tests().failedTests();
        return new SnapshotSummary(s.buildPassed(), run, failed, names, s.sonarAvailable(),
                s.sonarIssues().size(), s.snykAvailable(), s.snykIssues().size());
    }
}
