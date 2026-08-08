package com.havyn.booking.service;

import com.havyn.common.error.ConflictException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * A short-lived per-property Redis lock serializing concurrent {@code POST /bookings}
 * attempts for the same property — ADR-008's "short-lived Redis hold at checkout
 * start". This is a serialization aid, not the sole correctness guarantee: the
 * Postgres exclusion constraint on {@code booking} (see {@code V5__booking.sql}) is
 * the authoritative backstop if this lock is ever bypassed or expires mid-request.
 *
 * <p>The release check-then-delete below has a small theoretical race (this request's
 * TTL could expire, another request could acquire the key, and then this request's
 * {@code finally} could delete that other request's fresh lock) — a fully atomic
 * compare-and-delete would need a Lua script. Given the lock is a fail-fast UX/
 * contention aid backed by a real DB constraint, that's an accepted MVP tradeoff, not
 * a correctness gap.
 */
@Component
public class BookingHoldLock {

    private static final Logger log = LoggerFactory.getLogger(BookingHoldLock.class);
    private static final String KEY_PREFIX = "havyn:booking:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public BookingHoldLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public <T> T withLock(UUID propertyId, Supplier<T> action) {
        String key = KEY_PREFIX + propertyId;
        String token = UUID.randomUUID().toString();

        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        } catch (DataAccessException ex) {
            log.warn("Booking Redis lock unavailable, relying on Postgres booking constraint: {}", ex.getMessage());
            return action.get();
        }
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ConflictException(
                    "PROPERTY_BOOKING_IN_PROGRESS", "This property is currently being booked by someone else — please try again in a moment");
        }
        try {
            return action.get();
        } finally {
            try {
                if (token.equals(redisTemplate.opsForValue().get(key))) {
                    redisTemplate.delete(key);
                }
            } catch (DataAccessException ex) {
                log.warn("Booking Redis lock release failed after successful booking attempt: {}", ex.getMessage());
            }
        }
    }
}
