package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.knowledge.domain.KnowledgeException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class KnowledgeExceptionHandler {

    @ExceptionHandler(KnowledgeException.class)
    public ResponseEntity<Map<String, Object>> handle(KnowledgeException ex) {
        return problem(ex.status(), titleFor(ex.status()), ex.getMessage(), ex.errorCode());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() == null ? "id" : ex.getName();
        return problem(400, "Bad Request", "Path variable " + name + " is not a valid UUID.", null);
    }

    private static ResponseEntity<Map<String, Object>> problem(
            int status, String title, String detail, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        String slug = code == null ? "bad-request" : code.toLowerCase().replace('_', '-');
        body.put("type", URI.create("https://foshol.local/problems/" + slug));
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        if (code != null) {
            body.put("code", code);
        }
        body.put("correlationId", CorrelationId.current());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static String titleFor(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            default -> "Error";
        };
    }
}
