package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.spi.MeshFormat;
import com.sighs.apricityui.spi.MeshMode;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

public class FilterRenderer {
    private static final Stack<FboHandle> fboStack = new Stack<>();
    private static FboHandle mainRenderTarget;
    private static final List<FboHandle> fboPool = new ArrayList<>();
    private static int poolPointer = 0;
    private static final List<FboHandle> backdropPool = new ArrayList<>();
    private static int backdropPoolPointer = 0;
    private static final float MAX_REASONABLE_BACKDROP_BLUR = 32.0f;
    private static boolean stencilCapabilityResolved;
    private static boolean stencilAvailable = true;

    /**
     * Returns whether AUI may allocate/use a stencil attachment for the
     * current GL context. Desktop OpenGL keeps the existing path; GLES is
     * conservatively treated as unavailable because some Android drivers
     * reject Minecraft's depth/stencil framebuffer combination.
     */
    public static boolean isStencilAvailable() {
        resolveStencilCapability();
        return stencilAvailable;
    }

    private static void resolveStencilCapability() {
        if (stencilCapabilityResolved) return;

        // Resolve lazily from the render thread, after a context exists. If a
        // test/headless context cannot answer the query, preserve the desktop
        // behavior rather than disabling stencil for the whole process.
        stencilCapabilityResolved = true;
        try {
            if (!AuiServices.render().supportsStencil()) {
                stencilAvailable = false;
                ApricityUI.LOGGER.warn("[ApricityUI] stencil masks are unavailable on this render backend; using scissor fallback");
                return;
            }
            String version = AuiServices.render().getGLVersionString();
            if (version != null && version.toLowerCase(Locale.ROOT).contains("opengl es")) {
                stencilAvailable = false;
                ApricityUI.LOGGER.warn(
                        "[ApricityUI] OpenGL ES detected ({}); disabling stencil-backed masks for compatibility",
                        version
                );
            }
        } catch (RuntimeException ignored) {
            stencilAvailable = true;
        }
    }

    public static void beginFrame() {
        // 防御式清理：若上帧因异常或节点错配残留栈，避免 poolPointer 无界增长
        if (!fboStack.isEmpty()) {
            fboStack.clear();
        }
        mainRenderTarget = AuiServices.render().getMainRenderTarget();
        poolPointer = 0;
        backdropPoolPointer = 0;
    }

    public static void endFrame() {
        if (!fboStack.isEmpty()) {
            fboStack.clear();
            if (mainRenderTarget != null) {
                AuiServices.render().bindWrite(mainRenderTarget, false);
            }
        }
    }

    public static void pushFilter() {
        // Pending parent draws must land in the parent target before the child
        // filter binds its offscreen target. Otherwise they inherit the child's opacity.
        ImageDrawer.flushBatch();
        Graph.endBatch();

        if (fboStack.isEmpty()) {
            mainRenderTarget = AuiServices.render().getMainRenderTarget();
            poolPointer = 0;
        }

        FboHandle temp;
        double width = AuiServices.client().getWindowWidth();
        double height = AuiServices.client().getWindowHeight();

        if (poolPointer < fboPool.size()) {
            temp = fboPool.get(poolPointer);
            if (temp.width != (int) width || temp.height != (int) height) {
                AuiServices.render().destroyBuffers(temp);
                temp = AuiServices.render().createOffscreenTarget((int) width, (int) height, true);
                fboPool.set(poolPointer, temp);
            }
        } else {
            temp = AuiServices.render().createOffscreenTarget((int) width, (int) height, true);
            fboPool.add(temp);
        }
        poolPointer++;

        // 注意：这里的 clear 会清除当前绑定的 FBO 的缓冲区
        AuiServices.render().clear(temp, 0f, 0f, 0f, 0f);
        fboStack.push(temp);
        AuiServices.render().bindWrite(temp, false);
    }

    public static FboHandle getCurrentTarget() {
        return fboStack.isEmpty() ? AuiServices.render().getMainRenderTarget() : fboStack.peek();
    }

    public static void popFilter(Filter.FilterState state) {
        if (fboStack.isEmpty()) return;

        // 在切回父 FBO 之前 flush 批处理绘制，使 batched draw calls
        // 先写入当前离屏 FBO，避免绕过 filter/opacity 合成。
        ImageDrawer.flushBatch();
        Graph.endBatch();

        FboHandle currentFbo = fboStack.pop();
        FboHandle parentFbo = fboStack.isEmpty() ? mainRenderTarget : fboStack.peek();
        try {
            FboHandle filteredFbo = prepareFullFilterSource(currentFbo, state.blurRadius());
            FboHandle shadowFbo = state.hasDropShadow()
                    ? prepareFullFilterSource(currentFbo, state.dropShadowBlur()) : currentFbo;
            AuiServices.render().bindWrite(parentFbo, true);
            drawWithShader(filteredFbo, shadowFbo, state);
        } finally {
            if (parentFbo != null) AuiServices.render().bindWrite(parentFbo, true);
        }
    }

    /** Runs a shader pass with the standard filter blend/depth state. */
    private static void withBlendRenderState(boolean enableBlend, Runnable body) {
        Matrix4f oldProjection = new Matrix4f(Base.getProjectionMatrix());
        AuiRenderService.RenderStateScope scope = AuiServices.render().pushFilterRenderState();
        if (scope == null) scope = AuiRenderService.RenderStateScope.NOOP;
        if (enableBlend) {
            AuiServices.render().enableBlend();
            AuiServices.render().setBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA
            );
        } else {
            AuiServices.render().disableBlend();
        }
        AuiServices.render().disableDepthTest();
        AuiServices.render().setDepthMask(false);
        AuiServices.render().disableCull();
        try {
            body.run();
        } finally {
            try {
                AuiServices.render().setDepthMask(true);
                if (Base.isDepthTestEnabled()) AuiServices.render().enableDepthTest();
                else AuiServices.render().disableDepthTest();
                Base.setProjectionMatrix(oldProjection);
            } finally {
                scope.close();
            }
        }
    }

    private static void drawWithShader(FboHandle fbo, FboHandle shadowFbo, Filter.FilterState state) {
        Object shader = AuiServices.render().getFilterShader();

        withBlendRenderState(true, () -> {
            if (shader == null) {
                Base.setPositionColorShader();
            } else {
                Base.setShader(shader);
                // Blur is precomputed as two separable passes. The composite shader
                // only applies the inexpensive color/opacity/shadow operations.
                setupUniforms(shader, state, fbo, false, true,
                        1.0f / Math.max(1, AuiServices.client().getScaledWidth()),
                        1.0f / Math.max(1, AuiServices.client().getScaledHeight()));
            }

            AuiServices.render().bindColorTexture(fbo, 0);
            AuiServices.render().bindColorTexture(shadowFbo, 1);
            Base.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            float guiW = (float) AuiServices.client().getScaledWidth();
            float guiH = (float) AuiServices.client().getScaledHeight();
            Base.setProjectionMatrix(orthoProjection(guiW, guiH));

            MeshBuilder mesh = AuiServices.render().beginMesh(MeshMode.QUADS, MeshFormat.POSITION_TEX);
            Matrix4f identity = new Matrix4f();
            mesh.vertexUV(identity, 0, guiH, 0, 0, 0);
            mesh.vertexUV(identity, guiW, guiH, 0, 1, 0);
            mesh.vertexUV(identity, guiW, 0, 0, 1, 1);
            mesh.vertexUV(identity, 0, 0, 0, 0, 1);
            mesh.submit();
        });
    }

    public static void renderBackdrop(Element target, PoseStack poseStack) {
        // A backdrop snapshot must include every draw submitted before this
        // element. It also creates a natural batch boundary for the FBO copy.
        Base.commitDraws();

        FboHandle currentBound = fboStack.isEmpty() ? AuiServices.render().getMainRenderTarget() : fboStack.peek();
        Filter.FilterState state = Filter.getBackdropFilterOf(target);
        Rect rect = Rect.of(target);
        try {
            BackdropSource source = prepareBackdropSource(currentBound, rect, state.blurRadius());
            if (source == null) return;
            FboHandle shadowTarget = prepareBackdropShadow(source, state);

            AuiServices.render().bindWrite(currentBound, true);
            drawBackdropWithShader(source, shadowTarget, state, rect);
        } finally {
            if (currentBound != null) AuiServices.render().bindWrite(currentBound, true);
        }
    }

    private static void drawBackdropWithShader(BackdropSource source, FboHandle shadowTarget,
                                               Filter.FilterState state, Rect rect) {
        Object shader = AuiServices.render().getFilterShader();
        if (shader == null) return;

        withBlendRenderState(true, () -> {
            Position p = rect.getBodyRectPosition();
            Size s = rect.getBodyRectSize();

            float guiW = (float) AuiServices.client().getScaledWidth();
            float guiH = (float) AuiServices.client().getScaledHeight();
            Base.setProjectionMatrix(orthoProjection(guiW, guiH));

            Base.setShader(shader);
            setupUniforms(shader, state, source.target(), true, true, source.uvPerGuiX(), source.uvPerGuiY());
            setupBackdropClipUniforms(shader, rect, guiW, guiH);
            AuiServices.render().bindColorTexture(source.target(), 0);
            AuiServices.render().bindColorTexture(shadowTarget, 1);
            Base.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            Base.setProjectionMatrix(orthoProjection(guiW, guiH));

            MeshBuilder mesh = AuiServices.render().beginMesh(MeshMode.QUADS, MeshFormat.POSITION_TEX);
            Matrix4f identity = new Matrix4f();
            float x0 = (float) p.x;
            float y0 = (float) p.y;
            float x1 = x0 + (float) s.width();
            float y1 = y0 + (float) s.height();

            mesh.vertexUV(identity, x0, y1, 0, source.u0(), source.vBottom());
            mesh.vertexUV(identity, x1, y1, 0, source.u1(), source.vBottom());
            mesh.vertexUV(identity, x1, y0, 0, source.u1(), source.vTop());
            mesh.vertexUV(identity, x0, y0, 0, source.u0(), source.vTop());
            mesh.submit();
        });
    }

    private static BackdropSource prepareBackdropSource(FboHandle source, Rect rect, float cssBlurRadius) {
        if (source == null || source.width <= 0 || source.height <= 0) return null;
        float guiW = (float) AuiServices.client().getScaledWidth();
        float guiH = (float) AuiServices.client().getScaledHeight();
        if (guiW <= 0 || guiH <= 0) return null;

        Position position = rect.getBodyRectPosition();
        Size size = rect.getBodyRectSize();
        float scaleX = source.width / guiW;
        float scaleY = source.height / guiH;
        float physicalRadius = Math.min(MAX_REASONABLE_BACKDROP_BLUR, Math.max(0, cssBlurRadius))
                * Math.max(scaleX, scaleY);
        float padding = physicalRadius + 2.0f;

        int srcX0 = clamp((int) Math.floor(position.x * scaleX - padding), 0, source.width);
        int srcX1 = clamp((int) Math.ceil((position.x + size.width()) * scaleX + padding), 0, source.width);
        int srcY0 = clamp((int) Math.floor(source.height - (position.y + size.height()) * scaleY - padding), 0, source.height);
        int srcY1 = clamp((int) Math.ceil(source.height - position.y * scaleY + padding), 0, source.height);
        if (srcX1 <= srcX0 || srcY1 <= srcY0) return null;

        int downsample = chooseDownsample(physicalRadius);
        int targetWidth = Math.max(1, (int) Math.ceil((srcX1 - srcX0) / (double) downsample));
        int targetHeight = Math.max(1, (int) Math.ceil((srcY1 - srcY0) / (double) downsample));
        FboHandle ping = acquireBackdropTarget(targetWidth, targetHeight);
        blitRegion(source, ping, srcX0, srcY0, srcX1, srcY1);

        float reducedRadius = physicalRadius / downsample;
        if (reducedRadius >= 0.5f) {
            FboHandle pong = acquireBackdropTarget(targetWidth, targetHeight);
            drawBlurPass(ping, pong, reducedRadius, 1.0f / targetWidth, 0.0f);
            drawBlurPass(pong, ping, reducedRadius, 0.0f, 1.0f / targetHeight);
        }

        float sourceWidth = srcX1 - srcX0;
        float sourceHeight = srcY1 - srcY0;
        float u0 = (float) ((position.x * scaleX - srcX0) / sourceWidth);
        float u1 = (float) (((position.x + size.width()) * scaleX - srcX0) / sourceWidth);
        float vTop = (float) ((source.height - position.y * scaleY - srcY0) / sourceHeight);
        float vBottom = (float) ((source.height - (position.y + size.height()) * scaleY - srcY0) / sourceHeight);
        float uvPerGuiX = scaleX / sourceWidth;
        float uvPerGuiY = scaleY / sourceHeight;
        return new BackdropSource(ping, u0, vBottom, u1, vTop, uvPerGuiX, uvPerGuiY);
    }

    private static FboHandle prepareBackdropShadow(BackdropSource source, Filter.FilterState state) {
        if (!state.hasDropShadow() || state.dropShadowBlur() < 0.5f) return source.target();
        float textureRadius = state.dropShadowBlur() * Math.max(
                source.uvPerGuiX() * source.target().width,
                source.uvPerGuiY() * source.target().height
        );
        return blurTexture(source.target(), textureRadius);
    }

    private static FboHandle prepareFullFilterSource(FboHandle source, float cssBlurRadius) {
        if (source == null || cssBlurRadius < 0.5f) return source;
        float guiW = Math.max(1.0f, (float) AuiServices.client().getScaledWidth());
        float guiH = Math.max(1.0f, (float) AuiServices.client().getScaledHeight());
        float physicalRadius = Math.max(0, cssBlurRadius)
                * Math.max(source.width / guiW, source.height / guiH);
        return blurTexture(source, physicalRadius);
    }

    private static FboHandle blurTexture(FboHandle source, float physicalRadius) {
        int downsample = chooseDownsample(physicalRadius);
        int width = Math.max(1, (int) Math.ceil(source.width / (double) downsample));
        int height = Math.max(1, (int) Math.ceil(source.height / (double) downsample));
        FboHandle ping = acquireBackdropTarget(width, height);
        blitRegion(source, ping, 0, 0, source.width, source.height);
        FboHandle pong = acquireBackdropTarget(width, height);
        float reducedRadius = Math.min(32.0f, physicalRadius / downsample);
        drawBlurPass(ping, pong, reducedRadius, 1.0f / width, 0.0f);
        drawBlurPass(pong, ping, reducedRadius, 0.0f, 1.0f / height);
        return ping;
    }

    private static int chooseDownsample(float physicalRadius) {
        int result = physicalRadius >= 6.0f ? 2 : 1;
        while (physicalRadius / result > 18.0f && result < 64) result *= 2;
        return result;
    }

    private static FboHandle acquireBackdropTarget(int width, int height) {
        FboHandle target;
        if (backdropPoolPointer < backdropPool.size()) {
            target = backdropPool.get(backdropPoolPointer);
            if (target.width != width || target.height != height) {
                AuiServices.render().destroyBuffers(target);
                target = AuiServices.render().createOffscreenTarget(width, height, false);
                backdropPool.set(backdropPoolPointer, target);
            }
        } else {
            target = AuiServices.render().createOffscreenTarget(width, height, false);
            backdropPool.add(target);
        }
        backdropPoolPointer++;
        return target;
    }

    private static void blitRegion(FboHandle source, FboHandle target,
                                   int srcX0, int srcY0, int srcX1, int srcY1) {
        AuiServices.render().blitFramebuffer(source, target, srcX0, srcY0, srcX1, srcY1);
    }

    private static void drawBlurPass(FboHandle source, FboHandle target, float radius,
                                     float directionX, float directionY) {
        Object shader = AuiServices.render().getFilterBlurShader();
        if (shader == null) return;

        AuiServices.render().clear(target, 0, 0, 0, 0);
        AuiServices.render().bindWrite(target, true);
        withBlendRenderState(false, () -> {
            Base.setProjectionMatrix(orthoProjection(target.width, target.height));
            Base.setShader(shader);
            AuiServices.render().setShaderUniform2f("Direction", directionX, directionY);
            AuiServices.render().setShaderUniformFloat("Radius", Math.min(32.0f, radius));
            AuiServices.render().bindColorTexture(source, 0);
            Base.setShaderColor(1, 1, 1, 1);

            MeshBuilder mesh = AuiServices.render().beginMesh(MeshMode.QUADS, MeshFormat.POSITION_TEX);
            Matrix4f identity = new Matrix4f();
            mesh.vertexUV(identity, 0, target.height, 0, 0, 0);
            mesh.vertexUV(identity, target.width, target.height, 0, 1, 0);
            mesh.vertexUV(identity, target.width, 0, 0, 1, 1);
            mesh.vertexUV(identity, 0, 0, 0, 0, 1);
            mesh.submit();
        });
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record BackdropSource(FboHandle target, float u0, float vBottom, float u1, float vTop,
                                  float uvPerGuiX, float uvPerGuiY) {}

    private static void setupUniforms(Object shader, Filter.FilterState state, FboHandle fbo,
                                      boolean forceAlpha, boolean preBlurred,
                                      float uvPerGuiX, float uvPerGuiY) {
        float blurRadius = preBlurred ? 0.0f
                : (forceAlpha ? Math.min(state.blurRadius(), MAX_REASONABLE_BACKDROP_BLUR) : state.blurRadius());
        AuiServices.render().setShaderUniformFloat("BlurRadius", blurRadius);
        AuiServices.render().setShaderUniformFloat("Brightness", state.brightness());
        AuiServices.render().setShaderUniformFloat("Grayscale", state.grayscale());
        AuiServices.render().setShaderUniformFloat("Invert", state.invert());
        AuiServices.render().setShaderUniformFloat("HueRotate", state.hueRotate());
        AuiServices.render().setShaderUniformFloat("Opacity", state.opacity());
        AuiServices.render().setShaderUniform2f("ShadowOffset", state.dropShadowX(), state.dropShadowY());
        AuiServices.render().setShaderUniformFloat("ShadowBlur", state.dropShadowBlur());
        int c = state.dropShadowColor();
        float a = ((c >>> 24) & 0xFF) / 255f;
        float r = ((c >>> 16) & 0xFF) / 255f;
        float g = ((c >>> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        AuiServices.render().setShaderUniform4f("ShadowColor", r, g, b, a);
        AuiServices.render().setShaderUniform2f("InSize", (float) fbo.width, (float) fbo.height);
        AuiServices.render().setShaderUniformFloat("ForceAlpha", forceAlpha ? 1.0f : 0.0f);
        AuiServices.render().setShaderUniformFloat("ClipEnabled", 0.0f);
        AuiServices.render().setShaderUniform2f("GuiSize",
                (float) AuiServices.client().getScaledWidth(), (float) AuiServices.client().getScaledHeight());
        AuiServices.render().setShaderUniform2f("UvPerGuiPixel", uvPerGuiX, uvPerGuiY);
    }

    private static void setupBackdropClipUniforms(Object shader, Rect rect, float guiW, float guiH) {
        Position p = rect.getBodyRectPosition();
        Size s = rect.getBodyRectSize();
        float[] radii = rect.getBodyRadius();
        AuiServices.render().setShaderUniformFloat("ClipEnabled", 1.0f);
        AuiServices.render().setShaderUniform4f("ClipRect",
                (float) p.x, (float) p.y, (float) s.width(), (float) s.height());
        if (radii != null && radii.length >= 4) {
            AuiServices.render().setShaderUniform4f("ClipRadii", radii[0], radii[1], radii[2], radii[3]);
        }
        AuiServices.render().setShaderUniform2f("GuiSize", guiW, guiH);
    }

    private static Matrix4f orthoProjection(float width, float height) {
        return new Matrix4f().setOrtho(0, width, height, 0, -1000, 1000);
    }
}
