package com.havyn.properties.web;

import com.havyn.properties.repo.PropertyTypeRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public property type taxonomy — see project-docs/architecture/03-api-design.md. */
@RestController
@RequestMapping("/api/v1/property-types")
public class PropertyTypeController {

    private final PropertyTypeRepository propertyTypeRepository;

    public PropertyTypeController(PropertyTypeRepository propertyTypeRepository) {
        this.propertyTypeRepository = propertyTypeRepository;
    }

    @GetMapping
    public List<PropertyTypeSummary> list() {
        return propertyTypeRepository.findAllByOrderByNameAsc().stream()
                .map(PropertyTypeSummary::from)
                .toList();
    }
}
