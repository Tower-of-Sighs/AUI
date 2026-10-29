package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches immutable 26.3 {@link RenderPipeline}s for AUI's immediate-mode meshes.
 *
 * <p>1.21.5 removed the mutable global render state ({@code RenderSystem}
 * blend/depth/stencil setters) in favour of per-draw {@link RenderPipeline}
 * objects, so the state intent that common code expresses through
 * {@code AuiRenderService} is materialised here as cached pipeline
 * combinations. Stencil state is part of the key and is carried inside the
 * {@link DepthStencilState} (NeoForge 26.3 dropped
 * {@code RenderPipeline.Builder#withStencilTest}), which is how AUI's rounded
 * masks work on this version.</p>
 *
 * <p>Meshes are submitted through an explicit render pass bound to
 * {@link OutputTargets#currentTarget()}: a {@code RenderType} draw can no
 * longer be redirected to a specific framebuffer, and an offscreen compositing
 * group that AUI switched to (filter/opacity/mask/blend) would otherwise
 * silently receive nothing.</p>
 */
public final class PipelineCache {
    /** 26.3 replacement for 26.1's {@code RenderPipelines.MATRICES_PROJECTION_SNIPPET}. */
    private static final Snippet MATRICES_PROJECTION = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .buildSnippet();

    private static final Map<Key, RenderPipeline> PIPELINES = new ConcurrentHashMap<>();

    private PipelineCache() {
    }

    public static RenderPipeline pipeline(VertexFormat format, PrimitiveTopology mode,
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
        return PIPELINES.computeIfAbsent(key, PipelineCache::build);
    }

    private static RenderPipeline build(Key key) {
        String shader = shaderFor(key.format());
        RenderPipeline.Builder builder = RenderPipeline.builder(MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/mesh_" + Integer.toHexString(key.hashCode())))
                .withVertexShader(shader)
                .withFragmentShader(shader);
        // 26.3 validates every uniform the compiled shader declares against the
        // pipeline's bind groups, so the vanilla shaders' extra blocks/samplers
        // have to be declared per format.
        BindGroupLayout extraLayout = shaderExtraLayout(key.format());
        if (extraLayout != null) builder.withBindGroupLayout(extraLayout);
        builder.withVertexBinding(0, key.format())
                .withPrimitiveTopology(key.mode())
                .withColorTargetState(new ColorTargetState(
                        key.blend() ? Optional.of(blendFunction(key)) : Optional.<BlendFunction>empty(),
                        GpuFormat.RGBA8_UNORM,
                        key.colorWriteMask()))
                .withCull(key.cull());

        if (key.depthTest() || key.polygonOffset() || key.stencilEnabled()) {
            // AUI builds its own classic ortho projection, so the GL depth
            // convention still holds; 26.3's reversed-Z only applies to the
            // vanilla pipelines it renders the level with.
            CompareOp depth = key.depthTest() ? compareOp(key.depthFunc()) : CompareOp.ALWAYS_PASS;
            float biasScale = key.polygonOffset() ? key.biasScale() : 0.0f;
            float biasUnits = key.polygonOffset() ? key.biasUnits() : 0.0f;
            if (key.stencilEnabled()) {
                StencilPerFaceTest face = new StencilPerFaceTest(
                        stencilOp(key.stencilSfail()),
                        stencilOp(key.stencilDpfail()),
                        stencilOp(key.stencilDppass()),
                        compareOp(key.stencilFunc()));
                builder.withDepthStencilState(new DepthStencilState(depth, key.depthMask(), biasScale, biasUnits,
                        new StencilTest(face, key.stencilReadMask(), key.stencilWriteMask(), key.stencilRef())));
            } else {
                builder.withDepthStencilState(new DepthStencilState(depth, key.depthMask(), biasScale, biasUnits));
            }
        } else {
            builder.withDepthStencilState(Optional.empty());
        }

        return builder.build();
    }

    /**
     * Per-format bind group required by the vanilla shader that
     * {@link #shaderFor(VertexFormat)} picks, or {@code null} when the shader
     * needs nothing beyond {@code Projection}/{@code DynamicTransforms}:
     * <ul>
     *   <li>{@code core/position} imports {@code fog.glsl}, so it needs {@code Fog};</li>
     *   <li>{@code core/position_tex} samples {@code Sampler0};</li>
     *   <li>{@code core/gui} only uses the matrix blocks.</li>
     * </ul>
     * A layout must not be declared twice: the bind-group names have to stay
     * unique across the pipeline, so the {@code core/gui} case adds nothing.
     */
    private static BindGroupLayout shaderExtraLayout(VertexFormat format) {
        if (format == DefaultVertexFormat.POSITION) return BindGroupLayouts.FOG;
        if (format == DefaultVertexFormat.POSITION_TEX) return BindGroupLayouts.SAMPLER0;
        return null;
    }

    private static String shaderFor(VertexFormat format) {
        if (format == DefaultVertexFormat.POSITION) return "core/position";
        if (format == DefaultVertexFormat.POSITION_TEX) return "core/position_tex";
        return "core/gui";
    }

    /**
     * Maps a GL comparison enum to renderpearl's {@link CompareOp} without
     * reversing the ordering: AUI's ortho projection keeps GL's classic
     * near/far layout for both depth and stencil.
     */
    private static CompareOp compareOp(int func) {
        return switch (func) {
            case 512 -> CompareOp.NEVER_PASS;
            case 513 -> CompareOp.LESS_THAN;
            case 514 -> CompareOp.EQUAL;
            case 515 -> CompareOp.LESS_THAN_OR_EQUAL;
            case 516 -> CompareOp.GREATER_THAN;
            case 517 -> CompareOp.NOT_EQUAL;
            case 518 -> CompareOp.GREATER_THAN_OR_EQUAL;
            case 519 -> CompareOp.ALWAYS_PASS;
            default -> CompareOp.LESS_THAN_OR_EQUAL;
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
        return new BlendFunction(
                blendFactor(key.srcRgb(), BlendFactor.SRC_ALPHA),
                blendFactor(key.dstRgb(), BlendFactor.ONE_MINUS_SRC_ALPHA),
                blendFactor(key.srcAlpha(), BlendFactor.SRC_ALPHA),
                blendFactor(key.dstAlpha(), BlendFactor.ONE_MINUS_SRC_ALPHA));
    }

    /**
     * 26.3 merged {@code SourceFactor}/{@code DestFactor} into a single
     * {@link BlendFactor} enum. The 26.1 helpers had different fallbacks for
     * unknown GL enums ({@code SRC_ALPHA} for sources,
     * {@code ONE_MINUS_SRC_ALPHA} for destinations), so the fallback is a
     * parameter rather than a constant.
     */
    private static BlendFactor blendFactor(int value, BlendFactor fallback) {
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
            case 776 -> BlendFactor.SRC_ALPHA_SATURATE;
            case 34065 -> BlendFactor.CONSTANT_COLOR;
            case 34066 -> BlendFactor.ONE_MINUS_CONSTANT_COLOR;
            case 34067 -> BlendFactor.CONSTANT_ALPHA;
            case 34068 -> BlendFactor.ONE_MINUS_CONSTANT_ALPHA;
            default -> fallback;
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
