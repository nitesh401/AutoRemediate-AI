package com.company.autoremediate.orchestration;

import com.company.autoremediate.security.SecretRedactor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Append-only audit trail for one job, kept in memory and mirrored to audit.log. Secrets are redacted. */
public class AuditLog {
    public record Event(Instant timestamp, String message) {}

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final Path file;
    private final SecretRedactor redactor;

    public AuditLog(Path file, SecretRedactor redactor) {
        this.file = file;
        this.redactor = redactor;
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException ignored) {
            // memory log still works
        }
    }

    public synchronized void record(String message) {
        String clean = redactor.redact(message);
        Event event = new Event(Instant.now(), clean);
        events.add(event);
        try {
            Files.writeString(file, event.timestamp() + " " + clean + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // never fail a job because the log file is unwritable
        }
    }

    public String redact(String text) {
        return redactor.redact(text);
    }

    public List<Event> events() {
        return List.copyOf(events);
    }
}
