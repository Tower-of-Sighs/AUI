package com.sighs.apricityui.forge;

import com.sighs.apricityui.render.LegacyShader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.IResourceManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ShaderRegistry {
    private static Map<String, LegacyShader> shaders = new HashMap<>();

    private ShaderRegistry() { }

    public static void init(IResourceManager resources) throws IOException {
        Map<String, LegacyShader> loaded = new HashMap<>();
        try {
            for (String name : new String[]{"background", "filter", "filter_blur", "filter_blend",
                    "filter_mask", "filter_mask_intersect", "filter_mask_subtract", "filter_mask_exclude"}) {
                loaded.put(name, new LegacyShader(resources, name));
            }
        } catch (IOException | RuntimeException failure) {
            loaded.values().forEach(LegacyShader::close);
            throw failure;
        }
        shaders.values().forEach(LegacyShader::close);
        shaders = loaded;
    }

    private static IResourceManager registeredResources;

    public static void register(IResourceManager resources) {
        if (registeredResources == resources) return;
        if (resources instanceof net.minecraft.resources.IReloadableResourceManager reloadable) {
            reloadable.registerReloadListener((net.minecraft.resources.IResourceManagerReloadListener) manager -> {
                try { init(manager); }
                catch (IOException failure) { throw new IllegalStateException("Cannot reload AUI shaders", failure); }
            });
            registeredResources = resources;
        }
    }

    private static LegacyShader get(String name) {
        IResourceManager resources = Minecraft.getInstance().getResourceManager();
        register(resources);
        if (shaders.isEmpty()) {
            try { init(resources); }
            catch (IOException failure) { throw new IllegalStateException("Cannot load AUI shaders", failure); }
        }
        return shaders.get(name);
    }

    public static LegacyShader getBackgroundShader() { return get("background"); }
    public static LegacyShader getFilterShader() { return get("filter"); }
    public static LegacyShader getFilterBlurShader() { return get("filter_blur"); }
    public static LegacyShader getFilterBlendShader() { return get("filter_blend"); }
    public static LegacyShader getFilterMaskShader() { return get("filter_mask"); }
    public static LegacyShader getFilterMaskIntersectShader() { return get("filter_mask_intersect"); }
    public static LegacyShader getFilterMaskSubtractShader() { return get("filter_mask_subtract"); }
    public static LegacyShader getFilterMaskExcludeShader() { return get("filter_mask_exclude"); }
}
