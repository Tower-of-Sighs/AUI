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
        return new Rect(element);
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

        if (box.shadow.size() > 0) {
            double shadowSize = box.shadow.size();
            x += box.shadow.x() - shadowSize;
            y += box.shadow.y() - shadowSize;
            w += shadowSize * 2;
            h += shadowSize * 2;
        }

        return new AABB((float)x, (float)y, (float)w, (float)h);
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

        int topC = box.border.get("top").color().getValue();
        int bottomC = box.border.get("bottom").color().getValue();
        int leftC = box.border.get("left").color().getValue();
        int rightC = box.border.get("right").color().getValue();

        double x = position.x + box.getMarginLeft();
        double y = position.y + box.getMarginTop();
        double w = box.elementSize().width();
        double h = box.elementSize().height();

        float[] radii = box.getCalculatedRadii((float) w, (float) h, 0);
        float[] borders = new float[] {topW, rightW, bottomW, leftW};
        int[] colors = new int[] {topC, rightC, bottomC, leftC};
        Graph.drawComplexRoundedBorder(poseStack.last().pose(), (float)x, (float)y, (float)w, (float)h, radii, borders, colors);

        if (box.borderImage != null) {
            if (box.borderImage.gradient != null) {
                Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                        (float)x, (float)y, (float)w, (float)h,
                        radii, box.borderImage.gradient);
            }
            Position p = position.add(new Position(box.getMarginLeft(), box.getMarginTop()));
            Size s = getShadowSize();
            String path = Loader.resolve(documentPath, box.borderImage.source);
            ImageDrawer.drawNineSlice(poseStack, path, (int) p.x, (int) p.y, (int) s.width(), (int) s.height(), box.borderImage);
        }
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
        return box.getCalculatedRadii((float)s.width(), (float)s.height(), getMinBorderSize());
    }
    public void drawBody(PoseStack poseStack) {
        drawBody(poseStack, getBodyRectSize());
    }
    public void drawBody(PoseStack poseStack, Size s) {
        Position p = getBodyRectPosition();
        float[] radii = getBodyRadius();
        if (!background.color.equals("unset")) {
            Graph.drawUnifiedRoundedRect(poseStack.last().pose(), (float)p.x, (float)p.y, (float)s.width(), (float)s.height(), radii, new Color(background.color).getValue());
        }
        if (background.gradient != null) {
            Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                    (float)p.x, (float)p.y, (float)s.width(), (float)s.height(),
                    radii, background.gradient);
        }
        if (!background.imagePath.equals("unset")) {
            ImageDrawer.drawComplexBackground(poseStack, (int)p.x, (int)p.y, (int)s.width(), (int)s.height(), background);
        }
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
        if (box.shadow.size() == 0) return;
        Position p = getShadowPosition();
        Size s = getShadowSize();
        float[] radii = box.getCalculatedRadii((float)s.width(), (float)s.height(), 0);
        Graph.drawUnifiedShadow(poseStack.last().pose(), (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii, box.shadow.size(), box.shadow.color().getValue(), Color.parse("#00000000"));
    }

    public Position getContentPosition() {
        double x = position.x + box.getMarginLeft() + box.getBorderLeft() + box.getPaddingLeft();
        double y = position.y + box.getMarginTop() + box.getBorderTop() + box.getPaddingTop();
        return new Position(x, y);
    }
}
