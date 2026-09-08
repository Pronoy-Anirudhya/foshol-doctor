package com.rootcause.foshol;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final String SAFE_INTERNAL = "An unexpected error occurred.";
    private static final String SAFE_BAD_REQUEST = "The request could not be read.";

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return problem(403, ErrorCodes.ERR_FORBIDDEN, "You are not allowed to perform this action.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(
            AuthenticationException ex, HttpServletRequest request) {
        return problem(401, ErrorCodes.ERR_TOKEN_INVALID, "Authentication is required.", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception ex, HttpServletRequest request) {
        return problem(400, ErrorCodes.ERR_BAD_REQUEST, SAFE_BAD_REQUEST, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        String code = status == 403 ? ErrorCodes.ERR_FORBIDDEN : ErrorCodes.ERR_BAD_REQUEST;
        String detail = ex.getReason() == null ? SAFE_BAD_REQUEST : ex.getReason();
        return problem(status, code, detail, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unhandled(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception correlationId={}", CorrelationId.current(), ex);
        return problem(500, ErrorCodes.ERR_INTERNAL, SAFE_INTERNAL, request);
    }

    private static ResponseEntity<Map<String, Object>> problem(
            int status, String code, String detail, HttpServletRequest request) {
        String instance = request == null ? null : request.getRequestURI();
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemResponses.problem(status, code, detail, instance, null));
    }
}
