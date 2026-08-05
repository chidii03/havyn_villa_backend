package com.havyn.amenities.web;

import com.havyn.amenities.domain.Amenity;

public record AmenitySummary(String code, String name, String category) {

    public static AmenitySummary from(Amenity amenity) {
        return new AmenitySummary(amenity.getCode(), amenity.getName(), amenity.getCategory());
    }
}
