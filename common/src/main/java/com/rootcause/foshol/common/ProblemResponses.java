package com.rootcause.foshol.common;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProblemResponses {

    private ProblemResponses() {}

    public static Map<String, Object> problem(int status, String code, String detail) {
        return problem(status, code, detail, null, null);
    }

    public static Map<String, Object> problem(
            int status, String code, String detail, String instance, List<Map<String, String>> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", typeUri(code));
        body.put("title", titleFor(status));
        body.put("status", status);
        body.put("detail", detail);
        if (instance != null && !instance.isBlank()) {
            body.put("instance", instance);
        }
        body.put("code", code);
        body.put("correlationId", CorrelationId.current());
        if (errors != null && !errors.isEmpty()) {
            body.put("errors", errors);
        }
        return body;
    }

    public static URI typeUri(String code) {
        String slug = code == null || code.isBlank() ? "error" : code.toLowerCase().replace('_', '-');
        return URI.create("https://foshol.local/problems/" + slug);
    }

    public static String titleFor(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "Error";
        };
    }
}
