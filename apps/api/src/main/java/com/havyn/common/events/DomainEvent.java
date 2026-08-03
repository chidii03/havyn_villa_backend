package com.havyn.common.events;

import java.time.Instant;

/**
 * Marker for in-process domain events published on the Spring {@code ApplicationEventPublisher}
 * (e.g. booking confirmed, payment succeeded, review published). See
 * project-docs/architecture/01-system-architecture.md#3-domain-events-in-process-first.
 * Modules publish their own event records implementing this interface as they're built.
 */
public interface DomainEvent {
    Instant occurredAt();
}
