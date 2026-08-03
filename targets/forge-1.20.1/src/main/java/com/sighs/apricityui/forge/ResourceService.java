package com.sighs.apricityui.forge;

import com.sighs.apricityui.render.SmoothRenderType;
import com.sighs.apricityui.spi.AuiResourceService;
import com.sighs.apricityui.spi.RenderHandle;
import com.sighs.apricityui.spi.TextureKey;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
    public Optional<InputStream> openResource(String path) {
        ResourceManager manager = resourceManager();
        if (manager == null) return Optional.empty();
        ResourceLocation location = parseLocation(path);
        if (location == null) return Optional.empty();
        try {
            Optional<Resource> resource = manager.getResource(location);
            return resource.map(r -> {
                try {
                    return (InputStream) r.open();
                } catch (java.io.IOException ignored) {
                    return null;
                }
            });
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Map<String, String> listResourcePaths(String path, String suffix) {
        ResourceManager manager = resourceManager();
        if (manager == null) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        try {
            Map<ResourceLocation, Resource> resources = manager.listResources(path,
                    location -> suffix == null || suffix.isEmpty() || location.getPath().endsWith(suffix));
            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                String fullPath = entry.getKey().getPath();
                String relative = fullPath;
                String prefix = path;
                if (prefix != null && !prefix.isEmpty() && relative.startsWith(prefix + "/")) {
                    relative = relative.substring(prefix.length() + 1);
                }
                if (relative.isBlank()) continue;
                String sourcePack = entry.getValue().sourcePackId();
                result.put(relative, sourcePack == null ? "" : sourcePack);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    @Override
    public TextureKey locationOf(String key) {
        if (key == null) return null;
        String sanitizedPath = key.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
        int hash = Math.floorMod(key.hashCode(), 1 << 24);
        return TextureKey.of("dynamic/" + sanitizedPath + "-" + Integer.toHexString(hash));
    }

    @Override
    public TextureKey tryParseTextureKey(String src) {
        if (src == null || src.isBlank()) return null;
        return ResourceLocation.tryParse(src) == null ? null : TextureKey.of(src);
    }

    @Override
    public Object textureLocation(TextureKey key) {
        if (key == null) return null;
        return parseLocation(key.value());
    }

    @Override
    public RenderHandle smoothRenderType(TextureKey key, boolean blur, boolean depthTest) {
        return RenderHandle.of(SmoothRenderType.createSmooth(parseLocation(key.value()), blur, depthTest));
    }

    private static ResourceLocation parseLocation(String value) {
        int colon = value.indexOf(':');
        if (colon >= 0) return ResourceLocation.tryParse(value);
        return new ResourceLocation("apricityui", value);
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
