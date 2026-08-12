package com.havyn.admin.web;

import com.havyn.admin.domain.VerificationRequest;
import com.havyn.admin.service.VerificationService;
import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Host-facing KYC submission — see project-docs/prompts/18-admin-platform.md. Lives in
 * the {@code admin} package (not {@code hosts}) because {@code VerificationRequest}'s
 * lifecycle is admin-owned (review/approve/reject) — same reasoning
 * {@code HostListingController} lives in {@code properties.web}, not {@code hosts.web}:
 * the URL prefix reflects who calls it, the Java package reflects who owns the domain.
 */
@RestController
@RequestMapping("/api/v1/host/verification-requests")
@PreAuthorize("isAuthenticated()")
public class VerificationRequestController {

    private final VerificationService verificationService;

    public VerificationRequestController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping
    public ResponseEntity<VerificationRequestSummary> submit(
            Authentication authentication, @Valid @RequestBody SubmitVerificationRequest request) {
        VerificationRequest saved = verificationService.submit(principal(authentication), request.documentUrl(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(VerificationRequestSummary.from(saved));
    }

    @GetMapping
    public PageResponse<VerificationRequestSummary> listOwn(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(verificationService.listOwn(principal(authentication), pageable).map(VerificationRequestSummary::from));
    }

    @GetMapping("/{id}")
    public VerificationRequestSummary get(Authentication authentication, @PathVariable UUID id) {
        return VerificationRequestSummary.from(verificationService.getOwnedOrAdmin(principal(authentication), id));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
