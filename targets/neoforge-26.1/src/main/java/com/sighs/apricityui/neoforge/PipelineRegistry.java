package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Builds the custom {@link RenderPipeline}s that replace the old filter
 * {@code ShaderInstance}s (1.21.2 removed {@code ShaderInstance} in favour of
 * code-defined pipelines).
 *
 * <p>The composite samples the rendered and pre-blurred sources, applies the
 * color, opacity, shadow, and clip operations, and receives its std140
 * {@code FilterParams} block from {@link RenderService}. Blur itself is split
 * into horizontal and vertical passes to keep the per-pixel work bounded.</p>
 */
public final class PipelineRegistry {
    private static volatile RenderPipeline filterPipeline;
    private static volatile RenderPipeline filterBlurPipeline;
    private static volatile RenderPipeline filterCopyPipeline;

    private PipelineRegistry() {
    }

    public static RenderPipeline getFilter() {
        RenderPipeline p = filterPipeline;
        if (p == null) {
            p = buildFilter(false);
            filterPipeline = p;
        }
        return p;
    }

    public static RenderPipeline getFilterBlur() {
        RenderPipeline p = filterBlurPipeline;
        if (p == null) {
            p = buildFilter(true);
            filterBlurPipeline = p;
        }
        return p;
    }

    /**
     * Copies a texture region without filtering, blending, or alpha
     * conversion. This is used for backdrop snapshots before a filter pass.
     */
    public static RenderPipeline getFilterCopy() {
        RenderPipeline p = filterCopyPipeline;
        if (p == null) {
            p = buildCopy();
            filterCopyPipeline = p;
        }
        return p;
    }

    private static RenderPipeline buildFilter(boolean blur) {
        Identifier shader = Identifier.fromNamespaceAndPath("apricityui", "core/" + (blur ? "filter_blur" : "filter"));
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui",
                        "pipeline/" + (blur ? "filter_blur" : "filter")))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withSampler("Sampler0")
                .withUniform("FilterParams", UniformType.UNIFORM_BUFFER);
        if (!blur) builder.withSampler("Sampler1");
        return builder
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(
                        blur ? Optional.empty() : Optional.of(BlendFunction.TRANSLUCENT),
                        ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }

    private static RenderPipeline buildCopy() {
        Identifier shader = Identifier.fromNamespaceAndPath("apricityui", "core/filter_copy");
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/filter_copy"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }
}
