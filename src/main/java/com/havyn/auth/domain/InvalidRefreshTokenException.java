package com.havyn.auth.domain;

import com.havyn.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

    private InvalidRefreshTokenException(String code, String message) {
        super(code, HttpStatus.UNAUTHORIZED, message);
    }

    public static InvalidRefreshTokenException expiredOrRevoked() {
        return new InvalidRefreshTokenException("INVALID_REFRESH_TOKEN", "Session expired or already signed out");
    }

    /**
     * A refresh token was presented that had already been rotated away — the classic
     * signal that a token was stolen and used by two parties. The entire session
     * family is revoked by the caller before this is thrown.
     */
    public static InvalidRefreshTokenException reuseDetected() {
        return new InvalidRefreshTokenException(
                "REFRESH_TOKEN_REUSE_DETECTED", "This session was revoked for your security — please sign in again");
    }
}
