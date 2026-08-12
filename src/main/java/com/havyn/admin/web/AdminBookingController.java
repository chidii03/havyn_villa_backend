package com.havyn.admin.web;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.booking.web.BookingDetail;
import com.havyn.booking.web.BookingPropertySummary;
import com.havyn.common.web.PageResponse;
import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.properties.repo.PropertyRepository;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyMediaRepository propertyMediaRepository;

    public AdminBookingController(
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            PropertyMediaRepository propertyMediaRepository) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.propertyMediaRepository = propertyMediaRepository;
    }

    @GetMapping
    public PageResponse<BookingDetail> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(bookingRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDetail));
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
}
