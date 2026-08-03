package com.sighs.apricityui.spi;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Loader-side Minecraft resource-pack and image-texture access.
 *
 * <p>{@code common} loads shared files from its own filesystem via
 * {@code Loader}; the resource-pack overlay (Minecraft's {@code ResourceManager})
 * and the image render pipeline are loader/version-specific, so {@code common}
 * delegates those lookups here. Implemented in the loader target and registered
 * through {@link AuiServices}.</p>
 */
public interface AuiResourceService {
    /** Opens the resource-pack resource at {@code apricity/<path>}, or empty. */
    Optional<Resource> getResource(ResourceLocation location);

    /** Lists all resource-pack resources under the given path (e.g. {@code apricity}). */
    Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter);

    /**
     * Maps an image identity to the ResourceLocation used to register its texture
     * with the Minecraft texture manager. Deterministic (sanitized path + a hash
     * of the original key), so registration and the render path agree.
     */
    ResourceLocation locationOf(String key);

    /** Builds the smooth image {@link RenderType} for the given texture. */
    RenderType smoothRenderType(ResourceLocation location, boolean blur, boolean depthTest);
}
