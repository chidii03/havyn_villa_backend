package com.havyn.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A minimal key/value platform setting — seeded via {@code V5__booking.sql}
 * (currently just {@code commission_pct}). Doesn't extend {@link
 * com.havyn.common.persistence.BaseEntity} — its primary key is the setting name, not
 * a generated UUID. Read-only from pricing's side; the admin read/write API is
 * {@code admin.service.AdminSettingsService} (prompt 18, session 19).
 */
@Entity
@Table(name = "platform_setting")
public class PlatformSetting {

    @Id
    @Column(name = "key", nullable = false, updatable = false, length = 60)
    private String key;

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    protected PlatformSetting() {
        // JPA
    }

    public PlatformSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
