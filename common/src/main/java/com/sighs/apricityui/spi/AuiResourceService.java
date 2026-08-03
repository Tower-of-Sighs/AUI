package com.sighs.apricityui.spi;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Loader-side Minecraft resource-pack and image-texture access.
 *
 * <p>{@code common} loads shared files from its own filesystem via
 * {@code Loader}; the resource-pack overlay (Minecraft's {@code ResourceManager})
 * and the image render pipeline are loader/version-specific, so {@code common}
 * delegates those lookups here. Implemented in the loader target and registered
 * through {@link AuiServices}.</p>
 *
 * <p>Signatures use only JDK types ({@link String}/{@link InputStream}) so no
 * Minecraft version-bound type ({@code ResourceLocation}/{@code Resource}) leaks
 * into the SPI contract.</p>
 */
public interface AuiResourceService {
    /** Opens the resource-pack resource at the given relative path (e.g. {@code apricity/global.css}). */
    Optional<InputStream> openResource(String path);

    /**
     * Lists resource-pack entries under the given path whose names end with
     * {@code suffix}. Returns relative paths (the given path prefix stripped)
     * mapped to the source pack id they came from.
     */
    Map<String, String> listResourcePaths(String path, String suffix);

    /**
     * Maps a raw texture identity to the version-neutral {@link TextureKey} used
     * for registration and rendering. Deterministic (sanitized path + a hash of
     * the original key), so registration and the render path agree.
     */
    TextureKey locationOf(String key);

    /** Parses a texture source string into a {@link TextureKey}, or {@code null} when invalid. */
    TextureKey tryParseTextureKey(String src);

    /** Returns the loader's location object ({@code ResourceLocation}/{@code Identifier}) for a key. */
    Object textureLocation(TextureKey key);

    /** Builds the smooth image render type for the given texture, wrapped in a {@link RenderHandle}. */
    RenderHandle smoothRenderType(TextureKey key, boolean blur, boolean depthTest);
}
