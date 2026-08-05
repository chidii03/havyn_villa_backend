package com.havyn.admin.service;

import com.havyn.admin.domain.Dispute;
import com.havyn.admin.domain.DisputeStatus;
import com.havyn.admin.repo.DisputeRepository;
import com.havyn.audit.service.AuditLogService;
import com.havyn.booking.domain.Booking;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.properties.repo.PropertyRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking disputes — see project-docs/prompts/18-admin-platform.md. Only the
 * booking's guest or the listing's host may raise one — reads {@code booking/}'s and
 * {@code properties/}'s repositories directly (read-only), the same established
 * cross-module pattern used throughout this codebase (e.g. {@code ReviewService}
 * reading {@code BookingRepository}).
 */
@Service
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public DisputeService(
            DisputeRepository disputeRepository,
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            AuditLogService auditLogService,
            Clock clock) {
        this.disputeRepository = disputeRepository;
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public Dispute raise(UUID callerId, UUID bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> NotFoundException.of("Booking", bookingId));
        boolean isGuest = booking.getGuestId().equals(callerId);
        boolean isHost = !isGuest
                && propertyRepository.findById(booking.getPropertyId())
                        .map(property -> property.getHostId().equals(callerId))
                        .orElse(false);
        if (!isGuest && !isHost) {
            throw new ForbiddenException("You do not have access to this booking");
        }
        return disputeRepository.save(new Dispute(bookingId, callerId, reason));
    }

    @Transactional(readOnly = true)
    public Page<Dispute> listOpen(Pageable pageable) {
        return disputeRepository.findAllByStatusOrderByCreatedAtAsc(DisputeStatus.OPEN, pageable);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return disputeRepository.countByStatus(DisputeStatus.OPEN);
    }

    @Transactional
    public Dispute resolve(UUID adminId, UUID disputeId, String resolutionNotes) {
        Dispute dispute = findOpen(disputeId);
        dispute.resolve(adminId, resolutionNotes, Instant.now(clock));
        auditLogService.record(
                adminId, "DISPUTE_RESOLVED", "Dispute", disputeId,
                Map.of("status", "OPEN"), Map.of("status", "RESOLVED", "resolutionNotes", resolutionNotes));
        return dispute;
    }

    @Transactional
    public Dispute dismiss(UUID adminId, UUID disputeId, String resolutionNotes) {
        Dispute dispute = findOpen(disputeId);
        dispute.dismiss(adminId, resolutionNotes, Instant.now(clock));
        auditLogService.record(
                adminId, "DISPUTE_DISMISSED", "Dispute", disputeId,
                Map.of("status", "OPEN"), Map.of("status", "DISMISSED", "resolutionNotes", resolutionNotes));
        return dispute;
    }

    private Dispute findOpen(UUID disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId).orElseThrow(() -> NotFoundException.of("Dispute", disputeId));
        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new ConflictException("DISPUTE_NOT_OPEN", "This dispute has already been resolved or dismissed");
        }
        return dispute;
    }
}
