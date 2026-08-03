package com.havyn.common.reference;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * RBAC role reference data (GUEST, CUSTOMER, HOST, ADMIN), seeded via
 * {@code V1__init.sql}. See CLAUDE.md#roles.
 */
@Entity
@Table(name = "role")
public class Role extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    protected Role() {
        // JPA
    }

    public Role(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
