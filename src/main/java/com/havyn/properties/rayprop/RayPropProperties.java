package com.havyn.properties.rayprop;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code havyn.rayprop.*} — see https://rayprop.io/docs (Testing Guide: base URL is the same for sandbox/live, only the key prefix differs). */
@ConfigurationProperties(prefix = "havyn.rayprop")
public class RayPropProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.rayprop.io";
    private String listingsPath = "/listings";

    /** RayProp caps {@code limit} at 50 server-side regardless of what's requested (docs: Pagination). */
    private int pageSize = 50;

    /** Safety cap on pages walked per sync — matches the lowest documented plan tier (Free: 500 daily unique listings / 50 per page = 10 pages) with headroom. */
    private int maxPages = 20;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getListingsPath() {
        return listingsPath;
    }

    public void setListingsPath(String listingsPath) {
        this.listingsPath = listingsPath;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }
}
