package com.havyn.common.error;

import org.springframework.http.HttpStatus;

/** Base type for domain exceptions that should map to a specific HTTP status + error code. */
public abstract class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected ApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
