package com.company.autoremediate.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.autoremediate.security.CommandNotAllowedException;
import com.company.autoremediate.security.SecretRedactor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandRunnerTest {
    private final CommandRunner runner = new CommandRunner(SecretRedactor.of(List.of("s3cr3t-value")));

    @Test
    void rejectsExecutablesOutsideAllowList(@TempDir Path dir) {
        assertThatThrownBy(() -> runner.run(List.of("bash", "-c", "echo hi"), dir, Map.of(), Duration.ofSeconds(5)))
                .isInstanceOf(CommandNotAllowedException.class);
        assertThatThrownBy(() -> runner.run(List.of("/bin/rm", "-rf", "/"), dir, Map.of(), Duration.ofSeconds(5)))
                .isInstanceOf(CommandNotAllowedException.class);
        assertThatThrownBy(() -> runner.run(List.of(), dir, Map.of(), Duration.ofSeconds(5)))
                .isInstanceOf(CommandNotAllowedException.class);
    }

    @Test
    void runsGitAndCapturesOutput(@TempDir Path dir) {
        CommandResult r = runner.run(List.of("git", "--version"), dir, Map.of(), Duration.ofSeconds(20));
        assertThat(r.success()).isTrue();
        assertThat(r.output()).contains("git version");
    }

    @Test
    void redactsSecretsFromOutput(@TempDir Path dir) {
        CommandResult r = runner.run(List.of("git", "-c", "alias.x=!echo s3cr3t-value", "x"), dir, Map.of(),
                Duration.ofSeconds(20));
        assertThat(r.output()).doesNotContain("s3cr3t-value");
    }
}
