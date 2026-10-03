package com.company.autoremediate.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Prompts are versioned source files (classpath:prompts/NAME.txt). Substitution is single-pass, so
 * text inside substituted values (e.g. repository content) is never interpreted as a placeholder.
 */
@Component
public class PromptTemplates {
    public static final String SYSTEM = "system-v1";
    public static final String SONAR = "sonar-remediation-v1";
    public static final String SNYK = "snyk-remediation-v1";
    public static final String REVIEW = "code-review-v1";
    public static final String VERIFICATION = "verification-v1";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    public List<String> versions() {
        return List.of(SYSTEM, SONAR, SNYK, REVIEW, VERIFICATION);
    }

    public String load(String name) {
        try (InputStream in = new ClassPathResource("prompts/" + name + ".txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Prompt template not found: " + name, e);
        }
    }

    public String render(String name, Map<String, String> vars) {
        String template = load(name);
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            if (!vars.containsKey(key)) {
                throw new IllegalArgumentException("Missing prompt variable '" + key + "' for " + name);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(vars.get(key) == null ? "" : vars.get(key)));
        }
        m.appendTail(out);
        return out.toString();
    }
}
