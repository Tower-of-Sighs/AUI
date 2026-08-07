package com.sighs.apricityui.spi;

import org.joml.Matrix4f;

/**
 * Loader-side render-state access.
 *
 * <p>The render-state operations (projection matrix, depth test, blend) were
 * exposed through {@code RenderSystem} in 1.20.x but were removed in favour of
 * {@code RenderPipeline} state objects in 1.21.5+, so {@code common} expresses
 * only the state intent through this interface and the loader applies it.</p>
 */
public interface AuiRenderService {
    /**
     * A loader-owned snapshot of the render state changed by a filter pass.
     * Implementations must make {@link #close()} idempotent enough for normal
     * try/finally cleanup and must not throw while restoring state.
     */
    @FunctionalInterface
    interface RenderStateScope extends AutoCloseable {
        RenderStateScope NOOP = () -> { };

        @Override
        void close();
    }

    void setProjectionMatrix(Matrix4f matrix);

    Matrix4f getProjectionMatrix();

    void enableDepthTest();

    void disableDepthTest();

    void enableBlend();

    /** Sets the source/destination blend factors (OpenGL constant values). */
    void setBlendFunc(int srcFactor, int dstFactor);

    /** Begins a vertex mesh in the given mode/format. */
    MeshBuilder beginMesh(MeshMode mode, MeshFormat format);

    /** Emits a position+color vertex into the mesh. */
    void emitVertex(Object mesh, Matrix4f mat, float x, float y, float z, int r, int g, int b, int a);

    /** Finishes the mesh and submits it for drawing. */
    void submitMesh(Object mesh);

    /** Begins a batched textured-quad draw for the given render handle; returns an opaque batch token. */
    Object beginTextureBatch(RenderHandle render);

    /** Emits one textured quad (4 vertices) into the batch. UVs are normalized to [0,1]. */
    void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                         float u0, float v0, float u1, float v1);

    /** Ends and submits the batch, uploading the quads drawn for the given render handle. */
    void flushTextureBatch(Object batch, RenderHandle render);

    /** Emits a position+uv vertex (POSITION_TEX) into the mesh. */
    void emitVertexUV(Object mesh, Matrix4f mat, float x, float y, float z, float u, float v);

    /** Creates an offscreen render target (FBO) with a color attachment. */
    FboHandle createOffscreenTarget(int width, int height, boolean useDepth);

    /** Returns the main window render target. */
    FboHandle getMainRenderTarget();

    /** Enables a stencil attachment on the target. */
    void enableStencil(FboHandle target);

    /** Releases the target's GPU buffers (framebuffer/texture). */
    void destroyBuffers(FboHandle target);

    /** Sets the clear color and clears the target's color/depth buffers. */
    void clear(FboHandle target, float r, float g, float b, float a);

    /** Binds the target for writing, optionally resizing the viewport to it. */
    void bindWrite(FboHandle target, boolean setViewport);

    /** Binds the target's color attachment to a sampler unit for shader sampling. */
    void bindColorTexture(FboHandle target, int unit);

    /**
     * Captures every loader-global state value that filter shader passes may
     * change, including sampler bindings and renderer-side caches such as
     * {@code BlendMode.lastApplied}. Closing the scope restores the exact values
     * captured here. Pipeline backends may snapshot their local state model
     * instead of querying OpenGL.
     */
    default RenderStateScope pushFilterRenderState() {
        return RenderStateScope.NOOP;
    }

    /** Copies a pixel region between framebuffers (glBlitFramebuffer). */
    void blitFramebuffer(FboHandle source, FboHandle target, int srcX0, int srcY0, int srcX1, int srcY1);

    /** Creates a dynamic texture from a native image (RGBA). */
    Object createDynamicTexture(String name, Object nativeImage, boolean linear);

    /** Uploads a native-image region into the bound texture. */
    void uploadTextureRegion(Object texture, Object nativeImage, int x, int y, int width, int height, boolean linear);

    /**
     * Sets one ABGR-packed pixel of a native image (native image name differs
     * across versions: {@code setPixelRGBA} on 1.20.x/1.21.x, {@code setPixelABGR}
     * on 1.21.5+).
     */
    void setImagePixel(Object nativeImage, int x, int y, int pixel);

    /** Releases a dynamic texture's GPU resources. */
    void closeTexture(Object texture);

    /** Registers a texture with the loader's texture manager under the given location. */
    void registerTexture(Object texture, Object location);

    /** Releases a registered texture from the loader's texture manager. */
    void releaseTexture(Object location);

    /**
     * Binds the given shader program for subsequent draws. The object is the
     * loader's {@code ShaderInstance}/{@code ShaderProgram} (1.21.2 renamed).
     */
    void setShader(Object shader);

    /** Binds the default position-color shader. */
    void setPositionColorShader();

    /** Sets the shader color tint (alpha, red, green, blue components). */
    void setShaderColor(float a, float r, float g, float b);

    /** Returns the loader's filter composite shader, or {@code null}. */
    Object getFilterShader();

    /** Returns the loader's filter blur shader, or {@code null}. */
    Object getFilterBlurShader();

    // ------------------------------------------------------------------
    // Version-neutral render-state passthrough (added for the 26.1 target).
    // The pre-1.21.5 RenderSystem/GlStateManager global-state model was
    // removed in favour of per-draw RenderPipeline/RenderPass objects; common
    // expresses only the state intent here and the loader applies it.
    // ------------------------------------------------------------------

    /** Sets the depth comparison function (OpenGL constant value, e.g. GL_LEQUAL). */
    void setDepthFunc(int func);

    /** Sets whether the depth buffer is writable. */
    void setDepthMask(boolean write);

    /** Returns whether depth testing is currently enabled. */
    boolean isDepthTestEnabled();

    /** Returns whether the depth buffer is currently writable. */
    boolean isDepthMaskEnabled();

    /** Sets the source/destination blend factors separately for RGB and alpha (OpenGL constant values). */
    void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);

    /** Disables alpha blending. */
    void disableBlend();

    /** Enables back-face culling. */
    void enableCull();

    /** Disables back-face culling. */
    void disableCull();

    /** Returns whether back-face culling is currently enabled. */
    boolean isCullEnabled();

    /** Enables the depth polygon offset. */
    void enablePolygonOffset();

    /** Disables the depth polygon offset. */
    void disablePolygonOffset();

    /** Sets the polygon offset factor/units. */
    void polygonOffset(float factor, float units);

    /** Enables the scissor test. */
    void enableScissorTest();

    /** Sets the scissor box in device pixels. */
    void scissorBox(int x, int y, int width, int height);

    /** Disables the scissor test. */
    void disableScissorTest();

    /** Enables the stencil test (best-effort; unavailable on some backends). */
    void enableStencilTest();

    /** Disables the stencil test. */
    void disableStencilTest();

    /** Sets the stencil write mask. */
    void setStencilMask(int mask);

    /** Sets the stencil comparison function/ref/mask. */
    void setStencilFunc(int func, int ref, int mask);

    /** Sets the stencil operation on fail/stencil-fail/depth-fail. */
    void setStencilOp(int sfail, int dpfail, int dppass);

    /** Clears the stencil buffer of the bound target. */
    void clearStencilBuffer();

    /** Sets the color write mask. */
    void setColorMask(boolean red, boolean green, boolean blue, boolean alpha);

    /** Returns whether the current thread is the render thread. */
    boolean isOnRenderThread();

    /** Runs the task on the render thread (or immediately if already there). */
    void recordRenderCall(Runnable task);

    /** Returns the GL version string, or {@code null} if unavailable (used for GLES detection). */
    String getGLVersionString();

    /** Whether this loader can attach and draw against a stencil buffer. */
    default boolean supportsStencil() {
        return true;
    }

    /**
     * Whether the render target currently being drawn into actually has stencil
     * bits. Unlike {@link #supportsStencil()} (a static loader capability) this
     * reflects the live target — e.g. 26.1's vanilla PIP depth attachment is
     * depth-only even though the main target has stencil.
     */
    default boolean currentTargetHasStencil() {
        return true;
    }

    /** Flushes the loader's shared buffer source (e.g. {@code bufferSource().endBatch()}). */
    void flushSharedBuffers();

    /** Sets a float shader uniform on the current shader (tolerates missing uniforms). */
    void setShaderUniformFloat(String name, float value);

    /** Sets a vec2 shader uniform on the current shader (tolerates missing uniforms). */
    void setShaderUniform2f(String name, float a, float b);

    /** Sets a vec3 shader uniform on the current shader (tolerates missing uniforms). */
    void setShaderUniform3f(String name, float a, float b, float c);

    /** Sets a vec4 shader uniform on the current shader (tolerates missing uniforms). */
    void setShaderUniform4f(String name, float a, float b, float c, float d);

    /** Sets an int shader uniform on the current shader (tolerates missing uniforms). */
    void setShaderUniformI(String name, int value);
}
