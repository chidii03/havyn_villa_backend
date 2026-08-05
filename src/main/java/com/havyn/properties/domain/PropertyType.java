package com.havyn.properties.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Listing taxonomy reference data, seeded via {@code V1__init.sql}. */
@Entity
@Table(name = "property_type")
public class PropertyType extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    protected PropertyType() {
        // JPA
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
