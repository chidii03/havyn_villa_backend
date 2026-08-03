package com.havyn.properties.rayprop;

/**
 * {@code stoppedEarlyDueToQuota}/{@code dailyQuotaUsed}/{@code dailyQuotaLimit} are
 * real diagnostics from RayProp's own {@code dataAccess} response block (see {@link
 * RayPropDataAccess}), not inferred — an admin re-running the sync and seeing {@code
 * fetched} come back low can tell immediately whether that's "RayProp's whole
 * inventory" or "hit today's quota" without reading logs.
 */
public record RayPropSyncResult(
        int fetched,
        int created,
        int updated,
        int pagesFetched,
        boolean stoppedEarlyDueToQuota,
        Integer dailyQuotaUsed,
        Integer dailyQuotaLimit) {
}
