package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

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
    public void applyMargin(String side, String value) {
        margin.put(side, (double) Size.parse(value));
    }
    public void applyMarginAll(String value) {
        SIDE.forEach(side -> applyMargin(side, value));
    }
    public void applyPadding(String side, String value) {
        padding.put(side, (double) Size.parse(value));
    }
    public void applyPaddingAll(String value) {
        SIDE.forEach(side -> applyPadding(side, value));
    }

    private static boolean valid(String s) {
        return !s.equals("unset");
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

        if (valid(style.margin)) resultBox.applyMarginAll(style.margin);
        if (valid(style.marginTop)) resultBox.applyMargin("top", style.marginTop);
        if (valid(style.marginBottom)) resultBox.applyMargin("bottom", style.marginBottom);
        if (valid(style.marginLeft)) resultBox.applyMargin("left", style.marginLeft);
        if (valid(style.marginRight)) resultBox.applyMargin("right", style.marginRight);

        if (valid(style.padding)) resultBox.applyPaddingAll(style.padding);
        if (valid(style.paddingTop)) resultBox.applyPadding("top", style.paddingTop);
        if (valid(style.paddingBottom)) resultBox.applyPadding("bottom", style.paddingBottom);
        if (valid(style.paddingLeft)) resultBox.applyPadding("left", style.paddingLeft);
        if (valid(style.paddingRight)) resultBox.applyPadding("right", style.paddingRight);

        String radiusStr = style.borderRadius;
        if (!radiusStr.equals("unset")) {
            String[] parts = radiusStr.trim().split("\\s+");
            List<Integer> parsed = new ArrayList<>();
            for (String p : parts) {
                int val = Size.parse(p);
                parsed.add(val == -1 ? 0 : val);
            }

            // 1值: [r, r, r, r]
            // 2值: [TL, TR] -> [TL, TR, TL, TR] (对角)
            // 3值: [TL, TR, BR] -> [TL, TR, BR, TR]
            // 4值: [TL, TR, BR, BL]
            if (parsed.size() == 1) {
                int r = parsed.get(0);
                resultBox.borderRadius.addAll(List.of(r, r, r, r));
            } else if (parsed.size() == 2) {
                resultBox.borderRadius.addAll(List.of(parsed.get(0), parsed.get(1), parsed.get(0), parsed.get(1)));
            } else if (parsed.size() == 3) {
                resultBox.borderRadius.addAll(List.of(parsed.get(0), parsed.get(1), parsed.get(2), parsed.get(1)));
            } else if (parsed.size() >= 4) {
                resultBox.borderRadius.addAll(parsed.subList(0, 4));
            }
        } else {
            resultBox.borderRadius.addAll(List.of(0, 0, 0, 0));
        }

        resultBox.element = element;
        resultBox.shadow = parseShadow(style.boxShadow);
        resultBox.borderImage = parseBorderImage(style);
        if (resultBox.borderImage != null && isZero(resultBox.borderImage.width)) {
            resultBox.borderImage.width = new int[] {
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
        if (string == null || string.isBlank()) return SideBorder.getDefault();
        String[] res = string.trim().split("\\s+");
        if (res.length != 3) return SideBorder.getDefault();
        return new SideBorder(Size.parse(res[0]), res[1], new Color(res[2]));
    }
    public static Shadow parseShadow(String string) {
        String[] res = string.split(" ");
        if (res.length != 4) return Shadow.getDefault();
        int x = Size.parse(res[0]);
        if (res[0].contains("-")) x *= -1;
        int y = Size.parse(res[1]);
        if (res[1].contains("-")) y *= -1;
        return new Shadow(x, y, Size.parse(res[2]), new Color(res[3]));
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

    public record Shadow(int x, int y, int size, Color color) {
        public static Shadow getDefault() {
            return new Shadow(0, 0, 0, Color.BLACK);
        }

        @Override
        public String toString() {
            return x + "px " + y + "px " + size + "px " + color.toHexString();
        }
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

        return new float[] { tl * scale, tr * scale, br * scale, bl * scale };
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
