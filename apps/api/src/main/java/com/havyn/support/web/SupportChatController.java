package com.havyn.support.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.support.domain.SupportChatMessage;
import com.havyn.support.service.SupportChatService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support/chat")
public class SupportChatController {

    private final SupportChatService supportChatService;

    public SupportChatController(SupportChatService supportChatService) {
        this.supportChatService = supportChatService;
    }

    @GetMapping
    public SupportChatResponse history(Authentication authentication) {
        return response(supportChatService.history(principal(authentication)));
    }

    @PostMapping
    public SupportChatResponse send(Authentication authentication, @Valid @RequestBody SupportChatRequest request) {
        return response(supportChatService.send(principal(authentication), request.message()));
    }

    private SupportChatResponse response(List<SupportChatMessage> messages) {
        if (messages.isEmpty()) {
            return new SupportChatResponse(List.of(SupportChatMessageSummary.greeting()));
        }
        return new SupportChatResponse(messages.stream().map(SupportChatMessageSummary::from).toList());
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
