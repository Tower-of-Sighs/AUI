package com.sighs.apricityui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Image render type used by {@code ImageDrawer}: textured quads with a bound
 * texture, translucent blending, optional linear sampling, and optional depth
 * testing (no depth writes).
 *
 * <p>The pipeline uses vanilla's {@code core/position_tex_color} shader, so it
 * only needs the {@code Projection}/{@code DynamicTransforms} bind groups and
 * {@code Sampler0}. A {@code RenderType} no longer carries an output target in
 * 26.3 - the destination is whatever {@code RenderPass} the caller opened (see
 * {@code RenderService}), which is why the 26.1 {@code setOutputTarget} call is
 * gone.</p>
 *
 * <p>Pipelines are memoised per texture/state combination: 26.3 keys the
 * compiled-pipeline cache by {@code RenderPipeline} identity, so building a
 * fresh pipeline on every call would recompile the shader on every image.</p>
 */
public final class SmoothRenderType {
    /** 26.3 replacement for 26.1's {@code RenderPipelines.MATRICES_PROJECTION_SNIPPET}. */
    private static final Snippet MATRICES_PROJECTION = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .buildSnippet();

    private static final Map<String, RenderType> CACHE = new ConcurrentHashMap<>();

    private SmoothRenderType() {
    }

    public static RenderType createSmooth(Identifier location, boolean blur, boolean depthTest) {
        String key = location + "|" + blur + "|" + depthTest;
        return CACHE.computeIfAbsent(key, ignored -> build(location, blur, depthTest));
    }

    private static RenderType build(Identifier location, boolean blur, boolean depthTest) {
        RenderPipeline pipeline = RenderPipeline.builder(MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui",
                        "pipeline/image_" + Integer.toHexString(location.hashCode())))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                // Source convention is classic. RenderService adapts this material
                // to the live GUI/world depth domain and injects its current stencil.
                .withDepthStencilState(depthTest
                        ? Optional.of(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                        : Optional.empty())
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT),
                        GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", location,
                        () -> RenderSystem.getSamplerCache().getClampToEdge(
                                blur ? FilterMode.LINEAR : FilterMode.NEAREST))
                .createRenderSetup();
        return RenderType.create("apricityui_image_" + location, setup);
    }
}
