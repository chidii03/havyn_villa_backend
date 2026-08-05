package com.havyn.properties.domain.event;

import com.havyn.common.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published whenever a property's searchable state changes: fields edited, status
 * transitioned, or availability set. The search module (prompt 11) listens for this
 * to invalidate its Redis cache — see {@code search.cache.SearchCacheService} — without
 * the properties module needing to know search exists.
 */
public record PropertyChangedEvent(UUID propertyId, Instant occurredAt) implements DomainEvent {

    public static PropertyChangedEvent of(UUID propertyId) {
        return new PropertyChangedEvent(propertyId, Instant.now());
    }
}
