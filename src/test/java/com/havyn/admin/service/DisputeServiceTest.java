package com.havyn.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.havyn.admin.domain.Dispute;
import com.havyn.admin.repo.DisputeRepository;
import com.havyn.audit.service.AuditLogService;
import com.havyn.booking.domain.Booking;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.PropertyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisputeServiceTest {

    private final DisputeRepository disputeRepository = mock(DisputeRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);

    private final DisputeService service = new DisputeService(disputeRepository, bookingRepository, propertyRepository, auditLogService, clock);

    private final UUID adminId = UUID.randomUUID();
    private final UUID guestId = UUID.randomUUID();
    private final UUID hostId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    private Booking booking() {
        return new Booking(
                propertyId, guestId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, 2,
                BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(20000), "NGN");
    }

    private Property property() {
        PropertyType villa = mock(PropertyType.class);
        return new Property(
                hostId, villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
    }

    @Test
    void raise_rejectsAMissingBooking() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.raise(guestId, bookingId, "Property was not as described"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void raise_allowsTheBookingsGuest() {
        Booking booking = booking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Dispute dispute = service.raise(guestId, booking.getId(), "Property was not as described");

        assertThat(dispute.getRaisedBy()).isEqualTo(guestId);
        assertThat(dispute.getBookingId()).isEqualTo(booking.getId());
    }

    @Test
    void raise_allowsThePropertysHost() {
        Booking booking = booking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property()));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Dispute dispute = service.raise(hostId, booking.getId(), "Guest damaged the property");

        assertThat(dispute.getRaisedBy()).isEqualTo(hostId);
    }

    @Test
    void raise_rejectsAnUnrelatedCaller() {
        Booking booking = booking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property()));

        assertThatThrownBy(() -> service.raise(UUID.randomUUID(), booking.getId(), "Not my booking"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void resolve_marksResolvedAndRecordsAudit() {
        Dispute dispute = new Dispute(UUID.randomUUID(), guestId, "Property was not as described");
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        service.resolve(adminId, dispute.getId(), "Refund issued to guest");

        assertThat(dispute.getStatus().name()).isEqualTo("RESOLVED");
        assertThat(dispute.getResolvedBy()).isEqualTo(adminId);
    }

    @Test
    void dismiss_rejectsAnAlreadyResolvedDispute() {
        Dispute dispute = new Dispute(UUID.randomUUID(), guestId, "Property was not as described");
        dispute.resolve(adminId, "Already handled", Instant.now(clock));
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.dismiss(adminId, dispute.getId(), "Duplicate"))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("DISPUTE_NOT_OPEN");
    }
}
