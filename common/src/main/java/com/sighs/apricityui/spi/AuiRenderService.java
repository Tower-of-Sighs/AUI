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

    /** Copies a pixel region between framebuffers (glBlitFramebuffer). */
    void blitFramebuffer(FboHandle source, FboHandle target, int srcX0, int srcY0, int srcX1, int srcY1);

    /** Creates a dynamic texture from a native image (RGBA). */
    Object createDynamicTexture(String name, Object nativeImage, boolean linear);

    /** Uploads a native-image region into the bound texture. */
    void uploadTextureRegion(Object texture, Object nativeImage, int x, int y, int width, int height, boolean linear);

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
}
