package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.style.*;

public class Rect {
    public Position position;
    public Box box;
    public String documentPath;
    public Background background;

    public Rect(Element element) {
        position = Position.of(element);
        box = Box.of(element);
        background = Background.of(element);
        documentPath = element.document.getPath();
    }

    public static Rect of(Element element) {
        Rect cached = RectFrameCache.get(element);
        if (cached != null) return cached;
        Rect result = new Rect(element);
        RectFrameCache.put(element, result);
        return result;
//        Rect cache = Cache.rect.get(element);
//        if (cache != null) return cache;
//        else {
//            Rect result = new Rect(element);
//            Cache.rect.set(element, result);
//            return result;
//        }
    }

    public AABB getVisualBounds() {
        double x = position.x + box.getMarginLeft();
        double y = position.y + box.getMarginTop();
        double w = box.elementSize().width();
        double h = box.elementSize().height();

        double minExtendX = 0;
        double minExtendY = 0;
        double maxExtendX = 0;
        double maxExtendY = 0;
        for (Box.Shadow shadow : box.shadows) {
            if (shadow.size() <= 0) continue;
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

        return new AABB((float) x, (float) y, (float) w, (float) h);
    }

    private int getMinBorderSize() {
        return Math.min(Math.min(box.border.get("left").size(), box.border.get("top").size()), Math.min(box.border.get("right").size(), box.border.get("bottom").size()));
    }

    public void drawBorder(PoseStack poseStack) {
        int topW = box.border.get("top").size();
        int bottomW = box.border.get("bottom").size();
        int leftW = box.border.get("left").size();
        int rightW = box.border.get("right").size();

        if (topW == 0 && bottomW == 0 && leftW == 0 && rightW == 0) return;

        Graph.beginBatch();
        int topC = box.border.get("top").color().getValue();
        int bottomC = box.border.get("bottom").color().getValue();
        int leftC = box.border.get("left").color().getValue();
        int rightC = box.border.get("right").color().getValue();

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
        Graph.endBatch();
        ImageDrawer.flushBatch();
    }

    public Position getBodyRectPosition() {
        double x = position.x + box.getMarginLeft() + box.getBorderLeft();
        double y = position.y + box.getMarginTop() + box.getBorderTop();
        return new Position(x, y);
    }

    public Size getBodyRectSize() {
        double width = box.elementSize().width() - box.getBorderHorizontal();
        double height = box.elementSize().height() - box.getBorderVertical();
        return new Size(width, height);
    }

    public float[] getBodyRadius() {
        Size s = box.elementSize();
        return box.getCalculatedRadii((float) s.width(), (float) s.height(), getMinBorderSize());
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
                    Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                            (float) p.x, (float) p.y, (float) s.width(), (float) s.height(),
                            radii, layer.gradient);
                }
                if (!"unset".equals(layer.imagePath)) {
                    Graph.endBatch();
                    ImageDrawer.drawComplexBackground(poseStack, (int) p.x, (int) p.y, (int) s.width(), (int) s.height(), layer);
                    Graph.beginBatch();
                }
            }
            Graph.endBatch();
            ImageDrawer.flushBatch();
            return;
        }

        // 兼容旧单层字段
        if (background.gradient != null) {
            Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                    (float) p.x, (float) p.y, (float) s.width(), (float) s.height(),
                    radii, background.gradient);
        }
        if (!background.imagePath.equals("unset")) {
            Graph.endBatch();
            ImageDrawer.drawComplexBackground(poseStack, (int) p.x, (int) p.y, (int) s.width(), (int) s.height(), background);
            return;
        }
        Graph.endBatch();
        ImageDrawer.flushBatch();
    }

    public Position getShadowPosition() {
        double x = position.x + box.getMarginLeft() + box.shadow.x();
        double y = position.y + box.getMarginTop() + box.shadow.y();
        return new Position(x, y);
    }

    public Size getShadowSize() {
        double width = box.elementSize().width();
        double height = box.elementSize().height();
        return new Size(width, height);
    }

    public void drawShadow(PoseStack poseStack) {
        if (box.shadows.isEmpty()) return;
        Size s = getShadowSize();
        float[] radii = box.getCalculatedRadii((float) s.width(), (float) s.height(), 0);
        Graph.beginBatch();
        for (Box.Shadow shadow : box.shadows) {
            if (shadow.size() == 0) continue;
            double x = position.x + box.getMarginLeft() + shadow.x();
            double y = position.y + box.getMarginTop() + shadow.y();
            Graph.drawUnifiedShadow(poseStack.last().pose(), (float) x, (float) y, (float) s.width(), (float) s.height(), radii, shadow.size(), shadow.color().getValue(), Color.parse("#00000000"));
        }
        Graph.endBatch();
    }

    public Position getContentPosition() {
        double x = position.x + box.getMarginLeft() + box.getBorderLeft() + box.getPaddingLeft();
        double y = position.y + box.getMarginTop() + box.getBorderTop() + box.getPaddingTop();
        return new Position(x, y);
    }
}
