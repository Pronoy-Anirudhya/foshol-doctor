package com.rootcause.foshol.analysis.web;

import com.rootcause.foshol.analysis.domain.AnalysisException;
import com.rootcause.foshol.common.util.CorrelationId;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AnalysisExceptionHandler {

    @ExceptionHandler(AnalysisException.class)
    ResponseEntity<ProblemDetail> handle(AnalysisException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.status());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://foshol.local/problems/" + ex.errorCode().toLowerCase().replace('_', '-')));
        problem.setProperty("code", ex.errorCode());
        problem.setProperty("correlationId", CorrelationId.current());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
