package com.havyn.booking.web;

import com.havyn.booking.service.BookingService;
import com.havyn.pricing.service.PricingBreakdown;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /properties/{id}/quote} — public, no persistence. See project-docs/architecture/03-api-design.md. */
@RestController
@RequestMapping("/api/v1/properties/{id}/quote")
public class QuoteController {

    private final BookingService bookingService;

    public QuoteController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public QuoteResponse quote(@PathVariable UUID id, @Valid @RequestBody QuoteRequest request) {
        PricingBreakdown breakdown = bookingService.quote(id, request.checkIn(), request.checkOut(), request.guests());
        return QuoteResponse.from(breakdown);
    }
}
