package com.company.autoremediate.tools;

import com.company.autoremediate.security.CommandNotAllowedException;
import com.company.autoremediate.security.SecretRedactor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * The only place in the application that starts processes.
 * - executable allow-list (nothing an LLM produces is ever executed)
 * - no shell: arguments are passed as a list
 * - scrubbed environment: build/test code never sees the LLM key or other secrets
 * - timeout with process-tree kill, bounded output, secret redaction
 */
@Component
public class CommandRunner {
    private static final Set<String> ALLOWED_EXECUTABLES =
            Set.of("mvn", "mvn.cmd", "mvnw", "mvnw.cmd", "git", "snyk");
    private static final List<String> ENV_PASSTHROUGH = List.of(
            "PATH", "HOME", "JAVA_HOME", "M2_HOME", "MAVEN_OPTS", "MAVEN_ARGS", "MAVEN_CONFIG",
            "LANG", "LC_ALL", "TMPDIR", "USER", "SSL_CERT_FILE", "SSL_CERT_DIR",
            "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "no_proxy",
            "NODE_EXTRA_CA_CERTS");
    private static final int MAX_OUTPUT_CHARS = 400_000;

    private final SecretRedactor redactor;

    public CommandRunner(SecretRedactor redactor) {
        this.redactor = redactor;
    }

    public CommandResult run(List<String> command, Path workDir, Map<String, String> extraEnv, Duration timeout) {
        validate(command);
        Instant started = Instant.now();
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        pb.environment().clear();
        for (String key : ENV_PASSTHROUGH) {
            String value = System.getenv(key);
            if (value != null) pb.environment().put(key, value);
        }
        if (extraEnv != null) {
            extraEnv.forEach((k, v) -> {
                if (v != null && !v.isBlank()) pb.environment().put(k, v);
            });
        }

        StringBuilder out = new StringBuilder();
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return new CommandResult(-1, "Failed to start '" + command.get(0) + "': " + e.getMessage(),
                    false, Duration.between(started, Instant.now()));
        }

        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    synchronized (out) {
                        out.append(line).append('\n');
                        if (out.length() > MAX_OUTPUT_CHARS) {
                            out.delete(0, out.length() - MAX_OUTPUT_CHARS); // keep the tail
                        }
                    }
                }
            } catch (IOException ignored) {
                // process was killed
            }
        });

        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killTree(process);
            return new CommandResult(-1, "Interrupted", false, Duration.between(started, Instant.now()));
        }
        if (!finished) killTree(process);
        try {
            reader.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String output;
        synchronized (out) {
            output = redactor.redact(out.toString());
        }
        int exit = finished ? process.exitValue() : -1;
        return new CommandResult(exit, output, !finished, Duration.between(started, Instant.now()));
    }

    private static void killTree(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    static void validate(List<String> command) {
        if (command == null || command.isEmpty()) {
            throw new CommandNotAllowedException("Empty command");
        }
        for (String arg : command) {
            if (arg == null) throw new CommandNotAllowedException("Null argument in command");
        }
        String exe = Path.of(command.get(0)).getFileName().toString();
        if (!ALLOWED_EXECUTABLES.contains(exe)) {
            throw new CommandNotAllowedException("Executable not allowed: " + exe);
        }
    }
}
