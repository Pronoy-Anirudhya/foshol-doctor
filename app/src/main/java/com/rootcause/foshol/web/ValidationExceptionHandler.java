package com.rootcause.foshol.web;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.util.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(0)
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of("field", err.getField(), "message", String.valueOf(err.getDefaultMessage())))
                .toList();
        Map<String, Object> body = ProblemResponses.problem(
                400,
                ErrorCodes.ERR_BAD_REQUEST,
                "The request is not valid.",
                request == null ? null : request.getRequestURI(),
                errors);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
