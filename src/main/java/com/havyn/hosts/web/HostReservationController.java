package com.havyn.hosts.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.booking.domain.Booking;
import com.havyn.booking.service.BookingService;
import com.havyn.common.web.PageResponse;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.users.repo.ProfileRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reservations across every listing a host owns — see
 * project-docs/prompts/17-host-dashboard.md. Batch-resolves property titles and guest
 * names for the current page (two small {@code IN (...)} queries) rather than one
 * lookup per row, the same N+1-avoidance discipline documented in
 * architecture/01-system-architecture.md's session 11 notes.
 */
@RestController
@RequestMapping("/api/v1/host/reservations")
@PreAuthorize("hasRole('HOST')")
public class HostReservationController {

    private final BookingService bookingService;
    private final PropertyRepository propertyRepository;
    private final ProfileRepository profileRepository;

    public HostReservationController(
            BookingService bookingService, PropertyRepository propertyRepository, ProfileRepository profileRepository) {
        this.bookingService = bookingService;
        this.propertyRepository = propertyRepository;
        this.profileRepository = profileRepository;
    }

    @GetMapping
    public PageResponse<HostReservationSummary> list(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Booking> bookings = bookingService.listForHost(principal(authentication), propertyId, pageable);

        List<UUID> propertyIds = bookings.getContent().stream().map(Booking::getPropertyId).distinct().toList();
        List<UUID> guestIds = bookings.getContent().stream().map(Booking::getGuestId).distinct().toList();
        Map<UUID, String> propertyTitles = new HashMap<>();
        propertyRepository.findAllById(propertyIds).forEach(property -> propertyTitles.put(property.getId(), property.getTitle()));
        Map<UUID, String> guestNames = new HashMap<>();
        profileRepository.findAllByUser_IdIn(guestIds)
                .forEach(profile -> guestNames.put(profile.getUser().getId(), profile.getFullName()));

        return PageResponse.of(bookings.map(booking -> HostReservationSummary.from(
                booking,
                propertyTitles.getOrDefault(booking.getPropertyId(), "Listing no longer available"),
                guestNames.getOrDefault(booking.getGuestId(), "Guest"))));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
