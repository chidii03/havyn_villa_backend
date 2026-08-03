package com.havyn.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.audit.domain.AuditLog;
import com.havyn.audit.repo.AuditLogRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every sensitive admin/moderation action — see
 * project-docs/prompts/18-admin-platform.md's "AuditLog writes on all sensitive admin
 * actions" constraint. Every other admin service calls {@link #record} as part of its
 * own action, in the same transaction (not fire-and-forget) — an action and its audit
 * trail either both commit or neither does.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(UUID actorId, String action, String targetType, UUID targetId, Object before, Object after) {
        auditLogRepository.save(new AuditLog(actorId, action, targetType, targetId, toJson(before), toJson(after)));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> listForTarget(String targetType, UUID targetId, Pageable pageable) {
        return auditLogRepository.findAllByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, pageable);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // An audit entry with a missing before/after snapshot is still far better
            // than failing the underlying admin action over a serialization quirk.
            log.warn("Failed to serialize audit log snapshot: {}", e.getMessage());
            return null;
        }
    }
}
