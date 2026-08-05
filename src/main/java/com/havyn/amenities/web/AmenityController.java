package com.havyn.amenities.web;

import com.havyn.amenities.repo.AmenityRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public amenity taxonomy — see project-docs/architecture/03-api-design.md. */
@RestController
@RequestMapping("/api/v1/amenities")
public class AmenityController {

    private final AmenityRepository amenityRepository;

    public AmenityController(AmenityRepository amenityRepository) {
        this.amenityRepository = amenityRepository;
    }

    @GetMapping
    public List<AmenitySummary> list() {
        return amenityRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .map(AmenitySummary::from)
                .toList();
    }
}
