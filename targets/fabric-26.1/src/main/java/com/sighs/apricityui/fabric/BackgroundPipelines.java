package com.sighs.apricityui.fabric;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;

final class BackgroundPipelines {
    private static final Map<RenderPipeline, RenderPipeline> PIPELINES = new IdentityHashMap<>();

    static RenderPipeline shader() {
        return adapt(PipelineRegistry.getFilterCopy());
    }

    static RenderPipeline adapt(RenderPipeline source) {
        return PIPELINES.computeIfAbsent(source, pipeline -> RenderPipeline.builder(
                        net.minecraft.client.renderer.RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/background_" + PIPELINES.size()))
                .withVertexShader(Identifier.fromNamespaceAndPath("apricityui", "core/background"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("apricityui", "core/background"))
                .withVertexFormat(pipeline.getVertexFormat(), pipeline.getVertexFormatMode())
                .withDepthStencilState(java.util.Optional.ofNullable(pipeline.getDepthStencilState()))
                .withColorTargetState(pipeline.getColorTargetState())
                .withCull(pipeline.isCull())
                .withSampler("Sampler0")
                .withUniform("BackgroundParams", UniformType.UNIFORM_BUFFER)
                .build());
    }
}
