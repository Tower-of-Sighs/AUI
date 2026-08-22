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
import com.sighs.apricityui.style.MaskImage;
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

    /**
     * CSS mask 合成。调用前 {@link #pushFilter()} 已把内容子树切到离屏 FBO C。
     * 这里把 mask 层画进第二个池化 FBO M（M 的透明区域即被遮掉的区域），
     * 用 dst-in 混合（C 是预乘 alpha，乘以 M 的 mask 值即挖空）写回 C，
     * 最后以恒等 filter 把 C 合成回父目标。mask 值取 alpha 还是 luminance
     * 由 {@link MaskImage#effectiveLuminance} 决定（混合 mode 按 alpha）。
     *
     * <p>加载失败/未就绪导致一层都画不上时跳过 dst-in，内容保持可见
     * （fail-open，与浏览器"遮罩失败=全遮掉"的行为不同，见文档）。</p>
     */
    public static void popMaskImage(Element target, PoseStack poseStack) {
        if (fboStack.isEmpty()) return;

        // 与 popFilter 相同：先把子树剩余的批处理绘制落进内容 FBO
        ImageDrawer.flushBatch();
        Graph.endBatch();

        List<MaskImage.ResolvedLayer> layers = MaskImage.layersOf(target);

        // 注意顺序：必须在内容 FBO C 出栈之前取 mask 画布 M。
        // 根级 mask 下栈一空 pushFilter 会把 poolPointer 归零，M 可能复用到 C 本身。
        pushFilter();
        FboHandle maskFbo = fboStack.peek();
        boolean painted;
        try {
            painted = paintMaskLayers(target, poseStack, layers, maskFbo);
        } finally {
            ImageDrawer.flushBatch();
            Graph.endBatch();
            // mask 画布用完即出栈（不做 filter 合成）；stencil/scissor 状态
            // 已由 MaskImagePainter 内部的 Mask.push/pop 成对恢复
            fboStack.pop();
        }

        FboHandle contentFbo = fboStack.pop();
        FboHandle parentFbo = fboStack.isEmpty() ? mainRenderTarget : fboStack.peek();
        try {
            if (painted) {
                AuiServices.render().bindWrite(contentFbo, true);
                drawMaskBlit(maskFbo, MaskImage.effectiveLuminance(layers));
            }
            AuiServices.render().bindWrite(parentFbo, true);
            drawWithShader(contentFbo, contentFbo, Filter.FilterState.EMPTY, 1.0f);
        } finally {
            if (parentFbo != null) AuiServices.render().bindWrite(parentFbo, true);
        }
    }

    /**
     * 自下而上逐层累积 mask 画布 M：add 层直接以标准半透明（source-over）画进 M；
     * intersect/subtract/exclude 层先画进 scratch FBO L（栈上有 M，pushFilter 不会
     * 重置 poolPointer，L 必然是新 FBO），再以对应 Porter-Duff 混合 merge 回 M。
     * 最底层的 composite 值没有意义（下方无可合成对象），按 add 处理。
     */
    private static boolean paintMaskLayers(Element target, PoseStack poseStack,
                                           List<MaskImage.ResolvedLayer> layers, FboHandle maskFbo) {
        boolean any = false;
        for (int i = layers.size() - 1; i >= 0; i--) {
            MaskImage.ResolvedLayer layer = layers.get(i);
            AuiRenderService.MaskCompositeOp op = mergeOpOf(layer.composite());
            if (op == null || i == layers.size() - 1) {
                any |= MaskImagePainter.paintLayer(target, poseStack, layer);
                continue;
            }
            pushFilter();
            FboHandle scratchFbo = fboStack.peek();
            boolean scratchPainted;
            try {
                scratchPainted = MaskImagePainter.paintLayer(target, poseStack, layer);
            } finally {
                ImageDrawer.flushBatch();
                Graph.endBatch();
                fboStack.pop();
            }
            if (scratchPainted) {
                AuiServices.render().bindWrite(maskFbo, true);
                drawMaskMergeBlit(scratchFbo, op);
                any = true;
            }
        }
        return any;
    }

    /** 非 add 的 composite 值映射到 Porter-Duff merge 算子；add/未知值返回 null。 */
    private static AuiRenderService.MaskCompositeOp mergeOpOf(String composite) {
        return switch (composite) {
            case "intersect" -> AuiRenderService.MaskCompositeOp.INTERSECT;
            case "subtract" -> AuiRenderService.MaskCompositeOp.SUBTRACT;
            case "exclude" -> AuiRenderService.MaskCompositeOp.EXCLUDE;
            default -> null;
        };
    }

    /**
     * dst-in 全窗 blit：dest(C) *= src(M) 的 mask 值（alpha 或 luminance）。
     * legacy 端由 filter_mask 着色器 JSON 自带 zero/srcalpha 混合
     * （ShaderInstance.apply 会覆盖动态混合状态，不能靠 setBlendFuncSeparate），
     * 26.1 端由 filter_mask pipeline 烘焙同款混合。
     */
    private static void drawMaskBlit(FboHandle maskFbo, boolean luminance) {
        Object shader = AuiServices.render().getFilterMaskShader(luminance);
        if (shader == null) return; // 后端未提供 mask shader 时 fail-open

        withBlendRenderState(false, () -> {
            Base.setShader(shader);
            AuiServices.render().bindColorTexture(maskFbo, 0);
            Base.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            AuiServices.render().setShaderUniformFloat("MaskLuminance", luminance ? 1.0f : 0.0f);

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

    /**
     * mask-composite merge 全窗 blit：L(src) 按 Porter-Duff 算子合成进 M(dst)。
     * 算子完全由后端烘焙的混合状态表达（source-in / source-out / xor），
     * 着色器只做 Sampler0 透传。
     */
    private static void drawMaskMergeBlit(FboHandle scratchFbo, AuiRenderService.MaskCompositeOp op) {
        Object shader = AuiServices.render().getFilterMaskMergeShader(op);
        if (shader == null) return; // 后端不支持 merge 时丢弃该层（fail-open）

        withBlendRenderState(false, () -> {
            Base.setShader(shader);
            AuiServices.render().bindColorTexture(scratchFbo, 0);
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

    public static void popFilter(Filter.FilterState state) {
        popFilter(state, 1.0f);
    }

    public static void popFilter(Filter.FilterState state, float dynamicRangeLimit) {
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
            drawWithShader(filteredFbo, shadowFbo, state, dynamicRangeLimit);
        } finally {
            if (parentFbo != null) AuiServices.render().bindWrite(parentFbo, true);
        }
    }

    /** Composites an isolated element layer using the CSS mix-blend-mode operator. */
    public static void popBlend(String mode) {
        if (fboStack.isEmpty()) return;
        ImageDrawer.flushBatch();
        Graph.endBatch();
        FboHandle source = fboStack.pop();
        FboHandle parent = fboStack.isEmpty() ? mainRenderTarget : fboStack.peek();
        if (parent == null) return;
        AuiServices.render().bindWrite(parent, true);
        withBlendMode(mode, () -> drawTexture(source));
    }

    private static void withBlendMode(String mode, Runnable body) {
        AuiRenderService.RenderStateScope scope = AuiServices.render().pushFilterRenderState();
        if (scope == null) scope = AuiRenderService.RenderStateScope.NOOP;
        AuiServices.render().enableBlend();
        AuiServices.render().disableDepthTest();
        AuiServices.render().setDepthMask(false);
        int src = GL11.GL_SRC_ALPHA, dst = GL11.GL_ONE_MINUS_SRC_ALPHA;
        String m = mode == null ? "normal" : mode.toLowerCase(Locale.ROOT).trim();
        switch (m) {
            case "multiply" -> { src = GL11.GL_DST_COLOR; dst = GL11.GL_ONE_MINUS_SRC_ALPHA; }
            case "screen" -> { src = GL11.GL_ONE; dst = GL11.GL_ONE_MINUS_SRC_COLOR; }
            case "darken" -> { src = GL11.GL_ONE; dst = GL11.GL_ONE; }
            case "lighten" -> { src = GL11.GL_ONE; dst = GL11.GL_ONE; }
            case "difference" -> { src = GL11.GL_ONE_MINUS_DST_COLOR; dst = GL11.GL_ONE_MINUS_SRC_COLOR; }
            case "exclusion" -> { src = GL11.GL_ONE_MINUS_DST_COLOR; dst = GL11.GL_ONE_MINUS_SRC_COLOR; }
            default -> { }
        }
        AuiServices.render().setBlendFuncSeparate(src, dst, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try { body.run(); } finally {
            AuiServices.render().setDepthMask(true);
            if (Base.isDepthTestEnabled()) AuiServices.render().enableDepthTest();
            scope.close();
        }
    }

    private static void drawTexture(FboHandle fbo) {
        Object shader = AuiServices.render().getFilterShader();
        if (shader == null) return;
        Base.setShader(shader);
        setupUniforms(shader, Filter.FilterState.EMPTY, fbo, false, true, 1.0f,
                1.0f / Math.max(1, AuiServices.client().getScaledWidth()),
                1.0f / Math.max(1, AuiServices.client().getScaledHeight()));
        AuiServices.render().bindColorTexture(fbo, 0);
        Base.setShaderColor(1, 1, 1, 1);
        float w = (float) AuiServices.client().getScaledWidth();
        float h = (float) AuiServices.client().getScaledHeight();
        Base.setProjectionMatrix(orthoProjection(w, h));
        MeshBuilder mesh = AuiServices.render().beginMesh(MeshMode.QUADS, MeshFormat.POSITION_TEX);
        Matrix4f id = new Matrix4f();
        mesh.vertexUV(id, 0, h, 0, 0, 0); mesh.vertexUV(id, w, h, 0, 1, 0);
        mesh.vertexUV(id, w, 0, 0, 1, 1); mesh.vertexUV(id, 0, 0, 0, 0, 1); mesh.submit();
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

    private static void drawWithShader(FboHandle fbo, FboHandle shadowFbo, Filter.FilterState state,
                                       float dynamicRangeLimit) {
        Object shader = AuiServices.render().getFilterShader();

        withBlendRenderState(true, () -> {
            if (shader == null) {
                Base.setPositionColorShader();
            } else {
                Base.setShader(shader);
                // Blur is precomputed as two separable passes. The composite shader
                // only applies the inexpensive color/opacity/shadow operations.
                setupUniforms(shader, state, fbo, false, true, dynamicRangeLimit,
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
            setupUniforms(shader, state, source.target(), true, true, 1.0f, source.uvPerGuiX(), source.uvPerGuiY());
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
                                      boolean forceAlpha, boolean preBlurred, float dynamicRangeLimit,
                                      float uvPerGuiX, float uvPerGuiY) {
        float blurRadius = preBlurred ? 0.0f
                : (forceAlpha ? Math.min(state.blurRadius(), MAX_REASONABLE_BACKDROP_BLUR) : state.blurRadius());
        AuiServices.render().setShaderUniformFloat("BlurRadius", blurRadius);
        AuiServices.render().setShaderUniformFloat("Brightness", state.brightness());
        AuiServices.render().setShaderUniformFloat("Contrast", state.contrast());
        AuiServices.render().setShaderUniformFloat("Saturate", state.saturate());
        AuiServices.render().setShaderUniformFloat("Sepia", state.sepia());
        AuiServices.render().setShaderUniformFloat("Grayscale", state.grayscale());
        AuiServices.render().setShaderUniformFloat("Invert", state.invert());
        AuiServices.render().setShaderUniformFloat("HueRotate", state.hueRotate());
        AuiServices.render().setShaderUniformFloat("Opacity", state.opacity());
        AuiServices.render().setShaderUniformFloat("DynamicRangeLimit", dynamicRangeLimit);
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
