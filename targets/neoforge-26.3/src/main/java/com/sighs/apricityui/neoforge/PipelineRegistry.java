package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
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
 *
 * <p>26.3 (renderpearl) has no per-name uniform declarations and no
 * {@code MATRICES_PROJECTION_SNIPPET}: every uniform the compiled shader uses
 * must be present in one of the pipeline's {@link BindGroupLayout}s, otherwise
 * the pipeline fails to build. Hence the shared {@link #MATRICES_PROJECTION}
 * snippet plus explicit {@link BindGroupLayouts#SAMPLER0} /
 * {@link BindGroupLayouts#SAMPLER1} / {@link #FILTER_PARAMS} groups below.</p>
 */
public final class PipelineRegistry {
    /** 26.3 replacement for 26.1's {@code RenderPipelines.MATRICES_PROJECTION_SNIPPET}. */
    private static final Snippet MATRICES_PROJECTION = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .buildSnippet();

    /**
     * std140 block declared by {@code filter.fsh}, {@code filter_blur.fsh} and
     * {@code filter_blend.fsh} (9 {@code vec4}s = 144 B).
     */
    public static final BindGroupLayout FILTER_PARAMS =
            BindGroupLayout.builder().withUniform("FilterParams", UniformType.UNIFORM_BUFFER).build();

    private static volatile RenderPipeline filterPipeline;
    private static volatile RenderPipeline filterBlurPipeline;
    private static volatile RenderPipeline filterBlendPipeline;
    private static volatile RenderPipeline filterCopyPipeline;
    private static volatile RenderPipeline filterMaskPipeline;
    private static volatile RenderPipeline filterMaskLumPipeline;
    private static volatile RenderPipeline filterMaskIntersectPipeline;
    private static volatile RenderPipeline filterMaskSubtractPipeline;
    private static volatile RenderPipeline filterMaskExcludePipeline;

    private PipelineRegistry() {
    }

    /**
     * Registers the pipelines for precompilation at client startup.
     */
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(getFilter());
        event.registerPipeline(getFilterBlur());
        event.registerPipeline(getFilterBlend());
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
     * CSS blend pass: Sampler0 is the source layer, Sampler1 the backdrop.
     */
    public static RenderPipeline getFilterBlend() {
        RenderPipeline p = filterBlendPipeline;
        if (p == null) {
            p = buildBlend();
            filterBlendPipeline = p;
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
        RenderPipeline.Builder builder = RenderPipeline.builder(MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui",
                        "pipeline/" + (blur ? "filter_blur" : "filter")))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(FILTER_PARAMS);
        // filter.fsh samples Sampler1 for the shadow pass; filter_blur.fsh does not.
        if (!blur) builder.withBindGroupLayout(BindGroupLayouts.SAMPLER1);
        return builder
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                // Filter targets are AUI-owned and use AUI's classic ortho, so
                // no depth state is needed (and none is declared in 26.1 either).
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(
                        blur ? Optional.<BlendFunction>empty() : Optional.of(BlendFunction.TRANSLUCENT),
                        GpuFormat.RGBA8_UNORM,
                        ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }

    private static RenderPipeline buildBlend() {
        Identifier shader = Identifier.fromNamespaceAndPath("apricityui", "core/filter_blend");
        return RenderPipeline.builder(MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/filter_blend"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withBindGroupLayout(FILTER_PARAMS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(Optional.empty())
                // The fragment shader writes the premultiplied source-over
                // result directly; applying a second blend would double the
                // alpha factors and break transparent layers.
                .withColorTargetState(new ColorTargetState(Optional.empty(),
                        GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }

    private static RenderPipeline buildCopy() {
        Identifier shader = Identifier.fromNamespaceAndPath("apricityui", "core/filter_copy");
        return RenderPipeline.builder(MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/filter_copy"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(Optional.empty(),
                        GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }

    /**
     * CSS mask 的 dst-in blit：dest *= src.a。blend 在 pipeline 层烘焙
     * （26.3 同样没有可动态设置的混合状态），等价 legacy filter_mask.json 的
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
        return RenderPipeline.builder(MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/filter_mask"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(
                        Optional.of(new BlendFunction(BlendFactor.ZERO, BlendFactor.SRC_ALPHA)),
                        GpuFormat.RGBA8_UNORM,
                        ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }

    /**
     * mask-mode: luminance 的 dst-in blit：与 {@link #getFilterMask()} 同款
     * zero/src-alpha 混合，fragment 改取预乘 rgb 的亮度（26.3 的自定义
     * pipeline 无法在 RenderType 路径上动态传 uniform，只能拆独立 pipeline）。
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
        return RenderPipeline.builder(MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/filter_mask_lum"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(
                        Optional.of(new BlendFunction(BlendFactor.ZERO, BlendFactor.SRC_ALPHA)),
                        GpuFormat.RGBA8_UNORM,
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
                    new BlendFunction(BlendFactor.DST_ALPHA, BlendFactor.ZERO));
            filterMaskIntersectPipeline = p;
        }
        return p;
    }

    public static RenderPipeline getFilterMaskSubtract() {
        RenderPipeline p = filterMaskSubtractPipeline;
        if (p == null) {
            p = buildMaskMerge("filter_mask_subtract",
                    new BlendFunction(BlendFactor.ONE_MINUS_DST_ALPHA, BlendFactor.ZERO));
            filterMaskSubtractPipeline = p;
        }
        return p;
    }

    public static RenderPipeline getFilterMaskExclude() {
        RenderPipeline p = filterMaskExcludePipeline;
        if (p == null) {
            p = buildMaskMerge("filter_mask_exclude",
                    new BlendFunction(BlendFactor.ONE_MINUS_DST_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA));
            filterMaskExcludePipeline = p;
        }
        return p;
    }

    private static RenderPipeline buildMaskMerge(String name, BlendFunction blend) {
        Identifier shader = Identifier.fromNamespaceAndPath("apricityui", "core/filter_copy");
        return RenderPipeline.builder(MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/" + name))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(Optional.of(blend),
                        GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
    }
}
