package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.world.ShaderRegistry;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

public class FilterRenderer {
    private static final Stack<RenderTarget> fboStack = new Stack<>();
    private static RenderTarget mainRenderTarget;
    private static final List<RenderTarget> fboPool = new ArrayList<>();
    private static int poolPointer = 0;
    private static final List<RenderTarget> backdropPool = new ArrayList<>();
    private static int backdropPoolPointer = 0;
    private static final Map<String, Long> LOG_TIMES = new HashMap<>();
    private static final long LOG_INTERVAL_MS = 2000L;
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
            String version = GL11.glGetString(GL11.GL_VERSION);
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

    private static boolean shouldLog(String key, long intervalMs) {
        long now = System.currentTimeMillis();
        Long last = LOG_TIMES.get(key);
        if (last == null || now - last >= intervalMs) {
            LOG_TIMES.put(key, now);
            return true;
        }
        return false;
    }

    public static void beginFrame() {
        // 防御式清理：若上帧因异常或节点错配残留栈，避免 poolPointer 无界增长
        if (!fboStack.isEmpty()) {
            fboStack.clear();
        }
        mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
        if (mainRenderTarget != null && isStencilAvailable()) {
            mainRenderTarget.enableStencil();
        }
        poolPointer = 0;
        backdropPoolPointer = 0;
//        if (shouldLog("beginFrame", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] beginFrame mainTarget={} size={}x{} pool={} backdropPool={}",
//                    mainRenderTarget, mainRenderTarget.width, mainRenderTarget.height, fboPool.size(), backdropPool.size()
//            );
//        }
    }

    public static void endFrame() {
        if (!fboStack.isEmpty()) {
            fboStack.clear();
            if (mainRenderTarget != null) {
                mainRenderTarget.bindWrite(false);
            }
        }
//        if (shouldLog("endFrame", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] endFrame stackCleared={} poolPointer={} backdropPointer={}",
//                    fboStack.isEmpty(), poolPointer, backdropPoolPointer
//            );
//        }
    }

    public static void pushFilter() {
        boolean ON_OSX = Minecraft.ON_OSX;

        // Pending parent draws must land in the parent target before the child
        // filter binds its offscreen target. Otherwise they inherit the child's opacity.
        ImageDrawer.flushBatch();
        Graph.endBatch();

        if (fboStack.isEmpty()) {
            mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
            poolPointer = 0;
        }

        RenderTarget temp;
        double width = Client.getWindow().getWidth();
        double height = Client.getWindow().getHeight();

        if (poolPointer < fboPool.size()) {
            temp = fboPool.get(poolPointer);
            if (temp.width != (int) width || temp.height != (int) height) {
                temp.destroyBuffers();
                temp = new TextureTarget((int) width, (int) height, true, ON_OSX);
                if (isStencilAvailable()) temp.enableStencil();
                fboPool.set(poolPointer, temp);
//                com.sighs.apricityui.ApricityUI.LOGGER.info(
//                        "[FilterRenderer] pushFilter resized temp target index={} size={}x{}",
//                        poolPointer, temp.width, temp.height
//                );
            }
        } else {
            temp = new TextureTarget((int) width, (int) height, true, ON_OSX);
            if (isStencilAvailable()) temp.enableStencil();
            fboPool.add(temp);
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] pushFilter created temp target index={} size={}x{}",
//                    poolPointer, temp.width, temp.height
//            );
        }
        poolPointer++;

        temp.setClearColor(0f, 0f, 0f, 0f);
        // 注意：这里的 clear 会清除当前绑定的 FBO 的缓冲区
        temp.clear(ON_OSX);
        fboStack.push(temp);
        temp.bindWrite(false);
//        if (shouldLog("pushFilter.bind", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] pushFilter bind target size={}x{} stackDepth={}",
//                    temp.width, temp.height, fboStack.size()
//            );
//        }
    }

    public static RenderTarget getCurrentTarget() {
        return fboStack.isEmpty() ? Minecraft.getInstance().getMainRenderTarget() : fboStack.peek();
    }

    public static void popFilter(Filter.FilterState state) {
        if (fboStack.isEmpty()) return;

        // 在切回父 FBO 之前 flush 批处理绘制，使 batched draw calls
        // 先写入当前离屏 FBO，避免绕过 filter/opacity 合成。
        ImageDrawer.flushBatch();
        Graph.endBatch();

        RenderTarget currentFbo = fboStack.pop();
        RenderTarget parentFbo = fboStack.isEmpty() ? mainRenderTarget : fboStack.peek();
        RenderTarget filteredFbo = prepareFullFilterSource(currentFbo, state.blurRadius());
        RenderTarget shadowFbo = state.hasDropShadow()
                ? prepareFullFilterSource(currentFbo, state.dropShadowBlur()) : currentFbo;
        parentFbo.bindWrite(true);

//        if (shouldLog("popFilter", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] popFilter state={} current={}x{} parent={}x{} stackDepth={}",
//                    state, currentFbo.width, currentFbo.height, parentFbo.width, parentFbo.height, fboStack.size()
//            );
//        }
        drawWithShader(filteredFbo, shadowFbo, state);
    }

    private static void drawWithShader(RenderTarget fbo, RenderTarget shadowFbo, Filter.FilterState state) {
        ShaderInstance shader = ShaderRegistry.getFilterShader();

        Matrix4f oldProjection = new Matrix4f(Base.getProjectionMatrix());

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value,
                GlStateManager.SourceFactor.ONE.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value
        );
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();

        if (shader == null) {
            Base.setPositionColorShader();
        } else {
            Base.setShader(shader);
            // Blur is precomputed as two separable passes. The composite shader
            // only applies the inexpensive color/opacity/shadow operations.
            setupUniforms(shader, state, fbo, false, true,
                    1.0f / Math.max(1, Client.getWindow().getGuiScaledWidth()),
                    1.0f / Math.max(1, Client.getWindow().getGuiScaledHeight()));
        }

        Base.setShaderTexture(0, fbo.getColorTextureId());
        Base.setShaderTexture(1, shadowFbo.getColorTextureId());
        Base.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float guiW = (float) Client.getWindow().getGuiScaledWidth();
        float guiH = (float) Client.getWindow().getGuiScaledHeight();
        Matrix4f matrix = new Matrix4f().setOrtho(0, guiW, guiH, 0, -1000, 1000);
        Base.setProjectionMatrix(matrix);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();

        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(0, guiH, 0).uv(0, 0).endVertex();
        bufferbuilder.vertex(guiW, guiH, 0).uv(1, 0).endVertex();
        bufferbuilder.vertex(guiW, 0, 0).uv(1, 1).endVertex();
        bufferbuilder.vertex(0, 0, 0).uv(0, 1).endVertex();

        BufferUploader.drawWithShader(bufferbuilder.end());

        GlStateManager._depthMask(true);
        if (Base.isDepthTestEnabled()) GlStateManager._enableDepthTest();
        else GlStateManager._disableDepthTest();
        Base.setProjectionMatrix(oldProjection);
    }

    public static void renderBackdrop(Element target, PoseStack poseStack) {
        // A backdrop snapshot must include every draw submitted before this
        // element. It also creates a natural batch boundary for the FBO copy.
        Graph.endBatch();
        ImageDrawer.flushBatch();

        RenderTarget currentBound = fboStack.isEmpty() ? Minecraft.getInstance().getMainRenderTarget() : fboStack.peek();
        Filter.FilterState state = Filter.getBackdropFilterOf(target);
        Rect rect = Rect.of(target);
        BackdropSource source = prepareBackdropSource(currentBound, rect, state.blurRadius());
        if (source == null) return;
        RenderTarget shadowTarget = prepareBackdropShadow(source, state);

        currentBound.bindWrite(true);
        drawBackdropWithShader(source, shadowTarget, state, rect);
    }

    private static void drawBackdropWithShader(BackdropSource source, RenderTarget shadowTarget,
                                               Filter.FilterState state, Rect rect) {
        ShaderInstance shader = ShaderRegistry.getFilterShader();
        if (shader == null) return;

        Matrix4f oldProjection = new Matrix4f(Base.getProjectionMatrix());

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value,
                GlStateManager.SourceFactor.ONE.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value
        );
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();

        Position p = rect.getBodyRectPosition();
        Size s = rect.getBodyRectSize();

        float guiW = (float) Client.getWindow().getGuiScaledWidth();
        float guiH = (float) Client.getWindow().getGuiScaledHeight();
        Matrix4f matrix = new Matrix4f().setOrtho(0, guiW, guiH, 0, -1000, 1000);
        Base.setProjectionMatrix(matrix);

        Base.setShader(shader);
        setupUniforms(shader, state, source.target(), true, true, source.uvPerGuiX(), source.uvPerGuiY());
        setupBackdropClipUniforms(shader, rect, guiW, guiH);
        Base.setShaderTexture(0, source.target().getColorTextureId());
        Base.setShaderTexture(1, shadowTarget.getColorTextureId());
        Base.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Base.setProjectionMatrix(matrix);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        float x0 = (float) p.x;
        float y0 = (float) p.y;
        float x1 = x0 + (float) s.width();
        float y1 = y0 + (float) s.height();

        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(x0, y1, 0).uv(source.u0(), source.vBottom()).endVertex();
        bufferbuilder.vertex(x1, y1, 0).uv(source.u1(), source.vBottom()).endVertex();
        bufferbuilder.vertex(x1, y0, 0).uv(source.u1(), source.vTop()).endVertex();
        bufferbuilder.vertex(x0, y0, 0).uv(source.u0(), source.vTop()).endVertex();

        BufferUploader.drawWithShader(bufferbuilder.end());

        GlStateManager._depthMask(true);
        if (Base.isDepthTestEnabled()) GlStateManager._enableDepthTest();
        else GlStateManager._disableDepthTest();
        Base.setProjectionMatrix(oldProjection);
    }

    private static BackdropSource prepareBackdropSource(RenderTarget source, Rect rect, float cssBlurRadius) {
        if (source == null || source.width <= 0 || source.height <= 0) return null;
        float guiW = (float) Client.getWindow().getGuiScaledWidth();
        float guiH = (float) Client.getWindow().getGuiScaledHeight();
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
        RenderTarget ping = acquireBackdropTarget(targetWidth, targetHeight);
        blitRegion(source, ping, srcX0, srcY0, srcX1, srcY1);

        float reducedRadius = physicalRadius / downsample;
        if (reducedRadius >= 0.5f) {
            RenderTarget pong = acquireBackdropTarget(targetWidth, targetHeight);
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

    private static RenderTarget prepareBackdropShadow(BackdropSource source, Filter.FilterState state) {
        if (!state.hasDropShadow() || state.dropShadowBlur() < 0.5f) return source.target();
        float textureRadius = state.dropShadowBlur() * Math.max(
                source.uvPerGuiX() * source.target().width,
                source.uvPerGuiY() * source.target().height
        );
        return blurTexture(source.target(), textureRadius);
    }

    private static RenderTarget prepareFullFilterSource(RenderTarget source, float cssBlurRadius) {
        if (source == null || cssBlurRadius < 0.5f) return source;
        float guiW = Math.max(1.0f, (float) Client.getWindow().getGuiScaledWidth());
        float guiH = Math.max(1.0f, (float) Client.getWindow().getGuiScaledHeight());
        float physicalRadius = Math.max(0, cssBlurRadius)
                * Math.max(source.width / guiW, source.height / guiH);
        return blurTexture(source, physicalRadius);
    }

    private static RenderTarget blurTexture(RenderTarget source, float physicalRadius) {
        int downsample = chooseDownsample(physicalRadius);
        int width = Math.max(1, (int) Math.ceil(source.width / (double) downsample));
        int height = Math.max(1, (int) Math.ceil(source.height / (double) downsample));
        RenderTarget ping = acquireBackdropTarget(width, height);
        blitRegion(source, ping, 0, 0, source.width, source.height);
        RenderTarget pong = acquireBackdropTarget(width, height);
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

    private static RenderTarget acquireBackdropTarget(int width, int height) {
        boolean onOsx = Minecraft.ON_OSX;
        RenderTarget target;
        if (backdropPoolPointer < backdropPool.size()) {
            target = backdropPool.get(backdropPoolPointer);
            if (target.width != width || target.height != height) {
                target.destroyBuffers();
                target = new TextureTarget(width, height, false, onOsx);
                backdropPool.set(backdropPoolPointer, target);
            }
        } else {
            target = new TextureTarget(width, height, false, onOsx);
            backdropPool.add(target);
        }
        backdropPoolPointer++;
        return target;
    }

    private static void blitRegion(RenderTarget source, RenderTarget target,
                                   int srcX0, int srcY0, int srcX1, int srcY1) {
        int previousFbo = GlStateManager.getBoundFramebuffer();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                srcX0, srcY0, srcX1, srcY1,
                0, 0, target.width, target.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        );
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
    }

    private static void drawBlurPass(RenderTarget source, RenderTarget target, float radius,
                                     float directionX, float directionY) {
        ShaderInstance shader = ShaderRegistry.getFilterBlurShader();
        if (shader == null) return;

        Matrix4f oldProjection = new Matrix4f(Base.getProjectionMatrix());
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();

        Matrix4f projection = new Matrix4f().setOrtho(0, target.width, target.height, 0, -1000, 1000);
        Base.setProjectionMatrix(projection);
        Base.setShader(shader);
        if (shader.getUniform("Direction") != null) shader.getUniform("Direction").set(directionX, directionY);
        if (shader.getUniform("Radius") != null) shader.getUniform("Radius").set(Math.min(32.0f, radius));
        Base.setShaderTexture(0, source.getColorTextureId());
        Base.setShaderColor(1, 1, 1, 1);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(0, target.height, 0).uv(0, 0).endVertex();
        builder.vertex(target.width, target.height, 0).uv(1, 0).endVertex();
        builder.vertex(target.width, 0, 0).uv(1, 1).endVertex();
        builder.vertex(0, 0, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(builder.end());

        GlStateManager._depthMask(true);
        if (Base.isDepthTestEnabled()) GlStateManager._enableDepthTest();
        else GlStateManager._disableDepthTest();
        Base.setProjectionMatrix(oldProjection);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record BackdropSource(RenderTarget target, float u0, float vBottom, float u1, float vTop,
                                  float uvPerGuiX, float uvPerGuiY) {}

    private static void setupUniforms(ShaderInstance shader, Filter.FilterState state, RenderTarget fbo,
                                      boolean forceAlpha, boolean preBlurred,
                                      float uvPerGuiX, float uvPerGuiY) {
        float blurRadius = preBlurred ? 0.0f
                : (forceAlpha ? Math.min(state.blurRadius(), MAX_REASONABLE_BACKDROP_BLUR) : state.blurRadius());
        if (shader.getUniform("BlurRadius") != null) shader.getUniform("BlurRadius").set(blurRadius);
        if (shader.getUniform("Brightness") != null) shader.getUniform("Brightness").set(state.brightness());
        if (shader.getUniform("Grayscale") != null) shader.getUniform("Grayscale").set(state.grayscale());
        if (shader.getUniform("Invert") != null) shader.getUniform("Invert").set(state.invert());
        if (shader.getUniform("HueRotate") != null) shader.getUniform("HueRotate").set(state.hueRotate());
        if (shader.getUniform("Opacity") != null) shader.getUniform("Opacity").set(state.opacity());
        if (shader.getUniform("ShadowOffset") != null) shader.getUniform("ShadowOffset").set(state.dropShadowX(), state.dropShadowY());
        if (shader.getUniform("ShadowBlur") != null) shader.getUniform("ShadowBlur").set(state.dropShadowBlur());
        if (shader.getUniform("ShadowColor") != null) {
            int c = state.dropShadowColor();
            float a = ((c >>> 24) & 0xFF) / 255f;
            float r = ((c >>> 16) & 0xFF) / 255f;
            float g = ((c >>> 8) & 0xFF) / 255f;
            float b = (c & 0xFF) / 255f;
            shader.getUniform("ShadowColor").set(r, g, b, a);
        }
        if (shader.getUniform("InSize") != null) shader.getUniform("InSize").set((float) fbo.width, (float) fbo.height);
        if (shader.getUniform("ForceAlpha") != null) shader.getUniform("ForceAlpha").set(forceAlpha ? 1.0f : 0.0f);
        if (shader.getUniform("ClipEnabled") != null) shader.getUniform("ClipEnabled").set(0.0f);
        if (shader.getUniform("GuiSize") != null) {
            shader.getUniform("GuiSize").set((float) Client.getWindow().getGuiScaledWidth(), (float) Client.getWindow().getGuiScaledHeight());
        }
        if (shader.getUniform("UvPerGuiPixel") != null) {
            shader.getUniform("UvPerGuiPixel").set(uvPerGuiX, uvPerGuiY);
        }
    }

    private static void setupBackdropClipUniforms(ShaderInstance shader, Rect rect, float guiW, float guiH) {
        if (shader.getUniform("ClipEnabled") == null) return;
        Position p = rect.getBodyRectPosition();
        Size s = rect.getBodyRectSize();
        float[] radii = rect.getBodyRadius();
        shader.getUniform("ClipEnabled").set(1.0f);
        if (shader.getUniform("ClipRect") != null) {
            shader.getUniform("ClipRect").set((float) p.x, (float) p.y, (float) s.width(), (float) s.height());
        }
        if (shader.getUniform("ClipRadii") != null && radii != null && radii.length >= 4) {
            shader.getUniform("ClipRadii").set(radii[0], radii[1], radii[2], radii[3]);
        }
        if (shader.getUniform("GuiSize") != null) {
            shader.getUniform("GuiSize").set(guiW, guiH);
        }
//        if (shouldLog("drawBackdrop.clip", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] drawBackdropWithShader clip rect=({}, {}) size=({}, {}) radii=({}, {}, {}, {}) gui=({}, {})",
//                    p.x, p.y, s.width(), s.height(),
//                    radii[0], radii[1], radii[2], radii[3],
//                    guiW, guiH
//            );
//        }
    }
}
