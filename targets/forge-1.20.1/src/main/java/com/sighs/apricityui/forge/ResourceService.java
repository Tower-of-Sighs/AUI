package com.sighs.apricityui.forge;

import com.sighs.apricityui.render.SmoothRenderType;
import com.sighs.apricityui.spi.AuiResourceService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Forge implementation of {@link AuiResourceService}, backed by the Minecraft
 * client resource manager. Headless environments (no Minecraft instance) return
 * empty results so common loaders fall back to their filesystem paths.
 */
public final class ResourceService implements AuiResourceService {
    public static final ResourceService INSTANCE = new ResourceService();

    private ResourceService() {
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation location) {
        ResourceManager manager = resourceManager();
        if (manager == null) return Optional.empty();
        try {
            return manager.getResource(location);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
        ResourceManager manager = resourceManager();
        if (manager == null) return Map.of();
        return manager.listResources(path, filter);
    }

    @Override
    public ResourceLocation locationOf(String key) {
        if (key == null) return null;
        String sanitizedPath = key.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
        int hash = Math.floorMod(key.hashCode(), 1 << 24);
        return new ResourceLocation("apricityui", "dynamic/" + sanitizedPath + "-" + Integer.toHexString(hash));
    }

    @Override
    public RenderType smoothRenderType(ResourceLocation location, boolean blur, boolean depthTest) {
        return SmoothRenderType.createSmooth(location, blur, depthTest);
    }

    private static ResourceManager resourceManager() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return null;
            return minecraft.getResourceManager();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
