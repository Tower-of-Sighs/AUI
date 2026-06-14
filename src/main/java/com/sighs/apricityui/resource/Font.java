package com.sighs.apricityui.resource;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Font {
    private static final float BASE_FONT_SIZE = 48.0f;
    private static final Map<String, java.awt.Font> FONTS = Collections.synchronizedMap(new HashMap<>());
    private static final String DEFAULT_KEY = "default";
    private static final int SINGLE_FAMILY_CACHE_LIMIT = 128;
    private static final int BASE_FONT_CHAIN_CACHE_LIMIT = 64;
    private static final int RUN_PLAN_CACHE_LIMIT = 512;
    private static final Map<String, Optional<java.awt.Font>> SINGLE_FAMILY_CACHE = createLruCache(SINGLE_FAMILY_CACHE_LIMIT);
    private static final Map<String, List<java.awt.Font>> BASE_FONT_CHAIN_CACHE = createLruCache(BASE_FONT_CHAIN_CACHE_LIMIT);
    private static final Map<DerivedFontKey, java.awt.Font> DERIVED_FONT_CACHE = new ConcurrentHashMap<>();
    private static final Map<RunPlanKey, List<FontRun>> RUN_PLAN_CACHE = createLruCache(RUN_PLAN_CACHE_LIMIT);
    private static final Map<String, String> GENERIC_FAMILY_MAPPING = Map.ofEntries(
            Map.entry("serif", java.awt.Font.SERIF),
            Map.entry("sans-serif", java.awt.Font.SANS_SERIF),
            Map.entry("monospace", java.awt.Font.MONOSPACED),
            Map.entry("ui-serif", java.awt.Font.SERIF),
            Map.entry("ui-sans-serif", java.awt.Font.SANS_SERIF),
            Map.entry("ui-monospace", java.awt.Font.MONOSPACED),
            Map.entry("ui-rounded", java.awt.Font.SANS_SERIF),
            Map.entry("cursive", java.awt.Font.SANS_SERIF),
            Map.entry("fantasy", java.awt.Font.SANS_SERIF),
            Map.entry("system-ui", java.awt.Font.DIALOG),
            Map.entry("emoji", java.awt.Font.DIALOG),
            Map.entry("math", java.awt.Font.SERIF),
            Map.entry("fangsong", java.awt.Font.SERIF)
    );

    static {
        // 默认把一个系统字体注册为 fallback
        FONTS.put(DEFAULT_KEY, new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, (int) BASE_FONT_SIZE));
    }

    public static boolean registerFont(String key, InputStream stream) {
        if (key == null || stream == null) return false;
        try {
            java.awt.Font base = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream);
            java.awt.Font derived = base.deriveFont(java.awt.Font.PLAIN, BASE_FONT_SIZE);
            registerResolvedFont(key, derived);
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
            registerResolvedFont(key, derived);
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
        if (key == null || key.isBlank()) return FONTS.get(DEFAULT_KEY);
        java.awt.Font font = FONTS.get(key);
        if (font != null) return font;

        String cleanKey = cleanFamilyName(key);
        font = FONTS.get(cleanKey);
        if (font != null) return font;

        font = FONTS.get(toLookupKey(cleanKey));
        return font != null ? font : FONTS.get(DEFAULT_KEY);
    }

    public static java.awt.Font resolveBaseFont(String rawFamilyChain) {
        List<java.awt.Font> chain = resolveBaseFontChain(rawFamilyChain);
        return chain.isEmpty() ? FONTS.get(DEFAULT_KEY) : chain.get(0);
    }

    public static List<java.awt.Font> resolveBaseFontChain(String rawFamilyChain) {
        String cacheKey = normalizeFamilyChain(rawFamilyChain);
        List<java.awt.Font> cached = BASE_FONT_CHAIN_CACHE.get(cacheKey);
        if (cached != null) return cached;

        ArrayList<java.awt.Font> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String family : parseFontFamilies(rawFamilyChain)) {
            java.awt.Font font = resolveSingleFamily(family);
            if (font == null) continue;
            String key = font.getFontName(Locale.ROOT) + "|" + font.getFamily(Locale.ROOT);
            if (seen.add(key)) {
                result.add(font);
            }
        }

        java.awt.Font fallback = FONTS.get(DEFAULT_KEY);
        if (fallback != null) {
            String key = fallback.getFontName(Locale.ROOT) + "|" + fallback.getFamily(Locale.ROOT);
            if (seen.add(key)) {
                result.add(fallback);
            }
        }
        List<java.awt.Font> immutable = List.copyOf(result);
        BASE_FONT_CHAIN_CACHE.put(cacheKey, immutable);
        return immutable;
    }

    public static List<FontRun> planFontRuns(String rawFamilyChain, int fontStyle, float size, String content) {
        if (content == null || content.isEmpty()) return List.of();
        RunPlanKey cacheKey = new RunPlanKey(normalizeFamilyChain(rawFamilyChain), fontStyle, Float.floatToIntBits(size), content);
        List<FontRun> cached = RUN_PLAN_CACHE.get(cacheKey);
        if (cached != null) return cached;

        List<java.awt.Font> baseFonts = resolveBaseFontChain(rawFamilyChain);
        if (baseFonts.isEmpty()) return List.of();

        ArrayList<java.awt.Font> fonts = new ArrayList<>(baseFonts.size());
        for (java.awt.Font font : baseFonts) {
            fonts.add(deriveCachedFont(font, fontStyle, size));
        }

        ArrayList<FontRun> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        java.awt.Font currentFont = null;
        Map<Integer, java.awt.Font> codePointFontCache = new HashMap<>();

        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            java.awt.Font font = codePointFontCache.computeIfAbsent(cp, key -> pickDisplayFont(fonts, key));
            String glyph = new String(Character.toChars(cp));

            if (currentFont != null && currentFont.equals(font)) {
                current.append(glyph);
            } else {
                if (currentFont != null && current.length() > 0) {
                    runs.add(new FontRun(currentFont, current.toString()));
                }
                current.setLength(0);
                current.append(glyph);
                currentFont = font;
            }
            i += Character.charCount(cp);
        }

        if (currentFont != null && current.length() > 0) {
            runs.add(new FontRun(currentFont, current.toString()));
        }
        List<FontRun> immutable = List.copyOf(runs);
        RUN_PLAN_CACHE.put(cacheKey, immutable);
        return immutable;
    }

    public static List<String> parseFontFamilies(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        ArrayList<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
                continue;
            }

            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (c == ',') {
                appendFamily(result, current);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        appendFamily(result, current);
        return result;
    }

    public static float getBaseFontSize() {
        return BASE_FONT_SIZE;
    }

    public static void clear() {
        FONTS.clear();
        clearResolutionCaches();
        FONTS.put(DEFAULT_KEY, new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, (int) BASE_FONT_SIZE));
    }

    private static java.awt.Font resolveSingleFamily(String family) {
        if (family == null || family.isBlank()) return null;

        String cleanFamily = cleanFamilyName(family);
        Optional<java.awt.Font> cached = SINGLE_FAMILY_CACHE.get(cleanFamily);
        if (cached != null) return cached.orElse(null);

        java.awt.Font alias = resolveKnownWebFontAlias(cleanFamily);
        if (alias != null) {
            SINGLE_FAMILY_CACHE.put(cleanFamily, Optional.of(alias));
            return alias;
        }

        java.awt.Font registered = FONTS.get(cleanFamily);
        if (registered == null) {
            registered = FONTS.get(toLookupKey(cleanFamily));
        }
        if (registered != null) {
            SINGLE_FAMILY_CACHE.put(cleanFamily, Optional.of(registered));
            return registered;
        }

        String genericMapped = GENERIC_FAMILY_MAPPING.get(cleanFamily.toLowerCase(Locale.ROOT));
        if (genericMapped != null) {
            java.awt.Font resolved = new java.awt.Font(genericMapped, java.awt.Font.PLAIN, (int) BASE_FONT_SIZE);
            SINGLE_FAMILY_CACHE.put(cleanFamily, Optional.of(resolved));
            return resolved;
        }

        java.awt.Font systemFont = new java.awt.Font(cleanFamily, java.awt.Font.PLAIN, (int) BASE_FONT_SIZE);
        if (!java.awt.Font.DIALOG.equalsIgnoreCase(systemFont.getFamily(Locale.ROOT))
                || isDialogFamily(cleanFamily)) {
            SINGLE_FAMILY_CACHE.put(cleanFamily, Optional.of(systemFont));
            return systemFont;
        }
        SINGLE_FAMILY_CACHE.put(cleanFamily, Optional.empty());
        return null;
    }

    private static java.awt.Font resolveKnownWebFontAlias(String family) {
        String normalized = cleanFamilyName(family).toLowerCase(Locale.ROOT);
        if (!"rajdhani".equals(normalized)) return null;
        java.awt.Font bahnschrift = new java.awt.Font("Bahnschrift", java.awt.Font.PLAIN, (int) BASE_FONT_SIZE);
        if (!java.awt.Font.DIALOG.equalsIgnoreCase(bahnschrift.getFamily(Locale.ROOT))) {
            return bahnschrift;
        }
        java.awt.Font agency = new java.awt.Font("Agency FB", java.awt.Font.PLAIN, (int) BASE_FONT_SIZE);
        if (!java.awt.Font.DIALOG.equalsIgnoreCase(agency.getFamily(Locale.ROOT))) {
            return agency;
        }
        return null;
    }

    private static java.awt.Font pickDisplayFont(List<java.awt.Font> fonts, int codePoint) {
        if (fonts == null || fonts.isEmpty()) {
            return FONTS.get(DEFAULT_KEY);
        }
        for (java.awt.Font font : fonts) {
            if (font != null && font.canDisplay(codePoint)) {
                return font;
            }
        }
        java.awt.Font fallback = fonts.get(fonts.size() - 1);
        return fallback != null ? fallback : FONTS.get(DEFAULT_KEY);
    }

    private static void registerResolvedFont(String key, java.awt.Font derived) {
        String cleanKey = cleanFamilyName(key);
        if (cleanKey.isEmpty() || derived == null) return;
        FONTS.put(cleanKey, derived);
        FONTS.put(toLookupKey(cleanKey), derived);
        clearResolutionCaches();
    }

    private static boolean isDialogFamily(String family) {
        String normalized = cleanFamilyName(family).toLowerCase(Locale.ROOT);
        return normalized.equals("dialog") || normalized.equals("dialoginput");
    }

    private static void appendFamily(List<String> result, StringBuilder current) {
        String family = cleanFamilyName(current == null ? null : current.toString());
        if (!family.isEmpty()) result.add(family);
    }

    private static String cleanFamilyName(String key) {
        if (key == null) return "";
        String clean = key.trim();
        if (clean.length() >= 2) {
            char first = clean.charAt(0);
            char last = clean.charAt(clean.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                clean = clean.substring(1, clean.length() - 1).trim();
            }
        }
        return clean.replace("\"", "").replace("'", "").trim();
    }

    private static String toLookupKey(String family) {
        return cleanFamilyName(family).toLowerCase(Locale.ROOT);
    }

    private static java.awt.Font deriveCachedFont(java.awt.Font font, int fontStyle, float size) {
        if (font == null) return null;
        DerivedFontKey key = new DerivedFontKey(
                font.getFontName(Locale.ROOT),
                font.getFamily(Locale.ROOT),
                fontStyle,
                Float.floatToIntBits(size)
        );
        return DERIVED_FONT_CACHE.computeIfAbsent(key, unused -> font.deriveFont(fontStyle, size));
    }

    private static void clearResolutionCaches() {
        SINGLE_FAMILY_CACHE.clear();
        BASE_FONT_CHAIN_CACHE.clear();
        DERIVED_FONT_CACHE.clear();
        RUN_PLAN_CACHE.clear();
    }

    private static String normalizeFamilyChain(String rawFamilyChain) {
        List<String> families = parseFontFamilies(rawFamilyChain);
        return families.isEmpty() ? "" : String.join(",", families);
    }

    private static <K, V> Map<K, V> createLruCache(int maxSize) {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        });
    }

    public record FontRun(java.awt.Font font, String text) {
    }

    private record DerivedFontKey(String fontName, String family, int style, int sizeBits) {
    }

    private record RunPlanKey(String familyChain, int style, int sizeBits, String content) {
    }
}
