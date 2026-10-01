package com.sighs.apricityui.forge;

import com.sighs.apricityui.render.SmoothRenderType;
import com.sighs.apricityui.spi.AuiResourceService;
import com.sighs.apricityui.spi.RenderHandle;
import com.sighs.apricityui.spi.TextureKey;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
            // 1.18.2 的 ResourceManager#getResource 直接返回 Resource（找不到时抛 IOException），
            // 1.19 起才改成 Optional；流的访问器在 1.18.2 叫 Resource#getInputStream。
            return Optional.of(manager.getResource(location).getInputStream());
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
            // 1.18.2 的 listResources 返回 Collection<ResourceLocation>（1.19 起改成 Map），
            // 过滤谓词收的是资源路径字符串而不是 ResourceLocation。
            for (ResourceLocation location : manager.listResources(path,
                    name -> suffix == null || suffix.isEmpty() || name.endsWith(suffix))) {
                String fullPath = location.getPath();
                String relative = fullPath;
                String prefix = path;
                if (prefix != null && !prefix.isEmpty() && relative.startsWith(prefix + "/")) {
                    relative = relative.substring(prefix.length() + 1);
                }
                if (relative.isBlank()) continue;
                String sourcePack = sourcePackOf(manager, location);
                result.put(relative, sourcePack == null ? "" : sourcePack);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /**
     * 取资源所属资源包的标识。
     *
     * <p>1.18.2 的 {@code Resource} 上没有 1.19 才加入的 {@code sourcePackId()}，等价信息是
     * {@code getSourceName()}——原版经 {@code SimpleResource} 构造时传的就是
     * {@code PackResources#getName()}，即资源包名。</p>
     */
    private static String sourcePackOf(ResourceManager manager, ResourceLocation location) {
        try {
            return manager.getResource(location).getSourceName();
        } catch (Exception ignored) {
            return null;
        }
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
