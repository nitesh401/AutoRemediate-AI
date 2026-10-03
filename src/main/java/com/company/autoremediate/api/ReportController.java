package com.company.autoremediate.api;

import com.company.autoremediate.orchestration.JobRepository;
import com.company.autoremediate.report.ReportGenerator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/remediation/{jobId}")
public class ReportController {
    private final JobRepository jobs;
    private final ReportGenerator reports;

    public ReportController(JobRepository jobs, ReportGenerator reports) {
        this.jobs = jobs;
        this.reports = reports;
    }

    /** format = markdown (default) or json. */
    @GetMapping("/report")
    public ResponseEntity<String> report(@PathVariable String jobId,
                                         @RequestParam(defaultValue = "markdown") String format) {
        if (jobs.find(jobId).isEmpty()) return ResponseEntity.notFound().build();
        boolean json = "json".equalsIgnoreCase(format);
        return reports.read(jobId, json ? "json" : "md")
                .map(body -> ResponseEntity.ok()
                        .contentType(json ? MediaType.APPLICATION_JSON : MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                        .body(body))
                .orElseGet(() -> ResponseEntity.status(404).body("Report not available yet"));
    }

    @GetMapping("/audit")
    public ResponseEntity<String> audit(@PathVariable String jobId) {
        return jobs.find(jobId)
                .map(job -> {
                    StringBuilder sb = new StringBuilder();
                    job.audit().events().forEach(e -> sb.append(e.timestamp()).append(' ').append(e.message()).append('\n'));
                    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(sb.toString());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
