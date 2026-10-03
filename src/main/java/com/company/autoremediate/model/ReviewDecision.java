package com.company.autoremediate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewDecision(String decision, List<String> reasons) {
    public boolean approved() {
        return "APPROVE".equalsIgnoreCase(decision);
    }
}
