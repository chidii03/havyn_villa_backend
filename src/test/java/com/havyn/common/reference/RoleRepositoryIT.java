package com.havyn.common.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.havyn.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Repository/integration test exercising both Postgres (Flyway-seeded reference data
 * through Spring Data JPA) and Redis (round-trip through StringRedisTemplate), backed
 * by Testcontainers per {@link TestcontainersConfiguration}.
 *
 * <p>Requires a running Docker daemon — see apps/api/README section "Running tests".
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RoleRepositoryIT {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void flywaySeedsTheThreeAccountRoles() {
        List<Role> roles = roleRepository.findAll();

        assertThat(roles).extracting(Role::getCode)
                .containsExactlyInAnyOrder("CUSTOMER", "HOST", "ADMIN");
    }

    @Test
    void findByCodeReturnsTheSeededRole() {
        assertThat(roleRepository.findByCode("HOST"))
                .isPresent()
                .get()
                .satisfies(role -> assertThat(role.getName()).isEqualTo("Host"));

        assertThat(roleRepository.findByCode("NOT_A_ROLE")).isEmpty();
    }

    @Test
    void redisRoundTripsAValue() {
        String key = "havyn:test:role-repository-it";
        redisTemplate.opsForValue().set(key, "ok", Duration.ofMinutes(1));

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("ok");

        redisTemplate.delete(key);
    }
}
