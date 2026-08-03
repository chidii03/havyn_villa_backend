package com.havyn.media.storage;

import com.havyn.media.domain.MediaResourceType;

/** Object-storage port — ADR-005. Cloudinary is the only adapter shipped; the port keeps the provider swappable. */
public interface MediaStorage {

    SignedUpload createSignedUpload(String folder);

    void deleteAsset(String publicId, MediaResourceType resourceType);

    /** Guards against a client reporting metadata for an asset that was never actually uploaded to our configured storage. */
    boolean isValidAssetUrl(String secureUrl);
}
