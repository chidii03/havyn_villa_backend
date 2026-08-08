package com.havyn.support.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "support_chat_message")
public class SupportChatMessage extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private SupportChatRole role;

    @Column(name = "body", nullable = false)
    private String body;

    protected SupportChatMessage() {
        // JPA
    }

    public SupportChatMessage(UUID userId, SupportChatRole role, String body) {
        this.userId = userId;
        this.role = role;
        this.body = body;
    }

    public UUID getUserId() {
        return userId;
    }

    public SupportChatRole getRole() {
        return role;
    }

    public String getBody() {
        return body;
    }
}
