package com.havyn.auth.web;

/** Body carrying the refresh token for mobile clients. Web clients use the httpOnly cookie instead. */
public record RefreshRequest(String refreshToken) {
}
