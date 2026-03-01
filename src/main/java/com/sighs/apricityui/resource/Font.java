package com.sighs.apricityui.resource;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Font {
    private static final float BASE_FONT_SIZE = 48.0f;
    private static final Map<String, java.awt.Font> FONTS = Collections.synchronizedMap(new HashMap<>());
    private static final String DEFAULT_KEY = "default";

    static {
        // 默认把一个系统字体注册为 fallback
        FONTS.put(DEFAULT_KEY, new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, (int) BASE_FONT_SIZE));
    }

    public static boolean registerFont(String key, InputStream stream) {
        if (key == null || stream == null) return false;
        try {
            java.awt.Font base = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream);
            java.awt.Font derived = base.deriveFont(java.awt.Font.PLAIN, BASE_FONT_SIZE);
            String cleanKey = key.replace("'", "").replace("\"", "").trim();
            FONTS.put(cleanKey, derived);
            return true;
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean registerFont(String key, File fontFile) {
        if (key == null || fontFile == null || !fontFile.exists()) return false;
        try {
            java.awt.Font base = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, fontFile);
            java.awt.Font derived = base.deriveFont(java.awt.Font.PLAIN, BASE_FONT_SIZE);
            String cleanKey = key.replace("'", "").replace("\"", "").trim();
            FONTS.put(cleanKey, derived);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean registerFont(String key, Path path) {
        if (key == null || path == null || !Files.exists(path)) return false;
        return registerFont(key, path.toFile());
    }

    public static java.awt.Font getBaseFont(String key) {
        return getBaseFont(key, java.awt.Font.PLAIN);
    }

    public static java.awt.Font getBaseFont(String key, int style) {
        java.awt.Font f = FONTS.get(key);
        java.awt.Font base = f != null ? f : FONTS.get(DEFAULT_KEY);
        return base.deriveFont(style, BASE_FONT_SIZE);
    }

    public static float getBaseFontSize() {
        return BASE_FONT_SIZE;
    }

    public static void clear() {
        FONTS.clear();
        FONTS.put(DEFAULT_KEY, new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, (int) BASE_FONT_SIZE));
    }
}
