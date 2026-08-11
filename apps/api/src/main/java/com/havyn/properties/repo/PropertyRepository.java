package com.havyn.properties.repo;

import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    @Override
    @EntityGraph(attributePaths = "type")
    @NonNull Page<Property> findAll(@NonNull Pageable pageable);

    @EntityGraph(attributePaths = "type")
    @NonNull Page<Property> findAllByHostId(UUID hostId, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = "type")
    @NonNull Page<Property> findAllByStatus(@NonNull PropertyStatus status, @NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"type", "amenities"})
    @NonNull
    Optional<Property> findById(@NonNull UUID id);

    @EntityGraph(attributePaths = {"type", "amenities"})
    @NonNull
    Optional<Property> findByIdAndHostId(@NonNull UUID id, @NonNull UUID hostId);

    @Query("""
            select distinct p
            from Property p
            left join fetch p.type
            left join fetch p.amenities
            where p.id = :id and p.hostId = :hostId
            """)
    Optional<Property> findDetailByIdAndHostId(@Param("id") UUID id, @Param("hostId") UUID hostId);

    long countByHostIdAndStatus(UUID hostId, PropertyStatus status);

    long countByStatus(PropertyStatus status);

    /** Used by {@code properties.rayprop.RayPropSyncService} to upsert idempotently. */
    Optional<Property> findByExternalSourceAndExternalId(String externalSource, String externalId);
}
