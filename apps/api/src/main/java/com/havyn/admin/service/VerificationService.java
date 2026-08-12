package com.havyn.admin.service;

import com.havyn.admin.domain.VerificationRequest;
import com.havyn.admin.domain.VerificationStatus;
import com.havyn.admin.repo.VerificationRequestRepository;
import com.havyn.audit.service.AuditLogService;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.users.repo.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Host identity (KYC) verification — see project-docs/prompts/18-admin-platform.md.
 * A user may have at most one PENDING request at a time (checked here, not enforced
 * at the DB level — no partial-unique-index equivalent was worth adding for a single
 * status value gate). Access to a specific request is restricted to its owner and
 * admins — see {@link #getOwnedOrAdmin}.
 */
@Service
public class VerificationService {

    private final VerificationRequestRepository verificationRequestRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final UserRepository userRepository;

    public VerificationService(
            VerificationRequestRepository verificationRequestRepository,
            AuditLogService auditLogService,
            Clock clock,
            UserRepository userRepository) {
        this.verificationRequestRepository = verificationRequestRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.userRepository = userRepository;
    }

    @Transactional
    public VerificationRequest submit(UUID userId, String documentUrl, String notes) {
        boolean emailVerified = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId))
                .isEmailVerified();
        if (!emailVerified) {
            throw new BadRequestException("EMAIL_NOT_VERIFIED", "Verify your email before submitting host verification");
        }
        if (verificationRequestRepository.existsByUserIdAndStatus(userId, VerificationStatus.PENDING)) {
            throw new ConflictException("VERIFICATION_ALREADY_PENDING", "You already have a verification request awaiting review");
        }
        return verificationRequestRepository.save(new VerificationRequest(userId, documentUrl, notes));
    }

    @Transactional(readOnly = true)
    public Page<VerificationRequest> listOwn(UUID userId, Pageable pageable) {
        return verificationRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<VerificationRequest> listPending(Pageable pageable) {
        return verificationRequestRepository.findAllByStatusOrderByCreatedAtAsc(VerificationStatus.PENDING, pageable);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return verificationRequestRepository.countByStatus(VerificationStatus.PENDING);
    }

    @Transactional
    public VerificationRequest approve(UUID adminId, UUID requestId) {
        VerificationRequest request = findPending(requestId);
        request.approve(adminId, Instant.now(clock));
        auditLogService.record(
                adminId, "VERIFICATION_APPROVED", "VerificationRequest", requestId,
                Map.of("status", "PENDING"), Map.of("status", "APPROVED"));
        return request;
    }

    @Transactional
    public VerificationRequest reject(UUID adminId, UUID requestId, String reviewNotes) {
        VerificationRequest request = findPending(requestId);
        request.reject(adminId, reviewNotes, Instant.now(clock));
        auditLogService.record(
                adminId, "VERIFICATION_REJECTED", "VerificationRequest", requestId,
                Map.of("status", "PENDING"), Map.of("status", "REJECTED", "reviewNotes", reviewNotes));
        return request;
    }

    /** A caller may see a specific request only if they own it — admin-only listing/review endpoints don't call this. */
    @Transactional(readOnly = true)
    public VerificationRequest getOwnedOrAdmin(UUID callerId, UUID requestId) {
        VerificationRequest request = verificationRequestRepository.findById(requestId)
                .orElseThrow(() -> NotFoundException.of("VerificationRequest", requestId));
        if (!request.getUserId().equals(callerId)) {
            throw new ForbiddenException("You do not have access to this verification request");
        }
        return request;
    }

    private VerificationRequest findPending(UUID requestId) {
        VerificationRequest request = verificationRequestRepository.findById(requestId)
                .orElseThrow(() -> NotFoundException.of("VerificationRequest", requestId));
        if (request.getStatus() != VerificationStatus.PENDING) {
            throw new ConflictException("VERIFICATION_NOT_PENDING", "This request has already been reviewed");
        }
        return request;
    }
}
