package com.havyn.properties.rayprop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * REST client for https://rayprop.io/docs. Sandbox keys ({@code rp_sandbox_*}) only
 * grant GET access (search/availability/quotes) — this client never calls a
 * write endpoint, matching what this integration actually needs (import, not booking).
 *
 * <p><strong>Pagination is page-based, not cursor-based</strong> — confirmed directly
 * against the live docs (Pagination page): {@code page}/{@code limit} request params,
 * {@code meta.hasMore} response field, {@code limit} silently capped at 50 server-side.
 * There is no {@code next_cursor} anywhere in RayProp's API.
 *
 * <p><strong>The real ceiling is a daily unique-listing quota, not a page-depth
 * limit.</strong> Every {@code /listings} response carries a {@code dataAccess} block
 * ({@code accessedToday}/{@code dailyLimit}/{@code remainingToday}); RayProp's docs
 * state plainly: "Sandbox uses Free tier limits" (500 unique listings/day), scaling up
 * to 5,000/day only on their paid Growth tier with a live key. Hitting that cap returns
 * a documented {@code DAILY_LIMIT_REACHED} error — handled here as a graceful stop
 * ({@link RayPropFetchResult#stoppedEarlyDueToQuota()}), not a failure. No amount of
 * extra pagination, city iteration, or additional endpoints changes this: the quota is
 * account-wide, and RayProp has no other listing-returning endpoint (Availability/
 * Quotes/Holds/Reservations/Payments/Reviews are booking-flow endpoints, not inventory
 * discovery) — see project-docs's RayProp integration notes for the full audit.
 *
 * <p>Deliberately synchronous ({@link RestClient}, not {@code WebClient}) — this is an
 * admin-triggered, sequential sync job, not a high-throughput streaming consumer,  and
 * RayProp's own rate limit (5 req/sec on sandbox) means added parallelism would only
 * reach the account-wide daily quota faster, not retrieve more inventory. WebClient's
 * reactive overhead buys nothing here.
 */
@Component
public class RayPropClient {

    private static final Logger log = LoggerFactory.getLogger(RayPropClient.class);

    /** RayProp's rate-limit section documents 429 as transient (per-second) — worth one retry, not a real failure. */
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final long[] RETRY_BACKOFF_MS = {500, 1500, 3000};
    /** Guards against an implausible/clock-skewed X-RateLimit-Reset turning into an hours-long wait. */
    private static final long MAX_BACKOFF_MS = 10_000;

    private final RestClient restClient;
    private final RayPropProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Takes the Spring Boot auto-configured {@link RestClient.Builder} — same reasoning
     * as {@code PaystackPaymentProvider}: lets a test bind a {@code MockRestServiceServer}
     * to this exact client without a live RayProp account.
     */
    public RayPropClient(RestClient.Builder restClientBuilder, RayPropProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-api-key", properties.getApiKey())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .requestInterceptors(interceptors -> {
                    log.info("RayProp RestClient interceptors before diagnostic logger: {}", interceptorNames(interceptors));
                    interceptors.add((request, body, execution) -> {
                        log.info(
                                "RayProp outgoing request: method={} uri={} headers={}",
                                request.getMethod(), request.getURI(), redactedHeaders(request.getHeaders()));
                        return execution.execute(request, body);
                    });
                    log.info("RayProp RestClient interceptors after diagnostic logger: {}", interceptorNames(interceptors));
                })
                .build();
    }

    /**
     * Walks every page of {@code GET /listings} up to {@code havyn.rayprop.max-pages},
     * stopping when RayProp's own {@code meta.hasMore} says there's nothing left, or
     * earlier if the account's daily unique-listing quota is reached first (the more
     * likely stop condition in practice — see the class doc).
     */
    public RayPropFetchResult fetchAllListings() {
        List<RayPropListing> listings = new ArrayList<>();
        RayPropDataAccess lastDataAccess = null;
        int pagesFetched = 0;
        String cursor = null;

        while (pagesFetched < properties.getMaxPages()) {
            RayPropPageResult result;
            try {
                result = fetchPage(cursor, pagesFetched + 1);
            } catch (RayPropApiException ex) {
                if (ex.isDailyLimitReached()) {
                    log.warn(
                            "RayProp sync: daily unique-listing quota reached after {} page(s), {} listings fetched — stopping early (resets midnight UTC). {}",
                            pagesFetched, listings.size(), ex.getMessage());
                    return new RayPropFetchResult(listings, pagesFetched, true, lastDataAccess);
                }
                throw ex; // auth/validation/server errors are real failures — let them propagate
            }

            listings.addAll(result.listings());
            pagesFetched++;
            lastDataAccess = result.dataAccess() != null ? result.dataAccess() : lastDataAccess;
            int page = pagesFetched;
            if (result.dataAccess() != null) {
                log.info(
                        "RayProp sync: page {} fetched — {} listings so far, daily quota {}/{} used",
                        pagesFetched, listings.size(), result.dataAccess().accessedToday(), result.dataAccess().dailyLimit());
            } else {
                log.info("RayProp sync: page {} fetched — {} listings so far", page, listings.size());
            }

            if (!result.hasMore()) {
                return new RayPropFetchResult(listings, pagesFetched, false, lastDataAccess);
            }
            cursor = result.nextCursor();
        }

        log.warn(
                "RayProp sync: hit the configured safety cap of {} pages ({} listings) with more pages still available — raise havyn.rayprop.max-pages if the daily quota allows it",
                properties.getMaxPages(), listings.size());
        return new RayPropFetchResult(listings, pagesFetched, false, lastDataAccess);
    }

    private RayPropPageResult fetchPage(String cursor, int pageNumber) {
        for (int attempt = 1; attempt <= MAX_RATE_LIMIT_RETRIES; attempt++) {
            try {
                return doFetchPage(cursor, pageNumber);
            } catch (RayPropApiException ex) {
                if (!ex.isRateLimited() || attempt == MAX_RATE_LIMIT_RETRIES) {
                    throw ex;
                }
                long backoffMs = RETRY_BACKOFF_MS[Math.min(attempt - 1, RETRY_BACKOFF_MS.length - 1)];
                log.warn("RayProp sync: rate limited fetching page {} (attempt {}/{}), retrying in {}ms",
                        pageNumber, attempt, MAX_RATE_LIMIT_RETRIES, backoffMs);
                sleep(backoffMs);
            }
        }
        // Unreachable — the loop always either returns or throws on its final attempt.
        throw new IllegalStateException("Unreachable");
    }

    private RayPropPageResult doFetchPage(String cursor, int pageNumber) {
        return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(properties.getListingsPath()).queryParam("limit", properties.getPageSize());
                    if (cursor != null && !cursor.isBlank()) {
                        builder.queryParam("cursor", cursor);
                    } else if (pageNumber > 1) {
                        builder.queryParam("page", pageNumber);
                    }
                    return builder.build();
                })
                .exchange((request, response) -> {
                    JsonNode body = response.bodyTo(JsonNode.class);
                    if (body == null) {
                        body = MissingNode.getInstance();
                    }
                    if (response.getStatusCode().isError()) {
                        String code = body.path("error").path("code").asText(response.getStatusCode().toString());
                        String message = body.path("error").path("message").asText("RayProp API request failed");
                        String responseBody = rawBody(body);
                        log.warn(
                                "RayProp API request failed: status={} code={} body={}",
                                response.getStatusCode().value(), code, responseBody);
                        throw new RayPropApiException(response.getStatusCode().value(), code, message, responseBody);
                    }
                    logRateLimitHeadersIfLow(response.getHeaders());
                    return RayPropPageResult.from(body);
                });
    }

    private void logRateLimitHeadersIfLow(HttpHeaders headers) {
        String limitHeader = headers.getFirst("X-RateLimit-Limit");
        String remainingHeader = headers.getFirst("X-RateLimit-Remaining");
        if (limitHeader == null || remainingHeader == null) {
            return;
        }
        try {
            int limit = Integer.parseInt(limitHeader);
            int remaining = Integer.parseInt(remainingHeader);
            if (limit > 0 && remaining <= limit / 5) { // proactively flag the last 20% of the per-second budget
                log.warn("RayProp sync: rate limit budget running low — {}/{} remaining this window", remaining, limit);
            }
        } catch (NumberFormatException ignored) {
            // Header present but not a plain integer — not worth failing the sync over a logging nicety.
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(Math.min(millis, MAX_BACKOFF_MS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off a RayProp rate limit retry", e);
        }
    }

    /** One page's worth of {@code GET /listings} — internal to this class, {@link RayPropFetchResult} is the public shape. */
    private record RayPropPageResult(List<RayPropListing> listings, String nextCursor, boolean hasMore, RayPropDataAccess dataAccess) {
        static RayPropPageResult from(JsonNode body) {
            List<RayPropListing> listings = new ArrayList<>();
            for (JsonNode listingNode : body.path("data")) {
                listings.add(RayPropListing.from(listingNode));
            }
            String nextCursor = body.path("next_cursor").asText(null);
            boolean hasMore = (nextCursor != null && !nextCursor.isBlank())
                    || body.path("meta").path("hasMore").asBoolean(false);
            return new RayPropPageResult(listings, nextCursor, hasMore, RayPropDataAccess.from(body));
        }
    }

    private String rawBody(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return "<empty>";
        }
        try {
            String value = objectMapper.writeValueAsString(body);
            return value.length() <= 2_000 ? value : value.substring(0, 2_000) + "...";
        } catch (Exception ignored) {
            return body.toString();
        }
    }

    private static List<String> interceptorNames(List<?> interceptors) {
        return interceptors.stream()
                .map(interceptor -> interceptor.getClass().getName())
                .toList();
    }

    private static Map<String, List<String>> redactedHeaders(HttpHeaders headers) {
        Map<String, List<String>> redacted = new java.util.LinkedHashMap<>();
        headers.forEach((name, values) -> redacted.put(name, values.stream()
                .map(value -> redactedHeaderValue(name, value))
                .toList()));
        return redacted;
    }

    private static String redactedHeaderValue(String name, String value) {
        if (value == null) {
            return "<null>";
        }
        if ("x-api-key".equalsIgnoreCase(name)) {
            return redactedSecret(value);
        }
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
            String prefix = "Bearer ";
            if (value.startsWith(prefix)) {
                return prefix + redactedSecret(value.substring(prefix.length()));
            }
            return redactedSecret(value);
        }
        return value;
    }

    private static String redactedSecret(String value) {
        int prefixLength = Math.min(5, value.length());
        String prefix = value.substring(0, prefixLength);
        return "<redacted length=" + value.length() + " prefix=" + prefix + ">";
    }
}
