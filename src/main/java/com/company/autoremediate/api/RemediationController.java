package com.company.autoremediate.api;

import com.company.autoremediate.model.RemediationRequest;
import com.company.autoremediate.orchestration.JobRepository;
import com.company.autoremediate.orchestration.JobView;
import com.company.autoremediate.orchestration.RemediationJob;
import com.company.autoremediate.orchestration.RemediationJobService;
import com.company.autoremediate.security.RepositoryUrlValidator;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/remediation")
public class RemediationController {
    private final RemediationJobService service;
    private final JobRepository jobs;
    private final RepositoryUrlValidator validator;

    public RemediationController(RemediationJobService service, JobRepository jobs, RepositoryUrlValidator validator) {
        this.service = service;
        this.jobs = jobs;
        this.validator = validator;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> start(@Valid @RequestBody RemediationRequest request) {
        String error = validator.validateUrl(request.repositoryUrl());
        if (error == null) error = validator.validateBranch(request.branch());
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
        RemediationJob job = service.submit(request);
        return ResponseEntity.accepted().body(Map.of("jobId", job.id(), "status", "STARTED"));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobView> status(@PathVariable String jobId) {
        return jobs.find(jobId).map(j -> ResponseEntity.ok(JobView.of(j))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<JobView> list() {
        return jobs.all().stream().map(JobView::of).toList();
    }
}
