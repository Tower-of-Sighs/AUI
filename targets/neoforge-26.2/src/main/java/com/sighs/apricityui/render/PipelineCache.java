package com.sighs.apricityui.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches immutable 26.2 {@link RenderType}s for AUI's immediate-mode meshes.
 *
 * <p>1.21.5 removed the mutable global render state ({@code RenderSystem}
 * blend/depth/stencil setters) in favour of per-draw {@link RenderPipeline}
 * objects, so the state intent that common code expresses through
 * {@code AuiRenderService} is materialised here as cached pipeline/render-type
 * combinations. Stencil state is part of the key: NeoForge exposes it as
 * {@link StencilTest} pipeline state, which is how AUI's rounded masks work on
 * this version.</p>
 */
public final class PipelineCache {
    private static final Map<Key, RenderType> TYPES = new ConcurrentHashMap<>();

    private PipelineCache() {
    }

    public static RenderType renderType(VertexFormat format, PrimitiveTopology mode,
                                        boolean depthTest, int depthFunc, boolean depthMask,
                                        boolean blend, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
                                        boolean cull, boolean polygonOffset, float biasScale, float biasUnits,
                                        int colorWriteMask,
                                        boolean stencilEnabled, int stencilFunc, int stencilRef,
                                        int stencilReadMask, int stencilWriteMask,
                                        int stencilSfail, int stencilDpfail, int stencilDppass) {
        Key key = new Key(format, mode, depthTest, depthFunc, depthMask, blend,
                srcRgb, dstRgb, srcAlpha, dstAlpha, cull, polygonOffset, biasScale, biasUnits,
                colorWriteMask, stencilEnabled, stencilFunc, stencilRef, stencilReadMask, stencilWriteMask,
                stencilSfail, stencilDpfail, stencilDppass);
        return TYPES.computeIfAbsent(key, PipelineCache::build);
    }

    private static RenderType build(Key key) {
        String shader = shaderFor(key.format());
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/mesh_" + Integer.toHexString(key.hashCode())))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withVertexBinding(0, key.format())
                .withPrimitiveTopology(key.mode())
                .withColorTargetState(new ColorTargetState(
                        key.blend() ? Optional.of(blendFunction(key)) : Optional.empty(),
                        GpuFormat.RGBA8_UNORM,
                        key.colorWriteMask()))
                .withCull(key.cull());

        if (key.depthTest() || key.polygonOffset() || key.stencilEnabled()) {
            builder.withDepthStencilState(new DepthStencilState(
                    key.depthTest() ? compareOp(key.depthFunc()) : CompareOp.ALWAYS_PASS,
                    key.depthMask(),
                    key.polygonOffset() ? key.biasScale() : 0.0f,
                    key.polygonOffset() ? key.biasUnits() : 0.0f));
        } else {
            builder.withDepthStencilState(Optional.empty());
        }
        if (key.stencilEnabled()) {
            StencilPerFaceTest face = new StencilPerFaceTest(
                    stencilOp(key.stencilSfail()),
                    stencilOp(key.stencilDpfail()),
                    stencilOp(key.stencilDppass()),
                    compareOp(key.stencilFunc()));
            builder.withStencilTest(new StencilTest(face, key.stencilReadMask(), key.stencilWriteMask(), key.stencilRef()));
        }

        RenderPipeline pipeline = builder.build();
        return RenderType.create(
                "apricityui_mesh_" + Integer.toHexString(key.hashCode()),
                RenderSetup.builder(pipeline)
                        .setOutputTarget(OutputTargets.AUI_OUTPUT)
                        .createRenderSetup());
    }

    private static String shaderFor(VertexFormat format) {
        if (format == DefaultVertexFormat.POSITION) return "core/position";
        if (format == DefaultVertexFormat.POSITION_TEX) return "core/position_tex";
        return "core/gui";
    }

    private static CompareOp compareOp(int func) {
        return switch (func) {
            case 512 -> CompareOp.NEVER_PASS;
            case 513 -> CompareOp.GREATER_THAN;
            case 514 -> CompareOp.EQUAL;
            case 516 -> CompareOp.LESS_THAN;
            case 517 -> CompareOp.NOT_EQUAL;
            case 518 -> CompareOp.LESS_THAN_OR_EQUAL;
            case 519 -> CompareOp.ALWAYS_PASS;
            default -> CompareOp.GREATER_THAN_OR_EQUAL;
        };
    }

    private static StencilOperation stencilOp(int op) {
        return switch (op) {
            case 0 -> StencilOperation.ZERO;
            case 7681 -> StencilOperation.REPLACE;
            case 7682 -> StencilOperation.INCR;
            case 7683 -> StencilOperation.DECR;
            case 5386 -> StencilOperation.INVERT;
            case 34055 -> StencilOperation.INCR_WRAP;
            case 34056 -> StencilOperation.DECR_WRAP;
            default -> StencilOperation.KEEP;
        };
    }

    private static BlendFunction blendFunction(Key key) {
        return new BlendFunction(sourceFactor(key.srcRgb()), destinationFactor(key.dstRgb()),
                sourceFactor(key.srcAlpha()), destinationFactor(key.dstAlpha()));
    }

    private static BlendFactor sourceFactor(int value) {
        return switch (value) {
            case 0 -> BlendFactor.ZERO;
            case 1 -> BlendFactor.ONE;
            case 768 -> BlendFactor.SRC_COLOR;
            case 769 -> BlendFactor.ONE_MINUS_SRC_COLOR;
            case 770 -> BlendFactor.SRC_ALPHA;
            case 771 -> BlendFactor.ONE_MINUS_SRC_ALPHA;
            case 772 -> BlendFactor.DST_ALPHA;
            case 773 -> BlendFactor.ONE_MINUS_DST_ALPHA;
            case 774 -> BlendFactor.DST_COLOR;
            case 775 -> BlendFactor.ONE_MINUS_DST_COLOR;
            default -> BlendFactor.SRC_ALPHA;
        };
    }

    private static BlendFactor destinationFactor(int value) {
        return switch (value) {
            case 0 -> BlendFactor.ZERO;
            case 1 -> BlendFactor.ONE;
            case 768 -> BlendFactor.SRC_COLOR;
            case 769 -> BlendFactor.ONE_MINUS_SRC_COLOR;
            case 770 -> BlendFactor.SRC_ALPHA;
            case 771 -> BlendFactor.ONE_MINUS_SRC_ALPHA;
            case 772 -> BlendFactor.DST_ALPHA;
            case 773 -> BlendFactor.ONE_MINUS_DST_ALPHA;
            case 774 -> BlendFactor.DST_COLOR;
            case 775 -> BlendFactor.ONE_MINUS_DST_COLOR;
            default -> BlendFactor.ONE_MINUS_SRC_ALPHA;
        };
    }

    private record Key(VertexFormat format, PrimitiveTopology mode, boolean depthTest, int depthFunc,
                       boolean depthMask, boolean blend, int srcRgb, int dstRgb, int srcAlpha,
                       int dstAlpha, boolean cull, boolean polygonOffset, float biasScale, float biasUnits,
                       int colorWriteMask, boolean stencilEnabled, int stencilFunc, int stencilRef,
                       int stencilReadMask, int stencilWriteMask,
                       int stencilSfail, int stencilDpfail, int stencilDppass) {
    }
}
