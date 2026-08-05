package com.havyn.properties.rayprop;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The {@code dataAccess} block RayProp attaches to every {@code /listings} response
 * (rayprop.io/docs — Listings API), tracking the account's daily unique-listing quota
 * (Free/sandbox: 500/day — see rayprop.io/docs's Rate Limits page). Surfaced up through
 * {@link RayPropFetchResult}/{@code RayPropSyncResult} as real diagnostics rather than
 * inferred/guessed numbers.
 */
public record RayPropDataAccess(int accessedToday, int dailyLimit, int remainingToday) {

    static RayPropDataAccess from(JsonNode node) {
        JsonNode dataAccess = node.path("dataAccess");
        if (dataAccess.isMissingNode() || dataAccess.isNull()) {
            return null;
        }
        return new RayPropDataAccess(
                dataAccess.path("accessedToday").asInt(0),
                dataAccess.path("dailyLimit").asInt(0),
                dataAccess.path("remainingToday").asInt(0));
    }
}
