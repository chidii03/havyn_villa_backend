package com.havyn.admin.web;

import jakarta.validation.constraints.NotBlank;

public record UpdateSettingRequest(@NotBlank String value) {
}
