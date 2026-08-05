package com.havyn.media.service;

/**
 * Pure string manipulation over a Cloudinary {@code secure_url} — no network call.
 * Named card/hero/thumb sizes + a video poster frame, per project-docs/prompts/
 * 14-media-storage.md's delivery-helper deliverable ({@code f_auto,q_auto}, {@code
 * so_auto}).
 */
public final class CloudinaryUrlBuilder {

    private static final String UPLOAD_MARKER = "/upload/";

    private CloudinaryUrlBuilder() {
    }

    /** Inserts a transformation string right after {@code /upload/}. Returns the URL unchanged if it isn't a recognizable Cloudinary delivery URL. */
    public static String withTransformation(String secureUrl, String transformation) {
        int index = secureUrl.indexOf(UPLOAD_MARKER);
        if (index < 0) {
            return secureUrl;
        }
        int insertAt = index + UPLOAD_MARKER.length();
        return secureUrl.substring(0, insertAt) + transformation + "/" + secureUrl.substring(insertAt);
    }

    public static String cardUrl(String secureUrl) {
        return withTransformation(secureUrl, "c_fill,w_400,h_400,f_auto,q_auto");
    }

    public static String heroUrl(String secureUrl) {
        return withTransformation(secureUrl, "c_fill,w_1600,h_900,f_auto,q_auto");
    }

    public static String thumbUrl(String secureUrl) {
        return withTransformation(secureUrl, "c_fill,w_150,h_150,f_auto,q_auto");
    }

    /** A still frame auto-selected from the video (`so_auto`), delivered as a `.jpg` — Cloudinary's documented video-poster pattern (resource_type stays `video`). */
    public static String videoPosterUrl(String secureUrl) {
        String withPoster = withTransformation(secureUrl, "so_auto");
        int lastDot = withPoster.lastIndexOf('.');
        int lastSlash = withPoster.lastIndexOf('/');
        if (lastDot > lastSlash) {
            return withPoster.substring(0, lastDot) + ".jpg";
        }
        return withPoster + ".jpg";
    }
}
