package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.*;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.parser.CSS;

public class Rect {
    public Element element;
    public Position position;
    public Box box;
    public String documentPath;
    public Background background;
    private final Size elementSize;
    private AABB visualBounds;
    private Position bodyRectPosition;
    private Size bodyRectSize;
    private float[] bodyRadius;
    private Position shadowPosition;
    private Size shadowSize;
    private Position contentPosition;

    public Rect(Element element) {
        this.element = element;
        position = Position.forRender(element);
        box = Box.of(element);
        elementSize = box.elementSize();
        background = Background.of(element);
        documentPath = element.document.getPath();
    }

    public static Rect of(Element element) {
        Rect cached = RectFrameCache.get(element);
        if (cached != null) return cached;
        Rect result = createAndCache(element);
        if (element != null) {
            var renderer = element.getRenderer();
            long dependency = renderer.rectDependency(element.document);
            if (!renderer.hasCommittedRect(dependency)) {
                renderer.commitRect(result, dependency);
            }
        }
        return result;
//        Rect cache = Cache.rect.get(element);
//        if (cache != null) return cache;
//        else {
//            Rect result = new Rect(element);
//            Cache.rect.set(element, result);
//            return result;
//        }
    }

    public static Rect createAndCache(Element element) {
        Rect result = new Rect(element);
        RectFrameCache.put(element, result);
        return result;
    }

    /**
     * 滚动平移快速路径：滚动只让子树的绘制位置整体偏移，盒模型、尺寸、圆角、
     * 背景都不变。平移 position 并丢弃派生位置缓存（尺寸类缓存与位置无关，保留）。
     * 不就地修改 position 字段——它可能引用共享的 {@link Position#ZERO}。
     */
    public void translate(double dx, double dy) {
        position = new Position(position.x + dx, position.y + dy);
        visualBounds = null;
        bodyRectPosition = null;
        shadowPosition = null;
        contentPosition = null;
    }

    public AABB getVisualBounds() {
        if (visualBounds != null) return visualBounds;
        double x = position.x + box.getMarginLeft();
        double y = position.y + box.getMarginTop();
        double w = elementSize.width();
        double h = elementSize.height();

        double minExtendX = 0;
        double minExtendY = 0;
        double maxExtendX = 0;
        double maxExtendY = 0;
        for (Box.Shadow shadow : box.shadows) {
            if (shadow.inset()) continue;
            if ((shadow.color().getValue() >>> 24) == 0) continue;
            double extent = shadow.size() + shadow.spread();
            minExtendX = Math.min(minExtendX, shadow.x() - extent);
            minExtendY = Math.min(minExtendY, shadow.y() - extent);
            maxExtendX = Math.max(maxExtendX, shadow.x() + extent);
            maxExtendY = Math.max(maxExtendY, shadow.y() + extent);
        }
        Style style = element.getComputedStyle();
        OutlineSpec outline = OutlineSpec.parse(style, elementSize.width());
        if (outline != null && outline.width > 0) {
            double extent = Math.max(0, outline.width
                    + Size.resolveLength(style.outlineOffset, elementSize.width(), 0));
            minExtendX = Math.min(minExtendX, -extent);
            minExtendY = Math.min(minExtendY, -extent);
            maxExtendX = Math.max(maxExtendX, extent);
            maxExtendY = Math.max(maxExtendY, extent);
        }
        if (minExtendX != 0 || minExtendY != 0 || maxExtendX != 0 || maxExtendY != 0) {
            x += minExtendX;
            y += minExtendY;
            w += maxExtendX - minExtendX;
            h += maxExtendY - minExtendY;
        }

        visualBounds = new AABB((float) x, (float) y, (float) w, (float) h);
        return visualBounds;
    }

    private double getMinBorderSize() {
        return Math.min(Math.min(box.getBorderLeft(), box.getBorderTop()), Math.min(box.getBorderRight(), box.getBorderBottom()));
    }

    public void drawBorder(PoseStack poseStack) {
        Box.SideBorder topBorder = box.getBorderTopSide();
        Box.SideBorder rightBorder = box.getBorderRightSide();
        Box.SideBorder bottomBorder = box.getBorderBottomSide();
        Box.SideBorder leftBorder = box.getBorderLeftSide();
        float topW = (float) topBorder.size();
        float bottomW = (float) bottomBorder.size();
        float leftW = (float) leftBorder.size();
        float rightW = (float) rightBorder.size();

        if (topW <= 0 && bottomW <= 0 && leftW <= 0 && rightW <= 0) {
            drawOutline(poseStack);
            return;
        }

        Graph.beginBatch();
        int topC = topBorder.color().getValue();
        int bottomC = bottomBorder.color().getValue();
        int leftC = leftBorder.color().getValue();
        int rightC = rightBorder.color().getValue();

        double x = position.x + box.getMarginLeft();
        double y = position.y + box.getMarginTop();
        double w = elementSize.width();
        double h = elementSize.height();

        float[] radii = box.getCalculatedRadii((float) w, (float) h, 0);
        float[] borders = new float[]{topW, rightW, bottomW, leftW};
        int[] colors = new int[]{topC, rightC, bottomC, leftC};
        Graph.drawComplexRoundedBorder(poseStack.last().pose(), (float) x, (float) y, (float) w, (float) h, radii, borders, colors);

        if (box.borderImage != null && WorldWindowRenderContext.shouldRenderBackgroundDetails()) {
            if (box.borderImage.gradient != null) {
                Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                        (float) x, (float) y, (float) w, (float) h,
                        radii, box.borderImage.gradient);
            }
            Position p = position.add(new Position(box.getMarginLeft(), box.getMarginTop()));
            Size s = getShadowSize();
            String path = Loader.resolve(documentPath, box.borderImage.source);
            Base.commitDraws();
            ImageDrawer.drawNineSlice(poseStack, path, (int) p.x, (int) p.y, (int) s.width(), (int) s.height(), box.borderImage);
            drawOutline(poseStack);
            return;
        }
        drawOutline(poseStack);
    }

    private void drawOutline(PoseStack poseStack) {
        Style style = element.getComputedStyle();
        OutlineSpec outline = OutlineSpec.parse(style, elementSize.width());
        if (outline == null || outline.width <= 0 || (outline.color >>> 24) == 0) return;

        double offset = Size.resolveLength(style.outlineOffset, elementSize.width(), 0);
        double expansion = offset + outline.width;
        float x = (float) (position.x + box.getMarginLeft() - expansion);
        float y = (float) (position.y + box.getMarginTop() - expansion);
        float width = (float) Math.max(0, elementSize.width() + expansion * 2);
        float height = (float) Math.max(0, elementSize.height() + expansion * 2);
        float stroke = (float) outline.width;
        if (width <= 0 || height <= 0) return;

        if (outline.dashed) {
            drawDashedOutline(poseStack, x, y, width, height, stroke, outline.color);
            return;
        }
        float[] radii = box.getCalculatedRadii(width, height, (float) -expansion);
        float[] borders = new float[]{stroke, stroke, stroke, stroke};
        int[] colors = new int[]{outline.color, outline.color, outline.color, outline.color};
        Graph.drawComplexRoundedBorder(poseStack.last().pose(), x, y, width, height, radii, borders, colors);
    }

    private static void drawDashedOutline(PoseStack poseStack, float x, float y, float width, float height,
                                          float stroke, int color) {
        float dash = Math.max(1, stroke * 2);
        float step = dash * 2;
        for (float cursor = 0; cursor < width; cursor += step) {
            float length = Math.min(dash, width - cursor);
            Graph.drawFillRect(poseStack.last().pose(), x + cursor, y, x + cursor + length, y + stroke, color);
            Graph.drawFillRect(poseStack.last().pose(), x + cursor, y + height - stroke,
                    x + cursor + length, y + height, color);
        }
        for (float cursor = 0; cursor < height; cursor += step) {
            float length = Math.min(dash, height - cursor);
            Graph.drawFillRect(poseStack.last().pose(), x, y + cursor, x + stroke, y + cursor + length, color);
            Graph.drawFillRect(poseStack.last().pose(), x + width - stroke, y + cursor,
                    x + width, y + cursor + length, color);
        }
    }

    private record OutlineSpec(double width, boolean dashed, int color) {
        private static OutlineSpec parse(Style style, double percentBasis) {
            if (style == null || style.outline == null) return null;
            String raw = style.outline.trim();
            if (raw.isEmpty() || "none".equalsIgnoreCase(raw)) return null;
            double width = 3;
            boolean dashed = false;
            StringBuilder color = new StringBuilder();
            for (String token : raw.split("\\s+")) {
                Double length = Size.tryResolveLength(token, percentBasis);
                if (length != null) {
                    width = Math.max(0, length);
                } else if ("dashed".equalsIgnoreCase(token)) {
                    dashed = true;
                } else if (!"solid".equalsIgnoreCase(token) && !"dotted".equalsIgnoreCase(token)
                        && !"double".equalsIgnoreCase(token)) {
                    if (!color.isEmpty()) color.append(' ');
                    color.append(token);
                }
            }
            String colorValue = color.isEmpty() ? style.color : color.toString();
            if ("currentcolor".equalsIgnoreCase(colorValue)) colorValue = style.color;
            return new OutlineSpec(width, dashed, Color.parse(colorValue));
        }
    }

    public Position getBodyRectPosition() {
        if (bodyRectPosition != null) return bodyRectPosition;
        double x = position.x + box.getMarginLeft() + box.getBorderLeft();
        double y = position.y + box.getMarginTop() + box.getBorderTop();
        bodyRectPosition = new Position(x, y);
        return bodyRectPosition;
    }

    public Size getBodyRectSize() {
        if (bodyRectSize != null) return bodyRectSize;
        double width = elementSize.width() - box.getBorderHorizontal();
        double height = elementSize.height() - box.getBorderVertical();
        bodyRectSize = new Size(width, height);
        return bodyRectSize;
    }

    public Size getElementSize() {
        return elementSize;
    }

    public float[] getBodyRadius() {
        if (bodyRadius != null) return bodyRadius;
        Size s = getBodyRectSize();
        bodyRadius = box.getCalculatedRadii((float) s.width(), (float) s.height(), (float) getMinBorderSize());
        return bodyRadius;
    }

    public void drawBody(PoseStack poseStack) {
        drawBody(poseStack, getBodyRectSize());
        if (WorldWindowRenderContext.shouldRenderEffects()) drawInsetShadow(poseStack);
    }

    public void drawBody(PoseStack poseStack, Size s) {
        Position p = getBodyRectPosition();
        float[] radii = getBodyRadius();
        Background.Layer imageOnlyLayer = resolveImageOnlyLayer();
        if (imageOnlyLayer != null && WorldWindowRenderContext.shouldRenderBackgroundDetails()) {
            ImageDrawer.drawComplexBackground(
                    poseStack,
                    (float) p.x,
                    (float) p.y,
                    (float) s.width(),
                    (float) s.height(),
                    imageOnlyLayer,
                    element
            );
            return;
        }

        boolean layeredBackground = background.getLayers().size() > 1;
        if (layeredBackground) Graph.beginLayeredBatch();
        else Graph.beginBatch();
        if (!background.color.equals("unset")) {
            int color = new Color(background.color).getValue();
            var matrix = poseStack.last().pose();
            if (hasNoRadius(radii) && element.tagName.startsWith("::")
                    && hasCssTransform(element.getComputedStyle().transform)) {
                Graph.drawCssCoverageRect(matrix,
                        (float) p.x, (float) p.y, (float) (p.x + s.width()), (float) (p.y + s.height()), color);
            } else {
                Graph.drawUnifiedRoundedRect(matrix,
                        (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii, color);
            }
        }
        if (!WorldWindowRenderContext.shouldRenderBackgroundDetails()) {
            Graph.endBatch();
            return;
        }
        if (!background.getLayers().isEmpty()) {
            // CSS: background-image 第一层在最上方，因此按逆序绘制
            for (int i = background.getLayers().size() - 1; i >= 0; i--) {
                Background.Layer layer = background.getLayers().get(i);
                if (layer == null) continue;
                if (layer.gradient != null) {
                    drawGradientLayer(poseStack, p, s, radii, layer, layeredBackground);
                }
                if (!"unset".equals(layer.imagePath)) {
                    Graph.endBatch();
                    ImageDrawer.drawComplexBackground(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), layer, element);
                    if (layeredBackground) Graph.beginLayeredBatch();
                    else Graph.beginBatch();
                }
            }
            return;
        }

        // 兼容旧单层字段
        if (background.gradient != null) {
            Background.Layer legacyLayer = new Background.Layer();
            legacyLayer.gradient = background.gradient;
            legacyLayer.repeat = background.repeat;
            legacyLayer.size = background.size;
            legacyLayer.position = background.position;
            drawGradientLayer(poseStack, p, s, radii, legacyLayer, false);
        }
        if (!background.imagePath.equals("unset")) {
            Graph.endBatch();
            ImageDrawer.drawComplexBackground(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), background, element);
            return;
        }
    }

    private static boolean hasNoRadius(float[] radii) {
        if (radii == null) return true;
        for (float radius : radii) {
            if (Math.abs(radius) >= 0.0001f) return false;
        }
        return true;
    }

    private static boolean tileCoversBox(ImageDrawer.GradientTile tile, Size size) {
        return Math.abs(tile.x()) < 0.001f
                && Math.abs(tile.y()) < 0.001f
                && Math.abs(tile.width() - (float) size.width()) < 0.001f
                && Math.abs(tile.height() - (float) size.height()) < 0.001f;
    }

    private static boolean hasCssTransform(String transform) {
        return transform != null && !transform.isBlank()
                && !"none".equalsIgnoreCase(transform) && !"unset".equalsIgnoreCase(transform);
    }

    private Background.Layer resolveImageOnlyLayer() {
        if (!"unset".equals(background.color)) return null;

        if (background.getLayers().size() == 1) {
            Background.Layer layer = background.getLayers().get(0);
            if (layer != null && layer.gradient == null && !"unset".equals(layer.imagePath)) {
                return layer;
            }
            return null;
        }

        if (!background.getLayers().isEmpty()
                || background.gradient != null
                || "unset".equals(background.imagePath)) {
            return null;
        }

        Background.Layer layer = new Background.Layer();
        layer.imagePath = background.imagePath;
        layer.repeat = background.repeat;
        layer.size = background.size;
        layer.position = background.position;
        return layer;
    }

    private void drawGradientLayer(PoseStack poseStack, Position p, Size s, float[] radii, Background.Layer layer, boolean layered) {
        if (layer == null || layer.gradient == null) return;
        ImageDrawer.GradientTile tile = ImageDrawer.resolveGradientTile(layer, (float) s.width(), (float) s.height());
        Gradient scaled = layer.gradient.scaledTo(tile.width(), tile.height());
        if (hasNoRadius(radii) && tileCoversBox(tile, s)
                && Graph.drawAxisAlignedHardStopGradientRect(
                poseStack.last().pose(), (float) p.x, (float) p.y,
                (float) s.width(), (float) s.height(), scaled)) {
            return;
        }
        if (!tile.repeats()) {
            float x = (float) p.x + tile.x();
            float y = (float) p.y + tile.y();
            if (!Graph.requiresStopGeometry(scaled)) {
                Graph.drawUnifiedRoundedRect(poseStack.last().pose(), x, y, tile.width(), tile.height(), radii, scaled);
                return;
            }
            // Complex stop geometry is emitted as clipped triangles.  Reuse the
            // normal background mask so hard stops remain correct at rounded corners.
            Graph.endBatch();
            Mask.pushMask(poseStack, x, y, tile.width(), tile.height(), radii);
            if (layered) Graph.beginLayeredBatch();
            else Graph.beginBatch();
            Graph.drawGradientRect(poseStack.last().pose(), x, y, tile.width(), tile.height(), scaled);
            Graph.endBatch();
            Mask.popMask(poseStack, x, y, tile.width(), tile.height(), radii);
            if (layered) Graph.beginLayeredBatch();
            else Graph.beginBatch();
            return;
        }

        Graph.endBatch();
        Mask.pushMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii);
        if (layered) Graph.beginLayeredBatch();
        else Graph.beginBatch();
        for (float ix = tile.startX(); ix < tile.endX(); ix += tile.width()) {
            for (float iy = tile.startY(); iy < tile.endY(); iy += tile.height()) {
                boolean drawn = Graph.drawAxisAlignedHardStopGradientRect(poseStack.last().pose(), (float) p.x + ix, (float) p.y + iy,
                        tile.width(), tile.height(), scaled);
                if (!drawn) {
                    drawn = Graph.drawAxisAlignedStopGradientRect(poseStack.last().pose(), (float) p.x + ix, (float) p.y + iy,
                            tile.width(), tile.height(), scaled);
                }
                if (!drawn) {
                    Graph.drawGradientRect(poseStack.last().pose(), (float) p.x + ix, (float) p.y + iy,
                            tile.width(), tile.height(), scaled);
                }
            }
        }
        Graph.endBatch();
        Mask.popMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii);
        if (layered) Graph.beginLayeredBatch();
        else Graph.beginBatch();
    }

    public Position getShadowPosition() {
        if (shadowPosition != null) return shadowPosition;
        double x = position.x + box.getMarginLeft() + box.shadow.x();
        double y = position.y + box.getMarginTop() + box.shadow.y();
        shadowPosition = new Position(x, y);
        return shadowPosition;
    }

    public Size getShadowSize() {
        if (shadowSize != null) return shadowSize;
        double width = elementSize.width();
        double height = elementSize.height();
        shadowSize = new Size(width, height);
        return shadowSize;
    }

    public void drawShadow(PoseStack poseStack) {
        if (box.shadows.isEmpty()) return;
        Size s = getShadowSize();
        // 逐帧路径用索引循环而非 stream()：流管道对象在 JFR 采样里是明显的分配源。
        int outerShadowCount = 0;
        for (int i = 0; i < box.shadows.size(); i++) {
            if (!box.shadows.get(i).inset()) outerShadowCount++;
        }
        if (outerShadowCount == 0) return;
        boolean layered = outerShadowCount > 1;
        if (layered) Graph.beginLayeredBatch();
        else Graph.beginBatch();
        double sourceX = position.x + box.getMarginLeft();
        double sourceY = position.y + box.getMarginTop();
        // CSS paints the first shadow on top, so layers are submitted back-to-front.
        for (int i = box.shadows.size() - 1; i >= 0; i--) {
            Box.Shadow shadow = box.shadows.get(i);
            if (shadow.inset()) continue;
            if ((shadow.color().getValue() >>> 24) == 0) continue;
            double spread = shadow.spread();
            double x = sourceX + shadow.x() - spread;
            double y = sourceY + shadow.y() - spread;
            double width = Math.max(0, s.width() + spread * 2);
            double height = Math.max(0, s.height() + spread * 2);
            if (width <= 0 || height <= 0) continue;
            if (shadow.size() <= 0) {
                drawZeroBlurOuterShadow(
                        poseStack,
                        (float) sourceX,
                        (float) sourceY,
                        (float) s.width(),
                        (float) s.height(),
                        (float) x,
                        (float) y,
                        (float) width,
                        (float) height,
                        shadow.color().getValue()
                );
            } else {
                float[] shadowRadii = box.getCalculatedRadii((float) width, (float) height, (float) -spread);
                Graph.drawUnifiedShadow(poseStack.last().pose(), (float) x, (float) y, (float) width, (float) height, shadowRadii, (float) shadow.size(), shadow.color().getValue(), 0x00000000);
            }
        }
        if (layered) Graph.endBatch();
    }

    private void drawInsetShadow(PoseStack poseStack) {
        boolean hasInset = false;
        for (int i = 0; i < box.shadows.size(); i++) {
            if (box.shadows.get(i).inset()) {
                hasInset = true;
                break;
            }
        }
        if (!hasInset) return;
        Position p = getBodyRectPosition();
        Size s = getBodyRectSize();
        float width = (float) Math.max(0, s.width());
        float height = (float) Math.max(0, s.height());
        if (width <= 0 || height <= 0) return;

        float[] radii = getBodyRadius();
        boolean rounded = false;
        for (float radius : radii) {
            if (Math.abs(radius) > 0.001f) {
                rounded = true;
                break;
            }
        }

        Graph.endBatch();
        // Every strip emitted by drawInsetShadowLayer is already bounded by the
        // rectangular padding box. Avoid a scissor mask here: scissors live in
        // framebuffer coordinates and cannot follow the current PoseStack
        // transform. Rounded boxes still need the transform-aware stencil mask.
        if (rounded) Mask.pushMask(poseStack, (float) p.x, (float) p.y, width, height, radii, true);
        Graph.beginLayeredBatch();
        for (int i = box.shadows.size() - 1; i >= 0; i--) {
            Box.Shadow shadow = box.shadows.get(i);
            if (!shadow.inset() || (shadow.color().getValue() >>> 24) == 0) continue;
            drawInsetShadowLayer(poseStack, p, width, height, shadow);
        }
        Graph.endBatch();
        if (rounded) Mask.popMask(poseStack, (float) p.x, (float) p.y, width, height, radii);
        Graph.beginBatch();
    }

    private void drawInsetShadowLayer(PoseStack poseStack, Position p, float width, float height,
                                      Box.Shadow shadow) {
        double blurExtent = Math.max(0, shadow.size()) * 0.5;
        double spread = shadow.spread() + blurExtent;
        float top = (float) Math.min(height, Math.max(0, spread + shadow.y()));
        float bottom = (float) Math.min(height - top, Math.max(0, spread - shadow.y()));
        float left = (float) Math.min(width, Math.max(0, spread + shadow.x()));
        float right = (float) Math.min(width - left, Math.max(0, spread - shadow.x()));
        int color = shadow.color().getValue();
        float x0 = (float) p.x;
        float y0 = (float) p.y;
        float x1 = x0 + width;
        float y1 = y0 + height;

        if (top > 0) Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y0 + top, color);
        if (bottom > 0) Graph.drawFillRect(poseStack.last().pose(), x0, y1 - bottom, x1, y1, color);
        float middleTop = y0 + top;
        float middleBottom = y1 - bottom;
        if (middleBottom <= middleTop) return;
        if (left > 0) Graph.drawFillRect(poseStack.last().pose(), x0, middleTop, x0 + left, middleBottom, color);
        if (right > 0) Graph.drawFillRect(poseStack.last().pose(), x1 - right, middleTop, x1, middleBottom, color);
    }

    private void drawZeroBlurOuterShadow(PoseStack poseStack,
                                         float sourceX, float sourceY, float sourceWidth, float sourceHeight,
                                         float shadowX, float shadowY, float shadowWidth, float shadowHeight,
                                         int color) {
        float shadowRight = shadowX + shadowWidth;
        float shadowBottom = shadowY + shadowHeight;
        float sourceRight = sourceX + sourceWidth;
        float sourceBottom = sourceY + sourceHeight;

        float ix0 = Math.max(shadowX, sourceX);
        float iy0 = Math.max(shadowY, sourceY);
        float ix1 = Math.min(shadowRight, sourceRight);
        float iy1 = Math.min(shadowBottom, sourceBottom);

        if (ix0 >= ix1 || iy0 >= iy1) {
            Graph.drawFillRect(poseStack.last().pose(), shadowX, shadowY, shadowRight, shadowBottom, color);
            return;
        }

        if (shadowY < iy0) {
            Graph.drawFillRect(poseStack.last().pose(), shadowX, shadowY, shadowRight, iy0, color);
        }
        if (iy1 < shadowBottom) {
            Graph.drawFillRect(poseStack.last().pose(), shadowX, iy1, shadowRight, shadowBottom, color);
        }
        if (shadowX < ix0) {
            Graph.drawFillRect(poseStack.last().pose(), shadowX, iy0, ix0, iy1, color);
        }
        if (ix1 < shadowRight) {
            Graph.drawFillRect(poseStack.last().pose(), ix1, iy0, shadowRight, iy1, color);
        }
    }

    public Position getContentPosition() {
        if (contentPosition != null) return contentPosition;
        double x = position.x + box.getMarginLeft() + box.getBorderLeft() + box.getPaddingLeft();
        double y = position.y + box.getMarginTop() + box.getBorderTop() + box.getPaddingTop();
        contentPosition = new Position(x, y);
        return contentPosition;
    }
}
