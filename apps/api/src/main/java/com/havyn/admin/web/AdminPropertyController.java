package com.havyn.admin.web;

import com.havyn.admin.service.AdminPropertyService;
import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import com.havyn.properties.web.PropertyDetail;
import com.havyn.properties.web.PropertySummary;
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

/** Listing moderation — see project-docs/prompts/18-admin-platform.md. */
@RestController
@RequestMapping("/api/v1/admin/properties")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPropertyController {

    private final AdminPropertyService adminPropertyService;

    public AdminPropertyController(AdminPropertyService adminPropertyService) {
        this.adminPropertyService = adminPropertyService;
    }

    @GetMapping
    public PageResponse<PropertySummary> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(adminPropertyService.listSummaries(pageable));
    }

    @GetMapping("/{id}")
    public PropertyDetail get(@PathVariable UUID id) {
        return adminPropertyService.getDetail(id);
    }

    @PostMapping("/{id}/suspend")
    public PropertyDetail suspend(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ModerationActionRequest request) {
        return adminPropertyService.suspendDetail(principal(authentication), id, request.reason());
    }

    @PostMapping("/{id}/reject")
    public PropertyDetail reject(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ModerationActionRequest request) {
        return adminPropertyService.rejectDetail(principal(authentication), id, request.reason());
    }

    @PostMapping("/{id}/reactivate")
    public PropertyDetail reactivate(Authentication authentication, @PathVariable UUID id) {
        return adminPropertyService.reactivateDetail(principal(authentication), id);
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
