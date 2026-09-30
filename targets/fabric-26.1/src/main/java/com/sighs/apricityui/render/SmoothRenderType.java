package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Image render type used by {@code ImageDrawer}, the 26.1 equivalent of the
 * 1.21.1 composite-state {@code RenderType}: textured quads with a bound
 * texture, translucent blending, optional linear sampling, and optional depth
 * testing (no depth writes).
 *
 * <p>The output target resolves through {@link OutputTargets#AUI_OUTPUT} so
 * images drawn inside a filter region land in the filter FBO instead of the
 * main target.</p>
 */
public final class SmoothRenderType {
    private SmoothRenderType() {
    }

    public static RenderType createSmooth(Identifier location, boolean blur, boolean depthTest) {
        RenderPipeline pipeline = RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_PROJECTION_SNIPPET})
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/image_" + Integer.toHexString(location.hashCode())))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(depthTest
                        ? Optional.of(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                        : Optional.empty())
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT),
                        ColorTargetState.WRITE_ALL))
                .withCull(false)
                .build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", location,
                        () -> RenderSystem.getSamplerCache().getClampToEdge(
                                blur ? FilterMode.LINEAR : FilterMode.NEAREST))
                .setOutputTarget(OutputTargets.AUI_OUTPUT)
                .createRenderSetup();
        return RenderType.create("apricityui_image_" + location, setup);
    }
}
