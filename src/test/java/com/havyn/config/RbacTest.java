package com.havyn.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.havyn.auth.domain.JwtService;
import com.havyn.common.ratelimit.RateLimitFilter;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * No database/Redis needed — {@link JwtService} is pure (HMAC only), so this slice
 * mints real tokens and drives them through the real {@link SecurityConfig} filter
 * chain + {@code @PreAuthorize} method security. {@link RateLimitFilter} is excluded —
 * it needs Redis, out of scope here (see RateLimitFilterIT).
 */
@WebMvcTest(
        controllers = RbacTestController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
class RbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/__test/rbac/authenticated-only"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestWithAnyRoleIsAllowedOnAnAuthOnlyEndpoint() throws Exception {
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "guest@example.com", Set.of("CUSTOMER"));

        mockMvc.perform(get("/__test/rbac/authenticated-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminIsForbiddenFromAdminOnlyEndpoint() throws Exception {
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "customer@example.com", Set.of("CUSTOMER"));

        mockMvc.perform(get("/__test/rbac/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIsAllowedOnAdminOnlyEndpoint() throws Exception {
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "admin@example.com", Set.of("ADMIN"));

        mockMvc.perform(get("/__test/rbac/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userCanAccessTheirOwnResource() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, "me@example.com", Set.of("CUSTOMER"));

        mockMvc.perform(get("/__test/rbac/users/" + userId + "/own-resource")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotAccessAnotherUsersResource_objectLevelIdorCheck() throws Exception {
        UUID someoneElsesId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "me@example.com", Set.of("CUSTOMER"));

        mockMvc.perform(get("/__test/rbac/users/" + someoneElsesId + "/own-resource")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
