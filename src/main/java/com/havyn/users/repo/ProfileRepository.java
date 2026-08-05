package com.havyn.users.repo;

import com.havyn.users.domain.Profile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Optional<Profile> findByUser_Id(UUID userId);

    List<Profile> findAllByUser_IdIn(Collection<UUID> userIds);
}
