package com.havyn.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CloudinaryUrlBuilderTest {

    private static final String IMAGE_URL = "https://res.cloudinary.com/havyn/image/upload/v1730000000/properties/abc/sample.jpg";
    private static final String VIDEO_URL = "https://res.cloudinary.com/havyn/video/upload/v1730000000/properties/abc/clip.mp4";

    @Test
    void cardUrlInsertsTheFillTransformationRightAfterUpload() {
        assertThat(CloudinaryUrlBuilder.cardUrl(IMAGE_URL)).isEqualTo(
                "https://res.cloudinary.com/havyn/image/upload/c_fill,w_400,h_400,f_auto,q_auto/v1730000000/properties/abc/sample.jpg");
    }

    @Test
    void heroUrlUsesTheWidescreenSize() {
        assertThat(CloudinaryUrlBuilder.heroUrl(IMAGE_URL)).isEqualTo(
                "https://res.cloudinary.com/havyn/image/upload/c_fill,w_1600,h_900,f_auto,q_auto/v1730000000/properties/abc/sample.jpg");
    }

    @Test
    void thumbUrlUsesTheSmallSize() {
        assertThat(CloudinaryUrlBuilder.thumbUrl(IMAGE_URL)).isEqualTo(
                "https://res.cloudinary.com/havyn/image/upload/c_fill,w_150,h_150,f_auto,q_auto/v1730000000/properties/abc/sample.jpg");
    }

    @Test
    void videoPosterUrlKeepsTheVideoResourceTypeButSwapsTheExtensionToJpg() {
        assertThat(CloudinaryUrlBuilder.videoPosterUrl(VIDEO_URL)).isEqualTo(
                "https://res.cloudinary.com/havyn/video/upload/so_auto/v1730000000/properties/abc/clip.jpg");
    }

    @Test
    void returnsTheUrlUnchangedWhenItIsNotARecognizableCloudinaryDeliveryUrl() {
        String notCloudinary = "https://example.com/not-cloudinary.jpg";

        assertThat(CloudinaryUrlBuilder.cardUrl(notCloudinary)).isEqualTo(notCloudinary);
    }
}
