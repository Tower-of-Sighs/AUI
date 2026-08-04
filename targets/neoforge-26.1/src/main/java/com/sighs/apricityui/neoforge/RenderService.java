package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.render.OutputTargets;
import com.sighs.apricityui.render.PipelineCache;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.spi.MeshFormat;
import com.sighs.apricityui.spi.MeshMode;
import com.sighs.apricityui.spi.RenderHandle;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** NeoForge 26.1 render bridge. */
public final class RenderService implements AuiRenderService {
    public static final RenderService INSTANCE = new RenderService();

    private final Matrix4f projection = new Matrix4f();
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
    private RenderPipeline currentShader;
    private final GpuTextureView[] samplers = new GpuTextureView[8];
    private ProjectionMatrixBuffer projectionBuffer;
    private GpuBuffer filterVertexBuffer;
    private long filterVertexCapacity;
    private GpuBuffer filterUniformBuffer;

    // 26.1 stores custom shader values in std140 blocks instead of the old
    // per-uniform ShaderInstance setters.
    private float brightness = 1.0f;
    private float grayscale;
    private float invert;
    private float hueRotate;
    private float opacity = 1.0f;
    private float forceAlpha;
    private float clipEnabled;
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
    private final Matrix4f pipPose = new Matrix4f();
    private final Matrix4f pipProjection = new Matrix4f();
    private boolean pipRender;

    private RenderService() {
    }

    /**
     * 26.1 renders PIP content into a physical texture while AUI filter
     * meshes are submitted in GUI coordinates. Keep the native PIP transform
     * available for filter passes; ordinary AUI meshes already receive the
     * transform through their PoseStack and must not be transformed twice.
     */
    public void beginPipRender(PoseStack poseStack, int width, int height) {
        pipPose.set(poseStack.last().pose());
        pipProjection.setOrtho(
                0.0f, width, height, 0.0f, -1000.0f, 1000.0f,
                RenderSystem.getDevice().isZZeroToOne());
        pipRender = true;
    }

    public void endPipRender() {
        pipRender = false;
    }

    @Override
    public void setProjectionMatrix(Matrix4f matrix) {
        projection.set(matrix);
        if (projectionBuffer == null) projectionBuffer = new ProjectionMatrixBuffer("apricityui");
        // Filter quads are emitted in GUI coordinates, but the PIP target is
        // physical. Its model-view transform is supplied below in
        // drawWithPass, so use the native physical projection for that target.
        Matrix4f effective = isPipMainTarget() ? pipProjection : matrix;
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(effective), ProjectionType.ORTHOGRAPHIC);
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
        // PIP textures are composited as premultiplied-alpha surfaces. Keep
        // source-over alpha intact when callers only specify the RGB factors.
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
    @Override public void enableStencilTest() { }
    @Override public void disableStencilTest() { }
    @Override public void setStencilMask(int mask) { }
    @Override public void setStencilFunc(int func, int ref, int mask) { }
    @Override public void setStencilOp(int sfail, int dpfail, int dppass) { }
    @Override public void clearStencilBuffer() { }
    @Override public void setColorMask(boolean red, boolean green, boolean blue, boolean alpha) { }
    @Override public boolean isOnRenderThread() { return true; }
    @Override public void recordRenderCall(Runnable task) { task.run(); }

    @Override
    public String getGLVersionString() {
        return GL11.glGetString(GL11.GL_VERSION);
    }

    @Override
    public void flushSharedBuffers() {
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    @Override
    public void setShader(Object shader) {
        currentShader = (RenderPipeline) shader;
    }

    @Override public void setPositionColorShader() { currentShader = null; }
    @Override public void setShaderColor(float a, float r, float g, float b) { }
    @Override public Object getFilterShader() { return PipelineRegistry.getFilter(); }
    @Override public Object getFilterBlurShader() { return PipelineRegistry.getFilterBlur(); }
    @Override
    public void setShaderUniformFloat(String name, float value) {
        switch (name) {
            case "Brightness" -> brightness = value;
            case "Grayscale" -> grayscale = value;
            case "Invert" -> invert = value;
            case "HueRotate" -> hueRotate = value;
            case "Opacity" -> opacity = value;
            case "ForceAlpha" -> forceAlpha = value;
            case "ClipEnabled" -> clipEnabled = value;
            case "Radius" -> radius = value;
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
        return MeshBuilder.of(Tesselator.getInstance().begin(vertexMode, vertexFormat));
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
                RenderType type = PipelineCache.renderType(
                        state.format(), state.mode(), depthTest, depthFunc, depthMask, blend,
                        srcRgb, dstRgb, srcAlpha, dstAlpha, cull, polygonOffset, biasScale, biasUnits);
                type.draw(meshData);
            }
        } finally {
            meshData.close();
        }
    }

    private void drawWithPass(RenderPipeline pipeline, MeshData meshData) {
        boolean filterPipeline = pipeline == PipelineRegistry.getFilter()
                || pipeline == PipelineRegistry.getFilterBlur();
        boolean compositePipeline = pipeline == PipelineRegistry.getFilter();
        RenderTarget output = OutputTargets.currentTarget();
        if (output == null) return;
        GpuTextureView colorView = output.getColorTextureView();
        GpuTextureView depthView = output.useDepth ? output.getDepthTextureView() : null;
        // Main is the logical destination while a PIP renderer is active;
        // nested filter targets remain explicit GPU destinations.
        if (output == Minecraft.getInstance().getMainRenderTarget()) {
            if (RenderSystem.outputColorTextureOverride != null) {
                colorView = RenderSystem.outputColorTextureOverride;
            }
            if (RenderSystem.outputDepthTextureOverride != null) {
                depthView = RenderSystem.outputDepthTextureOverride;
            }
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
        GpuBuffer uniforms = filterPipeline ? updateFilterUniforms(device) : null;
        Matrix4f modelView = isPipMainTarget() && filterPipeline
                ? pipPose : RenderSystem.getModelViewMatrix();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView,
                new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
        RenderPass pass = device.createCommandEncoder().createRenderPass(
                () -> "apricityui_filter", colorView, OptionalInt.empty(),
                depthView, OptionalDouble.empty());
        try {
            pass.setPipeline(pipeline);
            if (uniforms != null) pass.setUniform("FilterParams", uniforms);
            ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
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

    private boolean isPipMainTarget() {
        return pipRender
                && OutputTargets.currentTarget() == Minecraft.getInstance().getMainRenderTarget()
                && RenderSystem.outputColorTextureOverride != null;
    }

    private GpuBuffer updateFilterUniforms(GpuDevice device) {
        final int size = 8 * 16;
        if (filterUniformBuffer == null || filterUniformBuffer.size() < size) {
            if (filterUniformBuffer != null) filterUniformBuffer.close();
            filterUniformBuffer = device.createBuffer(
                    () -> "apricityui_filter_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                    size);
        }
        try (GpuBuffer.MappedView mapped = device.createCommandEncoder()
                .mapBuffer(filterUniformBuffer, false, true)) {
            Std140Builder.intoBuffer(mapped.data())
                    .putVec4(brightness, grayscale, invert, hueRotate)
                    .putVec4(opacity, forceAlpha, clipEnabled, radius)
                    .putVec4(shadowOffsetX, shadowOffsetY, uvPerGuiX, uvPerGuiY)
                    .putVec4(guiWidth, guiHeight, inputWidth, inputHeight)
                    .putVec4(shadowColorR, shadowColorG, shadowColorB, shadowColorA)
                    .putVec4(clipX, clipY, clipWidth, clipHeight)
                    .putVec4(clipRadiusTopLeft, clipRadiusTopRight,
                            clipRadiusBottomRight, clipRadiusBottomLeft)
                    .putVec4(directionX, directionY, 0.0f, 0.0f)
                    .get();
        }
        return filterUniformBuffer;
    }

    @Override
    public Object beginTextureBatch(RenderHandle render) {
        RenderType type = render.as();
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        return new TextureBatchHandle(source, type);
    }

    @Override
    public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                                float u0, float v0, float u1, float v1) {
        TextureBatchHandle handle = (TextureBatchHandle) batch;
        VertexConsumer consumer = handle.source().getBuffer(handle.renderType());
        consumer.addVertex(mat, x, y + height, 0).setColor(255, 255, 255, 255).setUv(u0, v1);
        consumer.addVertex(mat, x + width, y + height, 0).setColor(255, 255, 255, 255).setUv(u1, v1);
        consumer.addVertex(mat, x + width, y, 0).setColor(255, 255, 255, 255).setUv(u1, v0);
        consumer.addVertex(mat, x, y, 0).setColor(255, 255, 255, 255).setUv(u0, v0);
    }

    @Override
    public void flushTextureBatch(Object batch, RenderHandle render) {
        TextureBatchHandle handle = (TextureBatchHandle) batch;
        handle.source().endBatch(handle.renderType());
    }

    @Override
    public FboHandle createOffscreenTarget(int width, int height, boolean useDepth) {
        TextureTarget target = new TextureTarget("apricityui_filter", width, height, useDepth);
        return FboHandle.of(target, width, height);
    }

    @Override
    public FboHandle getMainRenderTarget() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        OutputTargets.setCurrent(target);
        return FboHandle.of(target, target.width, target.height);
    }

    @Override public void enableStencil(FboHandle target) { }

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
        if (depth != null) {
            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorAndDepthTextures(color, argb, depth, 1.0);
        } else {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(color, argb);
        }
    }

    @Override
    public void bindWrite(FboHandle target, boolean setViewport) {
        OutputTargets.setCurrent(target.as());
    }

    @Override
    public void bindColorTexture(FboHandle target, int unit) {
        if (unit >= 0 && unit < samplers.length) {
            samplers[unit] = ((RenderTarget) target.as()).getColorTextureView();
        }
    }

    @Override
    public void blitFramebuffer(FboHandle source, FboHandle target,
                                int srcX0, int srcY0, int srcX1, int srcY1) {
        RenderTarget src = source.as();
        RenderTarget dst = target.as();
        // During a 26.1 PIP render the logical main RenderTarget is only a
        // placeholder. Vanilla redirects the actual color attachment to this
        // texture view, so backdrop-filter must snapshot that texture rather
        // than the world framebuffer behind it.
        GpuTexture sourceTexture = src.getColorTexture();
        GpuTextureView sourceView = src.getColorTextureView();
        if (src == Minecraft.getInstance().getMainRenderTarget()
                && RenderSystem.outputColorTextureOverride != null) {
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
    public boolean supportsStencil() {
        // 26.1 exposes depth state through RenderPipeline but no generic
        // stencil state or stencil attachment API.
        return false;
    }

    private record TextureBatchHandle(MultiBufferSource.BufferSource source, RenderType renderType) { }
}
