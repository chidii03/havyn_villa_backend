package com.havyn.common.error;

import org.springframework.http.HttpStatus;

/** Thrown for object-level authorization failures (e.g. a host editing another host's listing). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", HttpStatus.FORBIDDEN, message);
    }
}
