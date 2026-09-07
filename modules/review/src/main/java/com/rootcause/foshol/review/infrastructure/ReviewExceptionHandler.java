package com.rootcause.foshol.review.infrastructure;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.review.domain.ReviewException;
import java.net.URI;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReviewExceptionHandler {

    @ExceptionHandler(ReviewException.class)
    public ResponseEntity<Map<String, Object>> handle(ReviewException ex) {
        return problem(ex.status(), ex.errorCode(), ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleLock(OptimisticLockingFailureException ex) {
        ReviewException mapped = ReviewException.claimConflict();
        return problem(mapped.status(), mapped.errorCode(), mapped.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> problem(int status, String code, String detail) {
        Map<String, Object> body = Map.of(
                "type", URI.create("https://foshol.local/problems/" + code.toLowerCase().replace('_', '-')),
                "title", titleFor(status),
                "status", status,
                "detail", detail,
                "code", code,
                "correlationId", CorrelationId.current());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private static String titleFor(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            default -> "Error";
        };
    }
}
