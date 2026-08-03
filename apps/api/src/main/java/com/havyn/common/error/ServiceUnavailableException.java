package com.havyn.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a feature is intentionally, temporarily switched off — e.g. the {@code bookings_enabled} kill switch. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String code, String message) {
        super(code, HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
