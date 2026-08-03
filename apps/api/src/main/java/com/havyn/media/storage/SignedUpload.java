package com.havyn.media.storage;

/** Everything the client needs to upload directly to Cloudinary — no secret ever included. */
public record SignedUpload(String cloudName, String apiKey, long timestamp, String signature, String folder) {
}
