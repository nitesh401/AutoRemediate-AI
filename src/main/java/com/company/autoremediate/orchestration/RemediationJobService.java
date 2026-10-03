package com.company.autoremediate.orchestration;

import com.company.autoremediate.model.RemediationRequest;
import com.company.autoremediate.security.SecretRedactor;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RemediationJobService {
    private final JobRepository jobs;
    private final Workspace workspace;
    private final SecretRedactor redactor;
    private final RemediationOrchestrator orchestrator;
    private final ExecutorService executor;

    public RemediationJobService(JobRepository jobs, Workspace workspace, SecretRedactor redactor,
                                 RemediationOrchestrator orchestrator,
                                 @Qualifier("remediationExecutor") ExecutorService executor) {
        this.jobs = jobs;
        this.workspace = workspace;
        this.redactor = redactor;
        this.orchestrator = orchestrator;
        this.executor = executor;
    }

    public RemediationJob submit(RemediationRequest request) {
        String id = "job-" + UUID.randomUUID().toString().substring(0, 8);
        AuditLog audit = new AuditLog(workspace.reportDir(id).resolve("audit.log"), redactor);
        RemediationJob job = new RemediationJob(id, request, audit);
        jobs.save(job);
        audit.record("JOB START repository=" + request.repositoryUrl()
                + (request.branch() == null ? "" : " branch=" + request.branch()));
        executor.submit(() -> orchestrator.run(job));
        return job;
    }
}
