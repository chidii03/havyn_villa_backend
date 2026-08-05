package com.havyn.common.ratelimit;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter backed by Redis {@code INCR} (atomic) with a conditional
 * {@code EXPIRE} set only by the request that created the window (count == 1), so
 * concurrent callers can't each reset the window. See ADR-010.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String KEY_PREFIX = "havyn:ratelimit:";

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** @return true if this call is within {@code limit} requests per {@code window}. */
    public boolean allow(String key, int limit, Duration window) {
        String redisKey = KEY_PREFIX + key;
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(redisKey);
        } catch (DataAccessException ex) {
            // Redis unavailable/unreachable — Lettuce raises here rather than returning
            // null, so this catch is what actually implements "fail open rather than
            // blocking all traffic," not the count == null check below.
            log.warn("Rate limiter Redis call failed, failing open: {}", ex.getMessage());
            return true;
        }
        if (count == null) {
            return true; // Defensive: same fail-open intent if a driver ever returns null instead of throwing.
        }
        if (count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count <= limit;
    }
}
