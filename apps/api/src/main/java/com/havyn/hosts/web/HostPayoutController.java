package com.havyn.hosts.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import com.havyn.payments.service.PaymentService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Payout history for the authenticated host — see project-docs/prompts/17-host-dashboard.md. */
@RestController
@RequestMapping("/api/v1/host/payouts")
@PreAuthorize("hasRole('HOST')")
public class HostPayoutController {

    private final PaymentService paymentService;

    public HostPayoutController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public PageResponse<PayoutSummary> list(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(paymentService.listPayoutsForHost(principal(authentication), pageable).map(PayoutSummary::from));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
