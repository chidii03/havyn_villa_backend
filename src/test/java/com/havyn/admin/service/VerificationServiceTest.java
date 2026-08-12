package com.havyn.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.havyn.admin.domain.VerificationRequest;
import com.havyn.admin.domain.VerificationStatus;
import com.havyn.admin.repo.VerificationRequestRepository;
import com.havyn.audit.service.AuditLogService;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.common.error.BadRequestException;
import com.havyn.users.domain.User;
import com.havyn.users.repo.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationServiceTest {

    private final VerificationRequestRepository repository = mock(VerificationRequestRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);

    private final VerificationService service = new VerificationService(repository, auditLogService, clock, userRepository);

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void submit_rejectsASecondPendingRequestFromTheSameUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(verifiedUser()));
        when(repository.existsByUserIdAndStatus(userId, VerificationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(userId, "https://example.com/id.pdf", null))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("VERIFICATION_ALREADY_PENDING");
    }

    @Test
    void submit_savesANewPendingRequest() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(verifiedUser()));
        when(repository.existsByUserIdAndStatus(userId, VerificationStatus.PENDING)).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VerificationRequest request = service.submit(userId, "https://example.com/id.pdf", "My driver's license");

        assertThat(request.getUserId()).isEqualTo(userId);
        assertThat(request.getStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void submit_rejectsAnUnverifiedEmail() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User("guest@example.com", "hashed")));

        assertThatThrownBy(() -> service.submit(userId, "https://example.com/id.pdf", null))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void approve_marksApprovedAndRecordsAudit() {
        VerificationRequest request = new VerificationRequest(userId, "https://example.com/id.pdf", null);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        service.approve(adminId, request.getId());

        assertThat(request.getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo(adminId);
    }

    @Test
    void reject_marksRejectedWithReviewNotes() {
        VerificationRequest request = new VerificationRequest(userId, "https://example.com/id.pdf", null);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        service.reject(adminId, request.getId(), "Document was unreadable");

        assertThat(request.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(request.getReviewNotes()).isEqualTo("Document was unreadable");
    }

    @Test
    void approve_rejectsAnAlreadyReviewedRequest() {
        VerificationRequest request = new VerificationRequest(userId, "https://example.com/id.pdf", null);
        request.approve(adminId, Instant.now(clock));
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(adminId, request.getId()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("VERIFICATION_NOT_PENDING");
    }

    @Test
    void getOwnedOrAdmin_rejectsANonOwningCaller() {
        VerificationRequest request = new VerificationRequest(userId, "https://example.com/id.pdf", null);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.getOwnedOrAdmin(UUID.randomUUID(), request.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getOwnedOrAdmin_rejectsAMissingRequest() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnedOrAdmin(userId, missingId)).isInstanceOf(NotFoundException.class);
    }

    private User verifiedUser() {
        User user = new User("guest@example.com", "hashed");
        user.markEmailVerified(Instant.now(clock));
        return user;
    }
}
