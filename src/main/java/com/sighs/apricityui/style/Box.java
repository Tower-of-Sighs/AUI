package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Box {
    public static final List<String> SIDE = List.of("top", "bottom", "left", "right");
    public final HashMap<String, SideBorder> border = new HashMap<>();
    public final HashMap<String, Double> margin = new HashMap<>();
    public final HashMap<String, Double> padding = new HashMap<>();
    public final ArrayList<Integer> borderRadius = new ArrayList<>();
    public Shadow shadow = null;
    public Outline outline = null;
    public BorderImage borderImage = null;
    public Element element;

    public Box() {
        SIDE.forEach(side -> {
            border.put(side, SideBorder.getDefault());
            margin.put(side, 0d);
            padding.put(side, 0d);
        });
    }

    public void applyBorder(String side, String value) {
        SideBorder sideBorder = parseSideBorder(value);
        border.put(side, sideBorder);
    }

    public void applyBorderAll(String value) {
        SIDE.forEach(side -> applyBorder(side, value));
    }

    public void applyBorderColor(String side, String colorValue) {
        SideBorder current = border.get(side);
        if (current != null) {
            border.put(side, new SideBorder(current.size(), current.type(), new Color(colorValue)));
        }
    }

    public void applyBorderColorAll(String colorValue) {
        SIDE.forEach(side -> applyBorderColor(side, colorValue));
    }

    public void applyMargin(String side, String value) {
        applyMargin(side, value, null);
    }

    public void applyMargin(String side, String value, Element element) {
        double v = parseSpacingValue(value, element, true);
        margin.put(side, Math.max(0, v));
    }

    public void applyMarginAll(String value) {
        applyMarginAll(value, null);
    }

    public void applyMarginAll(String value, Element element) {
        double[] vals = parse4SpacingValues(value, element);
        margin.put("top", vals[0]);
        margin.put("right", vals[1]);
        margin.put("bottom", vals[2]);
        margin.put("left", vals[3]);
    }

    public void applyPadding(String side, String value) {
        applyPadding(side, value, null);
    }

    public void applyPadding(String side, String value, Element element) {
        double v = parseSpacingValue(value, element, false);
        padding.put(side, Math.max(0, v));
    }

    public void applyPaddingAll(String value) {
        applyPaddingAll(value, null);
    }

    public void applyPaddingAll(String value, Element element) {
        double[] vals = parse4SpacingValues(value, element);
        padding.put("top", vals[0]);
        padding.put("right", vals[1]);
        padding.put("bottom", vals[2]);
        padding.put("left", vals[3]);
    }

    /**
     * 解析单个 spacing 值，支持 px、%、auto（视为 0）
     */
    private static double parseSpacingValue(String value, Element element, boolean isMargin) {
        if (StringUtils.isNullOrEmptyEx(value) || value.equalsIgnoreCase("auto")) return 0;
        double parsed = Size.parseDouble(value);
        if (parsed < 0) return 0;
        if (value.contains("%") && element != null) {
            double ref = isMargin ? Size.getScaleWidth(element) : Size.getScaleWidth(element);
            return ref * parsed / 100d;
        }
        return parsed;
    }

    /**
     * 解析 margin/padding 简写：1值全边/2值上下左右/3值上左右下/4值上右下左；auto 视为 0；% 相对于包含块宽度
     */
    private static double[] parse4SpacingValues(String value, Element element) {
        if (StringUtils.isNullOrEmptyEx(value)) return new double[]{0, 0, 0, 0};
        String[] parts = value.trim().split("\\s+");
        double[] vals = new double[4];
        int n = 0;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            double v = p.equalsIgnoreCase("auto") ? 0 : Size.parseDouble(p);
            if (v >= 0) {
                if (element != null && p.contains("%")) {
                    double ref = Size.getScaleWidth(element);
                    v = ref * v / 100d;
                }
                vals[Math.min(n++, 3)] = Math.max(0, v);
            }
        }
        if (n == 1) return new double[]{vals[0], vals[0], vals[0], vals[0]};
        if (n == 2) return new double[]{vals[0], vals[1], vals[0], vals[1]};
        if (n == 3) return new double[]{vals[0], vals[1], vals[2], vals[1]};
        if (n >= 4) return new double[]{vals[0], vals[1], vals[2], vals[3]};
        return new double[]{0, 0, 0, 0};
    }

    private static boolean valid(String s) {
        return StringUtils.isNotNullOrEmptyEx(s) && !s.equals("unset");
    }

    /**
     * 供 BoxTransition 等外部使用
     */
    public static boolean isStyleValid(String s) {
        return valid(s);
    }

    public static Box of(Element element) {
        Box cache = element.getRenderer().box.get();
        if (cache != null) return cache;

        Box resultBox = new Box();
        Style style = element.getComputedStyle();

        if (valid(style.border)) resultBox.applyBorderAll(style.border);
        if (valid(style.borderTop)) resultBox.applyBorder("top", style.borderTop);
        if (valid(style.borderBottom)) resultBox.applyBorder("bottom", style.borderBottom);
        if (valid(style.borderLeft)) resultBox.applyBorder("left", style.borderLeft);
        if (valid(style.borderRight)) resultBox.applyBorder("right", style.borderRight);
        if (valid(style.borderColor)) resultBox.applyBorderColorAll(style.borderColor);
        if (valid(style.borderTopColor)) resultBox.applyBorderColor("top", style.borderTopColor);
        if (valid(style.borderRightColor)) resultBox.applyBorderColor("right", style.borderRightColor);
        if (valid(style.borderBottomColor)) resultBox.applyBorderColor("bottom", style.borderBottomColor);
        if (valid(style.borderLeftColor)) resultBox.applyBorderColor("left", style.borderLeftColor);

        if (valid(style.margin)) resultBox.applyMarginAll(style.margin, element);
        if (valid(style.marginTop)) resultBox.applyMargin("top", style.marginTop, element);
        if (valid(style.marginBottom)) resultBox.applyMargin("bottom", style.marginBottom, element);
        if (valid(style.marginLeft)) resultBox.applyMargin("left", style.marginLeft, element);
        if (valid(style.marginRight)) resultBox.applyMargin("right", style.marginRight, element);

        if (valid(style.padding)) resultBox.applyPaddingAll(style.padding, element);
        if (valid(style.paddingTop)) resultBox.applyPadding("top", style.paddingTop, element);
        if (valid(style.paddingBottom)) resultBox.applyPadding("bottom", style.paddingBottom, element);
        if (valid(style.paddingLeft)) resultBox.applyPadding("left", style.paddingLeft, element);
        if (valid(style.paddingRight)) resultBox.applyPadding("right", style.paddingRight, element);

        int tl = 0, tr = 0, br = 0, bl = 0;
        String radiusStr = style.borderRadius;
        if (!radiusStr.equals("unset")) {
            String[] parts = radiusStr.trim().split("\\s+");
            List<Integer> parsed = new ArrayList<>();
            for (String p : parts) {
                int val = Size.parse(p);
                parsed.add(val == -1 ? 0 : val);
            }
            if (parsed.size() == 1) {
                int r = parsed.get(0);
                tl = tr = br = bl = r;
            } else if (parsed.size() == 2) {
                tl = br = parsed.get(0);
                tr = bl = parsed.get(1);
            } else if (parsed.size() == 3) {
                tl = parsed.get(0);
                tr = bl = parsed.get(1);
                br = parsed.get(2);
            } else if (parsed.size() >= 4) {
                tl = parsed.get(0);
                tr = parsed.get(1);
                br = parsed.get(2);
                bl = parsed.get(3);
            }
        }
        if (valid(style.borderTopLeftRadius)) tl = Size.parse(style.borderTopLeftRadius);
        if (valid(style.borderTopRightRadius)) tr = Size.parse(style.borderTopRightRadius);
        if (valid(style.borderBottomRightRadius)) br = Size.parse(style.borderBottomRightRadius);
        if (valid(style.borderBottomLeftRadius)) bl = Size.parse(style.borderBottomLeftRadius);
        resultBox.borderRadius.addAll(List.of(tl, tr, br, bl));

        resultBox.element = element;
        resultBox.shadow = parseShadow(style.boxShadow);
        resultBox.outline = parseOutline(style);
        resultBox.borderImage = parseBorderImage(style);
        if (resultBox.borderImage != null && isZero(resultBox.borderImage.width)) {
            resultBox.borderImage.width = new int[]{
                    (int) resultBox.getBorderTop(),
                    (int) resultBox.getBorderRight(),
                    (int) resultBox.getBorderBottom(),
                    (int) resultBox.getBorderLeft()
            };
        }

        element.getRenderer().box.set(resultBox);
        return resultBox;
    }

    private static boolean isZero(int[] arr) {
        if (arr == null) return true;
        for (int i : arr) if (i > 0) return false;
        return true;
    }

    public Size size() {
        Size elementSize = Size.of(element);
        double resultWidth = elementSize.width() + getMarginHorizontal();
        double resultHeight = elementSize.height() + getMarginVertical();
        return new Size(resultWidth, resultHeight);
    }

    public Size innerSize() {
        Size elementSize = Size.of(element);
        double resultWidth = elementSize.width() - getBorderHorizontal() - getPaddingHorizontal();
        double resultHeight = elementSize.height() - getBorderVertical() - getPaddingVertical();
        return new Size(resultWidth, resultHeight);
    }

    public Size elementSize() {
        return Size.of(element);
    }

    public double offset(String side) {
        return border.getOrDefault(side, SideBorder.getDefault()).size + margin.getOrDefault(side, 0d) + padding.getOrDefault(side, 0d);
    }

    public double getMarginHorizontal() {
        return getMarginLeft() + getMarginRight();
    }

    public double getMarginVertical() {
        return getMarginTop() + getMarginBottom();
    }

    public double getMarginLeft() {
        return margin.getOrDefault("left", 0d);
    }

    public double getMarginTop() {
        return margin.getOrDefault("top", 0d);
    }

    public double getMarginRight() {
        return margin.getOrDefault("right", 0d);
    }

    public double getMarginBottom() {
        return margin.getOrDefault("bottom", 0d);
    }

    public double getBorderHorizontal() {
        return getBorderLeft() + getBorderRight();
    }

    public double getBorderVertical() {
        return getBorderTop() + getBorderBottom();
    }

    public double getBorderLeft() {
        return border.getOrDefault("left", SideBorder.getDefault()).size;
    }

    public double getBorderRight() {
        return border.getOrDefault("right", SideBorder.getDefault()).size;
    }

    public double getBorderTop() {
        return border.getOrDefault("top", SideBorder.getDefault()).size;
    }

    public double getBorderBottom() {
        return border.getOrDefault("bottom", SideBorder.getDefault()).size;
    }

    public double getPaddingHorizontal() {
        return getPaddingLeft() + getPaddingRight();
    }

    public double getPaddingVertical() {
        return getPaddingTop() + getPaddingBottom();
    }

    public double getPaddingLeft() {
        return padding.getOrDefault("left", 0d);
    }

    public double getPaddingRight() {
        return padding.getOrDefault("right", 0d);
    }

    public double getPaddingTop() {
        return padding.getOrDefault("top", 0d);
    }

    public double getPaddingBottom() {
        return padding.getOrDefault("bottom", 0d);
    }


    public static SideBorder parseSideBorder(String string) {
        String[] res = string.split(" ");
        if (res.length != 3) return SideBorder.getDefault();
        return new SideBorder(Size.parse(res[0]), res[1], new Color(res[2]));
    }

    public static Shadow parseShadow(String string) {
        if (StringUtils.isNullOrEmptyEx(string) || "unset".equals(string) || "none".equals(string)) {
            return Shadow.getDefault();
        }
        String[] res = string.trim().split("\\s+");
        if (res.length < 4) return Shadow.getDefault();
        int x = Size.parse(res[0]);
        if (res[0].contains("-")) x *= -1;
        int y = Size.parse(res[1]);
        if (res[1].contains("-")) y *= -1;
        int blur = Size.parse(res[2]);
        int spread = res.length >= 5 ? Size.parse(res[3]) : 0;
        Color color = res.length >= 5 ? new Color(res[4]) : new Color(res[3]);
        return new Shadow(x, y, blur, spread, color);
    }

    /**
     * 解析 outline 相关样式
     */
    public static Outline parseOutline(Style style) {
        Outline o = new Outline();
        if (valid(style.outline) && !style.outline.equals("none")) {
            String[] parts = style.outline.trim().split("\\s+");
            for (String p : parts) {
                if (p.equals("none")) return null;
                if (!p.isEmpty() && (Character.isDigit(p.charAt(0)) || p.endsWith("px"))) {
                    o.width = Size.parse(p);
                } else if (p.equals("solid") || p.equals("dashed") || p.equals("dotted") || p.equals("double")) {
                    o.style = p;
                } else if (p.startsWith("#") || p.startsWith("rgb") || p.matches("^[a-zA-Z]+$")) {
                    try {
                        o.color = new Color(p);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (valid(style.outlineWidth)) o.width = Size.parse(style.outlineWidth);
        if (valid(style.outlineStyle)) o.style = style.outlineStyle;
        if (valid(style.outlineColor)) o.color = new Color(style.outlineColor);
        if (valid(style.outlineOffset)) o.offset = Size.parse(style.outlineOffset);
        if (o.width <= 0) return null;
        return o;
    }

    public record SideBorder(int size, String type, Color color) {
        public static SideBorder getDefault() {
            return new SideBorder(0, "solid", Color.BLACK);
        }

        @Override
        public String toString() {
            return size + "px " + type + " " + color.toHexString();
        }
    }

    public record Shadow(int x, int y, int blur, int spread, Color color) {
        public static Shadow getDefault() {
            return new Shadow(0, 0, 0, 0, Color.BLACK);
        }

        public int size() {
            return blur;
        }

        @Override
        public String toString() {
            return spread != 0
                    ? x + "px " + y + "px " + blur + "px " + spread + "px " + color.toHexString()
                    : x + "px " + y + "px " + blur + "px " + color.toHexString();
        }
    }

    public static class Outline {
        public int width = 0;
        public String style = "solid";
        public Color color = Color.BLACK;
        public int offset = 0;
    }

    public static BorderImage parseBorderImage(Style style) {
        BorderImage bi = new BorderImage();

        if (valid(style.borderImageSource)) {
            bi.source = extractUrl(style.borderImageSource);
        } else if (style.borderImage.contains("url(")) {
            bi.source = extractUrl(style.borderImage);
        }

        String mainPart = style.borderImage.replaceAll("url\\(.*?\\)", "").trim();
        String[] repeats = {"stretch", "repeat", "round", "space"};
        for (String r : repeats) {
            if (mainPart.contains(r)) {
                bi.repeat = r;
                mainPart = mainPart.replace(r, ""); // 移除关键字
                break;
            }
        }
        mainPart = mainPart.trim();

        String[] sections = mainPart.split("/");
        if (sections.length > 0 && !sections[0].isBlank()) {
            String sliceStr = sections[0].trim();
            if (sliceStr.contains("fill")) {
                bi.fill = true;
                sliceStr = sliceStr.replace("fill", "").trim();
            }
            if (!sliceStr.isEmpty()) bi.slice = parse4Values(sliceStr);
        }
        if (sections.length > 1 && !sections[1].isBlank()) {
            bi.width = parse4Values(sections[1].trim());
        }
        if (sections.length > 2 && !sections[2].isBlank()) {
            bi.outset = parse4Values(sections[2].trim());
        }

        if (valid(style.borderImageSlice)) bi.slice = parse4Values(style.borderImageSlice);
        if (valid(style.borderImageWidth)) bi.width = parse4Values(style.borderImageWidth);
        if (valid(style.borderImageOutset)) bi.outset = parse4Values(style.borderImageOutset);
        if (valid(style.borderImageRepeat)) bi.repeat = style.borderImageRepeat;

        if (style.borderImage.startsWith("linear-gradient")) {
            bi.gradient = Gradient.parse(style.borderImage);
        }

        return bi.isEmpty() ? null : bi;
    }

    private static String extractUrl(String input) {
        if (input == null || !input.contains("url(")) return null;
        return input.substring(input.indexOf("url(") + 4, input.lastIndexOf(")")).replace("\"", "").replace("'", "");
    }

    private static int[] parse4Values(String input) {
        String[] parts = input.trim().split("\\s+");
        int[] res = new int[4];
        try {
            List<Integer> vals = new ArrayList<>();
            for (String p : parts) {
                if (p.equals("fill") || p.isEmpty()) continue;
                int v = Size.parse(p);
                vals.add(v == -1 ? 0 : v);
            }

            if (vals.size() == 1) { // all
                int v = vals.get(0);
                return new int[]{v, v, v, v};
            } else if (vals.size() == 2) { // top-bottom, left-right
                int tb = vals.get(0), lr = vals.get(1);
                return new int[]{tb, lr, tb, lr};
            } else if (vals.size() == 3) { // top, left-right, bottom
                int t = vals.get(0), lr = vals.get(1), b = vals.get(2);
                return new int[]{t, lr, b, lr};
            } else if (vals.size() >= 4) { // top, right, bottom, left
                return new int[]{vals.get(0), vals.get(1), vals.get(2), vals.get(3)};
            }
        } catch (Exception e) {
            return new int[]{0, 0, 0, 0};
        }
        return res;
    }

    public float[] getCalculatedRadii(float w, float h, float offset) {
        float tl = Math.max(0, borderRadius.get(0) - offset);
        float tr = Math.max(0, borderRadius.get(1) - offset);
        float br = Math.max(0, borderRadius.get(2) - offset);
        float bl = Math.max(0, borderRadius.get(3) - offset);

        // CSS 规范：如果两个半径之和超过边长，需要按比例缩小
        float scale = 1.0f;
        scale = Math.min(scale, w / (tl + tr));
        scale = Math.min(scale, h / (tr + br));
        scale = Math.min(scale, w / (br + bl));
        scale = Math.min(scale, h / (bl + tl));

        // 防止除以0或负数
        if (scale < 0) scale = 0;

        return new float[]{tl * scale, tr * scale, br * scale, bl * scale};
    }

    public static class BorderImage {
        public String source = null;
        public int[] slice = new int[]{0, 0, 0, 0};  // 上, 右, 下, 左
        public int[] width = new int[]{0, 0, 0, 0};
        public int[] outset = new int[]{0, 0, 0, 0};
        public String repeat = "stretch"; // stretch, repeat, round
        public boolean fill = false;      // 是否保留中间部分
        public Gradient gradient = null;

        public boolean isEmpty() {
            return source == null || source.equals("none");
        }
    }
}
