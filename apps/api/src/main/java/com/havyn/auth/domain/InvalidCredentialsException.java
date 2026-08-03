package com.havyn.auth.domain;

import com.havyn.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Deliberately identical for "no such user" and "wrong password" — see
 * project-docs/security/01-security-plan.md (no account-enumeration leaks).
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
