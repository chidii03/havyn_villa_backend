package com.havyn.media.web;

import com.havyn.media.domain.MediaResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Sent after the client has already uploaded directly to Cloudinary using a signature from {@code POST .../media/signature}. */
public record AddMediaRequest(
        @NotBlank String publicId,
        @NotBlank String secureUrl,
        @NotNull MediaResourceType resourceType,
        @NotBlank String format,
        Integer width,
        Integer height,
        BigDecimal duration,
        @NotNull @PositiveOrZero Long bytes,
        String alt) {
}
