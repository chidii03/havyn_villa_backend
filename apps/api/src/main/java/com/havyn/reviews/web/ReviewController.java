package com.havyn.reviews.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import com.havyn.reviews.service.ReviewService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reviews are gated on a completed, eligible booking — see
 * project-docs/prompts/15-reviews-favorites.md. Listing is public (like the property
 * catalog itself, prompts 10/11); creating one requires auth — see SecurityConfig's
 * explicit POST-only carve-out from the {@code /api/v1/properties/**} public wildcard.
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewSummary> create(
            Authentication authentication, @PathVariable UUID propertyId, @Valid @RequestBody CreateReviewRequest request) {
        UUID guestId = principal(authentication);
        ReviewSummary summary = ReviewSummary.from(reviewService.create(guestId, propertyId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @GetMapping
    public PageResponse<ReviewSummary> list(@PathVariable UUID propertyId, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(reviewService.listForProperty(propertyId, pageable).map(ReviewSummary::from));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
