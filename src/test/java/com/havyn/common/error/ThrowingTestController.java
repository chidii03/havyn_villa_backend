package com.havyn.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller (src/test, never shipped) used purely to exercise
 * {@link GlobalExceptionHandler} end to end through a real dispatcher/MockMvc.
 */
@RestController
@RequestMapping("/__test/errors")
class ThrowingTestController {

    @PostMapping("/not-found")
    void notFound() {
        throw new NotFoundException("Widget 123 not found");
    }

    @PostMapping("/conflict")
    void conflict() {
        throw new ConflictException("BOOKING_UNAVAILABLE", "Those dates are no longer available");
    }

    @PostMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("boom");
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody ValidatedBody body) {
        // no-op — reaching here means validation passed
    }

    record ValidatedBody(@NotBlank String name) {
    }
}
