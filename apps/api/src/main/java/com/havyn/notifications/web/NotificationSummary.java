package com.havyn.notifications.web;

import com.havyn.notifications.domain.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationSummary(UUID id, String type, String title, String body, UUID linkId, Instant readAt, Instant createdAt) {

    public static NotificationSummary from(Notification notification) {
        return new NotificationSummary(
                notification.getId(), notification.getType().name(), notification.getTitle(), notification.getBody(),
                notification.getLinkId(), notification.getReadAt(), notification.getCreatedAt());
    }
}
