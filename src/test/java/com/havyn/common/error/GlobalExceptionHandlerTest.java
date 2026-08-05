package com.havyn.common.error;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.havyn.auth.domain.JwtService;
import com.havyn.common.ratelimit.RateLimitFilter;
import com.havyn.config.ClockConfig;
import com.havyn.config.SecurityConfig;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Verifies the error envelope contract end to end (real dispatcher + GlobalExceptionHandler
 * + CorrelationIdFilter) through the test-only {@link ThrowingTestController}. A
 * {@code @WebMvcTest} slice loads only the web layer — no DataSource/Flyway/Redis — so this
 * runs without Docker. {@link SecurityConfig} (+ its {@link JwtService}/{@link ClockConfig}
 * dependencies) is imported so the slice's default secure-everything Spring Security
 * auto-configuration is replaced by the app's real JWT filter chain, while
 * CorrelationIdFilter stays active. {@link RateLimitFilter} is excluded — it needs Redis,
 * which is out of scope for this slice (see RateLimitFilterIT for that).
 */
@WebMvcTest(
        controllers = ThrowingTestController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    /**
     * These endpoints test error handling, not authorization — since {@link SecurityConfig}
     * requires authentication on everything except {@code /api/v1/auth/**}, every request
     * here carries a valid token so a 401 never masks the behavior under test.
     */
    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "test@example.com", Set.of("CUSTOMER"));
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Test
    void notFoundExceptionMapsTo404WithEnvelope() throws Exception {
        mockMvc.perform(authenticated(post("/__test/errors/not-found")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", equalTo("NOT_FOUND")))
                .andExpect(jsonPath("$.error.message", equalTo("Widget 123 not found")))
                .andExpect(jsonPath("$.error.traceId", notNullValue()))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void conflictExceptionMapsTo409WithDomainCode() throws Exception {
        mockMvc.perform(authenticated(post("/__test/errors/conflict")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", equalTo("BOOKING_UNAVAILABLE")));
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(authenticated(post("/__test/errors/unexpected")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code", equalTo("INTERNAL_ERROR")))
                .andExpect(jsonPath("$.error.message", equalTo("An unexpected error occurred")));
    }

    @Test
    void beanValidationFailureMapsTo422WithFieldDetails() throws Exception {
        mockMvc.perform(authenticated(post("/__test/errors/validate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.error.details[0].field", equalTo("name")));
    }

    @Test
    void requestPropagatedCorrelationIdIsEchoedBack() throws Exception {
        mockMvc.perform(authenticated(post("/__test/errors/not-found")).header("X-Correlation-Id", "test-trace-123"))
                .andExpect(header().string("X-Correlation-Id", "test-trace-123"))
                .andExpect(jsonPath("$.error.traceId", equalTo("test-trace-123")));
    }
}
