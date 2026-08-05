package com.havyn.auth.domain;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs and verifies the two token types. Access tokens carry role claims so
 * authorization never needs a DB round trip; refresh tokens carry a
 * {@code familyId}/{@code tokenId} pair that {@link RefreshTokenService} checks
 * against Redis for rotation + reuse detection.
 */
@Component
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_FAMILY_ID = "fid";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JwtService(
            @Value("${havyn.jwt.access-secret}") String accessSecret,
            @Value("${havyn.jwt.refresh-secret}") String refreshSecret,
            @Value("${havyn.jwt.access-ttl-seconds}") long accessTtlSeconds,
            @Value("${havyn.jwt.refresh-ttl-days}") long refreshTtlDays,
            Clock clock) {
        this.accessKey = deriveKey(accessSecret);
        this.refreshKey = deriveKey(refreshSecret);
        this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.clock = clock;
    }

    /**
     * HS256 requires a >=256-bit key. Hashing whatever secret is configured always
     * yields exactly 32 bytes, so a short local dev placeholder never crashes the app
     * — but the configured secret still needs to be high-entropy in production; this
     * only fixes length, not secrecy.
     */
    private static SecretKey deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Keys.hmacShaKeyFor(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    public String issueAccessToken(UUID userId, String email, Set<String> roles) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(accessKey)
                .compact();
    }

    public AccessClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser().verifyWith(accessKey).build().parseSignedClaims(token).getPayload();
        requireType(claims, TYPE_ACCESS);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        return new AccessClaims(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                Set.copyOf(roles));
    }

    public String issueRefreshToken(UUID userId, UUID familyId, UUID tokenId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId.toString())
                .claim(CLAIM_FAMILY_ID, familyId.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .signWith(refreshKey)
                .compact();
    }

    /** @throws JwtException if the signature is invalid, the token is expired, or malformed. */
    public RefreshClaims parseRefreshToken(String token) {
        Claims claims = Jwts.parser().verifyWith(refreshKey).build().parseSignedClaims(token).getPayload();
        requireType(claims, TYPE_REFRESH);
        return new RefreshClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(CLAIM_FAMILY_ID, String.class)),
                UUID.fromString(claims.getId()));
    }

    private void requireType(Claims claims, String expected) {
        if (!expected.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Unexpected token type — expected '" + expected + "'");
        }
    }

    public record AccessClaims(UUID userId, String email, Set<String> roles) {
    }

    public record RefreshClaims(UUID userId, UUID familyId, UUID tokenId) {
    }
}
