package com.company.autoremediate.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomVersionUpdaterTest {
    private final PomVersionUpdater updater = new PomVersionUpdater();

    private static String pom(String props, String deps) {
        return "<project>\n  <properties>\n" + props + "  </properties>\n  <dependencies>\n" + deps
                + "  </dependencies>\n</project>\n";
    }

    private static String dep(String artifact, String version) {
        return "    <dependency>\n      <groupId>org.apache.commons</groupId>\n      <artifactId>" + artifact
                + "</artifactId>\n" + (version == null ? "" : "      <version>" + version + "</version>\n")
                + "    </dependency>\n";
    }

    @Test
    void updatesLiteralVersionOnlyForTargetArtifact(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), pom("", dep("commons-text", "1.9") + dep("commons-lang3", "3.12.0")));
        var r = updater.updateDirectDependency(dir, "org.apache.commons", "commons-text", "1.10.0");
        assertThat(r.ok()).isTrue();
        String out = Files.readString(dir.resolve("pom.xml"));
        assertThat(out).contains("<version>1.10.0</version>").contains("<version>3.12.0</version>").doesNotContain("1.9<");
    }

    @Test
    void updatesDedicatedProperty(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"),
                pom("    <text.version>1.9</text.version>\n", dep("commons-text", "${text.version}")));
        var r = updater.updateDirectDependency(dir, "org.apache.commons", "commons-text", "1.10.0");
        assertThat(r.ok()).isTrue();
        assertThat(Files.readString(dir.resolve("pom.xml"))).contains("<text.version>1.10.0</text.version>");
    }

    @Test
    void refusesSharedProperty(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), pom("    <c.version>1.9</c.version>\n",
                dep("commons-text", "${c.version}") + dep("commons-lang3", "${c.version}")));
        var r = updater.updateDirectDependency(dir, "org.apache.commons", "commons-text", "1.10.0");
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("shared");
        assertThat(Files.readString(dir.resolve("pom.xml"))).contains("<c.version>1.9</c.version>");
    }

    @Test
    void refusesManagedVersion(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), pom("", dep("commons-text", null)));
        var r = updater.updateDirectDependency(dir, "org.apache.commons", "commons-text", "1.10.0");
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("managed");
    }

    @Test
    void refusesUnknownDependency(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), pom("", dep("commons-lang3", "3.12.0")));
        assertThat(updater.updateDirectDependency(dir, "org.apache.commons", "commons-text", "1.10.0").ok()).isFalse();
    }
}
