package com.rootcause.foshol.identity.infrastructure;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.identity.domain.IdentityException;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<Map<String, Object>> handle(IdentityException ex) {
        Map<String, Object> body = Map.of(
                "type", URI.create("https://foshol.local/problems/" + ex.errorCode().toLowerCase().replace('_', '-')),
                "title", titleFor(ex.status()),
                "status", ex.status(),
                "detail", ex.getMessage(),
                "code", ex.errorCode(),
                "correlationId", CorrelationId.current());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex.status() == 429) {
            builder.header(HttpHeaders.RETRY_AFTER, "600");
        }
        return builder.body(body);
    }

    private static String titleFor(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 429 -> "Too Many Requests";
            case 503 -> "Service Unavailable";
            default -> "Error";
        };
    }
}
