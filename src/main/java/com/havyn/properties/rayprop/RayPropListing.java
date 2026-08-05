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
        String category,
        long pricePerNightMinorUnits,
        List<String> imageUrls) {

    // Zero-width space/non-joiner/joiner, BOM, and the invisible-format-character
    // block RayProp interleaves into every title/description.
    private static final String ZERO_WIDTH_CHARS = "[\\u200B-\\u200F\\uFEFF\\u2060-\\u206F]";

    static RayPropListing from(JsonNode node) {
        List<String> images = new ArrayList<>();
        addImageUrls(images, node.path("listing_images"), "image_url", "url", "secure_url");
        addImageUrls(images, node.path("images"), "image_url", "url", "secure_url");
        addImageUrls(images, node.path("photos"), "image_url", "url", "secure_url");
        addImageUrls(images, node.path("media"), "image_url", "url", "secure_url");
        return new RayPropListing(
                node.path("id").asText(),
                stripZeroWidth(node.path("title").asText("")),
                stripZeroWidth(firstText(node, "description", "summary", "details")),
                node.path("currency").asText("NGN"),
                firstInt(node, 1, "max_guests", "maxGuests", "guests"),
                firstInt(node, 0, "bedrooms", "bedroom_count"),
                firstDecimal(node, BigDecimal.ONE, "bathrooms", "bathroom_count"),
                node.path("city").asText(""),
                node.path("state").asText(""),
                node.path("neighborhood").asText(""),
                firstText(node, "property_category", "property_type", "type", "category"),
                firstLong(node, 0, "price_per_night", "pricePerNight", "nightly_price"),
                images);
    }

    private static void addImageUrls(List<String> images, JsonNode nodes, String... fields) {
        if (!nodes.isArray()) {
            return;
        }
        for (JsonNode image : nodes) {
            String url = image.isTextual() ? image.asText(null) : firstText(image, fields);
            if (url != null && !url.isBlank()) {
                images.add(url);
            }
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) {
                return stripZeroWidth(value);
            }
        }
        return "";
    }

    private static int firstInt(JsonNode node, int fallback, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asInt();
            }
        }
        return fallback;
    }

    private static long firstLong(JsonNode node, long fallback, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asLong();
            }
        }
        return fallback;
    }

    private static BigDecimal firstDecimal(JsonNode node, BigDecimal fallback, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.decimalValue();
            }
        }
        return fallback;
    }

    private static String stripZeroWidth(String value) {
        return value.replaceAll(ZERO_WIDTH_CHARS, "").trim();
    }
}
