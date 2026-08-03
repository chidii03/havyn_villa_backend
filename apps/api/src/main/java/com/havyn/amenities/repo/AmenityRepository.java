package com.havyn.amenities.repo;

import com.havyn.amenities.domain.Amenity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmenityRepository extends JpaRepository<Amenity, UUID> {

    List<Amenity> findAllByCodeInIgnoreCase(Set<String> codes);

    List<Amenity> findAllByOrderByCategoryAscNameAsc();
}
