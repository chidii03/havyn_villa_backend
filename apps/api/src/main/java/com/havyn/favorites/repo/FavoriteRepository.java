package com.havyn.favorites.repo;

import com.havyn.favorites.domain.Favorite;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    Optional<Favorite> findByUserIdAndPropertyId(UUID userId, UUID propertyId);

    Page<Favorite> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
