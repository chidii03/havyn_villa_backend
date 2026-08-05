package com.havyn.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RaiseDisputeRequest(@NotBlank @Size(max = 4000) String reason) {
}
