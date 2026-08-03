package com.havyn.auth.domain;

import com.havyn.users.domain.User;

/** Everything a controller needs to build the AuthResponse DTO + the refresh cookie. */
public record AuthResult(String accessToken, String refreshToken, long expiresInSeconds, User user) {
}
