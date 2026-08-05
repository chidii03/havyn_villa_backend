package com.havyn.properties.domain;

import java.util.Map;
import java.util.Set;

/**
 * Listing lifecycle (project-docs/prompts/10-property-domain.md#3): {@code draft ->
 * pending -> active -> suspended}, plus the two reversals a host needs in practice —
 * withdrawing a submission ({@code PENDING -> DRAFT}) and reactivating a paused
 * listing ({@code SUSPENDED -> ACTIVE}). There is no admin-approval gate on {@code
 * PENDING -> ACTIVE} yet (that belongs to prompt 18 / admin platform, out of this
 * prompt's file scope) — for now a host publishes their own submitted listing.
 */
public enum PropertyStatus {
    DRAFT,
    PENDING,
    ACTIVE,
    SUSPENDED;

    private static final Map<PropertyStatus, Set<PropertyStatus>> ALLOWED_TRANSITIONS = Map.of(
            DRAFT, Set.of(PENDING),
            PENDING, Set.of(ACTIVE, DRAFT),
            ACTIVE, Set.of(SUSPENDED),
            SUSPENDED, Set.of(ACTIVE));

    public boolean canTransitionTo(PropertyStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
