package com.havyn.search.cache;

import com.havyn.common.web.PageResponse;
import com.havyn.search.web.SearchResultItem;
import java.util.List;

/**
 * A concrete (non-generic) mirror of {@code PageResponse<SearchResultItem>} used only
 * as the Redis cache value. {@code RedisConfig}'s {@code GenericJackson2JsonRedisSerializer}
 * tags non-final classes with {@code @class} for polymorphic deserialization, but
 * records are implicitly final, and {@code PageResponse<T>}'s {@code List<T> data}
 * field is erased to {@code List<Object>} at the class-file level — so caching {@code
 * PageResponse<SearchResultItem>} directly would deserialize {@code data} back as a
 * list of {@code LinkedHashMap}, not {@code SearchResultItem}. This class has no type
 * parameter, so {@code List<SearchResultItem> data} is a concrete field signature
 * Jackson can round-trip correctly without any {@code @class} tagging at all.
 */
public record CachedSearchPage(List<SearchResultItem> data, int page, int size, long total, String nextCursor) {

    public static CachedSearchPage from(PageResponse<SearchResultItem> page) {
        return new CachedSearchPage(page.data(), page.page(), page.size(), page.total(), page.nextCursor());
    }

    public PageResponse<SearchResultItem> toPageResponse() {
        return new PageResponse<>(data, page, size, total, nextCursor);
    }
}
