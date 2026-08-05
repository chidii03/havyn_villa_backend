package com.havyn.common.error;

import org.springframework.http.HttpStatus;

/** Thrown for malformed/semantically invalid requests that Bean Validation doesn't catch. */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(code, HttpStatus.BAD_REQUEST, message);
    }
}
