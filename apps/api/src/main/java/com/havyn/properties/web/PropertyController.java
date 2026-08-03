package com.havyn.properties.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import com.havyn.config.JwtAuthenticationFilter;
import com.havyn.properties.service.PropertyService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated property reads — see
 * project-docs/architecture/03-api-design.md. {@code GET /search} (prompt 11) is the
 * primary discovery path; these are the plain catalog/detail endpoints it links out to.
 *
 * <p>"Public" means no auth is <em>required</em> — {@link JwtAuthenticationFilter}
 * still authenticates every request that carries a valid bearer token regardless of
 * path, so {@code Authentication} here may be a real {@link AuthenticatedUser} even
 * though this controller never enforces one. {@code get} uses that identity (when
 * present) to decide how much location detail to return — see {@link
 * PropertyService#getActiveDetail}.
 */
@RestController
@RequestMapping("/api/v1/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public PageResponse<PropertySummary> list(@PageableDefault(size = 20) Pageable pageable) {
        // Always approximate — the public catalog never needs pinpoint precision;
        // see PropertySummary#withApproximateLocation's own doc for why this isn't
        // baked into PropertySummary.from itself.
        return PageResponse.of(propertyService.listActive(pageable).map(p -> PropertySummary.from(p).withApproximateLocation()));
    }

    @GetMapping("/{id}")
    public PropertyDetail get(Authentication authentication, @PathVariable UUID id) {
        return propertyService.getActiveDetail(id, viewerId(authentication), isAdmin(authentication));
    }

    private UUID viewerId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user ? user.userId() : null;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
    }
}
