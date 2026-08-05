package com.havyn.auth.domain;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed refresh token rotation with reuse detection ("session families") — see
 * project-docs/security/01-security-plan.md#authentication--sessions.
 *
 * <p>Each login starts a family. {@code havyn:auth:refresh-family:{familyId}} always
 * points at the one currently-valid {@code tokenId} for that family (TTL = refresh
 * TTL). Every refresh call rotates it: the presented token must match the pointer, or
 * the whole family is revoked immediately — that's the reuse-detection signal a stolen
 * token was used after the legitimate client already rotated past it.
 * {@code havyn:auth:user-sessions:{userId}} indexes every family a user has open, so a
 * password reset can revoke all of them at once.
 */
@Component
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String FAMILY_KEY_PREFIX = "havyn:auth:refresh-family:";
    private static final String USER_SESSIONS_PREFIX = "havyn:auth:user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final JwtService jwtService;

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtService jwtService) {
        this.redisTemplate = redisTemplate;
        this.jwtService = jwtService;
    }

    public Issued issue(UUID userId) {
        UUID familyId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        redisTemplate.opsForValue().set(familyKey(familyId), tokenId.toString(), jwtService.refreshTtl());
        trackSession(userId, familyId);
        return new Issued(jwtService.issueRefreshToken(userId, familyId, tokenId), userId, familyId);
    }

    /** @throws InvalidRefreshTokenException if the token is unknown, expired, or already rotated. */
    public Issued rotate(String refreshToken) {
        JwtService.RefreshClaims claims = jwtService.parseRefreshToken(refreshToken);
        String key = familyKey(claims.familyId());
        String currentTokenId = redisTemplate.opsForValue().get(key);

        if (currentTokenId == null) {
            throw InvalidRefreshTokenException.expiredOrRevoked();
        }
        if (!currentTokenId.equals(claims.tokenId().toString())) {
            redisTemplate.delete(key);
            // A stolen/replayed refresh token — the legitimate client already rotated past
            // this one. Revoking the whole family is the containment; this WARN is the
            // signal a real security-monitoring setup would alert on.
            log.warn("Refresh token reuse detected, family revoked userId={} familyId={}", claims.userId(), claims.familyId());
            throw InvalidRefreshTokenException.reuseDetected();
        }

        UUID newTokenId = UUID.randomUUID();
        redisTemplate.opsForValue().set(key, newTokenId.toString(), jwtService.refreshTtl());
        return new Issued(
                jwtService.issueRefreshToken(claims.userId(), claims.familyId(), newTokenId),
                claims.userId(),
                claims.familyId());
    }

    public void revokeFamily(UUID familyId) {
        redisTemplate.delete(familyKey(familyId));
    }

    public void revokeAllForUser(UUID userId) {
        String sessionsKey = userSessionsKey(userId);
        Set<String> familyIds = redisTemplate.opsForSet().members(sessionsKey);
        if (familyIds != null) {
            familyIds.forEach(familyId -> redisTemplate.delete(FAMILY_KEY_PREFIX + familyId));
        }
        redisTemplate.delete(sessionsKey);
    }

    private void trackSession(UUID userId, UUID familyId) {
        String sessionsKey = userSessionsKey(userId);
        redisTemplate.opsForSet().add(sessionsKey, familyId.toString());
        redisTemplate.expire(sessionsKey, jwtService.refreshTtl());
    }

    private String familyKey(UUID familyId) {
        return FAMILY_KEY_PREFIX + familyId;
    }

    private String userSessionsKey(UUID userId) {
        return USER_SESSIONS_PREFIX + userId;
    }

    public record Issued(String token, UUID userId, UUID familyId) {
    }
}
