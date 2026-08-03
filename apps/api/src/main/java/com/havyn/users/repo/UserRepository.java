package com.havyn.users.repo;

import com.havyn.users.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findAllByEmailContainingIgnoreCase(String email, Pageable pageable);

    long countByRoles_Code(String code);
}
