package com.rootcause.foshol.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.common.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblemWriter {

    private final ObjectMapper objectMapper;

    public SecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String code, String detail, HttpServletRequest request)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String instance = request == null ? null : request.getRequestURI();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ProblemResponses.problem(status, code, detail, instance, null));
    }
}
