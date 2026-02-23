package com.sighs.apricityui.instance.container.visual;

import java.util.Locale;

/**
 * slot 视觉/交互属性统一解析规则。
 * 统一模板分析阶段与运行时阶段，避免同语义在多处出现分叉实现。
 */
public final class SlotVisualRules {
    public record RenderRule(boolean renderBackground, boolean renderItem) {
    }

    public static Boolean parsePointerFlag(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return !"false".equals(normalized)
                && !"0".equals(normalized)
                && !"none".equals(normalized)
                && !"off".equals(normalized);
    }

    public static boolean resolvePointerEnabled(String raw, boolean fallback) {
        Boolean parsed = parsePointerFlag(raw);
        return parsed == null ? fallback : parsed;
    }

    public static RenderRule parseRenderRule(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "none" -> new RenderRule(false, false);
            case "item" -> new RenderRule(false, true);
            case "bg", "background" -> new RenderRule(true, false);
            default -> new RenderRule(
                    normalized.contains("bg") || normalized.contains("background"),
                    normalized.contains("item")
            );
        };
    }

    public static boolean resolveRenderBackground(String raw, boolean fallback) {
        RenderRule parsed = parseRenderRule(raw);
        return parsed == null ? fallback : parsed.renderBackground();
    }

    public static boolean resolveRenderItem(String raw, boolean fallback) {
        RenderRule parsed = parseRenderRule(raw);
        return parsed == null ? fallback : parsed.renderItem();
    }

    public static Integer parseSignedInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Integer parsePositiveInt(String raw) {
        Integer parsed = parseSignedInt(raw);
        if (parsed == null || parsed <= 0) return null;
        return parsed;
    }

    public static Integer parseNonNegativeInt(String raw) {
        Integer parsed = parseSignedInt(raw);
        if (parsed == null || parsed < 0) return null;
        return parsed;
    }

    public static Float parsePositiveFloat(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            float parsed = Float.parseFloat(raw.trim());
            if (parsed <= 0.0F) return null;
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
