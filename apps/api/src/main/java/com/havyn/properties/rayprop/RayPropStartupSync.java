package com.havyn.properties.rayprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RayPropStartupSync {

    private static final Logger log = LoggerFactory.getLogger(RayPropStartupSync.class);

    private final RayPropProperties properties;
    private final RayPropSyncService syncService;
    private final boolean syncOnStartup;

    public RayPropStartupSync(
            RayPropProperties properties,
            RayPropSyncService syncService,
            @Value("${havyn.rayprop.sync-on-startup:true}") boolean syncOnStartup) {
        this.properties = properties;
        this.syncService = syncService;
        this.syncOnStartup = syncOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncWhenReady() {
        String apiKey = properties.getApiKey();
        log.info(
                "RayProp config diagnostic: apiKeyLength={} apiKeyPrefix={} baseUrl={} listingsPath={}",
                apiKey != null ? apiKey.length() : 0,
                maskedPrefix(apiKey),
                properties.getBaseUrl(),
                properties.getListingsPath());
        if (!syncOnStartup || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return;
        }
        try {
            RayPropSyncResult result = syncService.sync();
            log.info(
                    "RayProp startup sync finished: fetched={} created={} updated={} pages={}",
                    result.fetched(), result.created(), result.updated(), result.pagesFetched());
        } catch (Exception ex) {
            log.warn("RayProp startup sync failed; API will continue serving existing listings: {}", ex.getMessage());
        }
    }

    private static String maskedPrefix(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value.length() <= 4 ? value : value.substring(0, 4);
    }
}
