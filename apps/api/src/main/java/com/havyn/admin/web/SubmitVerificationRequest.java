package com.havyn.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitVerificationRequest(@NotBlank String documentUrl, @Size(max = 2000) String notes) {
}
