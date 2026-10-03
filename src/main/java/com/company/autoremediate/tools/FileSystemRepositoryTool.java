package com.company.autoremediate.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** RepositoryTool over a local work tree. All paths are confined to the repository root. */
public class FileSystemRepositoryTool implements RepositoryTool {
    private static final Set<String> SKIPPED_DIRS = Set.of(".git", "target", "node_modules", ".scannerwork");
    private static final long MAX_FILE_BYTES = 512 * 1024;
    private static final int MAX_FILES = 5000;
    private static final int MAX_MATCHES = 50;

    private final Path root;

    public FileSystemRepositoryTool(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public RepositoryMetadata inspectRepository() {
        List<MavenModule> modules = discoverModules();
        String java = null;
        String boot = null;
        Path rootPom = root.resolve("pom.xml");
        if (Files.exists(rootPom)) {
            PomReader.PomInfo info = PomReader.read(rootPom);
            java = firstNonNull(info.properties().get("java.version"), info.properties().get("maven.compiler.release"),
                    info.properties().get("maven.compiler.source"));
            if ("spring-boot-starter-parent".equals(info.parentArtifactId())) boot = info.parentVersion();
        }
        boolean wrapper = Files.exists(root.resolve("mvnw"));
        return new RepositoryMetadata(root.getFileName().toString(), java, boot, wrapper, modules);
    }

    @Override
    public String readFile(String path) {
        Path file = safe(path);
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("File too large to read: " + path);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<String> listFiles(String directory) {
        Path dir = directory == null || directory.isBlank() ? root : safe(directory);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> !isSkipped(p))
                    .map(p -> rel(p))
                    .sorted()
                    .limit(MAX_FILES)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<CodeMatch> searchCode(String query) {
        List<CodeMatch> matches = new ArrayList<>();
        if (query == null || query.isBlank()) return matches;
        for (String file : listFiles("")) {
            if (!file.endsWith(".java")) continue;
            List<String> lines;
            try {
                lines = Files.readAllLines(root.resolve(file), StandardCharsets.UTF_8);
            } catch (IOException | UncheckedIOException e) {
                continue; // unreadable/non-UTF8 file
            }
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(query)) {
                    matches.add(new CodeMatch(file, i + 1, lines.get(i).trim()));
                    if (matches.size() >= MAX_MATCHES) return matches;
                }
            }
        }
        return matches;
    }

    @Override
    public List<MavenModule> discoverModules() {
        List<MavenModule> result = new ArrayList<>();
        collect("", result, 0);
        return result;
    }

    private void collect(String relDir, List<MavenModule> out, int depth) {
        if (depth > 8) return;
        Path pom = root.resolve(relDir).resolve("pom.xml");
        if (!Files.exists(pom)) return;
        PomReader.PomInfo info = PomReader.read(pom);
        out.add(new MavenModule(relDir.isEmpty() ? "." : relDir, info.groupId(), info.artifactId(), info.version()));
        for (String m : info.modules()) {
            String next = relDir.isEmpty() ? m : relDir + "/" + m;
            collect(next, out, depth + 1);
        }
    }

    @Override
    public Optional<String> resolveFile(String reportedPath) {
        if (reportedPath == null || reportedPath.isBlank()) return Optional.empty();
        try {
            if (Files.isRegularFile(safe(reportedPath))) return Optional.of(reportedPath);
        } catch (SecurityException ignored) {
            return Optional.empty();
        }
        String suffix = "/" + reportedPath;
        return listFiles("").stream().filter(f -> ("/" + f).endsWith(suffix)).findFirst();
    }

    private Path safe(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) throw new SecurityException("Path escapes repository: " + path);
        if (isSkipped(resolved)) throw new SecurityException("Path not readable: " + path);
        return resolved;
    }

    private boolean isSkipped(Path p) {
        Path rel = root.relativize(p.toAbsolutePath().normalize());
        for (Path part : rel) {
            if (SKIPPED_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    private String rel(Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null) return v;
        return null;
    }
}
