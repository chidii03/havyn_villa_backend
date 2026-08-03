package com.havyn.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit test — no Spring context, no Testcontainers, runs anywhere. */
class JwtServiceTest {

    private static final String ACCESS_SECRET = "unit-test-access-secret";
    private static final String REFRESH_SECRET = "unit-test-refresh-secret";

    private JwtService serviceAt(Instant now, long accessTtlSeconds) {
        return new JwtService(ACCESS_SECRET, REFRESH_SECRET, accessTtlSeconds, 30, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuesAndParsesAnAccessTokenRoundTrip() {
        JwtService jwtService = serviceAt(Instant.now(), 900);
        UUID userId = UUID.randomUUID();

        String token = jwtService.issueAccessToken(userId, "traveler@example.com", Set.of("CUSTOMER", "HOST"));
        JwtService.AccessClaims claims = jwtService.parseAccessToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.email()).isEqualTo("traveler@example.com");
        assertThat(claims.roles()).containsExactlyInAnyOrder("CUSTOMER", "HOST");
    }

    @Test
    void issuesAndParsesARefreshTokenRoundTrip() {
        JwtService jwtService = serviceAt(Instant.now(), 900);
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        String token = jwtService.issueRefreshToken(userId, familyId, tokenId);
        JwtService.RefreshClaims claims = jwtService.parseRefreshToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.familyId()).isEqualTo(familyId);
        assertThat(claims.tokenId()).isEqualTo(tokenId);
    }

    @Test
    void rejectsATamperedToken() {
        JwtService jwtService = serviceAt(Instant.now(), 900);
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "a@example.com", Set.of("CUSTOMER"));
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        // Issued far in the past with a 1-second TTL — the parser validates `exp`
        // against real wall-clock time, so by the time this runs it's long expired.
        JwtService jwtService = serviceAt(Instant.parse("2020-01-01T00:00:00Z"), 1);
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "a@example.com", Set.of("CUSTOMER"));

        assertThatThrownBy(() -> jwtService.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsARefreshTokenPresentedToTheAccessParser() {
        JwtService jwtService = serviceAt(Instant.now(), 900);
        String refreshToken = jwtService.issueRefreshToken(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // Different signing key (refreshKey vs accessKey) — must fail signature verification.
        assertThatThrownBy(() -> jwtService.parseAccessToken(refreshToken)).isInstanceOf(JwtException.class);
    }
}
