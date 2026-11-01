package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.style.MaskImage;
import com.sighs.apricityui.viewport.ApricityViewport;

import java.util.Set;

public final class ScreenDocumentCache implements DocumentPaintCache, AutoCloseable {
    private static final long MAX_BYTES = 128L * 1024L * 1024L;
    private static final Set<String> SAFE_TYPES = Set.of(
            "com.sighs.apricityui.element.Html",
            "com.sighs.apricityui.element.Body",
            "com.sighs.apricityui.element.Div",
            "com.sighs.apricityui.element.Span",
            "com.sighs.apricityui.element.Pre",
            "com.sighs.apricityui.element.A",
            "com.sighs.apricityui.element.Path",
            "com.sighs.apricityui.element.Svg",
            "com.sighs.apricityui.init.Element"
    );
    private static final Set<String> SAFE_TAGS = Set.of("HTML", "BODY", "DIV", "SPAN", "PRE", "A",
            "P", "H1", "H2", "H3", "H4", "H5", "H6", "SECTION", "MAIN", "HEADER", "FOOTER",
            "ARTICLE", "UL", "OL", "LI", "STRONG", "EM", "B", "I", "SMALL", "LABEL",
            "BLOCKQUOTE", "CODE", "BR", "HR", "SVG", "PATH", "::BEFORE", "::AFTER");

    public static ScreenDocumentCache attach(Document document) {
        if (document == null) return null;
        DocumentPaintCache existing = DocumentPaintCache.forDocument(document);
        if (existing instanceof ScreenDocumentCache cache) return cache;
        ScreenDocumentCache cache = new ScreenDocumentCache();
        DocumentPaintCache.register(document, cache);
        return cache;
    }

    public static void detach(Document document) {
        DocumentPaintCache cache = DocumentPaintCache.forDocument(document);
        if (cache instanceof ScreenDocumentCache screenCache) {
            DocumentPaintCache.unregister(document, screenCache);
            screenCache.close();
        }
    }

    private FboHandle target;
    private Key key;
    private long checkedVersion = Long.MIN_VALUE;
    private long checkedGeneration = Long.MIN_VALUE;
    private boolean safe;

    @Override
    public boolean render(Document document, PoseStack poseStack, Runnable paint) {
        long visualVersion = document.getVisualVersion();
        long generation = document.getRefreshGeneration();
        if (visualVersion != checkedVersion || generation != checkedGeneration) {
            safe = isSafe(document);
            checkedVersion = visualVersion;
            checkedGeneration = generation;
        }
        if (!Boolean.parseBoolean(System.getProperty("apricityui.screenCache", "true")) || !safe) {
            paint.run();
            return true;
        }

        ApricityViewport viewport = document.getViewport();
        int width = Math.max(1, (int) Math.ceil(viewport.layoutWidth() * viewport.renderScale()));
        int height = Math.max(1, (int) Math.ceil(viewport.layoutHeight() * viewport.renderScale()));
        if ((long) width * height * 12L > MAX_BYTES) {
            paint.run();
            return true;
        }
        Key next = new Key(document, generation, visualVersion, width, height,
                viewport.layoutWidth(), viewport.layoutHeight(), Float.floatToIntBits(viewport.renderScale()),
                Double.doubleToLongBits(viewport.scissorScale()));
        if (target == null || target.width != width || target.height != height) {
            releaseTarget();
            try {
                target = AuiServices.render().createOffscreenTarget(width, height, true);
                AuiServices.render().enableStencil(target);
            } catch (RuntimeException | LinkageError unavailable) {
                target = null;
                paint.run();
                return true;
            }
        }

        if (!next.equals(key)) {
            if (!repaint(paint)) {
                key = null;
                paint.run();
                return true;
            }
            key = next;
        }
        AuiServices.render().drawOffscreenTexture(PoseMatrices.of(poseStack), target,
                viewport.layoutWidth(), viewport.layoutHeight());
        return true;
    }

    private boolean repaint(Runnable paint) {
        AuiRenderService render = AuiServices.render();
        FontDrawer.beginPaintCompletenessTracking();
        boolean tracking = true;
        try (AuiRenderService.RenderStateScope targetScope = render.pushBackgroundTarget(target);
             AuiRenderService.RenderStateScope logicalTarget = FilterRenderer.pushScreenTarget(target)) {
            Base.commitDraws();
            render.clear(target, 0, 0, 0, 0);
            render.enableStencil(target);
            render.clearStencilBuffer();
            paint.run();
            Base.commitDraws();
            boolean complete = FontDrawer.endPaintCompletenessTracking();
            tracking = false;
            return complete;
        } catch (RuntimeException | LinkageError failure) {
            key = null;
            return false;
        } finally {
            if (tracking) FontDrawer.endPaintCompletenessTracking();
        }
    }

    static boolean isDefaultOpacity(String opacity) {
        try {
            return Math.abs(Double.parseDouble(opacity) - 1.0d) < 0.000001d;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isSafe(Document document) {
        if (document == null || document.getPaintList().isEmpty()) return false;
        for (Element element : document.getElements()) {
            if (!element.isConnected() || !SAFE_TYPES.contains(element.getClass().getName())) return false;
            String tag = element.tagName == null ? "" : element.tagName.toUpperCase(java.util.Locale.ROOT);
            if (!SAFE_TAGS.contains(tag)) return false;
            var style = element.getRawComputedStyle();
            if (style == null || !isDefaultOpacity(style.opacity) || !"standard".equalsIgnoreCase(style.dynamicRangeLimit)
                    || !"none".equals(style.filter) || !"none".equals(style.backdropFilter)
                    || !"normal".equalsIgnoreCase(style.mixBlendMode) || !"auto".equalsIgnoreCase(style.isolation)
                    || MaskImage.hasMask(element)) return false;
        }
        for (RenderNode node : document.getPaintList()) {
            if (node instanceof RenderNode.ElementPhaseNode || node instanceof RenderNode.ElementBackgroundNode
                    || node instanceof RenderNode.MaskPushNode || node instanceof RenderNode.MaskPopNode) continue;
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        releaseTarget();
    }

    private void releaseTarget() {
        if (target != null) {
            AuiServices.render().destroyBuffers(target);
            target = null;
        }
        key = null;
    }

    private record Key(Document document, long generation, long visualVersion, int width, int height,
                       int layoutWidth, int layoutHeight, int renderScale, long scissorScale) {
    }
}
