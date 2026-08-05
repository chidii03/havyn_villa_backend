package com.havyn.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CancellationPolicyCalculatorTest {

    @ParameterizedTest
    @CsvSource({
        "FLEXIBLE, 10, 100",
        "FLEXIBLE, 1, 100",
        "FLEXIBLE, 0, 0",
        "MODERATE, 10, 100",
        "MODERATE, 5, 100",
        "MODERATE, 4, 0",
        "MODERATE, 0, 0",
        "STRICT, 10, 50",
        "STRICT, 7, 50",
        "STRICT, 6, 0",
        "STRICT, 0, 0",
        "UNKNOWN_POLICY, 30, 0",
    })
    void computesTheDocumentedRefundPercentage(String policy, long daysUntilCheckIn, int expectedPercent) {
        BigDecimal result = CancellationPolicyCalculator.refundPercentage(policy, daysUntilCheckIn);

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(expectedPercent));
    }

    @ParameterizedTest
    @CsvSource({"FLEXIBLE", "MODERATE", "STRICT"})
    void aNegativeDaysUntilCheckInNeverRefunds(String policy) {
        assertThat(CancellationPolicyCalculator.refundPercentage(policy, -5)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
