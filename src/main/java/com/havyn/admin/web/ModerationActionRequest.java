package com.havyn.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModerationActionRequest(@NotBlank @Size(max = 2000) String reason) {
}
