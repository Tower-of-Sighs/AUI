package com.sighs.apricityui.instance;

import com.sighs.apricityui.render.ItemDrawer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Minecraft 客户端资源重载后清理 AUI item 模型与 RenderType 缓存。
 */
public final class ItemRenderReloadListener extends SimplePreparableReloadListener<Void> {
    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void ignored, ResourceManager resourceManager, ProfilerFiller profiler) {
        ItemDrawer.clearCache();
    }
}
