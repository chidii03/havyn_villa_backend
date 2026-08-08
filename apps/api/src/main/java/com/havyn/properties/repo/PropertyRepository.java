package com.havyn.properties.repo;

import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    @Override
    @EntityGraph(attributePaths = "type")
    Page<Property> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "type")
    Page<Property> findAllByHostId(UUID hostId, Pageable pageable);

    @EntityGraph(attributePaths = "type")
    Page<Property> findAllByStatus(PropertyStatus status, Pageable pageable);

    long countByHostIdAndStatus(UUID hostId, PropertyStatus status);

    long countByStatus(PropertyStatus status);

    /** Used by {@code properties.rayprop.RayPropSyncService} to upsert idempotently. */
    Optional<Property> findByExternalSourceAndExternalId(String externalSource, String externalId);
}
