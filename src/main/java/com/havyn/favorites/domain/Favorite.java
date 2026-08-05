package com.havyn.favorites.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * A user's saved property — see project-docs/prompts/15-reviews-favorites.md.
 * {@code userId}/{@code propertyId} are plain UUID columns at the JPA level, same as
 * every other cross-aggregate reference in this project; the DB-level FKs (ON DELETE
 * CASCADE, see the V8 migration) are what actually enforce referential integrity here —
 * a favorite has no "must survive independently" requirement like {@code Booking}/
 * {@code Review} do, so cascading on delete is the correct behavior.
 */
@Entity
@Table(name = "favorite", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "property_id"}))
public class Favorite extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    protected Favorite() {
        // JPA
    }

    public Favorite(UUID userId, UUID propertyId) {
        this.userId = userId;
        this.propertyId = propertyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }
}
