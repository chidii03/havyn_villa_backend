package com.havyn.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a requested resource does not exist (or is not visible to the caller). */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " " + id + " not found");
    }
}
