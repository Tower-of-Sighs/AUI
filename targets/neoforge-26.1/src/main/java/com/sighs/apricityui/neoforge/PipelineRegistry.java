package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.Optional;

/**
 * Builds the custom {@link RenderPipeline}s that replace the old filter
 * {@code ShaderInstance}s (1.21.2 removed {@code ShaderInstance} in favour of
 * code-defined pipelines; 1.21.5 removed the shader JSONs, so the GLSL files
 * under {@code assets/apricityui/shaders/core/} are referenced directly).
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
    private static volatile RenderPipeline filterMaskPipeline;
    private static volatile RenderPipeline filterMaskLumPipeline;
    private static volatile RenderPipeline filterMaskIntersectPipeline;
    private static volatile RenderPipeline filterMaskSubtractPipeline;
    private static volatile RenderPipeline filterMaskExcludePipeline;

    private PipelineRegistry() {
    }

    /** Registers the pipelines for precompilation at client startup. */
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(getFilter());
        event.registerPipeline(getFilterBlur());
        event.registerPipeline(getFilterCopy());
        event.registerPipeline(getFilterMask());
        event.registerPipeline(getFilterMaskLum());
        event.registerPipeline(getFilterMaskIntersect());
        event.registerPipeline(getFilterMaskSubtract());
        event.registerPipeline(getFilterMaskExclude());
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

    /**
     * CSS mask 的 dst-in blit：dest *= src.a。blend 在 pipeline 层烘焙
     * （26.1 没有可动态设置的混合状态），等价 legacy filter_mask.json 的
     * zero/src-alpha 声明。
     */
    public static RenderPipeline getFilterMask() {
        RenderPipeline p = filterMaskPipeline;
        if (p == null) {
            p = buildMask();
            filterMaskPipeline = p;
        }
        return p;
    }

    private static RenderPipeline buildMask() {
        Identifier shader = Identifier.fromNamespaceAndPath("apricityui", "core/filter_mask");
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/filter_mask"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(
                        Optional.of(new BlendFunction(SourceFactor.ZERO, DestFactor.SRC_ALPHA)),
                        ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }

    /**
     * mask-mode: luminance 的 dst-in blit：与 {@link #getFilterMask()} 同款
     * zero/src-alpha 混合，fragment 改取预乘 rgb 的亮度（26.1 无法给自定义
     * pipeline 动态传 uniform，只能拆独立 pipeline）。
     */
    public static RenderPipeline getFilterMaskLum() {
        RenderPipeline p = filterMaskLumPipeline;
        if (p == null) {
            p = buildMaskLum();
            filterMaskLumPipeline = p;
        }
        return p;
    }

    private static RenderPipeline buildMaskLum() {
        Identifier shader = Identifier.fromNamespaceAndPath("apricityui", "core/filter_mask_lum");
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/filter_mask_lum"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(
                        Optional.of(new BlendFunction(SourceFactor.ZERO, DestFactor.SRC_ALPHA)),
                        ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }

    /**
     * mask-composite 的 merge blit（源=当前层 scratch FBO，目标=下层累积 M）。
     * fragment 复用 filter_copy 的透传，Porter-Duff 算子由烘焙混合表达：
     * intersect=source-in、subtract=source-out、exclude=xor。
     */
    public static RenderPipeline getFilterMaskIntersect() {
        RenderPipeline p = filterMaskIntersectPipeline;
        if (p == null) {
            p = buildMaskMerge("filter_mask_intersect",
                    new BlendFunction(SourceFactor.DST_ALPHA, DestFactor.ZERO));
            filterMaskIntersectPipeline = p;
        }
        return p;
    }

    public static RenderPipeline getFilterMaskSubtract() {
        RenderPipeline p = filterMaskSubtractPipeline;
        if (p == null) {
            p = buildMaskMerge("filter_mask_subtract",
                    new BlendFunction(SourceFactor.ONE_MINUS_DST_ALPHA, DestFactor.ZERO));
            filterMaskSubtractPipeline = p;
        }
        return p;
    }

    public static RenderPipeline getFilterMaskExclude() {
        RenderPipeline p = filterMaskExcludePipeline;
        if (p == null) {
            p = buildMaskMerge("filter_mask_exclude",
                    new BlendFunction(SourceFactor.ONE_MINUS_DST_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA));
            filterMaskExcludePipeline = p;
        }
        return p;
    }

    private static RenderPipeline buildMaskMerge(String name, BlendFunction blend) {
        Identifier shader = Identifier.fromNamespaceAndPath("apricityui", "core/filter_copy");
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/" + name))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(Optional.of(blend), ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }
}
