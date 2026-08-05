package com.havyn.payments.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.payments.service.PaymentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** See project-docs/prompts/13-payments.md. */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/intent")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentIntentResponse createIntent(Authentication authentication, @Valid @RequestBody CreatePaymentIntentRequest request) {
        UUID guestId = ((AuthenticatedUser) authentication.getPrincipal()).userId();
        return paymentService.createIntent(guestId, request.bookingId());
    }

    /**
     * Public — providers can't authenticate with our bearer tokens. Takes the raw
     * body (not a parsed DTO): signature verification must run over the exact bytes
     * the provider signed, which a re-serialized JSON object wouldn't guarantee.
     */
    @PostMapping("/webhook/{provider}")
    public void webhook(@PathVariable String provider, @RequestBody String rawBody, @RequestHeader HttpHeaders headers) {
        paymentService.handleWebhook(provider, rawBody, headers);
    }
}
