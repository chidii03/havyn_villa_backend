package com.havyn.common.error;

import org.springframework.http.HttpStatus;

/** Thrown on state conflicts — e.g. double-booking, duplicate unique key, stale version. */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }
}
