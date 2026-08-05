package com.havyn.admin.web;

import com.havyn.admin.service.AdminSettingsService;
import com.havyn.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Commission/platform settings — see project-docs/prompts/18-admin-platform.md. */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;

    public AdminSettingsController(AdminSettingsService adminSettingsService) {
        this.adminSettingsService = adminSettingsService;
    }

    @GetMapping
    public List<PlatformSettingSummary> list() {
        return adminSettingsService.list().stream().map(PlatformSettingSummary::from).toList();
    }

    @PutMapping("/{key}")
    public PlatformSettingSummary update(Authentication authentication, @PathVariable String key, @Valid @RequestBody UpdateSettingRequest request) {
        return PlatformSettingSummary.from(adminSettingsService.update(principal(authentication), key, request.value()));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
