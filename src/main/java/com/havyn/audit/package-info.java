/**
 * Polymorphic AuditLog (actor, action, target type/id, before/after jsonb, timestamp)
 * for admin/moderation/payment-sensitive actions. Implemented in prompt 18 (session 19)
 * — see project-docs/prompts/18-admin-platform.md. {@code admin.service.*} calls
 * {@code AuditLogService#record} as part of every sensitive action's own transaction.
 */
package com.havyn.audit;
