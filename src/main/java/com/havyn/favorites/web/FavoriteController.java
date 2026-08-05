package com.havyn.favorites.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import com.havyn.favorites.service.FavoriteService;
import com.havyn.favorites.service.FavoriteService.FavoriteResult;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth-required save/unsave/list — see project-docs/prompts/15-reviews-favorites.md.
 * Not under {@code /api/v1/properties/**}, so it's authenticated by SecurityConfig's
 * default {@code anyRequest().authenticated()} fallback without needing any carve-out.
 */
@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{propertyId}")
    public ResponseEntity<FavoriteSummary> add(Authentication authentication, @PathVariable UUID propertyId) {
        FavoriteResult result = favoriteService.add(principal(authentication), propertyId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(FavoriteSummary.from(result.favorite()));
    }

    @DeleteMapping("/{propertyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication, @PathVariable UUID propertyId) {
        favoriteService.remove(principal(authentication), propertyId);
    }

    @GetMapping
    public PageResponse<FavoriteSummary> list(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(favoriteService.list(principal(authentication), pageable).map(FavoriteSummary::from));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
