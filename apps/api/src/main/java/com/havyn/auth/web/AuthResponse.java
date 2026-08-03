package com.havyn.auth.web;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn, String tokenType, UserSummary user) {

    public static AuthResponse bearer(String accessToken, String refreshToken, long expiresIn, UserSummary user) {
        return new AuthResponse(accessToken, refreshToken, expiresIn, "Bearer", user);
    }
}
