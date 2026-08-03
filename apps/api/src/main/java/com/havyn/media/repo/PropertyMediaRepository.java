package com.havyn.media.repo;

import com.havyn.media.domain.PropertyMedia;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyMediaRepository extends JpaRepository<PropertyMedia, UUID> {

    List<PropertyMedia> findAllByPropertyIdOrderByPositionAsc(UUID propertyId);

    /**
     * Batch variant for a page of search results — one query for the whole page
     * instead of N. Ordered by position only (not grouped by property first), which is
     * still correct for "first photo per property": every property's position-0 row
     * sorts before any property's position-1 row, so grouping by propertyId while
     * preserving encounter order (see SearchService) yields each property's photos in
     * the right sequence.
     */
    List<PropertyMedia> findAllByPropertyIdInOrderByPositionAsc(Collection<UUID> propertyIds);

    Optional<PropertyMedia> findByIdAndPropertyId(UUID id, UUID propertyId);

    long countByPropertyId(UUID propertyId);

    /** Used by {@code properties.rayprop.RayPropSyncService} to re-sync a listing's images on each run. */
    void deleteAllByPropertyId(UUID propertyId);
}
