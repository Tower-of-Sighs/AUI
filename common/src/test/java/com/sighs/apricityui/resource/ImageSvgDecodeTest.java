package com.sighs.apricityui.resource;

import com.sighs.apricityui.resource.async.image.DecodedImage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImageSvgDecodeTest {
    @Test
    void externalSvgResourceUsesTheSvgDecoderInsteadOfPng() throws Exception {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' width='24' height='24'>"
                .concat("<rect width='24' height='24' fill='#fff'/></svg>")
                .getBytes(StandardCharsets.UTF_8);
        try (DecodedImage decoded = Image.decode("test.svg", svg)) {
            assertNotNull(decoded);
            assertEquals(24, decoded.getWidth());
            assertEquals(24, decoded.getHeight());
        }
    }
}
