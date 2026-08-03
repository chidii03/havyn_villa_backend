package com.havyn.common.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.havyn.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real /api/v1/auth/** rule from application.yml (limit: 20,
 * window-seconds: 60) end to end. Requires Docker (Postgres + Redis).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterIT {

    private static final String LOGIN_BODY =
            "{\"email\":\"nobody@example.com\",\"password\":\"whatever-wrong-12345\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void resetRateLimitBuckets() {
        // Isolate each test's effect on these shared buckets so other IT classes
        // reusing the same cached Spring context + Redis aren't affected.
        for (String prefix : new String[] {"/api/v1/auth", "/api/v1/search", "/api/v1/bookings"}) {
            redisTemplate.keys("havyn:ratelimit:" + prefix + ":*").forEach(redisTemplate::delete);
        }
    }

    @Test
    void the21stRequestInAWindowIsRateLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_BODY))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void searchIsRateLimitedAfter60RequestsInAWindow() throws Exception {
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/v1/search")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/search")).andExpect(status().isTooManyRequests());
    }

    @Test
    void bookingsIsRateLimitedAfter30RequestsInAWindow() throws Exception {
        // The filter runs before Spring Security (outside its chain, see
        // RateLimitFilter's own doc comment) — even these unauthenticated requests
        // (normally 401) count toward the bucket, so no real auth/fixture is needed
        // to exercise the limit itself.
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isTooManyRequests());
    }
}
