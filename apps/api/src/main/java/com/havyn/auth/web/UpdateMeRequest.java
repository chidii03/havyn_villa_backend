package com.havyn.auth.web;

import jakarta.validation.constraints.Size;

/** Partial update — null fields are left unchanged. */
public record UpdateMeRequest(@Size(max = 120) String fullName, @Size(max = 30) String phone) {
}
