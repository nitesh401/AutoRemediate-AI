package com.company.autoremediate.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.autoremediate.TestProps;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryUrlValidatorTest {
    @Test
    void acceptsNormalRemotes() {
        var v = new RepositoryUrlValidator(TestProps.defaults("./w", false));
        assertThat(v.validateUrl("https://github.com/acme/demo.git")).isNull();
        assertThat(v.validateUrl("git@github.com:acme/demo.git")).isNull();
    }

    @Test
    void rejectsDangerousInput() {
        var v = new RepositoryUrlValidator(TestProps.defaults("./w", false));
        assertThat(v.validateUrl("--upload-pack=evil")).isNotNull();
        assertThat(v.validateUrl("ext::sh -c touch /tmp/x")).isNotNull();
        assertThat(v.validateUrl("http://github.com/acme/demo")).isNotNull();
        assertThat(v.validateUrl("https://user:pw@github.com/acme/demo")).isNotNull();
        assertThat(v.validateUrl("/tmp/some/local")).isNotNull(); // local disabled
        assertThat(v.validateUrl("")).isNotNull();
        assertThat(v.validateBranch("--evil")).isNotNull();
        assertThat(v.validateBranch("feature/ok-1")).isNull();
    }

    @Test
    void localRepositoriesOnlyWhenAllowed(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        assertThat(new RepositoryUrlValidator(TestProps.defaults("./w", true)).validateUrl(dir.toString())).isNull();
        assertThat(new RepositoryUrlValidator(TestProps.defaults("./w", false)).validateUrl(dir.toString())).isNotNull();
    }
}
