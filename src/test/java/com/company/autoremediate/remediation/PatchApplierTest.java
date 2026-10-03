package com.company.autoremediate.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.autoremediate.model.FileEdit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchApplierTest {
    private final PatchApplier applier = new PatchApplier();

    @Test
    void appliesUniqueEdit(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("A.java");
        Files.writeString(f, "import java.util.List;\nclass A {}\n");
        var r = applier.apply(dir, List.of(new FileEdit("A.java", "import java.util.List;\n", "")));
        assertThat(r.ok()).isTrue();
        assertThat(Files.readString(f)).isEqualTo("class A {}\n");
    }

    @Test
    void rejectsMissingAndAmbiguousBlocksWithoutWriting(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("A.java");
        String original = "x();\nx();\n";
        Files.writeString(f, original);
        assertThat(applier.apply(dir, List.of(new FileEdit("A.java", "y();", "z();"))).ok()).isFalse();
        assertThat(applier.apply(dir, List.of(new FileEdit("A.java", "x();", "z();"))).error()).contains("ambiguous");
        assertThat(Files.readString(f)).isEqualTo(original);
    }

    @Test
    void keepsCrlfLineEndings(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("A.java");
        Files.writeString(f, "a\r\nb\r\nc\r\n");
        assertThat(applier.apply(dir, List.of(new FileEdit("A.java", "b\n", "B\n"))).ok()).isTrue();
        assertThat(Files.readString(f)).isEqualTo("a\r\nB\r\nc\r\n");
    }

    @Test
    void refusesPathEscape(@TempDir Path dir) {
        var r = applier.apply(dir, List.of(new FileEdit("../evil.java", "a", "b")));
        assertThat(r.ok()).isFalse();
    }
}
