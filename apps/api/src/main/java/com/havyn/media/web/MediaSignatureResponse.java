package com.havyn.media.web;

import com.havyn.media.storage.SignedUpload;

public record MediaSignatureResponse(String cloudName, String apiKey, long timestamp, String signature, String folder) {

    public static MediaSignatureResponse from(SignedUpload upload) {
        return new MediaSignatureResponse(upload.cloudName(), upload.apiKey(), upload.timestamp(), upload.signature(), upload.folder());
    }
}
