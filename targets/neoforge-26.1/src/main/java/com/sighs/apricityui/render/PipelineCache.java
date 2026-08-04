package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Caches immutable 26.1 pipeline/render-type state combinations. */
public final class PipelineCache {
    private static final Map<Key, RenderType> TYPES = new ConcurrentHashMap<>();

    private PipelineCache() {
    }

    public static RenderType renderType(VertexFormat format, VertexFormat.Mode mode,
                                        boolean depthTest, int depthFunc, boolean depthMask,
                                        boolean blend, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
                                        boolean cull, boolean polygonOffset, float biasScale, float biasUnits) {
        Key key = new Key(format, mode, depthTest, depthFunc, depthMask, blend,
                srcRgb, dstRgb, srcAlpha, dstAlpha, cull, polygonOffset, biasScale, biasUnits);
        return TYPES.computeIfAbsent(key, PipelineCache::build);
    }

    private static RenderType build(Key key) {
        String shader = shaderFor(key.format);
        RenderPipeline pipeline = RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_PROJECTION_SNIPPET})
                .withLocation("pipeline/mesh_" + Integer.toHexString(key.hashCode()))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withVertexFormat(key.format, key.mode)
                .withDepthStencilState(key.depthTest || key.polygonOffset
                        ? Optional.of(new DepthStencilState(
                        compareOpFor(key.depthFunc, true), key.depthMask,
                        key.polygonOffset ? key.biasScale : 0.0f,
                        key.polygonOffset ? key.biasUnits : 0.0f))
                        : Optional.empty())
                .withColorTargetState(new ColorTargetState(
                        key.blend ? Optional.of(blendFunction(key)) : Optional.empty(),
                        ColorTargetState.WRITE_ALL))
                .withCull(key.cull)
                .build();
        return RenderType.create(
                "apricityui_mesh_" + Integer.toHexString(key.hashCode()),
                RenderSetup.builder(pipeline).createRenderSetup());
    }

    private static String shaderFor(VertexFormat format) {
        if (format == DefaultVertexFormat.POSITION) return "core/position";
        if (format == DefaultVertexFormat.POSITION_TEX) return "core/position_tex";
        return "core/gui";
    }

    private static CompareOp compareOpFor(int func, boolean enabled) {
        if (!enabled) return CompareOp.ALWAYS_PASS;
        return switch (func) {
            case 512 -> CompareOp.NEVER_PASS;
            case 513 -> CompareOp.LESS_THAN;
            case 515 -> CompareOp.LESS_THAN_OR_EQUAL;
            case 516 -> CompareOp.GREATER_THAN;
            case 517 -> CompareOp.NOT_EQUAL;
            case 518 -> CompareOp.GREATER_THAN_OR_EQUAL;
            case 514 -> CompareOp.EQUAL;
            default -> CompareOp.LESS_THAN_OR_EQUAL;
        };
    }

    private static BlendFunction blendFunction(Key key) {
        return new BlendFunction(sourceFactor(key.srcRgb), destinationFactor(key.dstRgb),
                sourceFactor(key.srcAlpha), destinationFactor(key.dstAlpha));
    }

    private static SourceFactor sourceFactor(int value) {
        return switch (value) {
            case 0 -> SourceFactor.ZERO;
            case 1 -> SourceFactor.ONE;
            case 768 -> SourceFactor.SRC_COLOR;
            case 769 -> SourceFactor.ONE_MINUS_SRC_COLOR;
            case 770 -> SourceFactor.SRC_ALPHA;
            case 771 -> SourceFactor.ONE_MINUS_SRC_ALPHA;
            case 772 -> SourceFactor.DST_ALPHA;
            case 773 -> SourceFactor.ONE_MINUS_DST_ALPHA;
            case 774 -> SourceFactor.DST_COLOR;
            case 775 -> SourceFactor.ONE_MINUS_DST_COLOR;
            default -> SourceFactor.SRC_ALPHA;
        };
    }

    private static DestFactor destinationFactor(int value) {
        return switch (value) {
            case 0 -> DestFactor.ZERO;
            case 1 -> DestFactor.ONE;
            case 768 -> DestFactor.SRC_COLOR;
            case 769 -> DestFactor.ONE_MINUS_SRC_COLOR;
            case 770 -> DestFactor.SRC_ALPHA;
            case 771 -> DestFactor.ONE_MINUS_SRC_ALPHA;
            case 772 -> DestFactor.DST_ALPHA;
            case 773 -> DestFactor.ONE_MINUS_DST_ALPHA;
            case 774 -> DestFactor.DST_COLOR;
            case 775 -> DestFactor.ONE_MINUS_DST_COLOR;
            default -> DestFactor.ONE_MINUS_SRC_ALPHA;
        };
    }

    private record Key(VertexFormat format, VertexFormat.Mode mode, boolean depthTest, int depthFunc,
                       boolean depthMask, boolean blend, int srcRgb, int dstRgb, int srcAlpha,
                       int dstAlpha, boolean cull, boolean polygonOffset, float biasScale, float biasUnits) { }
}
