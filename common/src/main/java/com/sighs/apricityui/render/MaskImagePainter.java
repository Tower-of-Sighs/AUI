package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.MaskImage;
import com.sighs.apricityui.style.Transform;

/**
 * 把单个 mask 层画进当前绑定的离屏 FBO（编排见 {@link FilterRenderer#popMaskImage}）。
 * 每层按自己的 mask-origin 决定定位区、mask-clip 决定裁剪区（no-clip 不裁剪，
 * 其余盒子含 border-radius 圆角裁剪，复用 {@link Mask} 的 stencil/scissor 路径）。
 *
 * <p>加载中或加载失败的图像层直接跳过——不做遮罩占位；一层都没画上时调用方
 * 保持内容可见（fail-open）。偏差：margin-box/fill-box 等不支持的盒子关键字
 * 已在 {@link MaskImage} 解析期归一为 border-box。</p>
 */
public final class MaskImagePainter {
    private MaskImagePainter() {
    }

    /**
     * Paints one resolved mask layer into the currently bound target.
     *
     * @return true if the layer produced mask content
     */
    public static boolean paintLayer(Element target, PoseStack poseStack, MaskImage.ResolvedLayer resolved) {
        Background.Layer layer = resolved.layer();
        if (layer == null) return false;
        boolean isGradient = layer.gradient != null;
        if (!isGradient) {
            if (!layer.hasDrawableContent()) return false;
            // mask 不画加载占位符：未就绪的层跳过，加载完成会触发 requester 重绘
            if (!ImageDrawer.isTextureReady(layer.imagePath, target)) return false;
        }

        Rect rect = Rect.of(target);
        float[] origin = boxArea(rect, resolved.origin());
        if (origin[2] <= 0 || origin[3] <= 0) return false;
        boolean clipped = !"no-clip".equals(resolved.clip());
        float[] clip = clipped ? boxArea(rect, resolved.clip()) : null;
        float[] clipRadii = clipped ? boxRadii(rect, resolved.clip(), clip[2], clip[3]) : null;

        poseStack.pushPose();
        try {
            Base.applyTransform(poseStack, target);
            if (clipped) {
                Mask.pushMask(poseStack, clip[0], clip[1], clip[2], clip[3], clipRadii, hasTransformedAncestor(target));
            }
            try {
                if (isGradient) {
                    paintGradientLayer(poseStack, origin[0], origin[1], origin[2], origin[3], layer);
                } else {
                    ImageDrawer.drawComplexBackground(poseStack, origin[0], origin[1], origin[2], origin[3], layer, target);
                }
            } finally {
                if (clipped) {
                    Mask.popMask(poseStack, clip[0], clip[1], clip[2], clip[3], clipRadii);
                }
            }
        } finally {
            poseStack.popPose();
        }
        return true;
    }

    /** 盒子定位/裁剪区（GUI 坐标）：border-box 含边框，padding-box 即 body rect，content-box 去掉 padding。 */
    private static float[] boxArea(Rect rect, String box) {
        Position p = rect.getBodyRectPosition();
        Size s = rect.getBodyRectSize();
        return switch (box) {
            case "padding-box" -> new float[]{(float) p.x, (float) p.y, (float) s.width(), (float) s.height()};
            case "content-box" -> new float[]{
                    (float) (p.x + rect.box.getPaddingLeft()),
                    (float) (p.y + rect.box.getPaddingTop()),
                    (float) (s.width() - rect.box.getPaddingHorizontal()),
                    (float) (s.height() - rect.box.getPaddingVertical())};
            default -> new float[]{
                    (float) (p.x - rect.box.getBorderLeft()),
                    (float) (p.y - rect.box.getBorderTop()),
                    (float) (s.width() + rect.box.getBorderHorizontal()),
                    (float) (s.height() + rect.box.getBorderVertical())};
        };
    }

    /**
     * 盒子圆角：border-box 用外缘半径（offset 0）；padding-box 复用 body 半径
     * （按最小边框内缩）；content-box 按最小边框+最小 padding 进一步内缩（近似，
     * 四边不均时取最小值统一内缩）。
     */
    private static float[] boxRadii(Rect rect, String box, float w, float h) {
        return switch (box) {
            case "padding-box" -> rect.getBodyRadius();
            case "content-box" -> rect.box.getCalculatedRadii(w, h, minBorder(rect) + minPadding(rect));
            default -> rect.box.getCalculatedRadii(w, h, 0);
        };
    }

    private static float minBorder(Rect rect) {
        return (float) Math.min(Math.min(rect.box.getBorderLeft(), rect.box.getBorderTop()),
                Math.min(rect.box.getBorderRight(), rect.box.getBorderBottom()));
    }

    private static float minPadding(Rect rect) {
        return (float) Math.min(Math.min(rect.box.getPaddingLeft(), rect.box.getPaddingTop()),
                Math.min(rect.box.getPaddingRight(), rect.box.getPaddingBottom()));
    }

    /** 渐变层的平铺绘制，对齐 Rect.drawGradientLayer 的 repeat 分支（外层已裁剪）。 */
    private static void paintGradientLayer(PoseStack poseStack, float x, float y, float w, float h,
                                           Background.Layer layer) {
        ImageDrawer.GradientTile tile = ImageDrawer.resolveGradientTile(layer, w, h);
        Graph.endBatch();
        Graph.beginBatch();
        Gradient scaled = layer.gradient.scaledTo(tile.width(), tile.height());
        for (float ix = tile.startX(); ix < tile.endX(); ix += tile.width()) {
            for (float iy = tile.startY(); iy < tile.endY(); iy += tile.height()) {
                boolean drawn = Graph.drawAxisAlignedHardStopGradientRect(poseStack.last().pose(),
                        x + ix, y + iy, tile.width(), tile.height(), scaled);
                if (!drawn) {
                    drawn = Graph.drawAxisAlignedStopGradientRect(poseStack.last().pose(),
                            x + ix, y + iy, tile.width(), tile.height(), scaled);
                }
                if (!drawn) {
                    Graph.drawGradientRect(poseStack.last().pose(),
                            x + ix, y + iy, tile.width(), tile.height(), scaled);
                }
            }
        }
        Graph.endBatch();
        Graph.beginBatch();
    }

    /**
     * XY 平面变换会让基于布局坐标的 scissor 裁剪错位（见 RenderNode 的同名约定），
     * 此时圆角裁剪强制走 stencil 路径。
     */
    private static boolean hasTransformedAncestor(Element target) {
        if (target == null) return false;
        for (Element element : target.getRouteArray()) {
            if (Transform.affectsXY(element.getComputedStyle().transform)) {
                return true;
            }
        }
        return false;
    }
}
