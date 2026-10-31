package com.sighs.apricityui.neoforge;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;

final class BackgroundPipelines {
    private static final Map<RenderPipeline, RenderPipeline> PIPELINES = new IdentityHashMap<>();
    private static final BindGroupLayout PARAMS = BindGroupLayout.builder()
            .withUniform("BackgroundParams", UniformType.UNIFORM_BUFFER).build();

    static RenderPipeline shader() {
        return adapt(PipelineRegistry.getFilterCopy());
    }

    static RenderPipeline adapt(RenderPipeline source) {
        return PIPELINES.computeIfAbsent(source, pipeline -> pipeline.toBuilder()
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/background_" + PIPELINES.size()))
                .withVertexShader(Identifier.fromNamespaceAndPath("apricityui", "core/background"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("apricityui", "core/background"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(PARAMS)
                .build());
    }
}
