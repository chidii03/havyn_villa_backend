package com.havyn.booking.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.booking.domain.Booking;
import com.havyn.booking.service.BookingHoldLock;
import com.havyn.booking.service.BookingService;
import com.havyn.booking.service.CancellationOutcome;
import com.havyn.common.web.PageResponse;
import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.properties.repo.PropertyRepository;
import jakarta.validation.Valid;
import java.util.Optional;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guest-facing booking CRUD — see project-docs/prompts/12-booking-engine.md. Every
 * endpoint requires authentication (the default {@code SecurityConfig} rule already
 * covers this — no specific role needed, any signed-in account can book).
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final BookingHoldLock bookingHoldLock;
    private final PropertyRepository propertyRepository;
    private final PropertyMediaRepository propertyMediaRepository;

    public BookingController(
            BookingService bookingService,
            BookingHoldLock bookingHoldLock,
            PropertyRepository propertyRepository,
            PropertyMediaRepository propertyMediaRepository) {
        this.bookingService = bookingService;
        this.bookingHoldLock = bookingHoldLock;
        this.propertyRepository = propertyRepository;
        this.propertyMediaRepository = propertyMediaRepository;
    }

    @PostMapping
    public ResponseEntity<BookingDetail> create(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        UUID guestId = principal(authentication);

        if (idempotencyKey != null) {
            Optional<Booking> replay = bookingService.findByIdempotencyKey(guestId, idempotencyKey);
            if (replay.isPresent()) {
                return ResponseEntity.ok(toDetail(replay.get()));
            }
        }

        Booking booking = bookingHoldLock.withLock(request.propertyId(), () -> bookingService.create(guestId, idempotencyKey, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(booking));
    }

    @GetMapping
    public PageResponse<BookingDetail> list(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(bookingService.listOwned(principal(authentication), pageable).map(this::toDetail));
    }

    @GetMapping("/{id}")
    public BookingDetail get(Authentication authentication, @PathVariable UUID id) {
        return toDetail(bookingService.getOwned(principal(authentication), id));
    }

    @PostMapping("/{id}/cancel")
    public CancellationResult cancel(Authentication authentication, @PathVariable UUID id) {
        CancellationOutcome outcome = bookingService.cancel(principal(authentication), id);
        return new CancellationResult(toDetail(outcome.booking()), outcome.refundPercentage(), outcome.refundAmount());
    }

    private BookingDetail toDetail(Booking booking) {
        BookingPropertySummary property = propertyRepository.findById(booking.getPropertyId())
                .map(found -> BookingPropertySummary.from(found, firstPhoto(found.getId())))
                .orElseGet(() -> BookingPropertySummary.unavailable(booking.getPropertyId()));
        return BookingDetail.from(booking, property);
    }

    private String firstPhoto(UUID propertyId) {
        return propertyMediaRepository.findAllByPropertyIdOrderByPositionAsc(propertyId).stream()
                .findFirst()
                .map(media -> media.getSecureUrl())
                .orElse(null);
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
