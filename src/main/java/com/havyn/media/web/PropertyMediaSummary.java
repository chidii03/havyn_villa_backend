package com.havyn.media.web;

import com.havyn.media.domain.MediaResourceType;
import com.havyn.media.domain.PropertyMedia;
import com.havyn.media.service.CloudinaryUrlBuilder;
import java.math.BigDecimal;
import java.util.UUID;

/** {@code cardUrl}/{@code heroUrl}/{@code thumbUrl}/{@code posterUrl} are pre-built transformed CDN URLs — see {@link CloudinaryUrlBuilder}. */
public record PropertyMediaSummary(
        UUID id,
        String secureUrl,
        String cardUrl,
        String heroUrl,
        String thumbUrl,
        String posterUrl,
        String resourceType,
        String format,
        Integer width,
        Integer height,
        BigDecimal duration,
        int position,
        String alt) {

    public static PropertyMediaSummary from(PropertyMedia media) {
        boolean isVideo = media.getResourceType() == MediaResourceType.VIDEO;
        return new PropertyMediaSummary(
                media.getId(),
                media.getSecureUrl(),
                CloudinaryUrlBuilder.cardUrl(media.getSecureUrl()),
                CloudinaryUrlBuilder.heroUrl(media.getSecureUrl()),
                CloudinaryUrlBuilder.thumbUrl(media.getSecureUrl()),
                isVideo ? CloudinaryUrlBuilder.videoPosterUrl(media.getSecureUrl()) : null,
                media.getResourceType().name(),
                media.getFormat(),
                media.getWidth(),
                media.getHeight(),
                media.getDuration(),
                media.getPosition(),
                media.getAlt());
    }
}
