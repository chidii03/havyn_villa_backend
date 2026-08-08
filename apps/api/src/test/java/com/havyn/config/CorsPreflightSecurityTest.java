package com.havyn.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.havyn.auth.domain.JwtService;
import com.havyn.common.ratelimit.RateLimitFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Browser CORS preflights never carry bearer tokens, so Spring Security must allow
 * OPTIONS before the authenticated fallback. This covers the protected surfaces that
 * otherwise fail identically: bookings, favorites, account, host, and admin routes.
 */
@WebMvcTest(
        controllers = RbacTestController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@Import({SecurityConfig.class, CorsConfig.class, JwtService.class, ClockConfig.class})
class CorsPreflightSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "/api/v1/bookings, POST",
            "/api/v1/favorites/11111111-1111-1111-1111-111111111111, POST",
            "/api/v1/me, PATCH",
            "/api/v1/host/onboarding, POST",
            "/api/v1/admin/settings/bookings_enabled, PUT"
    })
    void protectedCorsPreflightsAreNotRejectedAsUnauthorized(String path, String requestedMethod) throws Exception {
        mockMvc.perform(options(path)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, requestedMethod)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
