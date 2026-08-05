package com.havyn.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestPasswordResetRequest(@NotBlank @Email String email) {
}
