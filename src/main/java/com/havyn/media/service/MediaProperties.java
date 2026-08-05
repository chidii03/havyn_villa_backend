package com.havyn.media.service;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code havyn.media.*} — validation limits, per project-docs/prompts/14-media-storage.md#4. */
@ConfigurationProperties(prefix = "havyn.media")
public class MediaProperties {

    private int maxPerListing = 20;
    private long maxImageBytes = 10 * 1024 * 1024;
    private long maxVideoBytes = 200 * 1024 * 1024;
    private List<String> allowedImageFormats = List.of("jpg", "jpeg", "png", "webp");
    private List<String> allowedVideoFormats = List.of("mp4", "mov");

    public int getMaxPerListing() {
        return maxPerListing;
    }

    public void setMaxPerListing(int maxPerListing) {
        this.maxPerListing = maxPerListing;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxVideoBytes() {
        return maxVideoBytes;
    }

    public void setMaxVideoBytes(long maxVideoBytes) {
        this.maxVideoBytes = maxVideoBytes;
    }

    public List<String> getAllowedImageFormats() {
        return allowedImageFormats;
    }

    public void setAllowedImageFormats(List<String> allowedImageFormats) {
        this.allowedImageFormats = allowedImageFormats;
    }

    public List<String> getAllowedVideoFormats() {
        return allowedVideoFormats;
    }

    public void setAllowedVideoFormats(List<String> allowedVideoFormats) {
        this.allowedVideoFormats = allowedVideoFormats;
    }
}
