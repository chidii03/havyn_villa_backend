package com.havyn.notifications.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import com.havyn.notifications.service.NotificationService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** In-app notification inbox — auth-required, not under {@code /api/v1/properties/**}, so no SecurityConfig carve-out needed. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationSummary> list(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(notificationService.listForUser(principal(authentication), pageable).map(NotificationSummary::from));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("unreadCount", notificationService.unreadCount(principal(authentication)));
    }

    @PostMapping("/{id}/read")
    public void markRead(Authentication authentication, @PathVariable UUID id) {
        notificationService.markRead(principal(authentication), id);
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
