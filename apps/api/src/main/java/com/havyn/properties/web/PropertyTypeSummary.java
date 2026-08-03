package com.havyn.properties.web;

import com.havyn.properties.domain.PropertyType;

public record PropertyTypeSummary(String code, String name) {

    public static PropertyTypeSummary from(PropertyType type) {
        return new PropertyTypeSummary(type.getCode(), type.getName());
    }
}
