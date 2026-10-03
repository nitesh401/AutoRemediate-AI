package com.company.autoremediate.orchestration;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory job store (V1). Swap for a database when jobs must survive restarts. */
@Component
public class JobRepository {
    private final ConcurrentHashMap<String, RemediationJob> jobs = new ConcurrentHashMap<>();

    public void save(RemediationJob job) {
        jobs.put(job.id(), job);
    }

    public Optional<RemediationJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public Collection<RemediationJob> all() {
        return jobs.values();
    }
}
