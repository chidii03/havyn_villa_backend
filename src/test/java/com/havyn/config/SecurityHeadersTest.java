package com.havyn.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.havyn.auth.domain.JwtService;
import com.havyn.common.ratelimit.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * project-docs/security/01-security-plan.md#platform: "Secure headers (HSTS,
 * X-Content-Type-Options, Referrer-Policy, CSP)." No database/Redis needed — same
 * slice-test setup as {@link RbacTest}, since these headers are written by Spring
 * Security's filter chain regardless of the endpoint or auth outcome.
 */
@WebMvcTest(
        controllers = RbacTestController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
class SecurityHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyResponseCarriesTheBaselineSecurityHeaders_regardlessOfAuthOutcome() throws Exception {
        // /__test/rbac/authenticated-only 401s with no Authorization header — the
        // headers below are written by the security filter chain itself, before auth
        // is even evaluated, so they must still be present on a 401.
        mockMvc.perform(get("/__test/rbac/authenticated-only"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
    }

    @Test
    void hstsIsSentOverHttps_browsersIgnoreItOverPlainHttpAnyway() throws Exception {
        mockMvc.perform(get("/__test/rbac/authenticated-only").secure(true))
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().string("Strict-Transport-Security", org.hamcrest.Matchers.containsString("includeSubDomains")));
    }
}
