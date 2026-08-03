package com.havyn.hosts.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.hosts.service.HostDashboardService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/host/dashboard")
@PreAuthorize("hasRole('HOST')")
public class HostDashboardController {

    private final HostDashboardService hostDashboardService;

    public HostDashboardController(HostDashboardService hostDashboardService) {
        this.hostDashboardService = hostDashboardService;
    }

    @GetMapping("/summary")
    public HostDashboardSummary summary(Authentication authentication) {
        return hostDashboardService.summary(principal(authentication));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
