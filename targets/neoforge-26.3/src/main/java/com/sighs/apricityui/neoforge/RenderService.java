package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.sighs.apricityui.render.OutputTargets;
import com.sighs.apricityui.render.PipelineCache;
import com.sighs.apricityui.render.RenderBatchStats;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.spi.MeshFormat;
import com.sighs.apricityui.spi.MeshMode;
import com.sighs.apricityui.spi.RenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * NeoForge 26.3 render bridge.
 *
 * <p>26.3 keeps the immutable {@link RenderPipeline} model of 26.1 but deleted the
 * global output redirect ({@code RenderSystem.outputColorTextureOverride}) and the
 * shared {@code MultiBufferSource}: the {@link RenderPass} a draw is recorded into
 * <em>is</em> the destination. This service therefore records the state intent that
 * common code expresses through the SPI, materialises it at {@link #submitMesh} time
 * via {@link PipelineCache}, and draws every mesh with an explicit pass over
 * {@link OutputTargets#currentTarget()}.</p>
 *
 * <p>Batched textured quads ({@link #beginTextureBatch} / {@link #flushTextureBatch})
 * are queued into a local {@link BufferBuilder} and drawn in one pass at flush time,
 * built from {@code RenderType.prepare()} (pipeline + {@code DynamicTransforms} +
 * sampler bindings), which is the 26.3 shape of the old
 * {@code MultiBufferSource}/{@code endBatch} pair.</p>
 *
 * <p>Coordinate space: all AUI content is drawn in GUI coordinates with an
 * orthographic GUI projection and an identity model-view. The PIP renderer
 * installs that state explicitly, so there is no PIP-specific transform in
 * here (the previous port mis-mapped GUI coordinates through the vanilla PIP
 * pose, which places the content half a texture off-centre). {@link
 * #setProjectionMatrix} additionally reshapes every incoming ortho onto the
 * device's clip-space depth range, because 26.3 runs the GL backend with
 * {@code ZERO_TO_ONE} clip control while AUI's matrices (like vanilla's before
 * 26.3) assume {@code [-1, 1]} — see {@link #deviceClipSpace}.</p>
 */
public final class RenderService implements AuiRenderService {
    public static final RenderService INSTANCE = new RenderService();

    private final Matrix4f projection = new Matrix4f();
    private final GpuTextureView[] samplers = new GpuTextureView[8];
    /** Vertex arena shared by every immediate mesh built through {@link #beginMesh}. */
    private final ByteBufferBuilder meshByteBuffer = new ByteBufferBuilder(786432);
    /**
     * Separate arena for {@link #beginTextureBatch}: a texture batch may stay open
     * across a mesh submission, and both builders would otherwise interleave their
     * reservations in one arena.
     */
    private final ByteBufferBuilder imageByteBuffer = new ByteBufferBuilder(786432);
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
    /** Upload arena for the mesh a pass is about to draw (filter, image and plain meshes). */
    private GpuBuffer meshVertexBuffer;
    private long meshVertexCapacity;
    private GpuBuffer filterUniformBuffer;
    // Custom filter values travel in the std140 block below instead of the old
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
    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projection);
    }

    @Override
    public void setProjectionMatrix(Matrix4f matrix) {
        // The stored matrix stays exactly what the caller supplied (common code
        // saves and restores it through getProjectionMatrix()), while the buffer
        // handed to the GPU is reshaped to the device's clip-space depth range.
        projection.set(matrix);
        if (projectionBuffer == null) projectionBuffer = new ProjectionMatrixBuffer("apricityui");
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(deviceClipSpace(matrix)),
                ProjectionType.ORTHOGRAPHIC);
    }

    /**
     * Maps a GUI ortho built for OpenGL's {@code [-1, 1]} NDC depth range onto
     * the device's actual clip space.
     *
     * <p>26.3's GL backend selects {@code ARB_clip_control(LOWER_LEFT, ZERO_TO_ONE)}
     * ({@code GlDevice}), so NDC z must lie in <b>[0, 1]</b>: every AUI ortho
     * (all of them use {@code Matrix4f.setOrtho}, which emits the classic
     * -1..1 range) would otherwise map every element with a positive paint
     * depth — i.e. all document content, which starts at z = 1 — to a negative
     * z and get clipped away. Visually that looks like "nothing renders" while
     * the clear colour still lands. Remapping is the plain {@code z' = 0.5z + 0.5w}
     * scale/offset applied to the z row, so depth stays monotonic in paint z.</p>
     */
    private static Matrix4f deviceClipSpace(Matrix4f matrix) {
        if (!RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()) return matrix;
        Matrix4f out = new Matrix4f(matrix);
        float w0 = matrix.m03();
        float w1 = matrix.m13();
        float w2 = matrix.m23();
        float w3 = matrix.m33();
        out.m02(0.5f * (matrix.m02() + w0));
        out.m12(0.5f * (matrix.m12() + w1));
        out.m22(0.5f * (matrix.m22() + w2));
        out.m32(0.5f * (matrix.m32() + w3));
        return out;
    }

    @Override
    public void enableDepthTest() {
        depthTest = true;
    }

    @Override
    public void disableDepthTest() {
        depthTest = false;
    }

    @Override
    public void setDepthFunc(int func) {
        depthFunc = func;
    }

    @Override
    public void setDepthMask(boolean write) {
        depthMask = write;
    }

    @Override
    public boolean isDepthTestEnabled() {
        return depthTest;
    }

    @Override
    public boolean isDepthMaskEnabled() {
        return depthMask;
    }

    @Override
    public void enableBlend() {
        blend = true;
    }

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

    @Override
    public void disableBlend() {
        blend = false;
    }

    @Override
    public void enableCull() {
        cull = true;
    }

    @Override
    public void disableCull() {
        cull = false;
    }

    @Override
    public boolean isCullEnabled() {
        return cull;
    }

    @Override
    public void enablePolygonOffset() {
        polygonOffset = true;
    }

    @Override
    public void disablePolygonOffset() {
        polygonOffset = false;
    }

    @Override
    public void polygonOffset(float factor, float units) {
        biasScale = factor;
        biasUnits = units;
    }

    @Override
    public void enableScissorTest() {
    }

    @Override
    public void scissorBox(int x, int y, int width, int height) {
        RenderSystem.enableScissorForRenderTypeDraws(x, y, width, height);
    }

    @Override
    public void disableScissorTest() {
        RenderSystem.disableScissorForRenderTypeDraws();
    }

    // ------------------------------------------------------------------
    // Stencil (NeoForge 26.3 carries it inside DepthStencilState)
    // ------------------------------------------------------------------

    @Override
    public void enableStencilTest() {
        stencilTest = true;
    }

    @Override
    public void disableStencilTest() {
        stencilTest = false;
    }

    @Override
    public void setStencilMask(int mask) {
        stencilWriteMask = mask;
    }

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
        // 26.3's clearStencilTexture forces the GL stencil write mask to -1 itself,
        // so the 26.1 GlStateManager._stencilMask(0xFF) pre-step is no longer needed.
        RenderSystem.getDevice().createCommandEncoder().clearStencilTexture(depthTexture, 0);
    }

    @Override
    public void clearDepthBuffer() {
        GpuTexture depthTexture = currentDepthTexture();
        if (depthTexture != null) {
            // AUI keeps the classic (non reversed-Z) convention on the targets it
            // owns: depth clear 1.0 with LEQUAL pipelines.
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, 1.0);
        }
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
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
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
        // 26.3 has no shared MultiBufferSource: every image/text batch is drawn by
        // flushTextureBatch through its own explicit pass, so there is nothing left to
        // end here. Keep the frame statistic — per-item drawing multiplies the call
        // count and issue #95 tracks it.
        RenderBatchStats.recordSharedFlush();
    }

    // ------------------------------------------------------------------
    // Shaders / filter uniforms
    // ------------------------------------------------------------------

    @Override
    public void setShader(Object shader) {
        currentShader = (RenderPipeline) shader;
    }

    @Override
    public void setPositionColorShader() {
        currentShader = null;
    }

    @Override
    public void setShaderColor(float a, float r, float g, float b) {
    }

    @Override
    public Object getFilterShader() {
        return PipelineRegistry.getFilter();
    }

    @Override
    public Object getFilterBlurShader() {
        return PipelineRegistry.getFilterBlur();
    }

    @Override
    public Object getFilterBlendShader() {
        return PipelineRegistry.getFilterBlend();
    }

    @Override
    public Object getFilterMaskShader(boolean luminance) {
        return luminance ? PipelineRegistry.getFilterMaskLum() : PipelineRegistry.getFilterMask();
    }

    @Override
    public Object getFilterMaskMergeShader(MaskCompositeOp op) {
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
            default -> {
            }
        }
    }

    @Override
    public void setShaderUniform2f(String name, float a, float b) {
        switch (name) {
            case "ShadowOffset" -> {
                shadowOffsetX = a;
                shadowOffsetY = b;
            }
            case "UvPerGuiPixel" -> {
                uvPerGuiX = a;
                uvPerGuiY = b;
            }
            case "GuiSize" -> {
                guiWidth = a;
                guiHeight = b;
            }
            case "InSize" -> {
                inputWidth = a;
                inputHeight = b;
            }
            case "Direction" -> {
                directionX = a;
                directionY = b;
            }
            default -> {
            }
        }
    }

    @Override
    public void setShaderUniform3f(String name, float a, float b, float c) {
    }

    @Override
    public void setShaderUniform4f(String name, float a, float b, float c, float d) {
        switch (name) {
            case "ShadowColor" -> {
                shadowColorR = a;
                shadowColorG = b;
                shadowColorB = c;
                shadowColorA = d;
            }
            case "ClipRect" -> {
                clipX = a;
                clipY = b;
                clipWidth = c;
                clipHeight = d;
            }
            case "ClipRadii" -> {
                clipRadiusTopLeft = a;
                clipRadiusTopRight = b;
                clipRadiusBottomRight = c;
                clipRadiusBottomLeft = d;
            }
            default -> {
            }
        }
    }

    @Override
    public void setShaderUniformI(String name, int value) {
    }

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
        // 26.3 replaced VertexFormat.Mode with PrimitiveTopology.
        PrimitiveTopology topology = mode == MeshMode.QUADS
                ? PrimitiveTopology.QUADS
                : PrimitiveTopology.TRIANGLES;
        // Match vanilla GuiRenderer: reuse the native byte-buffer allocator
        // while creating a short-lived format/mode view for each mesh.
        return MeshBuilder.of(new BufferBuilder(meshByteBuffer, topology, vertexFormat));
    }

    @Override
    public void emitVertex(Object mesh, Matrix4f mat, float x, float y, float z,
                           int r, int g, int b, int a) {
        Vector3f pos = mat.transformPosition(x, y, z, new Vector3f());
        ((BufferBuilder) mesh).addVertex(pos.x, pos.y, pos.z).setColor(r, g, b, a);
    }

    @Override
    public void emitVertexUV(Object mesh, Matrix4f mat, float x, float y, float z, float u, float v) {
        Vector3f pos = mat.transformPosition(x, y, z, new Vector3f());
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
                // Draw through an explicit pass so the mesh follows AUI's logical
                // target; a RenderType draw would need a caller-supplied pass and
                // cannot carry the FilterParams uniform.
                RenderPipeline pipeline = PipelineCache.pipeline(
                        state.format(), state.primitiveTopology(), depthTest, depthFunc, depthMask, blend,
                        srcRgb, dstRgb, srcAlpha, dstAlpha, cull, polygonOffset, biasScale, biasUnits,
                        colorWriteMask,
                        stencilTest, stencilFunc, stencilRef, stencilReadMask, stencilWriteMask,
                        stencilSfail, stencilDpfail, stencilDppass);
                drawWithPass(pipeline, meshData);
            }
        } finally {
            meshData.close();
        }
    }

    /**
     * Draws a mesh with one of AUI's custom pipelines (filter composite, blur
     * passes, texture copies) through an explicit {@link RenderPass}, binding
     * the std140 {@code FilterParams} block and the Sampler0/Sampler1 views.
     *
     * <p>26.3 has no output override: the pass created over
     * {@link OutputTargets#currentTarget()} is the destination, so the PIP renderer
     * only has to install its own target there.</p>
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
        GpuTextureView depthView = output.hasDepth() ? output.getDepthTextureView() : null;
        if (colorView == null) return;

        MeshData.DrawState state = meshData.drawState();
        GpuBuffer vertexBuffer = uploadMeshVertices(meshData, "apricityui_mesh");

        // 26.3 keeps one command encoder and forbids issuing commands while a pass is
        // open; upload the std140 block before creating the pass (same ordering as 26.1).
        GpuBuffer uniforms = filterPipeline ? updateFilterUniforms() : null;
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(),
                new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
        ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        // 26.3 validates the scissor against the pass render area and rejects
        // empty or out-of-bounds rectangles instead of clipping them silently
        // (26.1's GL scissor just intersected). Elements scrolled past the
        // viewport can produce either, so clamp here and drop fully clipped draws.
        if (scissor.enabled()) {
            int scissorX = Math.max(0, scissor.x());
            int scissorY = Math.max(0, scissor.y());
            int scissorWidth = Math.min(output.width, scissor.x() + scissor.width()) - scissorX;
            int scissorHeight = Math.min(output.height, scissor.y() + scissor.height()) - scissorY;
            if (scissorWidth <= 0 || scissorHeight <= 0) return;
            ScissorState clamped = new ScissorState();
            clamped.enable(scissorX, scissorY, scissorWidth, scissorHeight);
            scissor = clamped;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "apricityui_mesh", colorView, Optional.empty(),
                depthView, OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            if (uniforms != null) pass.setUniform("FilterParams", uniforms);
            if (scissor.enabled()) {
                // Every pass starts with its render area as the scissor, so AUI's
                // clip rectangles have to be re-issued per draw. Dropping this makes
                // element fills ignore their clip (overflow/rounded masks leak) while
                // the text and image batches — which carry the scissor inside their
                // prepared RenderType — stay clipped.
                pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            }
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            // Only textured meshes may sample Sampler0; a leftover view from an
            // earlier image/filter pass would otherwise tint plain colour meshes.
            boolean textured = state.format() == DefaultVertexFormat.POSITION_TEX;
            if (textured && samplers[0] != null) {
                // 26.3 binds sampler state together with the view.
                pass.setUniform("Sampler0", samplers[0],
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            }
            if (compositePipeline && samplers[1] != null) {
                pass.setUniform("Sampler1", samplers[1],
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            }
            pass.setVertexBuffer(0, vertexBuffer.slice());
            AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(state.primitiveTopology());
            pass.setIndexBuffer(indices.getBuffer(state.indexCount()), indices.type());
            // 26.3 argument order: (indexCount, instanceCount, firstIndex, vertexOffset, firstInstance).
            pass.drawIndexed(state.indexCount(), 1, 0, 0, 0);
        }
    }

    /**
     * Uploads a mesh's vertex data into the shared upload arena, growing it on demand.
     */
    private GpuBuffer uploadMeshVertices(MeshData meshData, String label) {
        GpuDevice device = RenderSystem.getDevice();
        ByteBuffer vertices = meshData.vertexBuffer();
        long needed = vertices.remaining();
        if (meshVertexBuffer == null || meshVertexCapacity < needed) {
            if (meshVertexBuffer != null) meshVertexBuffer.close();
            meshVertexBuffer = device.createBuffer(() -> label,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, needed);
            meshVertexCapacity = needed;
        }
        device.createCommandEncoder().writeToBuffer(meshVertexBuffer.slice(0L, needed), vertices);
        return meshVertexBuffer;
    }

    private GpuBuffer updateFilterUniforms() {
        final int size = 9 * 16;
        if (filterUniformBuffer == null || filterUniformBuffer.size() < size) {
            if (filterUniformBuffer != null) filterUniformBuffer.close();
            filterUniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "apricityui_filter_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size);
        }
        // 26.3 removed CommandEncoder#mapBuffer: a one-shot upload through the encoder
        // is the backend-neutral replacement (both backends copy the bytes immediately).
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, size)
                    .putVec4(brightness, grayscale, invert, hueRotate)
                    .putVec4(opacity, forceAlpha, clipEnabled,
                            currentShader == PipelineRegistry.getFilterBlend() ? blendMode : radius)
                    .putVec4(shadowOffsetX, shadowOffsetY, uvPerGuiX, uvPerGuiY)
                    .putVec4(guiWidth, guiHeight, inputWidth, inputHeight)
                    .putVec4(shadowColorR, shadowColorG, shadowColorB, shadowColorA)
                    .putVec4(clipX, clipY, clipWidth, clipHeight)
                    .putVec4(clipRadiusTopLeft, clipRadiusTopRight,
                            clipRadiusBottomRight, clipRadiusBottomLeft)
                    .putVec4(directionX, directionY, 0.0f, 0.0f)
                    .putVec4(contrast, saturate, sepia, dynamicRangeLimit)
                    .get();
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(filterUniformBuffer.slice(), data);
        }
        return filterUniformBuffer;
    }

    // ------------------------------------------------------------------
    // Batched textured quads (images)
    // ------------------------------------------------------------------

    @Override
    public Object beginTextureBatch(RenderHandle render) {
        RenderType renderType = render == null ? null : render.<RenderType>as();
        // 26.3 dropped MultiBufferSource: every batch owns its vertex data and is
        // drawn by flushTextureBatch in one explicit pass.
        BufferBuilder builder = new BufferBuilder(imageByteBuffer,
                PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return new TextureBatch(builder, renderType);
    }

    @Override
    public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                                float u0, float v0, float u1, float v1) {
        emitTextureQuad(batch, mat, x, y, width, height, u0, v0, u1, v1, 0xFFFFFFFF);
    }

    @Override
    public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                                float u0, float v0, float u1, float v1, int colorArgb) {
        TextureBatch handle = (TextureBatch) batch;
        BufferBuilder builder = handle.builder();
        int a = (colorArgb >>> 24) & 0xFF;
        int r = (colorArgb >>> 16) & 0xFF;
        int g = (colorArgb >>> 8) & 0xFF;
        int b = colorArgb & 0xFF;
        builder.addVertex(mat.transformPosition(x, y + height, 0, new Vector3f()))
                .setColor(r, g, b, a).setUv(u0, v1);
        builder.addVertex(mat.transformPosition(x + width, y + height, 0, new Vector3f()))
                .setColor(r, g, b, a).setUv(u1, v1);
        builder.addVertex(mat.transformPosition(x + width, y, 0, new Vector3f()))
                .setColor(r, g, b, a).setUv(u1, v0);
        builder.addVertex(mat.transformPosition(x, y, 0, new Vector3f()))
                .setColor(r, g, b, a).setUv(u0, v0);
    }

    @Override
    public void flushTextureBatch(Object batch, RenderHandle render) {
        TextureBatch handle = (TextureBatch) batch;
        MeshData meshData;
        try {
            meshData = handle.builder().build();
        } catch (IllegalStateException ignored) {
            return;
        }
        if (meshData == null) return;
        try {
            RenderType renderType = handle.renderType();
            // Always finish the build: an abandoned result would leave its vertices in
            // the shared arena and the next batch's result would inherit them.
            if (renderType != null) drawTextureMesh(renderType, meshData);
        } finally {
            meshData.close();
        }
    }

    /**
     * Draws queued image quads in one pass built from {@code renderType.prepare()}:
     * the prepared type carries the pipeline, the {@code DynamicTransforms} slice
     * (model-view + texture matrix) and the Sampler0 view/sampler pair, which is
     * exactly what the 26.1 {@code RenderType.draw} bound internally.
     */
    private void drawTextureMesh(RenderType renderType, MeshData meshData) {
        MeshData.DrawState state = meshData.drawState();
        if (state.indexCount() <= 0) return;
        RenderTarget output = OutputTargets.currentTarget();
        if (output == null) return;
        GpuTextureView colorView = output.getColorTextureView();
        GpuTextureView depthView = output.hasDepth() ? output.getDepthTextureView() : null;
        if (colorView == null) return;

        // The prepared type snapshots the scissor and 26.3 rejects rectangles
        // outside the pass area, so clamp it for the duration of this batch.
        ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        ScissorState saved = null;
        if (scissor.enabled()) {
            int scissorX = Math.max(0, scissor.x());
            int scissorY = Math.max(0, scissor.y());
            int scissorWidth = Math.min(output.width, scissor.x() + scissor.width()) - scissorX;
            int scissorHeight = Math.min(output.height, scissor.y() + scissor.height()) - scissorY;
            if (scissorWidth <= 0 || scissorHeight <= 0) return;
            saved = new ScissorState(scissor);
            scissor.enable(scissorX, scissorY, scissorWidth, scissorHeight);
        }
        try {
            GpuBuffer vertexBuffer = uploadMeshVertices(meshData, "apricityui_image");
            AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(state.primitiveTopology());
            // Grow (and possibly switch SHORT->INT) before snapshotting the type.
            indices.getBuffer(state.indexCount());
            StagedVertexBuffer.ExecuteInfo info = new StagedVertexBuffer.ExecuteInfo(
                    vertexBuffer, null, indices.type(), 0, 0, state.indexCount(), state.primitiveTopology());
            PreparedRenderType prepared = renderType.prepare();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "apricityui_image", colorView, Optional.empty(),
                    depthView, OptionalDouble.empty())) {
                prepared.drawFromBuffer(info, pass);
            }
        } finally {
            if (saved != null) RenderSystem.getScissorStateForRenderTypeDraws().setFrom(saved);
        }
    }

    // ------------------------------------------------------------------
    // Render targets
    // ------------------------------------------------------------------

    @Override
    public FboHandle createOffscreenTarget(int width, int height, boolean useDepth) {
        // 26.3 takes formats instead of booleans. Stencil rides along with depth so
        // Mask's stencil path also works inside filter ping-pong targets (rounded
        // clips on filtered content).
        GpuFormat depthFormat = useDepth ? GpuFormat.D32_FLOAT_S8_UINT : null;
        TextureTarget target = new TextureTarget("apricityui_filter", width, height,
                GpuFormat.RGBA8_UNORM, depthFormat);
        return FboHandle.of(target, width, height);
    }

    @Override
    public FboHandle getMainRenderTarget() {
        // 26.3 only reaches the window target through GameRenderer, and the root AUI is
        // currently drawing into is whatever OutputTargets holds: common caches this
        // handle as the base of its filter stack (FilterRenderer), so during a PIP
        // render it must resolve to the PIP target that OutputTargets was pointed at,
        // otherwise every "restore the parent" write would strand the rest of the frame
        // on the window framebuffer.
        RenderTarget target = OutputTargets.currentTarget();
        if (target == null) {
            try {
                target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            } catch (RuntimeException | LinkageError ignored) {
                // Early init / headless: report an empty handle instead of killing the
                // frame; callers treat width <= 0 as "nothing to draw into".
                return FboHandle.of(null, 0, 0);
            }
        }
        return FboHandle.of(target, target.width, target.height);
    }

    @Override
    public void enableStencil(FboHandle target) {
        // The main target receives its stencil attachment at startup via
        // ConfigureMainRenderTargetEvent, and AUI's offscreen/PIP targets are created
        // with D32_FLOAT_S8_UINT; nothing to retrofit here. 26.3 cannot add an
        // attachment to a live RenderTarget.
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
        // 26.3 clears take a Vector4fc instead of a packed ARGB int.
        Vector4f clearColor = new Vector4f(r, g, b, a);
        GpuTexture depth = renderTarget.getDepthTexture();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if (depth != null) {
            // AUI keeps its own targets in the classic convention (depth 1.0). The
            // vanilla window target is never cleared through the SPI, so its
            // reverse-Z 0.0 clear is left to vanilla.
            encoder.clearColorAndDepthTextures(color, clearColor, depth, 1.0);
            if (depth.getFormat().hasStencilAspect()) {
                encoder.clearStencilTexture(depth, 0);
            }
        } else {
            encoder.clearColorTexture(color, clearColor);
        }
    }

    @Override
    public void bindWrite(FboHandle target, boolean setViewport) {
        // 26.3 passes bind their own viewport and the encoder resets the scissor per
        // pass; only the logical target bookkeeping is left.
        OutputTargets.setCurrent(target.as());
    }

    @Override
    public void bindColorTexture(FboHandle target, int unit) {
        if (unit < 0 || unit >= samplers.length) return;
        RenderTarget renderTarget = target.as();
        samplers[unit] = renderTarget.getColorTextureView();
    }

    @Override
    public RenderStateScope pushFilterRenderState() {
        return new PipelineFilterState();
    }

    /**
     * 26.3 has no "publish this attachment to samplers" command: the 26.1 readback
     * existed to defeat a driver-level sampler-cache stall that no engine call can
     * reproduce. The engine copy used by {@link #blitFramebuffer} goes through the
     * same DSA blit path the old workaround hand-rolled, so the workaround is dropped
     * rather than re-implemented against backend-internal GL classes.
     */
    @Override
    public void resolveTarget(FboHandle target) {
    }

    @Override
    public void blitFramebuffer(FboHandle source, FboHandle target,
                                int srcX0, int srcY0, int srcX1, int srcY1) {
        RenderTarget src = source.as();
        RenderTarget dst = target.as();
        // No output override in 26.3: the source target's own colour view is the
        // snapshot. The PIP renderer owns its target, so it carries COPY_SRC.
        GpuTextureView sourceView = src.getColorTextureView();
        GpuTexture sourceTexture = sourceView == null ? null : sourceView.texture();
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
            // 26.3 argument order: (src, dst, mipLevel, destX, destY, srcX, srcY, width, height).
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    sourceTexture, destinationTexture, 0,
                    0, 0, boundedSrcX0, boundedSrcY0, copyW, copyH);
            return;
        }

        // A 1:1 copy is unavailable (scaled blit, or a texture without COPY_SRC —
        // vanilla's PIP texture is usage 13): render a scaled region through a sampler.
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

        RenderSystem.getDevice().createCommandEncoder()
                .clearColorTexture(destinationTexture, new Vector4f(0, 0, 0, 0));
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
        // The live target may be a PIP or filter target installed in OutputTargets;
        // answer from whatever is installed, since the pass writes there.
        GpuTexture depthTexture = currentDepthTexture();
        return depthTexture != null && depthTexture.getFormat().hasStencilAspect();
    }

    private GpuTexture currentDepthTexture() {
        RenderTarget target = OutputTargets.currentTarget();
        if (target == null || !target.hasDepth()) return null;
        return target.getDepthTexture();
    }

    @Override
    public Object createDynamicTexture(String name, Object nativeImage, boolean linear) {
        return new DynamicTexture(() -> name, (NativeImage) nativeImage);
    }

    // ------------------------------------------------------------------
    // Textures
    // ------------------------------------------------------------------

    @Override
    public void uploadTextureRegion(Object texture, Object nativeImage, int x, int y,
                                    int width, int height, boolean linear) {
        // 26.3 dropped the NativeImage region overload: the byte overload takes tightly
        // packed rows, so a sub-rectangle of a wider image is compacted first.
        DynamicTexture dynamic = (DynamicTexture) texture;
        GpuTexture gpuTexture = dynamic.getTexture();
        if (gpuTexture == null) return;
        NativeImage image = (NativeImage) nativeImage;
        if (width <= 0 || height <= 0) return;
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        // The 26.1 encoder validated only the destination; reading outside the source
        // image would copy unrelated memory, so drop such a region instead.
        if (x < 0 || y < 0 || x + width > imageWidth || y + height > imageHeight) return;
        // 26.3's byte overload takes tightly packed rows, and 26.1's
        // NativeImage#getPixelBytes can hand out a buffer whose limit does not
        // cover the image (the platform/window path does this), so copy the
        // region into a small RGBA image and use the NativeImage overload: the
        // encoder then reads the region from the image itself.
        if (x + width > gpuTexture.getWidth(0) || y + height > gpuTexture.getHeight(0)) return;
        NativeImage region = new NativeImage(image.format(), width, height, false);
        try {
            image.copyRect(region, x, y, 0, 0, width, height, false, false);
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToTexture(gpuTexture, region, 0, 0, x, y);
        } finally {
            region.close();
        }
    }

    @Override
    public boolean uploadTexturePixels(Object texture, ByteBuffer pixels, int byteOffset,
                                       int x, int y, int width, int height) {
        GpuTexture gpuTexture = ((DynamicTexture) texture).getTexture();
        if (gpuTexture == null) return false;
        pixels.position(byteOffset);
        // 26.3 dropped the NativeImage.Format parameter; rows are read from the
        // buffer's current position.
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                gpuTexture, pixels, 0, 0, x, y, width, height);
        return true;
    }

    @Override
    public Object createDynamicTexture(String name, int width, int height, boolean linear) {
        return new DynamicTexture(() -> name, width, height, true);
    }

    @Override
    public void drawScreenTexture(Object graphics, Object texture, Object location,
                                  int x, int y, int width, int height, float alpha) {
        GpuTextureView view = ((DynamicTexture) texture).getTextureView();
        GpuSampler sampler = ((DynamicTexture) texture).getSampler();
        if (view == null || sampler == null) return;
        // 26.3 takes the bottom-right corner (x1, y1) and (u0, u1, v0, v1) — the 26.1
        // call passed width/height and (u0, v0, u1, v1). `alpha` was already unused.
        ((GuiGraphicsExtractor) graphics).blit(view, sampler,
                x, y, x + width, y + height, 0.0f, 1.0f, 1.0f, 0.0f);
    }

    @Override
    public void closeTexture(Object texture) {
        ((DynamicTexture) texture).close();
    }

    @Override
    public void registerTexture(Object texture, Object location) {
        Minecraft.getInstance().getTextureManager().register((Identifier) location, (AbstractTexture) texture);
    }

    @Override
    public void releaseTexture(Object location) {
        Minecraft.getInstance().getTextureManager().release((Identifier) location);
    }

    @Override
    public void setImagePixel(Object nativeImage, int x, int y, int pixel) {
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

    /**
     * Queued image quads plus the render type that supplies pipeline, transforms and
     * sampler at flush time. Only one batch is open at a time: {@code
     * TextureRenderQueue} flushes each batch before opening the next.
     */
    private record TextureBatch(BufferBuilder builder, RenderType renderType) {
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
}
