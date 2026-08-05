package com.havyn.admin.web;

import com.havyn.audit.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogSummary(UUID id, UUID actorId, String action, String targetType, UUID targetId, String before, String after, Instant createdAt) {

    public static AuditLogSummary from(AuditLog auditLog) {
        return new AuditLogSummary(
                auditLog.getId(), auditLog.getActorId(), auditLog.getAction(), auditLog.getTargetType(), auditLog.getTargetId(),
                auditLog.getBefore(), auditLog.getAfter(), auditLog.getCreatedAt());
    }
}
