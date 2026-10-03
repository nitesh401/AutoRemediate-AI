package com.company.autoremediate.remediation;

import com.company.autoremediate.model.FileEdit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Applies search/replace edits. Every 'search' block must match exactly once, otherwise nothing is
 * written. Edits are computed fully in memory first, so a failure never leaves a half-applied patch.
 */
@Component
public class PatchApplier {

    public record Result(boolean ok, List<String> files, String error) {}

    public Result apply(Path repoRoot, List<FileEdit> edits) {
        Path root = repoRoot.toAbsolutePath().normalize();
        Map<Path, String> updated = new LinkedHashMap<>();
        try {
            for (FileEdit edit : edits) {
                Path file = root.resolve(edit.file()).normalize();
                if (!file.startsWith(root)) return new Result(false, List.of(), "Path escapes repository: " + edit.file());
                if (!Files.isRegularFile(file)) return new Result(false, List.of(), "File not found: " + edit.file());
                String content = updated.containsKey(file)
                        ? updated.get(file)
                        : Files.readString(file, StandardCharsets.UTF_8);
                boolean crlf = content.contains("\r\n");
                String search = adaptNewlines(edit.search(), crlf);
                String replace = adaptNewlines(edit.replace(), crlf);
                int first = content.indexOf(search);
                if (first < 0) {
                    return new Result(false, List.of(), "Search block not found in " + edit.file()
                            + " (it must match the original file exactly, including whitespace)");
                }
                if (content.indexOf(search, first + 1) >= 0) {
                    return new Result(false, List.of(), "Search block is ambiguous (matches more than once) in " + edit.file());
                }
                updated.put(file, content.substring(0, first) + replace + content.substring(first + search.length()));
            }
            List<String> changed = new ArrayList<>();
            for (Map.Entry<Path, String> e : updated.entrySet()) {
                Files.writeString(e.getKey(), e.getValue(), StandardCharsets.UTF_8);
                changed.add(root.relativize(e.getKey()).toString().replace('\\', '/'));
            }
            return new Result(true, changed, null);
        } catch (IOException e) {
            return new Result(false, List.of(), "I/O error applying patch: " + e.getMessage());
        }
    }

    private static String adaptNewlines(String text, boolean crlf) {
        String lf = text.replace("\r\n", "\n");
        return crlf ? lf.replace("\n", "\r\n") : lf;
    }
}
