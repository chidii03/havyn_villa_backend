package com.havyn.favorites.service;

import com.havyn.common.error.NotFoundException;
import com.havyn.favorites.domain.Favorite;
import com.havyn.favorites.repo.FavoriteRepository;
import com.havyn.properties.repo.PropertyRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Save/unsave + list — see project-docs/prompts/15-reviews-favorites.md. Object-level
 * authorization is implicit: every query/mutation is scoped to the calling user's own id
 * (there's no {@code {id}} path variable for a favorite row itself, unlike bookings/media,
 * so there's no "another user's favorite" a caller could ever address by id). Adding an
 * already-favorited property is treated as an idempotent no-op (200, not 201) rather than
 * a conflict — unlike a review, favoriting is a toggle, not a one-time action.
 */
@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final PropertyRepository propertyRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, PropertyRepository propertyRepository) {
        this.favoriteRepository = favoriteRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public FavoriteResult add(UUID userId, UUID propertyId) {
        return favoriteRepository.findByUserIdAndPropertyId(userId, propertyId)
                .map(existing -> new FavoriteResult(existing, false))
                .orElseGet(() -> {
                    if (!propertyRepository.existsById(propertyId)) {
                        throw NotFoundException.of("Property", propertyId);
                    }
                    Favorite saved = favoriteRepository.save(new Favorite(userId, propertyId));
                    return new FavoriteResult(saved, true);
                });
    }

    @Transactional
    public void remove(UUID userId, UUID propertyId) {
        Favorite favorite = favoriteRepository.findByUserIdAndPropertyId(userId, propertyId)
                .orElseThrow(() -> NotFoundException.of("Favorite", propertyId));
        favoriteRepository.delete(favorite);
    }

    @Transactional(readOnly = true)
    public Page<Favorite> list(UUID userId, Pageable pageable) {
        return favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public record FavoriteResult(Favorite favorite, boolean created) {
    }
}
