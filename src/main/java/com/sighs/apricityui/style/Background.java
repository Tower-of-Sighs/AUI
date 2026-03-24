package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Background {
    public static class Layer {
        public String repeat = "no-repeat";
        public String size = "auto";
        public String position = "0 0";
        public String imagePath = "unset";
        public Gradient gradient = null;

        public boolean hasDrawableContent() {
            return gradient != null || (imagePath != null && !imagePath.isBlank() && !"unset".equals(imagePath));
        }
    }

    // legacy fields kept for compatibility (first drawable layer)
    public String repeat = "no-repeat";
    public String size = "auto";
    public String position = "0 0";
    public String imagePath = "unset";
    public String color = "unset";
    public Gradient gradient = null;

    private final List<Layer> layers = new ArrayList<>();

    public static Background of(Element element) {
        Background cache = element.getRenderer().background.get();
        if (cache != null) {
            cache.syncAnimatedFields(element.getComputedStyle(), element.document.getPath());
            return cache;
        }

        Style style = element.getComputedStyle();
        Background bg = new Background();
        bg.color = style.backgroundColor;
        bg.buildLayers(element.document.getPath(), style.backgroundImage, style.backgroundRepeat, style.backgroundSize, style.backgroundPosition);

        element.getRenderer().background.set(bg);
        return bg;
    }

    private void syncAnimatedFields(Style style, String contextPath) {
        if (style == null) return;
        this.color = style.backgroundColor;
        buildLayers(contextPath, style.backgroundImage, style.backgroundRepeat, style.backgroundSize, style.backgroundPosition);
    }

    private void buildLayers(String contextPath, String image, String repeat, String size, String position) {
        this.repeat = normalizeLayerValue(repeat, "no-repeat");
        this.size = normalizeLayerValue(size, "auto");
        this.position = normalizeLayerValue(position, "0 0");
        this.imagePath = "unset";
        this.gradient = null;
        this.layers.clear();

        List<String> images = splitTopLevelComma(image);
        if (images.isEmpty()) return;

        List<String> repeats = splitTopLevelComma(repeat);
        List<String> sizes = splitTopLevelComma(size);
        List<String> positions = splitTopLevelComma(position);

        for (int i = 0; i < images.size(); i++) {
            Layer layer = parseImageLayer(contextPath, images.get(i));
            layer.repeat = normalizeLayerValue(pickLayerToken(repeats, i, "no-repeat"), "no-repeat");
            layer.size = normalizeLayerValue(pickLayerToken(sizes, i, "auto"), "auto");
            layer.position = normalizeLayerValue(pickLayerToken(positions, i, "0 0"), "0 0");
            layers.add(layer);

            if (gradient == null && layer.gradient != null) gradient = layer.gradient;
            if ("unset".equals(imagePath) && !"unset".equals(layer.imagePath)) imagePath = layer.imagePath;
        }
    }

    public static List<String> resolveImagePaths(String contextPath, String imageValue) {
        List<String> result = new ArrayList<>();
        for (String token : splitTopLevelComma(imageValue)) {
            Layer layer = parseImageLayer(contextPath, token);
            if (layer.imagePath != null && !"unset".equals(layer.imagePath) && !layer.imagePath.isBlank()) {
                result.add(layer.imagePath);
            }
        }
        return result;
    }

    private static Layer parseImageLayer(String contextPath, String token) {
        Layer layer = new Layer();
        if (token == null) return layer;

        String image = token.trim();
        if (image.isEmpty() || "unset".equalsIgnoreCase(image) || "none".equalsIgnoreCase(image)) {
            return layer;
        }

        String lowered = image.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("linear-gradient")) {
            layer.gradient = Gradient.parse(image);
            return layer;
        }

        int urlStart = lowered.indexOf("url(");
        if (urlStart >= 0) {
            int start = urlStart + 4;
            int end = image.indexOf(')', start);
            if (end > start) {
                String path = image.substring(start, end).replace("\"", "").replace("'", "").trim();
                if (!path.isEmpty() && contextPath != null) {
                    layer.imagePath = Loader.resolve(contextPath, path);
                }
            }
        }
        return layer;
    }

    private static String pickLayerToken(List<String> values, int index, String fallback) {
        if (values == null || values.isEmpty()) return fallback;
        if (index < values.size()) return values.get(index);
        return values.get(values.size() - 1);
    }

    private static String normalizeLayerValue(String raw, String fallback) {
        if (raw == null) return fallback;
        String value = raw.trim();
        if (value.isEmpty() || "unset".equalsIgnoreCase(value)) return fallback;
        return value;
    }

    public static List<String> splitTopLevelComma(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isBlank()) return parts;
        if ("unset".equalsIgnoreCase(value.trim())) return parts;

        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && depth > 0) depth--;

            if (c == ',' && depth == 0) {
                String token = current.toString().trim();
                if (!token.isEmpty()) parts.add(token);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) parts.add(last);
        return parts;
    }

    public List<Layer> getLayers() {
        return layers;
    }
}
