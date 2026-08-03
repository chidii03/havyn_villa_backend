package com.havyn.properties.repo;

import com.havyn.properties.domain.PropertyType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyTypeRepository extends JpaRepository<PropertyType, UUID> {

    Optional<PropertyType> findByCodeIgnoreCase(String code);

    List<PropertyType> findAllByOrderByNameAsc();
}
