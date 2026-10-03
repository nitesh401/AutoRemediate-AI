package com.company.autoremediate.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryToolTest {
    @Test
    void inspectsAndConfinesAccess(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), """
                <project>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.3.5</version></parent>
                  <artifactId>demo</artifactId>
                  <properties><java.version>21</java.version></properties>
                </project>""");
        Path src = Files.createDirectories(dir.resolve("src/main/java/p"));
        Files.writeString(src.resolve("A.java"), "package p;\nclass A { void run() {} }\n");
        Files.createDirectories(dir.resolve("target"));
        Files.writeString(dir.resolve("target/Hidden.java"), "class Hidden {}");

        RepositoryTool tool = new FileSystemRepositoryTool(dir);
        var meta = tool.inspectRepository();
        assertThat(meta.javaVersion()).isEqualTo("21");
        assertThat(meta.springBootVersion()).isEqualTo("3.3.5");
        assertThat(meta.modules()).hasSize(1);
        assertThat(tool.listFiles("")).contains("pom.xml", "src/main/java/p/A.java").doesNotContain("target/Hidden.java");
        assertThat(tool.searchCode("void run")).hasSize(1);
        assertThat(tool.resolveFile("p/A.java")).contains("src/main/java/p/A.java");
        assertThatThrownBy(() -> tool.readFile("../outside.txt")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> tool.readFile(".git/config")).isInstanceOf(SecurityException.class);
    }
}
