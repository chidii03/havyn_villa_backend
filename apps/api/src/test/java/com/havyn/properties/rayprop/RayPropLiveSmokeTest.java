package com.havyn.properties.rayprop;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

class RayPropLiveSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RAYPROP_LIVE_SMOKE", matches = "true")
    void fetchesLiveListingsWithCurrentRequestShape() {
        RayPropProperties properties = new RayPropProperties();
        properties.setApiKey(System.getenv("RAYPROP_API_KEY"));
        properties.setBaseUrl(System.getenv().getOrDefault("RAYPROP_BASE_URL", "https://api.rayprop.io"));
        properties.setListingsPath(System.getenv().getOrDefault("RAYPROP_LISTINGS_PATH", "/listings"));
        properties.setPageSize(Integer.parseInt(System.getenv().getOrDefault("RAYPROP_PAGE_SIZE", "50")));
        properties.setMaxPages(1);

        RayPropClient client = new RayPropClient(RestClient.builder(), properties, new ObjectMapper());

        RayPropFetchResult result = client.fetchAllListings();

        assertThat(result.pagesFetched()).isEqualTo(1);
        assertThat(result.listings()).isNotEmpty();
    }
}
