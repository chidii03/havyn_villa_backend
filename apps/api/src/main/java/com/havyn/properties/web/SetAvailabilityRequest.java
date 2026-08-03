package com.havyn.properties.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SetAvailabilityRequest(@NotEmpty @Valid List<AvailabilityDayInput> days) {
}
