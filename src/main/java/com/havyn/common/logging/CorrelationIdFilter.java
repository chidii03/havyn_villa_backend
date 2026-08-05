package com.havyn.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads (or generates) a correlation id per request, stores it in the SLF4J MDC for
 * structured logging, and echoes it back on the response so clients can quote it in
 * support requests. See project-docs/backend/01-backend-foundation.md.
 *
 * <p>Deliberately a distinct MDC key from {@code traceId}/{@code spanId} (prompt 26 —
 * Micrometer Tracing/OpenTelemetry populates those two itself once a request is
 * inside an active span). This id is a simpler, always-present, app-level "quote this
 * in a support ticket" value that exists independently of whether tracing sampled
 * this particular request — the two are complementary, not the same concept.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
