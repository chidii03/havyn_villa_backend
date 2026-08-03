package com.havyn.properties.rayprop;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One listing as returned by {@code GET /listings} — fields observed directly from a
 * real sandbox response (rayprop.io/docs's own pagination sample only shows a partial
 * field set), same "parse the JsonNode by hand" style as
 * {@code PaystackPaymentProvider} rather than a Jackson-annotated DTO.
 *
 * <p>Titles/descriptions come back with zero-width Unicode characters interleaved
 * between letters (an anti-scraping measure on RayProp's side) — {@link #from} strips
 * them so imported listings don't render with invisible garbage in the UI.
 */
public record RayPropListing(
        String id,
        String title,
        String description,
        String currency,
        int maxGuests,
        int bedrooms,
        BigDecimal bathrooms,
        String city,
        String state,
        String neighborhood,
        long pricePerNightMinorUnits,
        List<String> imageUrls) {

    // Zero-width space/non-joiner/joiner, BOM, and the invisible-format-character
    // block RayProp interleaves into every title/description.
    private static final String ZERO_WIDTH_CHARS = "[\\u200B-\\u200F\\uFEFF\\u2060-\\u206F]";

    static RayPropListing from(JsonNode node) {
        List<String> images = new ArrayList<>();
        for (JsonNode image : node.path("listing_images")) {
            String url = image.path("image_url").asText(null);
            if (url != null && !url.isBlank()) {
                images.add(url);
            }
        }
        return new RayPropListing(
                node.path("id").asText(),
                stripZeroWidth(node.path("title").asText("")),
                stripZeroWidth(node.path("description").asText("")),
                node.path("currency").asText("NGN"),
                node.path("max_guests").asInt(1),
                node.path("bedrooms").asInt(0),
                node.path("bathrooms").decimalValue(),
                node.path("city").asText(""),
                node.path("state").asText(""),
                node.path("neighborhood").asText(""),
                node.path("price_per_night").asLong(0),
                images);
    }

    private static String stripZeroWidth(String value) {
        return value.replaceAll(ZERO_WIDTH_CHARS, "").trim();
    }
}
