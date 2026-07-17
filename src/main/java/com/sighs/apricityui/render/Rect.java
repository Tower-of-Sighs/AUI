package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.style.*;

public class Rect {
    public Element element;
    public Position position;
    public Box box;
    public String documentPath;
    public Background background;
    private AABB visualBounds;
    private Position bodyRectPosition;
    private Size bodyRectSize;
    private float[] bodyRadius;
    private Position shadowPosition;
    private Size shadowSize;
    private Position contentPosition;

    public Rect(Element element) {
        this.element = element;
        position = Position.of(element);
        box = Box.of(element);
        background = Background.of(element);
        documentPath = element.document.getPath();
    }

    public static Rect of(Element element) {
        Rect cached = RectFrameCache.get(element);
        if (cached != null) return cached;
        Rect result = createAndCache(element);
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

    public AABB getVisualBounds() {
        if (visualBounds != null) return visualBounds;
        double x = position.x + box.getMarginLeft();
        double y = position.y + box.getMarginTop();
        double w = box.elementSize().width();
        double h = box.elementSize().height();

        double minExtendX = 0;
        double minExtendY = 0;
        double maxExtendX = 0;
        double maxExtendY = 0;
        for (Box.Shadow shadow : box.shadows) {
            if ((shadow.color().getValue() >>> 24) == 0) continue;
            minExtendX = Math.min(minExtendX, shadow.x() - shadow.size());
            minExtendY = Math.min(minExtendY, shadow.y() - shadow.size());
            maxExtendX = Math.max(maxExtendX, shadow.x() + shadow.size());
            maxExtendY = Math.max(maxExtendY, shadow.y() + shadow.size());
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

        if (topW <= 0 && bottomW <= 0 && leftW <= 0 && rightW <= 0) return;

        Graph.beginBatch();
        int topC = topBorder.color().getValue();
        int bottomC = bottomBorder.color().getValue();
        int leftC = leftBorder.color().getValue();
        int rightC = rightBorder.color().getValue();

        double x = position.x + box.getMarginLeft();
        double y = position.y + box.getMarginTop();
        double w = box.elementSize().width();
        double h = box.elementSize().height();

        float[] radii = box.getCalculatedRadii((float) w, (float) h, 0);
        float[] borders = new float[]{topW, rightW, bottomW, leftW};
        int[] colors = new int[]{topC, rightC, bottomC, leftC};
        Graph.drawComplexRoundedBorder(poseStack.last().pose(), (float) x, (float) y, (float) w, (float) h, radii, borders, colors);

        if (box.borderImage != null) {
            if (box.borderImage.gradient != null) {
                Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                        (float) x, (float) y, (float) w, (float) h,
                        radii, box.borderImage.gradient);
            }
            Position p = position.add(new Position(box.getMarginLeft(), box.getMarginTop()));
            Size s = getShadowSize();
            String path = Loader.resolve(documentPath, box.borderImage.source);
            Graph.endBatch();
            ImageDrawer.flushBatch();
            ImageDrawer.drawNineSlice(poseStack, path, (int) p.x, (int) p.y, (int) s.width(), (int) s.height(), box.borderImage);
            return;
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
        double width = box.elementSize().width() - box.getBorderHorizontal();
        double height = box.elementSize().height() - box.getBorderVertical();
        bodyRectSize = new Size(width, height);
        return bodyRectSize;
    }

    public float[] getBodyRadius() {
        if (bodyRadius != null) return bodyRadius;
        Size s = getBodyRectSize();
        bodyRadius = box.getCalculatedRadii((float) s.width(), (float) s.height(), (float) getMinBorderSize());
        return bodyRadius;
    }

    public void drawBody(PoseStack poseStack) {
        drawBody(poseStack, getBodyRectSize());
    }

    public void drawBody(PoseStack poseStack, Size s) {
        Position p = getBodyRectPosition();
        float[] radii = getBodyRadius();
        Graph.beginBatch();
        if (!background.color.equals("unset")) {
            Graph.drawUnifiedRoundedRect(poseStack.last().pose(), (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii, new Color(background.color).getValue());
        }
        if (!background.getLayers().isEmpty()) {
            // CSS: background-image 第一层在最上方，因此按逆序绘制
            for (int i = background.getLayers().size() - 1; i >= 0; i--) {
                Background.Layer layer = background.getLayers().get(i);
                if (layer == null) continue;
                if (layer.gradient != null) {
                    drawGradientLayer(poseStack, p, s, radii, layer);
                }
                if (!"unset".equals(layer.imagePath)) {
                    Graph.endBatch();
                    ImageDrawer.drawComplexBackground(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), layer, element);
                    Graph.beginBatch();
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
            drawGradientLayer(poseStack, p, s, radii, legacyLayer);
        }
        if (!background.imagePath.equals("unset")) {
            Graph.endBatch();
            ImageDrawer.drawComplexBackground(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), background, element);
            return;
        }
    }

    private void drawGradientLayer(PoseStack poseStack, Position p, Size s, float[] radii, Background.Layer layer) {
        if (layer == null || layer.gradient == null) return;
        ImageDrawer.GradientTile tile = ImageDrawer.resolveGradientTile(layer, (float) s.width(), (float) s.height());
        if (!tile.repeats()) {
            Graph.drawUnifiedRoundedRect(poseStack.last().pose(), (float) p.x + tile.x(), (float) p.y + tile.y(),
                    tile.width(), tile.height(), radii, layer.gradient.scaledTo(tile.width(), tile.height()));
            return;
        }

        Graph.endBatch();
        Mask.pushMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii);
        Graph.beginBatch();
        Gradient scaled = layer.gradient.scaledTo(tile.width(), tile.height());
        for (float ix = tile.startX(); ix < tile.endX(); ix += tile.width()) {
            for (float iy = tile.startY(); iy < tile.endY(); iy += tile.height()) {
                boolean drawn = Graph.drawAxisAlignedStopGradientRect(poseStack.last().pose(), (float) p.x + ix, (float) p.y + iy,
                        tile.width(), tile.height(), scaled);
                if (!drawn) {
                    drawn = Graph.drawAxisAlignedHardStopGradientRect(poseStack.last().pose(), (float) p.x + ix, (float) p.y + iy,
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
        Graph.beginBatch();
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
        double width = box.elementSize().width();
        double height = box.elementSize().height();
        shadowSize = new Size(width, height);
        return shadowSize;
    }

    public void drawShadow(PoseStack poseStack) {
        if (box.shadows.isEmpty()) return;
        Size s = getShadowSize();
        float[] radii = box.getCalculatedRadii((float) s.width(), (float) s.height(), 0);
        Graph.beginBatch();
        double sourceX = position.x + box.getMarginLeft();
        double sourceY = position.y + box.getMarginTop();
        for (Box.Shadow shadow : box.shadows) {
            if ((shadow.color().getValue() >>> 24) == 0) continue;
            double x = sourceX + shadow.x();
            double y = sourceY + shadow.y();
            if (shadow.size() <= 0) {
                drawZeroBlurOuterShadow(
                        poseStack,
                        (float) sourceX,
                        (float) sourceY,
                        (float) x,
                        (float) y,
                        (float) s.width(),
                        (float) s.height(),
                        shadow.color().getValue()
                );
            } else {
                Graph.drawUnifiedShadow(poseStack.last().pose(), (float) x, (float) y, (float) s.width(), (float) s.height(), radii, (float) shadow.size(), shadow.color().getValue(), Color.parse("#00000000"));
            }
        }
    }

    private void drawZeroBlurOuterShadow(PoseStack poseStack, float sourceX, float sourceY, float shadowX, float shadowY, float width, float height, int color) {
        float shadowRight = shadowX + width;
        float shadowBottom = shadowY + height;
        float sourceRight = sourceX + width;
        float sourceBottom = sourceY + height;

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
