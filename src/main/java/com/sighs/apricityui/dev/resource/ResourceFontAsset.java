package com.sighs.apricityui.dev.resource;

import com.sighs.apricityui.instance.ClientLoader;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.Font;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Loads font resources under a stable family name shared by cards and previews. */
public final class ResourceFontAsset {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("ttf", "otf");
    private static final String FAMILY_PREFIX = "aui-resource-font-";

    private ResourceFontAsset() {
    }

    public static boolean isFont(Loader.StaticResourceEntry entry) {
        return entry != null && SUPPORTED_EXTENSIONS.contains(safe(entry.extension()).toLowerCase(Locale.ROOT));
    }

    public static String familyName(Loader.StaticResourceEntry entry) {
        String path = entry == null ? "" : safe(entry.path());
        UUID id = UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8));
        return FAMILY_PREFIX + id;
    }

    public static boolean ensureLoaded(Loader.StaticResourceEntry entry) {
        if (!isFont(entry)) return false;
        String family = familyName(entry);
        if (Font.isRegistered(family)) return true;
        try (InputStream stream = ClientLoader.getResourceStream(safe(entry.path()))) {
            return stream != null && Font.registerFont(family, stream);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
