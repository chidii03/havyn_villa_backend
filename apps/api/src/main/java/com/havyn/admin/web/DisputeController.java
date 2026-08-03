package com.havyn.admin.web;

import com.havyn.admin.domain.Dispute;
import com.havyn.admin.service.DisputeService;
import com.havyn.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant-facing dispute raising — see project-docs/prompts/18-admin-platform.md.
 * Nested under {@code /bookings/{bookingId}/disputes} for a clean REST shape even
 * though the controller class lives in {@code admin.web} — same "URL reflects the
 * caller, package reflects domain ownership" reasoning as
 * {@code VerificationRequestController}. Not under {@code /api/v1/properties/**}, so
 * no SecurityConfig carve-out is needed — the default authenticated fallback applies.
 */
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/disputes")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping
    public ResponseEntity<DisputeSummary> raise(
            Authentication authentication, @PathVariable UUID bookingId, @Valid @RequestBody RaiseDisputeRequest request) {
        Dispute dispute = disputeService.raise(principal(authentication), bookingId, request.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(DisputeSummary.from(dispute));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
