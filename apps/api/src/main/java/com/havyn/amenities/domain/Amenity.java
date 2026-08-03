package com.havyn.amenities.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Amenity taxonomy reference data, seeded via {@code V1__init.sql}. */
@Entity
@Table(name = "amenity")
public class Amenity extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "category", length = 40)
    private String category;

    protected Amenity() {
        // JPA
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }
}
