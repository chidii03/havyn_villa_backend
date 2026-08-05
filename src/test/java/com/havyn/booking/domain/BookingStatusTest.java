package com.havyn.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class BookingStatusTest {

    @ParameterizedTest
    @CsvSource({
        "PENDING, CONFIRMED, true",
        "PENDING, CANCELLED, true",
        "PENDING, COMPLETED, false",
        "PENDING, REFUNDED, false",
        "CONFIRMED, CANCELLED, true",
        "CONFIRMED, COMPLETED, true",
        "CONFIRMED, REFUNDED, true",
        "CONFIRMED, PENDING, false",
        "CANCELLED, PENDING, false",
        "CANCELLED, CONFIRMED, false",
        "COMPLETED, REFUNDED, true",
        "COMPLETED, CANCELLED, false",
        "REFUNDED, CANCELLED, false",
        "REFUNDED, CONFIRMED, false",
    })
    void enforcesTheDocumentedLifecycle(BookingStatus from, BookingStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    void noStatusTransitionsToItself(BookingStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"PENDING, true", "CONFIRMED, true", "COMPLETED, true", "CANCELLED, false", "REFUNDED, false"})
    void isActiveMatchesTheExclusionConstraintsStatusSet(BookingStatus status, boolean active) {
        // Must stay in lockstep with V5__booking.sql's EXCLUDE ... WHERE (status IN (...)).
        assertThat(status.isActive()).isEqualTo(active);
    }
}
