package com.havyn.properties.cache;

import com.havyn.properties.domain.event.PropertyChangedEvent;
import com.havyn.properties.web.PropertyDetail;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Redis-backed cache for {@code GET /properties/{id}} — prompt 25's "caching for
 * popular properties." Unlike {@code search.cache.SearchCacheService}'s
 * generation-counter scheme (needed there because one property change must
 * invalidate *many* different cached search-result pages), a property detail change
 * only ever needs to invalidate *one* key — its own — so a direct delete on {@link
 * PropertyChangedEvent} is simpler and just as correct, without a KEYS/SCAN in sight.
 *
 * <p><strong>Never used for price-critical reads.</strong> {@code
 * PropertyService.getActive(UUID)} (used by booking/quote — see
 * project-docs/security/01-security-plan.md-adjacent "cache is best-effort, don't
 * cache price-critical data unsafely") stays entirely uncached, always hitting
 * Postgres directly; only the new read-only {@code getActiveDetail(UUID)} — backing
 * the public detail page — goes through this cache.
 */
@Service
public class PropertyCacheService {

    private static final Logger log = LoggerFactory.getLogger(PropertyCacheService.class);
    private static final String KEY_PREFIX = "havyn:property:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final RedisTemplate<String, Object> objectRedisTemplate;

    public PropertyCacheService(RedisTemplate<String, Object> objectRedisTemplate) {
        this.objectRedisTemplate = objectRedisTemplate;
    }

    /** Best-effort per this class's own contract — a Redis outage must degrade to a cache miss, never fail the request. */
    public Optional<PropertyDetail> get(UUID propertyId) {
        try {
            Object cached = objectRedisTemplate.opsForValue().get(key(propertyId));
            return cached instanceof PropertyDetail detail ? Optional.of(detail) : Optional.empty();
        } catch (DataAccessException ex) {
            log.warn("Property cache read failed, treating as a miss: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(UUID propertyId, PropertyDetail detail) {
        try {
            objectRedisTemplate.opsForValue().set(key(propertyId), detail, TTL);
        } catch (DataAccessException ex) {
            log.warn("Property cache write failed, skipping: {}", ex.getMessage());
        }
    }

    /** Same AFTER_COMMIT/fallbackExecution reasoning as SearchCacheService's listener. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPropertyChanged(PropertyChangedEvent event) {
        try {
            objectRedisTemplate.delete(key(event.propertyId()));
        } catch (DataAccessException ex) {
            log.warn("Property cache invalidation failed, skipping: {}", ex.getMessage());
        }
    }

    private String key(UUID propertyId) {
        return KEY_PREFIX + propertyId;
    }
}
