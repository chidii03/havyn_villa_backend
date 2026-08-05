package com.havyn.properties.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PropertyStatusTest {

    @ParameterizedTest
    @CsvSource({
        "DRAFT, PENDING, true",
        "DRAFT, ACTIVE, false",
        "DRAFT, SUSPENDED, false",
        "PENDING, ACTIVE, true",
        "PENDING, DRAFT, true",
        "PENDING, SUSPENDED, false",
        "ACTIVE, SUSPENDED, true",
        "ACTIVE, PENDING, false",
        "ACTIVE, DRAFT, false",
        "SUSPENDED, ACTIVE, true",
        "SUSPENDED, DRAFT, false",
        "SUSPENDED, PENDING, false",
    })
    void enforcesTheDocumentedLifecycle(PropertyStatus from, PropertyStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @EnumSource(PropertyStatus.class)
    void noStatusTransitionsToItself(PropertyStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }
}
