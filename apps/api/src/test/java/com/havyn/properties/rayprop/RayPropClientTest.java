package com.havyn.properties.rayprop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Real HTTP request-shaping against a mocked server — see {@code
 * PaystackPaymentProviderTest} for the same pattern — plus the pagination/quota/retry
 * behavior documented in {@link RayPropClient}'s class doc, all verified against the
 * exact response shapes quoted from rayprop.io/docs (no live RayProp account needed,
 * and none is available in this environment — see the class doc's evidence trail).
 */
class RayPropClientTest {

    private MockRestServiceServer mockServer;
    private RayPropClient client;
    private RayPropProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RayPropProperties();
        properties.setApiKey("rp_sandbox_test_key");
        properties.setBaseUrl("https://api.rayprop.io");
        properties.setPageSize(50);
        properties.setMaxPages(20);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new RayPropClient(builder, properties, new ObjectMapper());
    }

    @Test
    void fetchAllListings_sendsTheApiKeyHeaderAndDocumentedQueryParams() {
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "rp_sandbox_test_key"))
                .andRespond(withSuccess(page(java.util.List.of(listingJson("rp_lst_1")), false, null), MediaType.APPLICATION_JSON));

        RayPropFetchResult result = client.fetchAllListings();

        assertThat(result.listings()).hasSize(1);
        assertThat(result.listings().get(0).id()).isEqualTo("rp_lst_1");
        mockServer.verify();
    }

    @Test
    void fetchAllListings_stopsAssoonAsHasMoreIsFalse_withoutRequestingANextPage() {
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50"))
                .andRespond(withSuccess(page(java.util.List.of(listingJson("rp_lst_1")), false, null), MediaType.APPLICATION_JSON));

        RayPropFetchResult result = client.fetchAllListings();

        assertThat(result.pagesFetched()).isEqualTo(1);
        assertThat(result.stoppedEarlyDueToQuota()).isFalse();
        mockServer.verify(); // fails if a page-2 request was ever made
    }

    @Test
    void fetchAllListings_walksEveryPage_untilHasMoreIsFalse() {
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50"))
                .andRespond(withSuccess(cursorPage(java.util.List.of(listingJson("rp_lst_1")), "cursor_1", null), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50&cursor=cursor_1"))
                .andRespond(withSuccess(cursorPage(java.util.List.of(listingJson("rp_lst_2")), "cursor_2", null), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50&cursor=cursor_2"))
                .andRespond(withSuccess(page(java.util.List.of(listingJson("rp_lst_3")), false, null), MediaType.APPLICATION_JSON));

        RayPropFetchResult result = client.fetchAllListings();

        assertThat(result.listings()).extracting(RayPropListing::id).containsExactly("rp_lst_1", "rp_lst_2", "rp_lst_3");
        assertThat(result.pagesFetched()).isEqualTo(3);
        assertThat(result.stoppedEarlyDueToQuota()).isFalse();
        mockServer.verify();
    }

    @Test
    void fetchAllListings_stopsAtTheConfiguredSafetyCap_ifHasMoreNeverGoesFalse() {
        properties.setMaxPages(2);
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50"))
                .andRespond(withSuccess(page(java.util.List.of(listingJson("rp_lst_1")), true, null), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50&page=2"))
                .andRespond(withSuccess(page(java.util.List.of(listingJson("rp_lst_2")), true, null), MediaType.APPLICATION_JSON));

        RayPropFetchResult result = client.fetchAllListings();

        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.listings()).hasSize(2);
        mockServer.verify(); // fails if a page-3 request was made past the cap
    }

    @Test
    void fetchAllListings_capturesTheDailyQuotaDataAccessBlock() {
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50"))
                .andRespond(withSuccess(page(java.util.List.of(listingJson("rp_lst_1")), false, dataAccessJson(45, 500)),
                        MediaType.APPLICATION_JSON));

        RayPropFetchResult result = client.fetchAllListings();

        assertThat(result.lastDataAccess()).isNotNull();
        assertThat(result.lastDataAccess().accessedToday()).isEqualTo(45);
        assertThat(result.lastDataAccess().dailyLimit()).isEqualTo(500);
        assertThat(result.lastDataAccess().remainingToday()).isEqualTo(455);
    }

    /**
     * The exact scenario this whole audit was about: RayProp's docs say hitting the
     * daily unique-listing quota mid-walk returns a documented {@code
     * DAILY_LIMIT_REACHED} error — that must stop pagination gracefully and keep
     * whatever was already fetched, not fail the whole sync.
     */
    @Test
    void fetchAllListings_stopsGracefullyAndKeepsPriorPages_onDailyLimitReached() {
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50"))
                .andRespond(withSuccess(page(java.util.List.of(listingJson("rp_lst_1")), true, dataAccessJson(500, 500)),
                        MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50&page=2"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("DAILY_LIMIT_REACHED", "Daily unique-listing limit reached")));

        RayPropFetchResult result = client.fetchAllListings();

        assertThat(result.listings()).extracting(RayPropListing::id).containsExactly("rp_lst_1");
        assertThat(result.pagesFetched()).isEqualTo(1);
        assertThat(result.stoppedEarlyDueToQuota()).isTrue();
        mockServer.verify();
    }

    @Test
    void fetchAllListings_propagatesARealFailure_insteadOfSwallowingIt() {
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("INVALID_API_KEY", "Invalid or revoked API key")));

        assertThatThrownBy(() -> client.fetchAllListings())
                .isInstanceOf(RayPropApiException.class)
                .satisfies(ex -> {
                    RayPropApiException apiEx = (RayPropApiException) ex;
                    assertThat(apiEx.getHttpStatus()).isEqualTo(401);
                    assertThat(apiEx.getErrorCode()).isEqualTo("INVALID_API_KEY");
                    assertThat(apiEx.getResponseBody()).contains("INVALID_API_KEY");
                    assertThat(apiEx.isDailyLimitReached()).isFalse();
                });
    }

    /** A plain 429 (no DAILY_LIMIT_REACHED code) is the documented per-second throttle — transient, worth retrying. */
    @Test
    void fetchAllListings_retriesATransientRateLimit_andSucceedsOnTheNextAttempt() {
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson("RATE_LIMIT_EXCEEDED", "Too many requests per second")));
        mockServer.expect(requestTo("https://api.rayprop.io/listings?limit=50"))
                .andRespond(withSuccess(page(java.util.List.of(listingJson("rp_lst_1")), false, null), MediaType.APPLICATION_JSON));

        RayPropFetchResult result = client.fetchAllListings();

        assertThat(result.listings()).extracting(RayPropListing::id).containsExactly("rp_lst_1");
        mockServer.verify();
    }

    private static String page(java.util.List<String> listingsJson, boolean hasMore, String dataAccessJson) {
        String dataAccessField = dataAccessJson != null ? ",\"dataAccess\":" + dataAccessJson : "";
        return "{\"success\":true,\"data\":[" + String.join(",", listingsJson) + "],"
                + "\"meta\":{\"hasMore\":" + hasMore + "}"
                + dataAccessField + "}";
    }

    private static String cursorPage(java.util.List<String> listingsJson, String nextCursor, String dataAccessJson) {
        String dataAccessField = dataAccessJson != null ? ",\"dataAccess\":" + dataAccessJson : "";
        return "{\"success\":true,\"data\":[" + String.join(",", listingsJson) + "],"
                + "\"next_cursor\":\"" + nextCursor + "\""
                + dataAccessField + "}";
    }

    private static String listingJson(String id) {
        return "{\"id\":\"" + id + "\",\"title\":\"Test listing\",\"city\":\"Lagos\",\"state\":\"Lagos\","
                + "\"bedrooms\":2,\"bathrooms\":2,\"max_guests\":4,\"price_per_night\":60000,\"currency\":\"NGN\","
                + "\"listing_images\":[]}";
    }

    private static String dataAccessJson(int accessedToday, int dailyLimit) {
        return "{\"accessedToday\":" + accessedToday + ",\"dailyLimit\":" + dailyLimit
                + ",\"remainingToday\":" + (dailyLimit - accessedToday) + "}";
    }

    private static String errorJson(String code, String message) {
        return "{\"success\":false,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}";
    }
}
