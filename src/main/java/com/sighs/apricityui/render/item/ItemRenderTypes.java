package com.sighs.apricityui.render.item;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AUI 物品节点使用的 RenderType 缓存。
 */
public final class ItemRenderTypes {
    private static final Map<ResourceLocation, RenderType> CUTOUT = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANSLUCENT = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> GLINT = new ConcurrentHashMap<>();

    private ItemRenderTypes() {
    }

    public static RenderType cutout(ResourceLocation atlasLocation) {
        if (atlasLocation == null)
            return RenderType.entityCutoutNoCull(new ResourceLocation("textures/atlas/blocks.png"));
        return CUTOUT.computeIfAbsent(atlasLocation, RenderType::entityCutoutNoCull);
    }

    public static RenderType translucent(ResourceLocation atlasLocation) {
        if (atlasLocation == null)
            return RenderType.entityTranslucent(new ResourceLocation("textures/atlas/blocks.png"));
        return TRANSLUCENT.computeIfAbsent(atlasLocation, RenderType::entityTranslucent);
    }

    public static RenderType glint(ResourceLocation atlasLocation) {
        ResourceLocation key = atlasLocation == null ? new ResourceLocation("textures/misc/enchanted_glint_item.png") : atlasLocation;
        return GLINT.computeIfAbsent(key, ignored -> RenderType.glintDirect());
    }

    public static RenderType overlay() {
        return RenderType.guiOverlay();
    }

    public static void clearCache() {
        CUTOUT.clear();
        TRANSLUCENT.clear();
        GLINT.clear();
    }
}
