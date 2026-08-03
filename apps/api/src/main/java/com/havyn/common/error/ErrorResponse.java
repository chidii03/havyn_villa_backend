package com.havyn.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The API's consistent error envelope:
 * {@code { "error": { "code", "message", "details": [...], "traceId" } } }.
 * See project-docs/architecture/03-api-design.md.
 */
public record ErrorResponse(Body error) {

    public static ErrorResponse of(String code, String message, List<ErrorDetail> details, String traceId) {
        return new ErrorResponse(new Body(code, message, details, traceId));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, List<ErrorDetail> details, String traceId) {
    }
}
