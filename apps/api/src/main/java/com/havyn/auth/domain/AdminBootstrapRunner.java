package com.havyn.auth.domain;

import com.havyn.common.reference.Role;
import com.havyn.common.reference.RoleRepository;
import com.havyn.users.domain.Profile;
import com.havyn.users.domain.User;
import com.havyn.users.repo.ProfileRepository;
import com.havyn.users.repo.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(
            AdminBootstrapProperties properties,
            UserRepository userRepository,
            ProfileRepository profileRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || isBlank(properties.email()) || isBlank(properties.password())) {
            return;
        }

        String email = properties.email().trim().toLowerCase();
        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role must be seeded"));
        Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role must be seeded"));

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User created = new User(email, passwordEncoder.encode(properties.password()));
            created.addRole(customerRole);
            created.markEmailVerified(Instant.now());
            User saved = userRepository.save(created);
            profileRepository.save(new Profile(saved, fullName(email)));
            log.info("Bootstrap admin account created userId={}", saved.getId());
            return saved;
        });

        if (!user.isEmailVerified()) {
            user.markEmailVerified(Instant.now());
        }
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.addRole(customerRole);
        user.addRole(adminRole);
        log.info("Bootstrap admin role ensured userId={}", user.getId());
    }

    private String fullName(String email) {
        if (!isBlank(properties.fullName())) {
            return properties.fullName().trim();
        }
        return email;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
