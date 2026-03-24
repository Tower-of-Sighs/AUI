package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        if (mainRenderTarget != null) {
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
                temp.enableStencil();
                fboPool.set(poolPointer, temp);
//                com.sighs.apricityui.ApricityUI.LOGGER.info(
//                        "[FilterRenderer] pushFilter resized temp target index={} size={}x{}",
//                        poolPointer, temp.width, temp.height
//                );
            }
        } else {
            temp = new TextureTarget((int) width, (int) height, true, ON_OSX);
            temp.enableStencil();
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

        RenderTarget currentFbo = fboStack.pop();
        RenderTarget parentFbo = fboStack.isEmpty() ? mainRenderTarget : fboStack.peek();
        parentFbo.bindWrite(false);

//        if (shouldLog("popFilter", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] popFilter state={} current={}x{} parent={}x{} stackDepth={}",
//                    state, currentFbo.width, currentFbo.height, parentFbo.width, parentFbo.height, fboStack.size()
//            );
//        }
        drawWithShader(currentFbo, state);
    }

    private static void drawWithShader(RenderTarget fbo, Filter.FilterState state) {
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
            setupUniforms(shader, state, fbo, false);
        }

        Base.setShaderTexture(0, fbo.getColorTextureId());
        Base.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float guiW = (float) Client.getWindow().getGuiScaledWidth();
        float guiH = (float) Client.getWindow().getGuiScaledHeight();
        Matrix4f matrix = new Matrix4f().setOrtho(0, guiW, guiH, 0, -1000, 1000);
        Base.setProjectionMatrix(matrix);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        bufferbuilder.addVertex(0, guiH, 0).setUv(0, 0);
        bufferbuilder.addVertex(guiW, guiH, 0).setUv(1, 0);
        bufferbuilder.addVertex(guiW, 0, 0).setUv(1, 1);
        bufferbuilder.addVertex(0, 0, 0).setUv(0, 1);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        GlStateManager._depthMask(true);
        GlStateManager._enableDepthTest();
        Base.setProjectionMatrix(oldProjection);
    }

    public static void renderBackdrop(Element target, PoseStack poseStack) {
        RenderTarget currentBound = fboStack.isEmpty() ? Minecraft.getInstance().getMainRenderTarget() : fboStack.peek();
        RenderTarget sourceTarget = mainRenderTarget != null ? mainRenderTarget : currentBound;
        RenderTarget sourceCopy = copyToBackdropSource(sourceTarget);
//        if (sourceCopy == null) {
//            if (shouldLog("renderBackdrop.null", LOG_INTERVAL_MS)) {
//                com.sighs.apricityui.ApricityUI.LOGGER.warn(
//                        "[FilterRenderer] renderBackdrop skipped: sourceCopy null target={} currentBound={} sourceTarget={}",
//                        target.uuid, currentBound, sourceTarget
//                );
//            }
//            return;
//        }
        currentBound.bindWrite(false);
//        if (shouldLog("renderBackdrop", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] renderBackdrop target={} currentBound={}x{} sourceTarget={}x{} sourceCopy={}x{}",
//                    target.uuid, currentBound.width, currentBound.height,
//                    sourceTarget.width, sourceTarget.height,
//                    sourceCopy.width, sourceCopy.height
//            );
//        }
        drawBackdropWithShader(sourceCopy, target, poseStack);
    }

    private static void drawBackdropWithShader(RenderTarget sourceFbo, Element target, PoseStack poseStack) {
        Graph.endBatch();
        ImageDrawer.flushBatch();
        ShaderInstance shader = ShaderRegistry.getFilterShader();
        Filter.FilterState state = Filter.getBackdropFilterOf(target);
//        if (shader == null || state == null) {
//            if (shouldLog("drawBackdrop.skip", LOG_INTERVAL_MS)) {
//                com.sighs.apricityui.ApricityUI.LOGGER.warn(
//                        "[FilterRenderer] drawBackdropWithShader skipped shader={} state={} target={}",
//                        shader, state, target.uuid
//                );
//            }
//            return;
//        }
//        if (shouldLog("drawBackdrop.state", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] drawBackdropWithShader state blur={} brightness={} grayscale={} invert={} hueRotate={} opacity={}",
//                    state.blurRadius(), state.brightness(), state.grayscale(), state.invert(), state.hueRotate(), state.opacity()
//            );
//        }

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

        Rect rect = Rect.of(target);
        Position p = rect.getBodyRectPosition();
        Size s = rect.getBodyRectSize();
//        if (shouldLog("drawBackdrop.details", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] drawBackdropWithShader target={} state={} rect=({}, {}) size=({}, {}) radius={}",
//                    target.uuid, state, p.x, p.y, s.width(), s.height(), rect.getBodyRadius()
//            );
//        }

        float guiW = (float) Client.getWindow().getGuiScaledWidth();
        float guiH = (float) Client.getWindow().getGuiScaledHeight();
        Matrix4f matrix = new Matrix4f().setOrtho(0, guiW, guiH, 0, -1000, 1000);
        Base.setProjectionMatrix(matrix);

        Base.setShader(shader);
        setupUniforms(shader, state, sourceFbo, true);
        setupBackdropClipUniforms(shader, rect, guiW, guiH);
        Base.setShaderTexture(0, sourceFbo.getColorTextureId());
        Base.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Base.setProjectionMatrix(matrix);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        bufferbuilder.addVertex(0, guiH, 0).setUv(0, 0);
        bufferbuilder.addVertex(guiW, guiH, 0).setUv(1, 0);
        bufferbuilder.addVertex(guiW, 0, 0).setUv(1, 1);
        bufferbuilder.addVertex(0, 0, 0).setUv(0, 1);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        GlStateManager._depthMask(true);
        GlStateManager._enableDepthTest();
        Base.setProjectionMatrix(oldProjection);
    }

    private static RenderTarget copyToBackdropSource(RenderTarget source) {
        if (source == null) return null;
        boolean ON_OSX = Minecraft.ON_OSX;
        int width = source.width;
        int height = source.height;

        RenderTarget temp;
        if (backdropPoolPointer < backdropPool.size()) {
            temp = backdropPool.get(backdropPoolPointer);
            if (temp.width != width || temp.height != height) {
                temp.destroyBuffers();
                temp = new TextureTarget(width, height, true, ON_OSX);
                backdropPool.set(backdropPoolPointer, temp);
//                com.sighs.apricityui.ApricityUI.LOGGER.info(
//                        "[FilterRenderer] backdrop resize index={} size={}x{}",
//                        backdropPoolPointer, width, height
//                );
            }
        } else {
            temp = new TextureTarget(width, height, true, ON_OSX);
            backdropPool.add(temp);
            com.sighs.apricityui.ApricityUI.LOGGER.info(
                    "[FilterRenderer] backdrop create index={} size={}x{}",
                    backdropPoolPointer, width, height
            );
        }
        backdropPoolPointer++;

//        if (shouldLog("backdrop.copy", LOG_INTERVAL_MS)) {
//            com.sighs.apricityui.ApricityUI.LOGGER.info(
//                    "[FilterRenderer] backdrop copy source={}x{} target={}x{} index={} srcTex={} dstTex={}",
//                    width, height, temp.width, temp.height, backdropPoolPointer - 1,
//                    source.getColorTextureId(), temp.getColorTextureId()
//            );
//        }

        temp.setClearColor(0f, 0f, 0f, 0f);
        temp.clear(ON_OSX);

        int prevFbo = GlStateManager.getBoundFramebuffer();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, temp.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0, 0, width, height,
                0, 0, temp.width, temp.height,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);

        return temp;
    }

    private static void setupUniforms(ShaderInstance shader, Filter.FilterState state, RenderTarget fbo, boolean forceAlpha) {
        if (shader.getUniform("BlurRadius") != null) shader.getUniform("BlurRadius").set(state.blurRadius());
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