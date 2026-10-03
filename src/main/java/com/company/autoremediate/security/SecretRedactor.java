package com.company.autoremediate.security;

import com.company.autoremediate.config.RemediationProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Scrubs configured secrets from anything that gets logged, stored or reported. */
@Component
public class SecretRedactor {
    private final List<String> secrets = new ArrayList<>();

    @Autowired
    public SecretRedactor(RemediationProperties props) {
        add(props.sonar().token());
        add(props.snyk().token());
        add(props.ai().apiKey());
        add(props.git().token());
        add(props.api().key());
    }

    private SecretRedactor() {}

    public static SecretRedactor of(Collection<String> values) {
        SecretRedactor r = new SecretRedactor();
        values.forEach(r::add);
        return r;
    }

    private void add(String secret) {
        if (secret != null && secret.length() >= 4) secrets.add(secret);
    }

    public String redact(String text) {
        if (text == null) return null;
        String out = text;
        for (String s : secrets) out = out.replace(s, "***");
        return out;
    }
}
