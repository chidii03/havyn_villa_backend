package com.havyn.properties.repo;

import com.havyn.properties.domain.Availability;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findAllByProperty_IdAndDateBetweenOrderByDateAsc(UUID propertyId, LocalDate from, LocalDate to);

    Optional<Availability> findByProperty_IdAndDate(UUID propertyId, LocalDate date);
}
