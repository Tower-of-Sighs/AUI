package com.sighs.apricityui.layout;

import java.util.Locale;
import com.sighs.apricityui.parser.CSS;

/**
 * 盒对齐关键字的规范化形式（start/end/center/stretch），grid 的
 * justify/align 与 flex 的交叉轴对齐共用，避免各布局器重复解析同一组字符串。
 */
enum Align {
    START, CENTER, END, STRETCH;

    /** 把 CSS 对齐关键字归一化到枚举；未识别或 unset/auto 时回退到 fallback。 */
    static Align normalize(String raw, Align fallback) {
        if (raw == null) return fallback;
        raw = raw.trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank() || "unset".equals(raw) || "auto".equals(raw)) return fallback;
        return switch (raw) {
            case "start", "flex-start", "left", "top" -> START;
            case "center" -> CENTER;
            case "end", "flex-end", "right", "bottom" -> END;
            case "stretch" -> STRETCH;
            default -> fallback;
        };
    }
}
