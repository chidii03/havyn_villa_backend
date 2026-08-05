package com.havyn.admin.web;

import com.havyn.admin.service.DisputeService;
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

/** Admin dispute resolution queue — see project-docs/prompts/18-admin-platform.md. */
@RestController
@RequestMapping("/api/v1/admin/disputes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDisputeController {

    private final DisputeService disputeService;

    public AdminDisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping
    public PageResponse<DisputeSummary> listOpen(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(disputeService.listOpen(pageable).map(DisputeSummary::from));
    }

    @PostMapping("/{id}/resolve")
    public DisputeSummary resolve(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ModerationActionRequest request) {
        return DisputeSummary.from(disputeService.resolve(principal(authentication), id, request.reason()));
    }

    @PostMapping("/{id}/dismiss")
    public DisputeSummary dismiss(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ModerationActionRequest request) {
        return DisputeSummary.from(disputeService.dismiss(principal(authentication), id, request.reason()));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
