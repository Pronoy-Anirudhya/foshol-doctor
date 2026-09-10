package com.rootcause.foshol.knowledge.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

public final class RemedyStepsParser {

    private RemedyStepsParser() {}

    public static List<String> parse(String stepsBn) {
        if (stepsBn == null || stepsBn.isBlank()) {
            return List.of();
        }
        String trimmed = stepsBn.trim();
        if ("[]".equals(trimmed)) {
            return List.of();
        }
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of();
        }
        List<String> steps = new ArrayList<>();
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escape = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (escape) {
                current.append(ch);
                escape = false;
                continue;
            }
            if (ch == '\\') {
                escape = true;
                continue;
            }
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (ch == ',' && !inQuotes) {
                addStep(steps, current);
                continue;
            }
            if (inQuotes) {
                current.append(ch);
            }
        }
        addStep(steps, current);
        return List.copyOf(steps);
    }

    private static void addStep(List<String> steps, StringBuilder current) {
        String value = current.toString();
        current.setLength(0);
        if (!value.isBlank()) {
            steps.add(value);
        }
    }
}
