package com.havyn.media.web;

import jakarta.validation.constraints.Size;

/** Partial media metadata edit. Null leaves the current value unchanged. */
public record UpdateMediaRequest(@Size(max = 500) String alt) {
}
