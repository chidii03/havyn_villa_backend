package com.havyn.media.web;

import com.havyn.media.service.MediaService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public — media for a listing the public can already see (ACTIVE only; same 404-on-anything-else story as PropertyController). */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/media")
public class PropertyMediaController {

    private final MediaService mediaService;

    public PropertyMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping
    public List<PropertyMediaSummary> list(@PathVariable UUID propertyId) {
        return mediaService.listPublic(propertyId).stream().map(PropertyMediaSummary::from).toList();
    }
}
