package com.sighs.apricityui.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Decoder for browser-style data URIs used by images, masks, canvas and audio. */
public final class DataUri {
    private DataUri() {
    }

    public static Decoded decode(String value) {
        if (value == null || !value.regionMatches(true, 0, "data:", 0, 5)) return null;
        int comma = value.indexOf(',');
        if (comma < 0) throw new IllegalArgumentException("Malformed data URI");
        String metadata = value.substring(5, comma);
        boolean base64 = metadata.toLowerCase().endsWith(";base64")
                || metadata.toLowerCase().contains(";base64;");
        String mediaType = metadata.replaceAll("(?i);base64(?:;|$)", ";");
        int parameter = mediaType.indexOf(';');
        if (parameter >= 0) mediaType = mediaType.substring(0, parameter);
        if (mediaType.isBlank()) mediaType = "text/plain";
        String body = value.substring(comma + 1);
        byte[] bytes = base64 ? Base64.getDecoder().decode(body) : percentDecode(body);
        return new Decoded(mediaType, bytes);
    }

    private static byte[] percentDecode(String value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '%' && index + 2 < value.length()) {
                int high = Character.digit(value.charAt(index + 1), 16);
                int low = Character.digit(value.charAt(index + 2), 16);
                if (high >= 0 && low >= 0) {
                    output.write((high << 4) | low);
                    index += 2;
                    continue;
                }
            }
            byte[] encoded = String.valueOf(current).getBytes(StandardCharsets.UTF_8);
            output.writeBytes(encoded);
        }
        return output.toByteArray();
    }

    public record Decoded(String mediaType, byte[] bytes) {
        public Decoded {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }
    }
}
