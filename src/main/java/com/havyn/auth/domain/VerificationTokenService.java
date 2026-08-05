package com.havyn.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Single-use, expiring tokens for email verification and password reset. Only a
 * SHA-256 hash of the token is ever persisted (in Redis, with a TTL) — the raw token
 * exists only in the email sent to the user, exactly like a password. Consuming a
 * token atomically deletes it (GETDEL), so it cannot be replayed.
 */
@Component
public class VerificationTokenService {

    private static final String EMAIL_VERIFY_PREFIX = "havyn:auth:verify-email:";
    private static final String PASSWORD_RESET_PREFIX = "havyn:auth:password-reset:";
    private static final Duration EMAIL_VERIFY_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    public VerificationTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issueEmailVerificationToken(UUID userId) {
        return issue(EMAIL_VERIFY_PREFIX, userId, EMAIL_VERIFY_TTL);
    }

    public Optional<UUID> consumeEmailVerificationToken(String rawToken) {
        return consume(EMAIL_VERIFY_PREFIX, rawToken);
    }

    public String issuePasswordResetToken(UUID userId) {
        return issue(PASSWORD_RESET_PREFIX, userId, PASSWORD_RESET_TTL);
    }

    public Optional<UUID> consumePasswordResetToken(String rawToken) {
        return consume(PASSWORD_RESET_PREFIX, rawToken);
    }

    private String issue(String prefix, UUID userId, Duration ttl) {
        String rawToken = generateRawToken();
        redisTemplate.opsForValue().set(prefix + hash(rawToken), userId.toString(), ttl);
        return rawToken;
    }

    private Optional<UUID> consume(String prefix, String rawToken) {
        String userId = redisTemplate.opsForValue().getAndDelete(prefix + hash(rawToken));
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }
}
