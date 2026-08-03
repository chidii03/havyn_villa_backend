package com.havyn.properties.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import com.havyn.properties.domain.Availability;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.service.PropertyService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Host-scoped listing CRUD + status lifecycle + availability — see
 * project-docs/prompts/10-property-domain.md. Every method requires the {@code HOST}
 * role; {@link PropertyService} additionally enforces that the caller owns the
 * specific listing (object-level authz, not just role-based).
 */
@RestController
@RequestMapping("/api/v1/host/listings")
@PreAuthorize("hasRole('HOST')")
public class HostListingController {

    private final PropertyService propertyService;

    public HostListingController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyDetail create(Authentication authentication, @Valid @RequestBody CreatePropertyRequest request) {
        return PropertyDetail.from(propertyService.create(hostId(authentication), request));
    }

    @GetMapping
    public PageResponse<PropertySummary> list(
            Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(propertyService.listOwned(hostId(authentication), pageable).map(PropertySummary::from));
    }

    @GetMapping("/{id}")
    public PropertyDetail get(Authentication authentication, @PathVariable UUID id) {
        return PropertyDetail.from(propertyService.getOwned(hostId(authentication), id));
    }

    @PatchMapping("/{id}")
    public PropertyDetail update(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdatePropertyRequest request) {
        return PropertyDetail.from(propertyService.update(hostId(authentication), id, request));
    }

    @PostMapping("/{id}/submit")
    public PropertyDetail submit(Authentication authentication, @PathVariable UUID id) {
        return transition(authentication, id, PropertyStatus.PENDING);
    }

    @PostMapping("/{id}/publish")
    public PropertyDetail publish(Authentication authentication, @PathVariable UUID id) {
        return transition(authentication, id, PropertyStatus.ACTIVE);
    }

    @PostMapping("/{id}/suspend")
    public PropertyDetail suspend(Authentication authentication, @PathVariable UUID id) {
        return transition(authentication, id, PropertyStatus.SUSPENDED);
    }

    @PostMapping("/{id}/reactivate")
    public PropertyDetail reactivate(Authentication authentication, @PathVariable UUID id) {
        return transition(authentication, id, PropertyStatus.ACTIVE);
    }

    @PutMapping("/{id}/availability")
    public List<AvailabilityDay> setAvailability(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody SetAvailabilityRequest request) {
        List<Availability> days = propertyService.setAvailability(hostId(authentication), id, request);
        return days.stream().map(AvailabilityDay::from).toList();
    }

    @GetMapping("/{id}/availability")
    public List<AvailabilityDay> getAvailability(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return propertyService.getAvailability(hostId(authentication), id, from, to).stream()
                .map(AvailabilityDay::from)
                .toList();
    }

    private PropertyDetail transition(Authentication authentication, UUID id, PropertyStatus target) {
        Property property = propertyService.transition(hostId(authentication), id, target);
        return PropertyDetail.from(property);
    }

    private UUID hostId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
