package com.havyn.search.cache;

import com.havyn.properties.domain.event.PropertyChangedEvent;
import com.havyn.search.service.SearchCriteria;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Redis-backed cache for {@code GET /search}, keyed by a generation counter rather
 * than per-entry deletes — see project-docs/prompts/11-search-discovery.md's "sane
 * invalidation" requirement. Every cache key embeds the current generation
 * ({@code havyn:search:v<gen>:<criteria>}); bumping the generation on any {@link
 * PropertyChangedEvent} instantly makes every previously-cached key unreachable
 * without needing to enumerate or scan for them (Redis's {@code KEYS}/{@code SCAN} is
 * a landmine at production scale). Stale entries simply age out via TTL. Postgres
 * remains the source of truth throughout — see {@code architecture/03...} "cache is
 * best-effort".
 */
@Service
public class SearchCacheService {

    private static final Logger log = LoggerFactory.getLogger(SearchCacheService.class);
    private static final String GENERATION_KEY = "havyn:search:gen";
    private static final String KEY_PREFIX = "havyn:search:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> objectRedisTemplate;

    public SearchCacheService(StringRedisTemplate stringRedisTemplate, RedisTemplate<String, Object> objectRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectRedisTemplate = objectRedisTemplate;
    }

    /** Best-effort per this class's own contract — a Redis outage must degrade to a cache miss, never fail the search. */
    public Optional<CachedSearchPage> get(SearchCriteria criteria, Pageable pageable) {
        try {
            Object cached = objectRedisTemplate.opsForValue().get(cacheKey(criteria, pageable));
            return cached instanceof CachedSearchPage page ? Optional.of(page) : Optional.empty();
        } catch (DataAccessException ex) {
            log.warn("Search cache read failed, treating as a miss: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(SearchCriteria criteria, Pageable pageable, CachedSearchPage page) {
        try {
            objectRedisTemplate.opsForValue().set(cacheKey(criteria, pageable), page, TTL);
        } catch (DataAccessException ex) {
            log.warn("Search cache write failed, skipping: {}", ex.getMessage());
        }
    }

    /**
     * {@code fallbackExecution = true} so this still runs if a caller ever invokes it
     * outside a transaction; {@code AFTER_COMMIT} so a change that gets rolled back
     * never invalidates a cache entry that was actually still accurate.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPropertyChanged(PropertyChangedEvent event) {
        try {
            stringRedisTemplate.opsForValue().increment(GENERATION_KEY);
        } catch (DataAccessException ex) {
            log.warn("Search cache invalidation failed, skipping: {}", ex.getMessage());
        }
    }

    private String cacheKey(SearchCriteria criteria, Pageable pageable) {
        return KEY_PREFIX + "v" + currentGeneration() + ":" + criteria + ":page="
                + pageable.getPageNumber() + ":size=" + pageable.getPageSize();
    }

    private long currentGeneration() {
        String raw = stringRedisTemplate.opsForValue().get(GENERATION_KEY);
        return raw == null ? 0L : Long.parseLong(raw);
    }
}
