package com.havyn.admin.web;

import com.havyn.admin.service.VerificationService;
import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin KYC review queue — see project-docs/prompts/18-admin-platform.md. */
@RestController
@RequestMapping("/api/v1/admin/verification-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVerificationController {

    private final VerificationService verificationService;

    public AdminVerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping
    public PageResponse<VerificationRequestSummary> listPending(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(verificationService.listPending(pageable).map(VerificationRequestSummary::from));
    }

    @PostMapping("/{id}/approve")
    public VerificationRequestSummary approve(Authentication authentication, @PathVariable UUID id) {
        return VerificationRequestSummary.from(verificationService.approve(principal(authentication), id));
    }

    @PostMapping("/{id}/reject")
    public VerificationRequestSummary reject(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ModerationActionRequest request) {
        return VerificationRequestSummary.from(verificationService.reject(principal(authentication), id, request.reason()));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
