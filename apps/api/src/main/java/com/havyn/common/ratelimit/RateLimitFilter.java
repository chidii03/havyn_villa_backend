package com.havyn.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.common.error.ErrorResponse;
import com.havyn.common.logging.CorrelationIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the first matching {@link RateLimitProperties.Rule} (by path prefix), keyed
 * on client IP. Runs as a plain servlet filter (outside Spring Security's chain) so it
 * rejects before authentication is even attempted — see
 * project-docs/security/01-security-plan.md#platform and ADR-010.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // after CorrelationIdFilter, before everything else
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Rule rule = matchingRule(request.getRequestURI());
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String key = rule.getPathPrefix() + ":" + clientIp(request);
        boolean allowed = rateLimiter.allow(key, rule.getLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
        if (!allowed) {
            log.warn("Rate limit exceeded rule={} ip={}", rule.getPathPrefix(), clientIp(request));
            meterRegistry.counter("havyn.rate_limit.rejected", "rule", rule.getPathPrefix()).increment();
            respondTooManyRequests(response, rule.getWindowSeconds());
            return;
        }

        chain.doFilter(request, response);
    }

    private RateLimitProperties.Rule matchingRule(String path) {
        return properties.getRules().stream()
                .filter(rule -> path.startsWith(rule.getPathPrefix()))
                .findFirst()
                .orElse(null);
    }

    private String clientIp(HttpServletRequest request) {
        // No reverse proxy in front of the API yet (see prompts 27/29) — once one
        // exists, prefer a trusted X-Forwarded-For here instead.
        return request.getRemoteAddr();
    }

    private void respondTooManyRequests(HttpServletResponse response, int windowSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(windowSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        ErrorResponse body = ErrorResponse.of(
                "RATE_LIMITED", "Too many requests — please try again shortly.", null, traceId);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
