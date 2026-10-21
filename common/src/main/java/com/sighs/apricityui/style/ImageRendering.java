package com.sighs.apricityui.style;

import java.util.Locale;

/**
 * CSS {@code image-rendering} 取值归一化与采样决策。
 *
 * <p>按浏览器语义：{@code auto} 使用 UA 默认的平滑缩放，{@code smooth}/{@code high-quality}
 * 为线性过滤，{@code crisp-edges} 保留边缘（最近邻），{@code pixelated} 在放大时用最近邻、
 * 缩小时与 {@code auto} 一样用平滑过滤——放大小图时才出现"大像素"，缩小时则保持抗锯齿。</p>
 *
 * <p>字段本身保留书写关键字（计算值归一为小写），只在绘制时决定采样方式，
 * 与本项目其它枚举属性一致。</p>
 */
public final class ImageRendering {
    private ImageRendering() {
    }

    public enum Mode {
        /** 浏览器默认：UA 决定，本项目取线性过滤。 */
        AUTO,
        /** {@code smooth}：线性过滤。 */
        SMOOTH,
        /** {@code high-quality}：质量优先，引擎只有线性过滤可用，按 {@link #SMOOTH} 处理。 */
        HIGH_QUALITY,
        /** {@code crisp-edges}：保留边缘，最近邻过滤。 */
        CRISP_EDGES,
        /** {@code pixelated}：放大最近邻，缩小平滑。 */
        PIXELATED
    }

    /**
     * 合法关键字的小写形式；不是合法取值时返回 {@code null}。
     *
     * <p>CSS 在解析期丢弃非法声明，计算值里不会出现未知关键字；调用方据此回退。</p>
     */
    public static String keyword(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "auto", "smooth", "high-quality", "crisp-edges", "pixelated" -> normalized;
            default -> null;
        };
    }

    public static Mode parse(String value) {
        String keyword = keyword(value);
        if (keyword == null) return Mode.AUTO;
        return switch (keyword) {
            case "smooth" -> Mode.SMOOTH;
            case "high-quality" -> Mode.HIGH_QUALITY;
            case "crisp-edges" -> Mode.CRISP_EDGES;
            case "pixelated" -> Mode.PIXELATED;
            default -> Mode.AUTO;
        };
    }

    /**
     * 绘制尺寸相对固有尺寸的缩放系数，用于区分放大与缩小。
     *
     * <p>任一轴放大都按放大处理（取两者较大值），符合"放大时保留像素块"的直觉；
     * 固有尺寸不可用时返回 {@code 0}，调用方按放大处理。</p>
     */
    public static double scale(double drawnWidth, double drawnHeight,
                               double intrinsicWidth, double intrinsicHeight) {
        double x = scale(drawnWidth, intrinsicWidth);
        double y = scale(drawnHeight, intrinsicHeight);
        if (x <= 0) return y;
        if (y <= 0) return x;
        return Math.max(x, y);
    }

    private static double scale(double drawn, double intrinsic) {
        if (!(drawn > 0) || !(intrinsic > 0)) return 0;
        return drawn / intrinsic;
    }

    /**
     * 是否使用线性过滤。
     *
     * @param value {@code image-rendering} 的书写值
     * @param scale 绘制尺寸 / 固有尺寸；{@code <= 0} 表示未知（按放大处理）
     */
    public static boolean linearFiltering(String value, double scale) {
        return switch (parse(value)) {
            // 浏览器默认与 smooth/high-quality 都走平滑缩放。
            case AUTO, SMOOTH, HIGH_QUALITY -> true;
            case CRISP_EDGES -> false;
            // pixelated 缩小等同 auto；放大/未知尺寸用最近邻。
            case PIXELATED -> scale > 0 && scale < 1.0;
        };
    }
}
