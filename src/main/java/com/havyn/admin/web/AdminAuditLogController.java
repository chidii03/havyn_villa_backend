package com.havyn.admin.web;

import com.havyn.audit.service.AuditLogService;
import com.havyn.common.web.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Browsing the audit trail itself is a real admin capability, not just a write-only log — see project-docs/prompts/18-admin-platform.md. */
@RestController
@RequestMapping("/api/v1/admin/audit-log")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public PageResponse<AuditLogSummary> list(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @PageableDefault(size = 20) Pageable pageable) {
        if (targetType != null && targetId != null) {
            return PageResponse.of(auditLogService.listForTarget(targetType, targetId, pageable).map(AuditLogSummary::from));
        }
        return PageResponse.of(auditLogService.list(pageable).map(AuditLogSummary::from));
    }
}
