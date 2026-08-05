package com.havyn.audit.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A queryable, append-only record of every sensitive admin/moderation action — see
 * project-docs/prompts/18-admin-platform.md and database/01-data-model.md#6
 * ("captures actor, action, target type/id, before/after (jsonb), timestamp"). This
 * is the real table sessions 7/15/16 all deferred, each explicitly noting "no real
 * jsonb convention exists yet in this schema" — this is where that convention gets
 * established, using Hibernate 6's native {@code @JdbcTypeCode(SqlTypes.JSON)}
 * support (no extra dependency needed).
 *
 * {@code actorId} is a real, nullable FK (ON DELETE SET NULL — the log entry must
 * outlive the actor account being later removed). {@code targetType}/{@code targetId}
 * are a plain, polymorphic pair (no single FK target is possible across the several
 * tables admin actions touch) — matches the data model's own "polymorphic
 * actor/target" description. Never updated after creation, despite extending
 * {@link BaseEntity} (whose {@code updatedAt} simply never moves past creation).
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "target_type", nullable = false, length = 60)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before", columnDefinition = "jsonb")
    private String before;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after", columnDefinition = "jsonb")
    private String after;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(UUID actorId, String action, String targetType, UUID targetId, String before, String after) {
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.before = before;
        this.after = after;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getBefore() {
        return before;
    }

    public String getAfter() {
        return after;
    }
}
