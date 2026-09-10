package com.rootcause.foshol.web;

import com.rootcause.foshol.common.util.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        try {
            CorrelationId.adoptOrGenerate(request.getHeader(CorrelationId.HEADER));
            response.setHeader(CorrelationId.HEADER, CorrelationId.current());
            log.info("http start {} {}", method, uri);
            filterChain.doFilter(request, response);
            log.info(
                    "http done {} {} {} ({} ms)",
                    method,
                    uri,
                    response.getStatus(),
                    (System.nanoTime() - started) / 1_000_000L);
        } finally {
            CorrelationId.clear();
        }
    }
}
