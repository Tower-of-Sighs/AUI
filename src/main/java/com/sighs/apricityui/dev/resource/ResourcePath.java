package com.sighs.apricityui.dev.resource;

import java.util.Locale;

/** Shared path and display helpers for the resource browser. */
public final class ResourcePath {
    private ResourcePath() {
    }

    public static String normalize(String path) {
        String normalized = safe(path).replace('\\', '/').trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    public static String parent(String path) {
        String normalized = normalize(path);
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? "" : normalized.substring(0, separator);
    }

    public static String fileName(String path) {
        String normalized = normalize(path);
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? normalized : normalized.substring(separator + 1);
    }

    public static String formatSize(long bytes) {
        if (bytes < 0) return "--";
        if (bytes < 1024) return bytes + " B";
        double kilobytes = bytes / 1024.0d;
        if (kilobytes < 1024) return String.format(Locale.ROOT, "%.1f KB", kilobytes);
        return String.format(Locale.ROOT, "%.1f MB", kilobytes / 1024.0d);
    }

    public static String safe(String value) {
        return value == null ? "" : value;
    }
}
