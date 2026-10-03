package com.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.text.similarity.LevenshteinDistance;

public class UserService {
    private final Map<String, String> users = new HashMap<>();

    public void add(String id, String name) {
        users.put(id, name);
    }

    public Optional<String> find(String id) {
        return Optional.ofNullable(users.get(id));
    }

    /** Sonar java:S3655 - Optional.get() without isPresent(). Existing tests only cover the happy path. */
    public String nameOf(String id) {
        Optional<String> name = find(id);
        return name.get();
    }

    /** Sonar java:S1481 - unused local variable. */
    public int distance(String a, String b) {
        int unusedCounter = 0;
        return LevenshteinDistance.getDefaultInstance().apply(a, b);
    }
}
