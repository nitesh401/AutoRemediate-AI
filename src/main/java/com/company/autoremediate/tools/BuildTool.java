package com.company.autoremediate.tools;

import com.company.autoremediate.config.RemediationProperties;
import com.company.autoremediate.model.BuildResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Runs the configured Maven build (default: mvn clean verify) and reports the result. */
@Component
public class BuildTool {
    private final CommandRunner runner;
    private final TestTool testTool;
    private final RemediationProperties props;

    public BuildTool(CommandRunner runner, TestTool testTool, RemediationProperties props) {
        this.runner = runner;
        this.testTool = testTool;
        this.props = props;
    }

    public BuildResult build(Path repo) {
        CommandResult r = runner.run(props.build().command(), repo, Map.of(),
                Duration.ofSeconds(props.build().timeoutSeconds()));
        return new BuildResult(r.success(), r.exitCode(), r.output(), testTool.summarize(repo).failedTests());
    }
}
