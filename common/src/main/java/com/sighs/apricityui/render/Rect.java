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
    private AABB transformedBounds;
    private boolean transformedBoundsComputed;
    /** {@link #transformedBounds} 对应的 transform 依赖；祖先 transform 一变这个缓存就失效。 */
    private long transformedBoundsDependency = Long.MIN_VALUE;
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
        transformedBounds = null;
        transformedBoundsComputed = false;
        transformedBoundsDependency = Long.MIN_VALUE;
        bodyRectPosition = null;
        shadowPosition = null;
        contentPosition = null;
    }

    public AABB getVisualBounds() {
        if (visualBounds != null) return visualBounds;
        double[] box = visualBox();
        visualBounds = new AABB((float) box[0], (float) box[1], (float) box[2], (float) box[3]);
        return visualBounds;
    }

    /**
     * 应用祖先 transform 之后的视觉包围盒；链上没有 transform 时返回 {@code null}。
     *
     * <p>和 {@link #getVisualBounds()}（文档坐标系、不含 transform）配套：裁剪剔除必须两个
     * 坐标系的矩形都落在框外才敢剔。只看文档坐标系会漏画——被 transform 缩放/平移进裁剪框的
     * 内容（rewind_screen 流程图画布最深处那张卡片，变换前 y≈857、画布只到 761）会被整块剔掉；
     * 只看变换后坐标系会误剔——被 transform 移出裁剪框、但仍应由 scissor 裁掉的内容
     * （.progress-2-bar 的 translateX(150%)）会不再提交给绘制。</p>
     */
    public AABB getTransformedBounds() {
        // 缓存必须挂在 transform 依赖上：Rect 可能是跨帧存活的 committed 实例，
        // 祖先 transform 一变（例如拖动画布）它上面的旧结果就成了过期几何，
        // 拿它做剔除会把还在画面里的元素整片剔掉。
        long dependency = element == null || element.document == null
                ? Long.MIN_VALUE
                : element.getRenderer().transformDependency(element.document);
        if (transformedBoundsComputed && transformedBoundsDependency == dependency) return transformedBounds;
        transformedBoundsComputed = true;
        transformedBoundsDependency = dependency;
        double[] box = visualBox();
        double[] transformed = Base.visualBounds(element, box[0], box[1], box[2], box[3]);
        transformedBounds = transformed == null ? null : new AABB(
                (float) transformed[0], (float) transformed[1],
                (float) transformed[2], (float) transformed[3]);
        return transformedBounds;
    }

    /**
     * 把一块"元素局部坐标系里的矩形"按该元素的 transform 链映射成包围盒；
     * 链上没有 transform 时返回 {@code null}（调用方沿用原矩形）。
     *
     * <p>命中盒、滚动条条带、裁剪框都是按元素局部坐标算出来的，比较对象却是文档坐标的光标，
     * 所以要先映射。</p>
     */
    public AABB transformLocalRect(double x, double y, double width, double height) {
        double[] transformed = Base.visualBounds(element, x, y, width, height);
        return transformed == null ? null : new AABB(
                (float) transformed[0], (float) transformed[1],
                (float) transformed[2], (float) transformed[3]);
    }

    /** 元素矩形（含阴影外扩），文档坐标系，不含 transform。 */
    private double[] visualBox() {
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
        // outline 画在边框盒之外，视觉包围盒必须跟着外扩：贴边的元素若只按盒判剔除，
        // 盒刚好在裁剪框外时整条焦点框会被剔掉。负 offset 落在盒内，不外扩。
        double outlineReach = box.outline.isVisible() ? box.outline.offset() + box.outline.width() : 0;
        if (outlineReach > 0) {
            maxExtendX = Math.max(maxExtendX, outlineReach);
            maxExtendY = Math.max(maxExtendY, outlineReach);
            minExtendX = Math.min(minExtendX, -outlineReach);
            minExtendY = Math.min(minExtendY, -outlineReach);
        }
        if (minExtendX != 0 || minExtendY != 0 || maxExtendX != 0 || maxExtendY != 0) {
            x += minExtendX;
            y += minExtendY;
            w += maxExtendX - minExtendX;
            h += maxExtendY - minExtendY;
        }
        return new double[]{x, y, w, h};
    }

    private double getMinBorderSize() {
        return Math.min(Math.min(box.getBorderLeft(), box.getBorderTop()), Math.min(box.getBorderRight(), box.getBorderBottom()));
    }

    /**
     * CSS outline：内边缘 = 边框盒 + outline-offset，带宽 = outline-width，不占布局、不进盒模型。
     *
     * <p>复用边框那套圆角带绘制：把矩形外扩 (offset + width)、按同带宽画一圈，画出的带正好落在
     * [边框盒+offset, 边框盒+offset+width]。offset 为负时外扩量为负，带随之落到盒内侧；圆角半径
     * 同样按外扩量放大（外边缘半径 = border-radius + offset + width），三者都与浏览器一致。</p>
     */
    public void drawOutline(PoseStack poseStack) {
        if (!box.outline.isVisible()) return;

        float expand = (float) (box.outline.offset() + box.outline.width());
        float outerWidth = (float) elementSize.width() + expand * 2;
        float outerHeight = (float) elementSize.height() + expand * 2;
        if (outerWidth <= 0 || outerHeight <= 0) return;

        float width = (float) box.outline.width();
        float[] radii = box.getCalculatedRadii(outerWidth, outerHeight, -expand);
        int color = box.outline.color();

        Graph.beginBatch();
        Graph.drawComplexRoundedBorder(
                PoseMatrices.of(poseStack),
                (float) (position.x + box.getMarginLeft()) - expand,
                (float) (position.y + box.getMarginTop()) - expand,
                outerWidth,
                outerHeight,
                radii,
                new float[]{width, width, width, width},
                new int[]{color, color, color, color});
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
        double w = elementSize.width();
        double h = elementSize.height();

        float[] radii = box.getCalculatedRadii((float) w, (float) h, 0);
        float[] borders = new float[]{topW, rightW, bottomW, leftW};
        int[] colors = new int[]{topC, rightC, bottomC, leftC};
        Box.SideBorder[] sides = {topBorder, rightBorder, bottomBorder, leftBorder};
        boolean patterned = false;
        for (Box.SideBorder side : sides) patterned |= "dashed".equals(side.type()) || "double".equals(side.type());
        if (patterned) {
            drawPatternedBorder(poseStack, (float) x, (float) y, (float) w, (float) h, radii, borders, colors, sides);
        } else {
            Graph.drawComplexRoundedBorder(PoseMatrices.of(poseStack), (float) x, (float) y, (float) w, (float) h, radii, borders, colors);
        }

        if (box.borderImage != null && WorldWindowRenderContext.shouldRenderBackgroundDetails()) {
            if (box.borderImage.gradient != null) {
                Graph.drawUnifiedRoundedRect(PoseMatrices.of(poseStack),
                        (float) x, (float) y, (float) w, (float) h,
                        radii, box.borderImage.gradient);
            }
            Position p = position.add(new Position(box.getMarginLeft(), box.getMarginTop()));
            Size s = getShadowSize();
            String path = Loader.resolve(documentPath, box.borderImage.source);
            Base.commitDraws();
            ImageDrawer.drawNineSlice(poseStack, path, (int) p.x, (int) p.y, (int) s.width(), (int) s.height(), box.borderImage);
            return;
        }
    }

    /** 按边的中线弧长分段；不以背景色覆盖空隙，透明背景也能透出下层。 */
    private static void drawPatternedBorder(PoseStack pose, float x, float y, float w, float h,
                                         float[] radii, float[] widths, int[] colors, Box.SideBorder[] sides) {
        var matrix = PoseMatrices.of(pose);
        for (int side = 0; side < 4; side++) {
            float thickness = widths[side];
            if (thickness <= 0 || (colors[side] >>> 24) == 0) continue;
            float length = side % 2 == 0 ? w : h;
            float before = widths[(side + 3) % 4], after = widths[(side + 1) % 4];
            // 将每条边旋转到局部 top，路径从前一圆角中点到后一圆角中点。
            int first = side, last = (side + 1) % 4;
            float rx0 = radii[first * 2 + side % 2], ry0 = radii[first * 2 + 1 - side % 2];
            float rx1 = radii[last * 2 + side % 2], ry1 = radii[last * 2 + 1 - side % 2];
            java.util.List<float[]> path = new java.util.ArrayList<>();
            borderHalfCorner(path, 0, rx0, ry0, before, thickness, false);
            borderHalfCorner(path, length, rx1, ry1, after, thickness, true);
            float total = 0;
            float[] distances = new float[path.size()];
            for (int i = 1; i < path.size(); i++) {
                float[] a = path.get(i - 1), b = path.get(i);
                total += (float) Math.hypot((b[0] + b[2] - a[0] - a[2]) / 2,
                        (b[1] + b[3] - a[1] - a[3]) / 2);
                distances[i] = total;
            }
            if (total <= 0) continue;
            boolean dashed = "dashed".equals(sides[side].type());
            int count = Math.max(1, Math.round(total / (5 * thickness)));
            float period = total / count;
            // dash:gap = 3:2，首尾半段使相邻边在角点衔接。
            for (int i = 1; i < path.size(); i++) {
                float from = distances[i - 1], to = distances[i];
                if (to - from < 0.0001f) continue;
                int firstDash = dashed ? Math.max(0, (int) Math.floor(from / period)) : 0;
                int lastDash = dashed ? Math.min(count, (int) Math.ceil(to / period)) : 0;
                for (int dash = firstDash; dash <= lastDash; dash++) {
                    float start = dashed ? Math.max(from, (dash - 0.3f) * period) : from;
                    float end = dashed ? Math.min(to, (dash + 0.3f) * period) : to;
                    if (end <= start) continue;
                    float[] a = borderInterpolate(path.get(i - 1), path.get(i), (start - from) / (to - from));
                    float[] b = borderInterpolate(path.get(i - 1), path.get(i), (end - from) / (to - from));
                    boolean doubleLine = "double".equals(sides[side].type()) && thickness >= 3;
                    float stripe = Math.round(thickness / 3) / thickness;
                    for (int band = 0; band < (doubleLine ? 2 : 1); band++) {
                        float outer = !doubleLine || band == 0 ? 0 : 1 - stripe;
                        float inner = !doubleLine || band == 1 ? 1 : stripe;
                        float ax = a[0] + (a[2] - a[0]) * outer, ay = a[1] + (a[3] - a[1]) * outer;
                        float bx = b[0] + (b[2] - b[0]) * outer, by = b[1] + (b[3] - b[1]) * outer;
                        float cx = a[0] + (a[2] - a[0]) * inner, cy = a[1] + (a[3] - a[1]) * inner;
                        float dx = b[0] + (b[2] - b[0]) * inner, dy = b[1] + (b[3] - b[1]) * inner;
                        float[][] points = {{ax, ay}, {cx, cy}, {dx, dy}, {ax, ay}, {dx, dy}, {bx, by}};
                        for (float[] p : points) {
                            float px = switch (side) { case 0 -> p[0]; case 1 -> w - p[1]; case 2 -> w - p[0]; default -> p[1]; };
                            float py = switch (side) { case 0 -> p[1]; case 1 -> p[0]; case 2 -> h - p[1]; default -> h - p[0]; };
                            Graph.vtx(Base.getMesh(), matrix, x + px, y + py, colors[side]);
                        }
                    }
                }
            }
        }
    }

    private static float[] borderInterpolate(float[] a, float[] b, float t) {
        float[] result = new float[4];
        for (int i = 0; i < 4; i++) result[i] = a[i] + (b[i] - a[i]) * t;
        return result;
    }

    private static void borderHalfCorner(java.util.List<float[]> path, float edge, float rx, float ry,
                                         float adjacent, float thickness, boolean end) {
        if (rx <= 0 || ry <= 0) {
            float inner = end ? edge - adjacent : adjacent;
            float[] corner = {edge, 0, inner, thickness};
            float[] tangent = {inner, 0, inner, thickness};
            path.add(end ? tangent : corner);
            path.add(end ? corner : tangent);
            return;
        }
        float cx = end ? edge - rx : rx;
        float innerRx = Math.max(0, rx - adjacent), innerRy = Math.max(0, ry - thickness);
        float innerCx = end ? edge - adjacent - innerRx : adjacent + innerRx;
        float innerCy = thickness + innerRy;
        for (int step = 0; step <= 6; step++) {
            double angle = Math.toRadians(end ? -90 + step * 7.5 : -135 + step * 7.5);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            path.add(new float[]{cx + rx * cos, ry + ry * sin,
                    innerCx + innerRx * cos, innerCy + innerRy * sin});
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
            Graph.drawUnifiedRoundedRect(PoseMatrices.of(poseStack), (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii, new Color(background.color).getValue());
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
        if (!tile.repeats()) {
            float x = (float) p.x + tile.x();
            float y = (float) p.y + tile.y();
            Gradient scaled = layer.gradient.scaledTo(tile.width(), tile.height());
            boolean fillsClip = tile.x() == 0 && tile.y() == 0
                    && tile.width() == (float) s.width() && tile.height() == (float) s.height();
            if (fillsClip && !Graph.requiresStopGeometry(scaled)) {
                Graph.drawUnifiedRoundedRect(PoseMatrices.of(poseStack), x, y, tile.width(), tile.height(), radii, scaled);
                return;
            }

            // CSS rounds the background painting area, not each positioned image.
            // Keep the tile rectangular and clip it against the element's corners.
            Graph.endBatch();
            Mask.pushMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii);
            if (layered) Graph.beginLayeredBatch();
            else Graph.beginBatch();
            Graph.drawGradientRect(PoseMatrices.of(poseStack), x, y, tile.width(), tile.height(), scaled);
            Graph.endBatch();
            Mask.popMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii);
            if (layered) Graph.beginLayeredBatch();
            else Graph.beginBatch();
            return;
        }

        Graph.endBatch();
        Mask.pushMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), radii);
        if (layered) Graph.beginLayeredBatch();
        else Graph.beginBatch();
        Gradient scaled = layer.gradient.scaledTo(tile.width(), tile.height());
        for (float ix = tile.startX(); ix < tile.endX(); ix += tile.width()) {
            for (float iy = tile.startY(); iy < tile.endY(); iy += tile.height()) {
                boolean drawn = Graph.drawAxisAlignedHardStopGradientRect(PoseMatrices.of(poseStack), (float) p.x + ix, (float) p.y + iy,
                        tile.width(), tile.height(), scaled);
                if (!drawn) {
                    drawn = Graph.drawAxisAlignedStopGradientRect(PoseMatrices.of(poseStack), (float) p.x + ix, (float) p.y + iy,
                            tile.width(), tile.height(), scaled);
                }
                if (!drawn) {
                    Graph.drawGradientRect(PoseMatrices.of(poseStack), (float) p.x + ix, (float) p.y + iy,
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
                // 外阴影只画在 border box 之外（CSS 规定，与背景是否透明无关），
                // 偏移与扩散改变的是高斯衰减的参考位置，不再有"阴影形状内部"的填充。
                float[] shadowRadii = box.getCalculatedRadii((float) s.width(), (float) s.height(), 0);
                Graph.drawUnifiedShadow(PoseMatrices.of(poseStack), (float) sourceX, (float) sourceY,
                        (float) s.width(), (float) s.height(), shadowRadii,
                        (float) shadow.x(), (float) shadow.y(), (float) spread, (float) shadow.size(),
                        shadow.color().getValue(), 0x00000000);
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

        Graph.endBatch();
        Mask.pushMask(poseStack, (float) p.x, (float) p.y, width, height, getBodyRadius());
        Graph.beginLayeredBatch();
        for (int i = box.shadows.size() - 1; i >= 0; i--) {
            Box.Shadow shadow = box.shadows.get(i);
            if (!shadow.inset() || (shadow.color().getValue() >>> 24) == 0) continue;
            drawInsetShadowLayer(poseStack, p, width, height, shadow);
        }
        Graph.endBatch();
        Mask.popMask(poseStack, (float) p.x, (float) p.y, width, height, getBodyRadius());
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

        if (top > 0) Graph.drawFillRect(PoseMatrices.of(poseStack), x0, y0, x1, y0 + top, color);
        if (bottom > 0) Graph.drawFillRect(PoseMatrices.of(poseStack), x0, y1 - bottom, x1, y1, color);
        float middleTop = y0 + top;
        float middleBottom = y1 - bottom;
        if (middleBottom <= middleTop) return;
        if (left > 0) Graph.drawFillRect(PoseMatrices.of(poseStack), x0, middleTop, x0 + left, middleBottom, color);
        if (right > 0) Graph.drawFillRect(PoseMatrices.of(poseStack), x1 - right, middleTop, x1, middleBottom, color);
    }

    private void drawZeroBlurOuterShadow(PoseStack poseStack,
                                         float sourceX, float sourceY, float sourceWidth, float sourceHeight,
                                         float shadowX, float shadowY, float shadowWidth, float shadowHeight,
                                         int color) {
        float spreadX = (shadowWidth - sourceWidth) / 2;
        float spreadY = (shadowHeight - sourceHeight) / 2;
        float[] radii = box.getCalculatedRadii(sourceWidth, sourceHeight, 0);
        boolean rounded = false;
        for (float radius : radii) rounded |= radius > 0;
        if (rounded && spreadX > 0 && Math.abs(spreadX - spreadY) < 0.0001f
                && Math.abs(shadowX - (sourceX - spreadX)) < 0.0001f
                && Math.abs(shadowY - (sourceY - spreadY)) < 0.0001f) {
            for (int i = 0; i < radii.length; i++) {
                if (radii[i] > 0) radii[i] += spreadX;
            }
            Graph.drawComplexRoundedBorder(PoseMatrices.of(poseStack), shadowX, shadowY, shadowWidth, shadowHeight,
                    radii, new float[]{spreadX, spreadX, spreadX, spreadX}, new int[]{color, color, color, color});
            return;
        }
        float shadowRight = shadowX + shadowWidth;
        float shadowBottom = shadowY + shadowHeight;
        float sourceRight = sourceX + sourceWidth;
        float sourceBottom = sourceY + sourceHeight;

        float ix0 = Math.max(shadowX, sourceX);
        float iy0 = Math.max(shadowY, sourceY);
        float ix1 = Math.min(shadowRight, sourceRight);
        float iy1 = Math.min(shadowBottom, sourceBottom);

        if (ix0 >= ix1 || iy0 >= iy1) {
            Graph.drawFillRect(PoseMatrices.of(poseStack), shadowX, shadowY, shadowRight, shadowBottom, color);
            return;
        }

        if (shadowY < iy0) {
            Graph.drawFillRect(PoseMatrices.of(poseStack), shadowX, shadowY, shadowRight, iy0, color);
        }
        if (iy1 < shadowBottom) {
            Graph.drawFillRect(PoseMatrices.of(poseStack), shadowX, iy1, shadowRight, shadowBottom, color);
        }
        if (shadowX < ix0) {
            Graph.drawFillRect(PoseMatrices.of(poseStack), shadowX, iy0, ix0, iy1, color);
        }
        if (ix1 < shadowRight) {
            Graph.drawFillRect(PoseMatrices.of(poseStack), ix1, iy0, shadowRight, iy1, color);
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
