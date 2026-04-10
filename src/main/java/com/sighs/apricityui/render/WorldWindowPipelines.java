package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.client.pipeline.PipelineModifier;
import net.neoforged.neoforge.client.pipeline.RegisterPipelineModifiersEvent;

public final class WorldWindowPipelines {
    private WorldWindowPipelines() {
    }

    public static final ResourceKey<PipelineModifier> WORLD_WINDOW_UI = ResourceKey.create(
            PipelineModifier.MODIFIERS_KEY,
            Identifier.fromNamespaceAndPath(ApricityUI.MODID, "world_window_ui")
    );

    private static final DepthStencilState WORLD_WINDOW_DEPTH = new DepthStencilState(
            CompareOp.LESS_THAN_OR_EQUAL,
            false,
            -1.0F,
            -1.0F
    );

    public static void registerPipelineModifiers(RegisterPipelineModifiersEvent event) {
        event.register(WORLD_WINDOW_UI, WorldWindowPipelines::applyWorldWindowDepth);
    }

    private static RenderPipeline applyWorldWindowDepth(RenderPipeline pipeline, Identifier name) {
        Identifier location = pipeline.getLocation();
        if (location.getNamespace().equals(ApricityUI.MODID) && location.getPath().startsWith("pipeline/filter")) {
            return pipeline;
        }
        return pipeline.toBuilder().withLocation(name).withDepthStencilState(WORLD_WINDOW_DEPTH).build();
    }
}
