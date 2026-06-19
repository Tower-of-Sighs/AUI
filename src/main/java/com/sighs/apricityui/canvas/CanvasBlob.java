package com.sighs.apricityui.canvas;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CanvasBlob {
    private final byte[] bytes;
    private final String type;

    public CanvasBlob(byte[] bytes, String type) {
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
        this.type = type == null || type.isBlank() ? "application/octet-stream" : type;
    }

    public int getSize() {
        return bytes.length;
    }

    public String getType() {
        return type;
    }

    public byte[] arrayBuffer() {
        return bytes.clone();
    }

    public String text() {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public String toDataURL() {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
