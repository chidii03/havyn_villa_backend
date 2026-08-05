package com.havyn.properties.rayprop;

/**
 * RayProp's documented error envelope ({@code {"success": false, "error": {"code",
 * "message", "details"}}}, rayprop.io/docs — Error Handling) surfaced as a real typed
 * exception instead of letting {@link org.springframework.web.client.RestClient}'s
 * default status handling throw a generic {@code RestClientResponseException} that
 * discards the actual {@code error.code}/{@code error.message} RayProp sent.
 *
 * <p>Two documented codes get special handling in {@link RayPropClient}: {@code
 * DAILY_LIMIT_REACHED} (stop paginating, not a failure — see {@link
 * #isDailyLimitReached()}) and a plain HTTP 429 without that code (transient
 * per-second rate limiting — see {@link #isRateLimited()}, retried with backoff).
 * Everything else (401 bad key, 400 validation, 404, 500) is a real failure and
 * propagates as-is.
 */
public class RayPropApiException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;
    private final String responseBody;

    public RayPropApiException(int httpStatus, String errorCode, String message) {
        this(httpStatus, errorCode, message, "");
    }

    public RayPropApiException(int httpStatus, String errorCode, String message, String responseBody) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.responseBody = responseBody;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isDailyLimitReached() {
        return "DAILY_LIMIT_REACHED".equals(errorCode);
    }

    /** Per-second throughput limit, distinct from the daily unique-listing quota above. */
    public boolean isRateLimited() {
        return httpStatus == 429 && !isDailyLimitReached();
    }
}
