package com.havyn.properties.rayprop;

import java.util.List;

/**
 * Outcome of walking every page of {@code GET /listings} — see {@link
 * RayPropClient#fetchAllListings()}. {@code stoppedEarlyDueToQuota} distinguishes
 * "reached the end of RayProp's inventory" ({@code hasMore: false}) from "hit the
 * account's daily unique-listing cap mid-walk" (RayProp's documented {@code
 * DAILY_LIMIT_REACHED}) — both are a normal, successful stop, not a failure, but
 * callers (and {@code RayPropSyncResult}) need to tell them apart.
 */
public record RayPropFetchResult(
        List<RayPropListing> listings,
        int pagesFetched,
        boolean stoppedEarlyDueToQuota,
        RayPropDataAccess lastDataAccess) {
}
