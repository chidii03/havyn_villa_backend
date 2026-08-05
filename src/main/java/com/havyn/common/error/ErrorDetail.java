package com.havyn.common.error;

/** A single field-level validation failure inside an {@link ErrorResponse}. */
public record ErrorDetail(String field, String message) {
}
