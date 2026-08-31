package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.render.OutputTargets;
import com.sighs.apricityui.render.PipelineCache;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.spi.MeshFormat;
import com.sighs.apricityui.spi.MeshMode;
import com.sighs.apricityui.spi.RenderHandle;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * NeoForge 26.1 render bridge.
 *
 * <p>26.1 removed the mutable {@code RenderSystem} global state (blend, depth
 * func, stencil, ...) in favour of immutable {@link RenderPipeline} objects, so
 * this service records the state intent that common code expresses through the
 * SPI and materialises it at {@link #submitMesh} time via
 * {@link PipelineCache}. Meshes are drawn with {@link RenderType#draw}, which
 * honours {@code RenderSystem.outputColorTextureOverride} — that is what makes
 * the same immediate-mode code work both on the main target and inside the
 * Picture-in-Picture texture that carries AUI's GUI in 26.1.</p>
 *
 * <p>Coordinate space: all AUI content is drawn in GUI coordinates with an
 * orthographic GUI projection and an identity model-view. The PIP renderer
 * installs that state explicitly, so there is no PIP-specific transform in
 * here (the previous port mis-mapped GUI coordinates through the vanilla PIP
 * pose, which places the content half a texture off-centre).</p>
 */
public final class RenderService implements AuiRenderService {
    public static final RenderService INSTANCE = new RenderService();
    private static final int FILTER_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4()
            .get();

    private final Matrix4f projection = new Matrix4f();
    private ProjectionMatrixBuffer projectionBuffer;

    private boolean depthTest;
    private int depthFunc = GL11.GL_LEQUAL;
    private boolean depthMask = true;
    private boolean blend;
    private int srcRgb = GL11.GL_SRC_ALPHA;
    private int dstRgb = GL11.GL_ONE_MINUS_SRC_ALPHA;
    private int srcAlpha = GL11.GL_ONE;
    private int dstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
    private boolean cull;
    private boolean polygonOffset;
    private float biasScale;
    private float biasUnits;
    private int colorWriteMask = 0xF;

    private boolean stencilTest;
    private int stencilWriteMask = 0xFF;
    private int stencilFunc = GL11.GL_ALWAYS;
    private int stencilRef;
    private int stencilReadMask = 0xFF;
    private int stencilSfail = GL11.GL_KEEP;
    private int stencilDpfail = GL11.GL_KEEP;
    private int stencilDppass = GL11.GL_KEEP;

    private RenderPipeline currentShader;
    private final GpuTextureView[] samplers = new GpuTextureView[8];
    private GpuBuffer filterVertexBuffer;
    private long filterVertexCapacity;
    private DynamicUniformStorage<FilterUniformData> filterUniformStorage;
    private final ByteBufferBuilder meshByteBuffer = new ByteBufferBuilder(786432);
    private int blitReadFbo;
    private int blitDrawFbo;

    // 26.1 stores custom shader values in std140 blocks instead of the old
    // per-uniform ShaderInstance setters. These fields mirror the FilterParams
    // block (9 vec4s) consumed by the filter/filter_blur fragment shaders.
    private float brightness = 1.0f;
    private float contrast = 1.0f;
    private float saturate = 1.0f;
    private float sepia;
    private float grayscale;
    private float invert;
    private float hueRotate;
    private float opacity = 1.0f;
    private float forceAlpha;
    private float clipEnabled;
    private float dynamicRangeLimit = 1.0f;
    private float radius;
    private float shadowOffsetX;
    private float shadowOffsetY;
    private float uvPerGuiX = 1.0f;
    private float uvPerGuiY = 1.0f;
    private float guiWidth;
    private float guiHeight;
    private float inputWidth;
    private float inputHeight;
    private float shadowColorR;
    private float shadowColorG;
    private float shadowColorB;
    private float shadowColorA;
    private float clipX;
    private float clipY;
    private float clipWidth;
    private float clipHeight;
    private float clipRadiusTopLeft;
    private float clipRadiusTopRight;
    private float clipRadiusBottomRight;
    private float clipRadiusBottomLeft;
    private float directionX;
    private float directionY;
    private float blendMode;

    private RenderService() {
    }

    @Override
    public void setProjectionMatrix(Matrix4f matrix) {
        projection.set(matrix);
        if (projectionBuffer == null) projectionBuffer = new ProjectionMatrixBuffer("apricityui");
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(matrix), ProjectionType.ORTHOGRAPHIC);
    }

    @Override
    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projection);
    }

    @Override public void enableDepthTest() { depthTest = true; }
    @Override public void disableDepthTest() { depthTest = false; }
    @Override public void setDepthFunc(int func) { depthFunc = func; }
    @Override public void setDepthMask(boolean write) { depthMask = write; }
    @Override public boolean isDepthTestEnabled() { return depthTest; }
    @Override public boolean isDepthMaskEnabled() { return depthMask; }
    @Override public void enableBlend() { blend = true; }

    @Override
    public void setBlendFunc(int srcFactor, int dstFactor) {
        srcRgb = srcFactor;
        dstRgb = dstFactor;
        // GlStateManager._blendFunc historically left the alpha factors at
        // their last separate setting; keep AUI's standard alpha setup.
        srcAlpha = GL11.GL_ONE;
        dstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
    }

    @Override
    public void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        this.srcRgb = srcRgb;
        this.dstRgb = dstRgb;
        this.srcAlpha = srcAlpha;
        this.dstAlpha = dstAlpha;
    }

    @Override public void disableBlend() { blend = false; }
    @Override public void enableCull() { cull = true; }
    @Override public void disableCull() { cull = false; }
    @Override public boolean isCullEnabled() { return cull; }
    @Override public void enablePolygonOffset() { polygonOffset = true; }
    @Override public void disablePolygonOffset() { polygonOffset = false; }
    @Override public void polygonOffset(float factor, float units) { biasScale = factor; biasUnits = units; }

    @Override public void enableScissorTest() { }

    @Override
    public void scissorBox(int x, int y, int width, int height) {
        RenderSystem.enableScissorForRenderTypeDraws(x, y, width, height);
    }

    @Override public void disableScissorTest() { RenderSystem.disableScissorForRenderTypeDraws(); }

    // ------------------------------------------------------------------
    // Stencil (NeoForge 26.1 exposes it as RenderPipeline state)
    // ------------------------------------------------------------------

    @Override public void enableStencilTest() { stencilTest = true; }
    @Override public void disableStencilTest() { stencilTest = false; }
    @Override public void setStencilMask(int mask) { stencilWriteMask = mask; }

    @Override
    public void setStencilFunc(int func, int ref, int mask) {
        stencilFunc = func;
        stencilRef = ref;
        stencilReadMask = mask;
    }

    @Override
    public void setStencilOp(int sfail, int dpfail, int dppass) {
        stencilSfail = sfail;
        stencilDpfail = dpfail;
        stencilDppass = dppass;
    }

    @Override
    public void clearStencilBuffer() {
        GpuTexture depthTexture = currentDepthTexture();
        if (depthTexture == null || !depthTexture.getFormat().hasStencilAspect()) return;
        // glClear(GL_STENCIL_BUFFER_BIT) is masked by the GL stencil write mask.
        // On 26.1 that mask only exists inside render passes (content pipelines
        // leave it at 0x00), so without forcing it here the clear silently
        // no-ops and stale mask regions leak into later masks' EQUAL tests.
        GlStateManager._stencilMask(0xFF);
        RenderSystem.getDevice().createCommandEncoder().clearStencilTexture(depthTexture, 0);
    }

    @Override
    public void setColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        colorWriteMask = (red ? 1 : 0) | (green ? 2 : 0) | (blue ? 4 : 0) | (alpha ? 8 : 0);
    }

    @Override
    public boolean isOnRenderThread() {
        try {
            return RenderSystem.isOnRenderThread();
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    @Override
    public void recordRenderCall(Runnable task) {
        if (isOnRenderThread()) {
            task.run();
            return;
        }
        // The client main thread is the render thread; queue for the next tick.
        Minecraft.getInstance().execute(task);
    }

    @Override
    public String getGLVersionString() {
        try {
            return GL11.glGetString(GL11.GL_VERSION);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public boolean supportsStencil() {
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            GpuTexture depth = main == null ? null : main.getDepthTexture();
            if (depth != null) return depth.getFormat().hasStencilAspect();
        } catch (RuntimeException | LinkageError ignored) {
        }
        // Unknown (headless / early init): keep the desktop behaviour rather
        // than latching the scissor fallback for the whole process.
        return true;
    }

    @Override
    public void flushSharedBuffers() {
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    // ------------------------------------------------------------------
    // Shaders / filter uniforms
    // ------------------------------------------------------------------

    @Override
    public void setShader(Object shader) {
        currentShader = (RenderPipeline) shader;
    }

    @Override public void setPositionColorShader() { currentShader = null; }
    @Override public void setShaderColor(float a, float r, float g, float b) { }
    @Override public Object getFilterShader() { return PipelineRegistry.getFilter(); }
    @Override public Object getFilterBlurShader() { return PipelineRegistry.getFilterBlur(); }
    @Override public Object getFilterBlendShader() { return PipelineRegistry.getFilterBlend(); }
    @Override public Object getFilterMaskShader(boolean luminance) {
        return luminance ? PipelineRegistry.getFilterMaskLum() : PipelineRegistry.getFilterMask();
    }
    @Override public Object getFilterMaskMergeShader(MaskCompositeOp op) {
        return switch (op) {
            case INTERSECT -> PipelineRegistry.getFilterMaskIntersect();
            case SUBTRACT -> PipelineRegistry.getFilterMaskSubtract();
            case EXCLUDE -> PipelineRegistry.getFilterMaskExclude();
        };
    }

    @Override
    public void setShaderUniformFloat(String name, float value) {
        switch (name) {
            case "Brightness" -> brightness = value;
            case "Contrast" -> contrast = value;
            case "Saturate" -> saturate = value;
            case "Sepia" -> sepia = value;
            case "Grayscale" -> grayscale = value;
            case "Invert" -> invert = value;
            case "HueRotate" -> hueRotate = value;
            case "Opacity" -> opacity = value;
            case "ForceAlpha" -> forceAlpha = value;
            case "ClipEnabled" -> clipEnabled = value;
            case "DynamicRangeLimit" -> dynamicRangeLimit = value;
            case "Radius" -> radius = value;
            case "BlendMode" -> blendMode = value;
            default -> { }
        }
    }

    @Override
    public void setShaderUniform2f(String name, float a, float b) {
        switch (name) {
            case "ShadowOffset" -> { shadowOffsetX = a; shadowOffsetY = b; }
            case "UvPerGuiPixel" -> { uvPerGuiX = a; uvPerGuiY = b; }
            case "GuiSize" -> { guiWidth = a; guiHeight = b; }
            case "InSize" -> { inputWidth = a; inputHeight = b; }
            case "Direction" -> { directionX = a; directionY = b; }
            default -> { }
        }
    }

    @Override public void setShaderUniform3f(String name, float a, float b, float c) { }

    @Override
    public void setShaderUniform4f(String name, float a, float b, float c, float d) {
        switch (name) {
            case "ShadowColor" -> {
                shadowColorR = a; shadowColorG = b; shadowColorB = c; shadowColorA = d;
            }
            case "ClipRect" -> {
                clipX = a; clipY = b; clipWidth = c; clipHeight = d;
            }
            case "ClipRadii" -> {
                clipRadiusTopLeft = a; clipRadiusTopRight = b;
                clipRadiusBottomRight = c; clipRadiusBottomLeft = d;
            }
            default -> { }
        }
    }

    @Override public void setShaderUniformI(String name, int value) { }

    // ------------------------------------------------------------------
    // Immediate meshes
    // ------------------------------------------------------------------

    @Override
    public MeshBuilder beginMesh(MeshMode mode, MeshFormat format) {
        VertexFormat vertexFormat = format == MeshFormat.POSITION
                ? DefaultVertexFormat.POSITION
                : format == MeshFormat.POSITION_TEX
                ? DefaultVertexFormat.POSITION_TEX
                : DefaultVertexFormat.POSITION_COLOR;
        VertexFormat.Mode vertexMode = mode == MeshMode.QUADS
                ? VertexFormat.Mode.QUADS
                : VertexFormat.Mode.TRIANGLES;
        // Match vanilla GuiRenderer: reuse the native byte-buffer allocator
        // while creating a short-lived format/mode view for each mesh.
        return MeshBuilder.of(new BufferBuilder(meshByteBuffer, vertexMode, vertexFormat));
    }

    @Override
    public void emitVertex(Object mesh, Matrix4f mat, float x, float y, float z,
                           int r, int g, int b, int a) {
        Vector3f pos = Base.projectPosition(mat, x, y, z, new Vector3f());
        ((BufferBuilder) mesh).addVertex(pos.x, pos.y, pos.z).setColor(r, g, b, a);
    }

    @Override
    public void emitVertexUV(Object mesh, Matrix4f mat, float x, float y, float z, float u, float v) {
        Vector3f pos = Base.projectPosition(mat, x, y, z, new Vector3f());
        ((BufferBuilder) mesh).addVertex(pos.x, pos.y, pos.z).setUv(u, v);
    }

    @Override
    public void submitMesh(Object mesh) {
        BufferBuilder buffer = (BufferBuilder) mesh;
        MeshData meshData;
        try {
            meshData = buffer.build();
        } catch (IllegalStateException ignored) {
            return;
        }
        if (meshData == null) return;
        try {
            if (currentShader != null) {
                drawWithPass(currentShader, meshData);
            } else {
                MeshData.DrawState state = meshData.drawState();
                RenderType type = PipelineCache.renderType(
                        state.format(), state.mode(), depthTest, depthFunc, depthMask, blend,
                        srcRgb, dstRgb, srcAlpha, dstAlpha, cull, polygonOffset, biasScale, biasUnits,
                        colorWriteMask,
                        stencilTest, stencilFunc, stencilRef, stencilReadMask, stencilWriteMask,
                        stencilSfail, stencilDpfail, stencilDppass);
                drawOnLogicalTarget(() -> type.draw(meshData));
            }
        } finally {
            meshData.close();
        }
    }

    private void drawOnLogicalTarget(Runnable draw) {
        RenderTarget logicalTarget = OutputTargets.currentTarget();
        if (logicalTarget == null || logicalTarget == Minecraft.getInstance().getMainRenderTarget()) {
            draw.run();
            return;
        }

        GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
        try {
            draw.run();
        } finally {
            RenderSystem.outputColorTextureOverride = previousColorOverride;
            RenderSystem.outputDepthTextureOverride = previousDepthOverride;
        }
    }

    /**
     * Draws a mesh with one of AUI's custom pipelines (filter composite, blur
     * passes, texture copies) through an explicit {@link RenderPass}, binding
     * the std140 {@code FilterParams} block and the Sampler0/Sampler1 views.
     */
    private void drawWithPass(RenderPipeline pipeline, MeshData meshData) {
        boolean filterPipeline = pipeline == PipelineRegistry.getFilter()
                || pipeline == PipelineRegistry.getFilterBlur()
                || pipeline == PipelineRegistry.getFilterBlend();
        boolean compositePipeline = pipeline == PipelineRegistry.getFilter()
                || pipeline == PipelineRegistry.getFilterBlend();
        RenderTarget output = OutputTargets.currentTarget();
        if (output == null) return;
        GpuTextureView colorView = output.getColorTextureView();
        GpuTextureView depthView = output.useDepth ? output.getDepthTextureView() : null;
        // During a PIP render the logical main target is redirected to the
        // live PIP attachments. Custom filter/blend passes must use the same
        // views or their output would be written to the hidden main target.
        if (output == Minecraft.getInstance().getMainRenderTarget()
                && RenderSystem.outputColorTextureOverride != null) {
            colorView = RenderSystem.outputColorTextureOverride;
        }
        if (output == Minecraft.getInstance().getMainRenderTarget()
                && RenderSystem.outputDepthTextureOverride != null) {
            depthView = RenderSystem.outputDepthTextureOverride;
        }
        if (colorView == null) return;

        GpuDevice device = RenderSystem.getDevice();
        long needed = meshData.vertexBuffer().remaining();
        if (filterVertexBuffer == null || filterVertexCapacity < needed) {
            if (filterVertexBuffer != null) filterVertexBuffer.close();
            filterVertexBuffer = device.createBuffer(() -> "apricityui_filter_mesh", 40, needed);
            filterVertexCapacity = needed;
        }
        device.createCommandEncoder().writeToBuffer(
                filterVertexBuffer.slice(0L, needed), meshData.vertexBuffer());

        // 26.1 keeps the command encoder occupied while a render pass is open;
        // upload the std140 block before creating the pass.
        GpuBufferSlice uniforms = filterPipeline ? updateFilterUniforms() : null;
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
        ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        RenderPass pass = device.createCommandEncoder().createRenderPass(
                () -> "apricityui_filter", colorView, OptionalInt.empty(),
                depthView, OptionalDouble.empty());
        try {
            pass.setPipeline(pipeline);
            if (uniforms != null) pass.setUniform("FilterParams", uniforms);
            if (scissor.enabled()) {
                pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            }
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            if (samplers[0] != null) {
                pass.bindTexture("Sampler0", samplers[0],
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            }
            if (compositePipeline && samplers[1] != null) {
                pass.bindTexture("Sampler1", samplers[1],
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            }
            pass.setVertexBuffer(0, filterVertexBuffer);
            MeshData.DrawState state = meshData.drawState();
            AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(state.mode());
            pass.setIndexBuffer(indices.getBuffer(state.indexCount()), indices.type());
            pass.drawIndexed(0, 0, state.indexCount(), 1);
        } finally {
            pass.close();
        }
    }

    private GpuBufferSlice updateFilterUniforms() {
        if (filterUniformStorage == null) {
            filterUniformStorage = new DynamicUniformStorage<>(
                    "apricityui_filter_uniforms", FILTER_UNIFORM_SIZE, 16);
        }
        return filterUniformStorage.writeUniform(new FilterUniformData(
                brightness, grayscale, invert, hueRotate,
                opacity, forceAlpha, clipEnabled, radius, blendMode,
                shadowOffsetX, shadowOffsetY, uvPerGuiX, uvPerGuiY,
                guiWidth, guiHeight, inputWidth, inputHeight,
                shadowColorR, shadowColorG, shadowColorB, shadowColorA,
                clipX, clipY, clipWidth, clipHeight,
                clipRadiusTopLeft, clipRadiusTopRight,
                clipRadiusBottomRight, clipRadiusBottomLeft,
                directionX, directionY, contrast, saturate, sepia,
                dynamicRangeLimit,
                currentShader == PipelineRegistry.getFilterBlend()));
    }

    private record FilterUniformData(
            float brightness, float grayscale, float invert, float hueRotate,
            float opacity, float forceAlpha, float clipEnabled,
            float radius, float blendMode,
            float shadowOffsetX, float shadowOffsetY, float uvPerGuiX, float uvPerGuiY,
            float guiWidth, float guiHeight, float inputWidth, float inputHeight,
            float shadowColorR, float shadowColorG, float shadowColorB, float shadowColorA,
            float clipX, float clipY, float clipWidth, float clipHeight,
            float clipRadiusTopLeft, float clipRadiusTopRight,
            float clipRadiusBottomRight, float clipRadiusBottomLeft,
            float directionX, float directionY,
            float contrast, float saturate, float sepia, float dynamicRangeLimit,
            boolean blendPipeline) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(brightness, grayscale, invert, hueRotate)
                    .putVec4(opacity, forceAlpha, clipEnabled,
                            blendPipeline ? blendMode : radius)
                    .putVec4(shadowOffsetX, shadowOffsetY, uvPerGuiX, uvPerGuiY)
                    .putVec4(guiWidth, guiHeight, inputWidth, inputHeight)
                    .putVec4(shadowColorR, shadowColorG, shadowColorB, shadowColorA)
                    .putVec4(clipX, clipY, clipWidth, clipHeight)
                    .putVec4(clipRadiusTopLeft, clipRadiusTopRight,
                            clipRadiusBottomRight, clipRadiusBottomLeft)
                    .putVec4(directionX, directionY, 0.0f, 0.0f)
                    .putVec4(contrast, saturate, sepia, dynamicRangeLimit)
                    .get();
        }
    }

    // ------------------------------------------------------------------
    // Batched textured quads (images)
    // ------------------------------------------------------------------

    @Override
    public Object beginTextureBatch(RenderHandle render) {
        RenderType type = render.as();
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        return new TextureBatchHandle(source, type);
    }

    @Override
    public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                                float u0, float v0, float u1, float v1) {
        emitTextureQuad(batch, mat, x, y, width, height, u0, v0, u1, v1, 0xFFFFFFFF);
    }

    @Override
    public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                                float u0, float v0, float u1, float v1, int colorArgb) {
        TextureBatchHandle handle = (TextureBatchHandle) batch;
        VertexConsumer consumer = handle.source().getBuffer(handle.renderType());
        int a = (colorArgb >>> 24) & 0xFF;
        int r = (colorArgb >>> 16) & 0xFF;
        int g = (colorArgb >>> 8) & 0xFF;
        int b = colorArgb & 0xFF;
        Vector3f position = new Vector3f();
        consumer.addVertex(Base.projectPosition(mat, x, y + height, 0, position))
                .setColor(r, g, b, a).setUv(u0, v1);
        consumer.addVertex(Base.projectPosition(mat, x + width, y + height, 0, position))
                .setColor(r, g, b, a).setUv(u1, v1);
        consumer.addVertex(Base.projectPosition(mat, x + width, y, 0, position))
                .setColor(r, g, b, a).setUv(u1, v0);
        consumer.addVertex(Base.projectPosition(mat, x, y, 0, position))
                .setColor(r, g, b, a).setUv(u0, v0);
    }

    @Override
    public void flushTextureBatch(Object batch, RenderHandle render) {
        TextureBatchHandle handle = (TextureBatchHandle) batch;
        drawOnLogicalTarget(() -> handle.source().endBatch(handle.renderType()));
    }

    // ------------------------------------------------------------------
    // Render targets
    // ------------------------------------------------------------------

    @Override
    public FboHandle createOffscreenTarget(int width, int height, boolean useDepth) {
        // Stencil comes along with depth so Mask's stencil path also works
        // inside filter ping-pong targets (rounded clips on filtered content).
        TextureTarget target = new TextureTarget("apricityui_filter", width, height, useDepth, useDepth);
        return FboHandle.of(target, width, height);
    }

    @Override
    public FboHandle getMainRenderTarget() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        OutputTargets.setCurrent(target);
        return FboHandle.of(target, target.width, target.height);
    }

    @Override
    public void enableStencil(FboHandle target) {
        // The main target receives its stencil attachment at startup via
        // ConfigureMainRenderTargetEvent, and AUI offscreen targets are created
        // with one; nothing to retrofit here.
    }

    @Override
    public void destroyBuffers(FboHandle target) {
        RenderTarget renderTarget = target.as();
        renderTarget.destroyBuffers();
    }

    @Override
    public void clear(FboHandle target, float r, float g, float b, float a) {
        RenderTarget renderTarget = target.as();
        GpuTexture color = renderTarget.getColorTexture();
        if (color == null) return;
        int argb = ((int) (a * 255) << 24)
                | ((int) (r * 255) << 16)
                | ((int) (g * 255) << 8)
                | (int) (b * 255);
        GpuTexture depth = renderTarget.getDepthTexture();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if (depth != null) {
            encoder.clearColorAndDepthTextures(color, argb, depth, 1.0);
            if (depth.getFormat().hasStencilAspect()) {
                encoder.clearStencilTexture(depth, 0);
            }
        } else {
            encoder.clearColorTexture(color, argb);
        }
    }

    @Override
    public void bindWrite(FboHandle target, boolean setViewport) {
        OutputTargets.setCurrent(target.as());
    }

    @Override
    public void bindColorTexture(FboHandle target, int unit) {
        if (unit < 0 || unit >= samplers.length) return;
        RenderTarget renderTarget = target.as();
        GpuTextureView view = renderTarget.getColorTextureView();
        if (renderTarget == Minecraft.getInstance().getMainRenderTarget()
                && RenderSystem.outputColorTextureOverride != null) {
            view = RenderSystem.outputColorTextureOverride;
        }
        samplers[unit] = view;
    }

    @Override
    public RenderStateScope pushFilterRenderState() {
        if (filterUniformStorage != null) filterUniformStorage.endFrame();
        return new PipelineFilterState();
    }

    private final class PipelineFilterState implements RenderStateScope {
        private final boolean previousDepthTest = depthTest;
        private final int previousDepthFunc = depthFunc;
        private final boolean previousDepthMask = depthMask;
        private final boolean previousBlend = blend;
        private final int previousSrcRgb = srcRgb;
        private final int previousDstRgb = dstRgb;
        private final int previousSrcAlpha = srcAlpha;
        private final int previousDstAlpha = dstAlpha;
        private final boolean previousCull = cull;
        private final RenderPipeline previousShader = currentShader;
        private final GpuTextureView previousSampler0 = samplers[0];
        private final GpuTextureView previousSampler1 = samplers[1];
        private final float previousBlendMode = blendMode;
        private boolean closed;

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            depthTest = previousDepthTest;
            depthFunc = previousDepthFunc;
            depthMask = previousDepthMask;
            blend = previousBlend;
            srcRgb = previousSrcRgb;
            dstRgb = previousDstRgb;
            srcAlpha = previousSrcAlpha;
            dstAlpha = previousDstAlpha;
            cull = previousCull;
            currentShader = previousShader;
            samplers[0] = previousSampler0;
            samplers[1] = previousSampler1;
            blendMode = previousBlendMode;
        }
    }

    /**
     * Copies a source region into {@code destination}, scaled, via a hardware
     * framebuffer blit (DSA {@code glBlitNamedFramebuffer}) instead of a shader
     * sampling pass.
     *
     * <p>Backdrop-filter must snapshot the PIP texture while it is still the
     * active render target (the {@code outputColorTextureOverride}). On some
     * drivers sampling a texture that was just rendered to through an FBO
     * attachment returns stale contents (the pre-render cleared state) from the
     * sampler cache — every later sample of that texture in the frame reads
     * black. A framebuffer blit reads the attachment storage directly (the same
     * path as glReadPixels), so it always sees the freshly rendered content and
     * additionally forces the driver to sync the texture for later samples. A
     * full-size readback, glMemoryBarrier and glFlush/glFinish all fail to
     * un-stick the sampler cache; only this resolve path works.</p>
     *
     * @return true if the blit happened; false if the hardware path is unusable
     *         and the caller should fall back to the sampling quad.
     */
    private boolean blitRegionHardware(GpuTextureView sourceView, GpuTexture sourceTexture,
                                       RenderTarget destination, int srcX0, int srcY0,
                                       int srcX1, int srcY1) {
        if (!(sourceTexture instanceof com.mojang.blaze3d.opengl.GlTexture glSrc)) return false;
        GpuTexture destinationTexture = destination.getColorTexture();
        if (!(destinationTexture instanceof com.mojang.blaze3d.opengl.GlTexture glDst)) return false;
        int dstW = destination.width;
        int dstH = destination.height;
        if (dstW <= 0 || dstH <= 0) return false;

        int readFbo = blitReadFbo();
        int drawFbo = blitDrawFbo();
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture(readFbo, 36064, glSrc.glId(), 0);
        org.lwjgl.opengl.ARBDirectStateAccess.glNamedFramebufferTexture(drawFbo, 36064, glDst.glId(), 0);
        if (org.lwjgl.opengl.ARBDirectStateAccess.glCheckNamedFramebufferStatus(readFbo, 36009) != 36053
                || org.lwjgl.opengl.ARBDirectStateAccess.glCheckNamedFramebufferStatus(drawFbo, 36009) != 36053) {
            return false;
        }

        ScissorState previous = RenderSystem.getScissorStateForRenderTypeDraws();
        if (previous.enabled()) RenderSystem.disableScissorForRenderTypeDraws();
        try {
            // 36009 = GL_FRAMEBUFFER, 0x4000 = GL_COLOR_BUFFER_BIT, 0x2601 = GL_LINEAR
            org.lwjgl.opengl.ARBDirectStateAccess.glBlitNamedFramebuffer(
                    readFbo, drawFbo, srcX0, srcY0, srcX1, srcY1, 0, 0, dstW, dstH, 0x4000, 0x2601);
            return true;
        } finally {
            if (previous.enabled()) {
                RenderSystem.enableScissorForRenderTypeDraws(
                        previous.x(), previous.y(), previous.width(), previous.height());
            }
        }
    }

    private int blitReadFbo() {
        if (blitReadFbo == 0) blitReadFbo = org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
        return blitReadFbo;
    }

    private int blitDrawFbo() {
        if (blitDrawFbo == 0) blitDrawFbo = org.lwjgl.opengl.ARBDirectStateAccess.glCreateFramebuffers();
        return blitDrawFbo;
    }

    @Override
    public void blitFramebuffer(FboHandle source, FboHandle target,
                                int srcX0, int srcY0, int srcX1, int srcY1) {
        RenderTarget src = source.as();
        RenderTarget dst = target.as();
        // During a PIP render the logical main RenderTarget is only a
        // placeholder. Vanilla redirects the actual color attachment to the PIP
        // texture, so backdrop-filter must snapshot that texture rather than
        // the world framebuffer behind it.
        GpuTextureView sourceView = src.getColorTextureView();
        GpuTexture sourceTexture = sourceView == null ? null : sourceView.texture();
        boolean sourceIsLiveOverride = src == Minecraft.getInstance().getMainRenderTarget()
                && RenderSystem.outputColorTextureOverride != null;
        if (sourceIsLiveOverride) {
            sourceView = RenderSystem.outputColorTextureOverride;
            sourceTexture = sourceView.texture();
        }
        GpuTexture destinationTexture = dst.getColorTexture();
        if (sourceView == null || sourceTexture == null || destinationTexture == null) return;
        int sourceWidth = sourceTexture.getWidth(0);
        int sourceHeight = sourceTexture.getHeight(0);
        int destinationWidth = destinationTexture.getWidth(0);
        int destinationHeight = destinationTexture.getHeight(0);
        int boundedSrcX0 = Math.max(0, Math.min(srcX0, sourceWidth));
        int boundedSrcY0 = Math.max(0, Math.min(srcY0, sourceHeight));
        int boundedSrcX1 = Math.max(boundedSrcX0, Math.min(srcX1, sourceWidth));
        int boundedSrcY1 = Math.max(boundedSrcY0, Math.min(srcY1, sourceHeight));
        int copyW = boundedSrcX1 - boundedSrcX0;
        int copyH = boundedSrcY1 - boundedSrcY0;
        if (copyW <= 0 || copyH <= 0 || destinationWidth <= 0 || destinationHeight <= 0) return;

        boolean canCopy = (sourceTexture.usage() & GpuTexture.USAGE_COPY_SRC) != 0
                && (destinationTexture.usage() & GpuTexture.USAGE_COPY_DST) != 0
                && copyW == destinationWidth
                && copyH == destinationHeight;
        if (canCopy) {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    sourceTexture, destinationTexture, boundedSrcX0, boundedSrcY0, 0,
                    0, 0, copyW, copyH);
            return;
        }

        // Sampling the PIP texture while it is the active render target returns
        // stale (black) contents from the sampler cache on this driver. Use a
        // hardware framebuffer blit for that case; the generic sampling quad
        // remains for offscreen-to-offscreen copies (blur passes), which are
        // unaffected.
        if (sourceIsLiveOverride
                && blitRegionHardware(sourceView, sourceTexture, dst,
                boundedSrcX0, boundedSrcY0, boundedSrcX1, boundedSrcY1)) {
            return;
        }

        // Vanilla's PIP texture is sampleable and renderable, but deliberately
        // has no COPY_SRC usage. Render a scaled region through a sampler when
        // a direct texture copy is unavailable (or when downsampling is needed).
        drawTextureRegion(sourceView, sourceTexture, dst,
                boundedSrcX0, boundedSrcY0, boundedSrcX1, boundedSrcY1);
    }

    private void drawTextureRegion(GpuTextureView sourceView, GpuTexture sourceTexture,
                                   RenderTarget destination, int sourceX0, int sourceY0,
                                   int sourceX1, int sourceY1) {
        GpuTexture destinationTexture = destination.getColorTexture();
        if (sourceView == null || sourceTexture == null || destinationTexture == null) return;

        RenderTarget previousTarget = OutputTargets.currentTarget();
        RenderPipeline previousShader = currentShader;
        GpuTextureView previousSampler0 = samplers[0];
        GpuTextureView previousSampler1 = samplers[1];
        Matrix4f previousProjection = new Matrix4f(projection);
        ScissorState previousScissor = RenderSystem.getScissorStateForRenderTypeDraws();
        var modelView = RenderSystem.getModelViewStack();

        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(destinationTexture, 0);
        OutputTargets.setCurrent(destination);
        currentShader = PipelineRegistry.getFilterCopy();
        samplers[0] = sourceView;

        boolean modelViewPushed = false;
        try {
            RenderSystem.disableScissorForRenderTypeDraws();
            setProjectionMatrix(new Matrix4f().setOrtho(
                    0, destination.width, destination.height, 0, -1000, 1000));
            modelView.pushMatrix();
            modelView.identity();
            modelViewPushed = true;

            float textureWidth = Math.max(1.0f, sourceTexture.getWidth(0));
            float textureHeight = Math.max(1.0f, sourceTexture.getHeight(0));
            float u0 = sourceX0 / textureWidth;
            float u1 = sourceX1 / textureWidth;
            float v0 = sourceY0 / textureHeight;
            float v1 = sourceY1 / textureHeight;
            MeshBuilder mesh = beginMesh(MeshMode.QUADS, MeshFormat.POSITION_TEX);
            Matrix4f identity = new Matrix4f();
            mesh.vertexUV(identity, 0, destination.height, 0, u0, v0);
            mesh.vertexUV(identity, destination.width, destination.height, 0, u1, v0);
            mesh.vertexUV(identity, destination.width, 0, 0, u1, v1);
            mesh.vertexUV(identity, 0, 0, 0, u0, v1);
            mesh.submit();
        } finally {
            if (modelViewPushed) modelView.popMatrix();
            if (previousScissor.enabled()) {
                RenderSystem.enableScissorForRenderTypeDraws(
                        previousScissor.x(), previousScissor.y(),
                        previousScissor.width(), previousScissor.height());
            } else {
                RenderSystem.disableScissorForRenderTypeDraws();
            }
            setProjectionMatrix(previousProjection);
            currentShader = previousShader;
            samplers[0] = previousSampler0;
            samplers[1] = previousSampler1;
            OutputTargets.setCurrent(previousTarget);
        }
    }

    @Override
    public boolean currentTargetHasStencil() {
        // The live draw target may be the PIP override rather than the main
        // target; vanilla allocates the PIP depth attachment as DEPTH32 (no
        // stencil bits), so Mask's stencil clips would silently no-op there.
        GpuTexture depthTexture = currentDepthTexture();
        return depthTexture != null && depthTexture.getFormat().hasStencilAspect();
    }

    private GpuTexture currentDepthTexture() {
        if (RenderSystem.outputDepthTextureOverride != null) {
            return RenderSystem.outputDepthTextureOverride.texture();
        }
        RenderTarget target = OutputTargets.currentTarget();
        if (target == null || !target.useDepth) return null;
        return target.getDepthTexture();
    }

    // ------------------------------------------------------------------
    // Textures
    // ------------------------------------------------------------------

    @Override
    public Object createDynamicTexture(String name, Object nativeImage, boolean linear) {
        return new DynamicTexture(() -> name, (NativeImage) nativeImage);
    }

    @Override public void uploadTextureRegion(Object texture, Object nativeImage, int x, int y,
                                               int width, int height, boolean linear) {
        ((DynamicTexture) texture).upload();
    }

    @Override public void closeTexture(Object texture) { ((DynamicTexture) texture).close(); }

    @Override
    public void registerTexture(Object texture, Object location) {
        Minecraft.getInstance().getTextureManager().register((Identifier) location, (AbstractTexture) texture);
    }

    @Override
    public void releaseTexture(Object location) {
        Minecraft.getInstance().getTextureManager().release((Identifier) location);
    }

    @Override public void setImagePixel(Object nativeImage, int x, int y, int pixel) {
        ((NativeImage) nativeImage).setPixelABGR(x, y, pixel);
    }

    @Override
    public void writeImagePixels(Object nativeImage, int x, int y, int width, int height, int[] abgrPixels) {
        NativeImage image = (NativeImage) nativeImage;
        int index = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                image.setPixelABGR(x + col, y + row, abgrPixels[index++]);
            }
        }
    }

    private record TextureBatchHandle(MultiBufferSource.BufferSource source, RenderType renderType) { }
}
