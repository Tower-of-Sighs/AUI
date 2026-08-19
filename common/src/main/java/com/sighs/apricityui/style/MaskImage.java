package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CSS mask-image 的解析与缓存。mask 与 background 共用 {@link Background} 的
 * 分层解析，差别只在初始值（mask-repeat 默认 repeat）与消费方式：
 * 层不直接上屏，而是画进离屏 FBO 后与内容合成。
 *
 * {@link ResolvedLayer} 把 mask-mode/mask-clip/mask-origin/mask-composite 的
 * 逗号列表按层循环对齐（以 mask-image 层数为准），并消解 match-source。
 * 偏差：margin-box/fill-box/stroke-box/view-box 一律按 border-box 处理。
 */
public final class MaskImage {
    private MaskImage() {
    }

    /** 逐层解析后的 mask 层：绘制参数 + 该层的几何框与合成方式。 */
    public record ResolvedLayer(Background.Layer layer, String mode, String clip, String origin, String composite) {
    }

    /** 元素是否有非 none 的 mask-image（决定是否生成离屏合成节点）。 */
    public static boolean hasMask(Element element) {
        if (element == null) return false;
        String image = element.getComputedStyle().maskImage;
        if (image == null) return false;
        String value = image.trim();
        return !value.isEmpty() && !"none".equalsIgnoreCase(value) && !"unset".equalsIgnoreCase(value);
    }

    /** 解析后的 mask 层列表（缓存在 RenderElement.maskLayers，样式变化时失效）。 */
    public static List<ResolvedLayer> layersOf(Element element) {
        List<ResolvedLayer> cached = element.getRenderer().maskLayers.get();
        if (cached != null) {
            return cached;
        }

        Style style = element.getComputedStyle();
        List<Background.Layer> layers = Background.parseLayers(
                element.document == null ? null : element.document.getPath(),
                style.maskImage, style.maskRepeat, style.maskSize, style.maskPosition,
                "repeat", "auto", "0% 0%");

        List<String> modes = Background.splitTopLevelComma(nullSafe(style.maskMode));
        List<String> clips = Background.splitTopLevelComma(nullSafe(style.maskClip));
        List<String> origins = Background.splitTopLevelComma(nullSafe(style.maskOrigin));
        List<String> composites = Background.splitTopLevelComma(nullSafe(style.maskComposite));

        List<ResolvedLayer> resolved = new ArrayList<>(layers.size());
        for (int i = 0; i < layers.size(); i++) {
            String mode = resolveMode(cyclic(modes, i, "match-source"));
            String clip = resolveBox(cyclic(clips, i, "border-box"), true);
            String origin = resolveBox(cyclic(origins, i, "border-box"), false);
            String composite = resolveComposite(cyclic(composites, i, "add"));
            resolved.add(new ResolvedLayer(layers.get(i), mode, clip, origin, composite));
        }

        element.getRenderer().maskLayers.set(resolved);
        return resolved;
    }

    /**
     * 元素最终 blit 用 luminance 还是 alpha：仅当全部可绘制层都是 luminance 时
     * 才用 luminance（混合 mode 按 alpha，文档化偏差）。
     */
    public static boolean effectiveLuminance(List<ResolvedLayer> layers) {
        boolean any = false;
        for (ResolvedLayer layer : layers) {
            if (!layer.layer().hasDrawableContent()) continue;
            if (!"luminance".equals(layer.mode())) return false;
            any = true;
        }
        return any;
    }

    /** match-source 对 CSS 图像源一律等价 alpha。 */
    private static String resolveMode(String mode) {
        return "luminance".equals(mode) ? "luminance" : "alpha";
    }

    /** 不支持的盒子关键字（margin-box/fill-box/stroke-box/view-box）按 border-box。 */
    private static String resolveBox(String box, boolean allowNoClip) {
        return switch (box) {
            case "padding-box", "content-box" -> box;
            case "no-clip" -> allowNoClip ? "no-clip" : "border-box";
            default -> "border-box";
        };
    }

    private static String resolveComposite(String composite) {
        return switch (composite) {
            case "subtract", "intersect", "exclude" -> composite;
            default -> "add";
        };
    }

    private static String cyclic(List<String> list, int index, String fallback) {
        if (list.isEmpty()) return fallback;
        String value = list.get(index % list.size());
        if (value == null) return fallback;
        value = value.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() || "unset".equals(value) ? fallback : value;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
