package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Box {
    public static final List<String> SIDE = List.of("top", "bottom", "left", "right");
    public static final String BOX_SIZING_CONTENT_BOX = "content-box";
    public static final String BOX_SIZING_BORDER_BOX = "border-box";
    private SideBorder borderTop = SideBorder.getDefault();
    private SideBorder borderRight = SideBorder.getDefault();
    private SideBorder borderBottom = SideBorder.getDefault();
    private SideBorder borderLeft = SideBorder.getDefault();
    private double marginTop = 0d;
    private double marginRight = 0d;
    private double marginBottom = 0d;
    private double marginLeft = 0d;
    private boolean autoMarginTop = false;
    private boolean autoMarginRight = false;
    private boolean autoMarginBottom = false;
    private boolean autoMarginLeft = false;
    private double paddingTop = 0d;
    private double paddingRight = 0d;
    private double paddingBottom = 0d;
    private double paddingLeft = 0d;
    public final ArrayList<Integer> borderRadius = new ArrayList<>();
    public final List<Shadow> shadows = new ArrayList<>();
    public Shadow shadow = null;
    public BorderImage borderImage = null;
    public Element element;
    private Size cachedRawElementSize;
    private Size cachedRawInnerSize;
    private Size cachedInnerRawSize;
    private Size cachedInnerSize;
    private Size cachedSizeElementSize;
    private Size cachedSize;
    private double cachedSizeMarginHorizontal = Double.NaN;
    private double cachedSizeMarginVertical = Double.NaN;
    private double cachedRawBorderHorizontal = Double.NaN;
    private double cachedRawBorderVertical = Double.NaN;
    private double cachedRawPaddingHorizontal = Double.NaN;
    private double cachedRawPaddingVertical = Double.NaN;
    private double cachedInnerVerticalGutter = Double.NaN;
    private double cachedInnerHorizontalGutter = Double.NaN;

    public Box() {
    }

    public void applyBorder(String side, String value) {
        SideBorder sideBorder = parseSideBorder(value);
        setBorder(side, sideBorder);
    }

    public void applyBorderAll(String value) {
        SIDE.forEach(side -> applyBorder(side, value));
    }

    public void applyMargin(String side, String value) {
        boolean auto = isAuto(value);
        setAutoMargin(side, auto);
        setMargin(side, auto ? 0d : resolveBoxLength(value));
    }

    public void applyMarginAll(String value) {
        BoxLength[] values = parseFourSideBoxLengths(value);
        applyParsedMargin("top", values[0]);
        applyParsedMargin("right", values[1]);
        applyParsedMargin("bottom", values[2]);
        applyParsedMargin("left", values[3]);
    }

    public void applyPadding(String side, String value) {
        setPadding(side, resolveBoxLength(value));
    }

    public void applyPaddingAll(String value) {
        double[] values = parseFourSideLengths(value);
        paddingTop = values[0];
        paddingRight = values[1];
        paddingBottom = values[2];
        paddingLeft = values[3];
    }

    private static boolean valid(String s) {
        return !s.equals("unset");
    }

    private static boolean isAuto(String value) {
        return value != null && "auto".equalsIgnoreCase(value.trim());
    }

    private void applyParsedMargin(String side, BoxLength value) {
        if (value == null) value = BoxLength.zero();
        setAutoMargin(side, value.auto());
        setMargin(side, value.length());
    }

    public static Box of(Element element) {
        Box cache = element.getRenderer().box.get();
        if (cache != null) return cache;

        Box resultBox = new Box();
        resultBox.element = element;
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

        resultBox.shadows.clear();
        resultBox.shadows.addAll(parseShadowList(style.boxShadow));
        resultBox.shadow = resultBox.shadows.isEmpty() ? Shadow.getDefault() : resultBox.shadows.get(0);
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
        double marginHorizontal = getMarginHorizontal();
        double marginVertical = getMarginVertical();
        if (cachedSize != null
                && cachedSizeElementSize == elementSize
                && Double.compare(cachedSizeMarginHorizontal, marginHorizontal) == 0
                && Double.compare(cachedSizeMarginVertical, marginVertical) == 0) {
            return cachedSize;
        }
        double resultWidth = elementSize.width() + marginHorizontal;
        double resultHeight = elementSize.height() + marginVertical;
        cachedSizeElementSize = elementSize;
        cachedSizeMarginHorizontal = marginHorizontal;
        cachedSizeMarginVertical = marginVertical;
        cachedSize = new Size(resultWidth, resultHeight);
        return cachedSize;
    }

    public Size innerSize() {
        Size raw = rawInnerSize();
        double verticalGutter = element.getVerticalScrollbarGutter();
        double horizontalGutter = element.getHorizontalScrollbarGutter();
        if (cachedInnerSize != null
                && cachedInnerRawSize == raw
                && Double.compare(cachedInnerVerticalGutter, verticalGutter) == 0
                && Double.compare(cachedInnerHorizontalGutter, horizontalGutter) == 0) {
            return cachedInnerSize;
        }
        double resultWidth = Math.max(0, raw.width() - verticalGutter);
        double resultHeight = Math.max(0, raw.height() - horizontalGutter);
        cachedInnerRawSize = raw;
        cachedInnerVerticalGutter = verticalGutter;
        cachedInnerHorizontalGutter = horizontalGutter;
        cachedInnerSize = new Size(resultWidth, resultHeight);
        return cachedInnerSize;
    }

    /** Content-box size before classic scrollbars consume their gutter. */
    public Size rawInnerSize() {
        Size elementSize = Size.of(element);
        double borderHorizontal = getBorderHorizontal();
        double borderVertical = getBorderVertical();
        double paddingHorizontal = getPaddingHorizontal();
        double paddingVertical = getPaddingVertical();
        if (cachedRawInnerSize != null
                && cachedRawElementSize.equals(elementSize)
                && Double.compare(cachedRawBorderHorizontal, borderHorizontal) == 0
                && Double.compare(cachedRawBorderVertical, borderVertical) == 0
                && Double.compare(cachedRawPaddingHorizontal, paddingHorizontal) == 0
                && Double.compare(cachedRawPaddingVertical, paddingVertical) == 0) {
            return cachedRawInnerSize;
        }
        double resultWidth = elementSize.width() - borderHorizontal - paddingHorizontal;
        double resultHeight = elementSize.height() - borderVertical - paddingVertical;
        cachedRawElementSize = elementSize;
        cachedRawBorderHorizontal = borderHorizontal;
        cachedRawBorderVertical = borderVertical;
        cachedRawPaddingHorizontal = paddingHorizontal;
        cachedRawPaddingVertical = paddingVertical;
        cachedRawInnerSize = new Size(resultWidth, resultHeight);
        cachedInnerRawSize = null;
        cachedInnerSize = null;
        return cachedRawInnerSize;
    }

    public Size elementSize() {
        return Size.of(element);
    }

    public String getBoxSizing() {
        if (element == null) return BOX_SIZING_CONTENT_BOX;
        return normalizeBoxSizing(element.getComputedStyle().boxSizing);
    }

    public boolean isBorderBox() {
        return BOX_SIZING_BORDER_BOX.equals(getBoxSizing());
    }

    public static String normalizeBoxSizing(String raw) {
        if (raw == null) return BOX_SIZING_CONTENT_BOX;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (BOX_SIZING_BORDER_BOX.equals(value)) return BOX_SIZING_BORDER_BOX;
        return BOX_SIZING_CONTENT_BOX;
    }

    private double resolveBoxLength(String value) {
        if (element == null) return Math.max(0, Size.resolveLength(value, 0, 0));
        double basis = Size.isPercent(value) ? Size.getScaleWidth(element) : 0;
        return Math.max(0, Size.resolveLength(value, basis, 0));
    }

    private double[] parseFourSideLengths(String raw) {
        if (raw == null || raw.isBlank() || "unset".equals(raw)) {
            return new double[]{0, 0, 0, 0};
        }

        String[] parts = raw.trim().split("\\s+");
        double[] parsed = new double[Math.min(parts.length, 4)];
        for (int i = 0; i < parsed.length; i++) {
            parsed[i] = resolveBoxLength(parts[i]);
        }

        return switch (parsed.length) {
            case 1 -> new double[]{parsed[0], parsed[0], parsed[0], parsed[0]};
            case 2 -> new double[]{parsed[0], parsed[1], parsed[0], parsed[1]};
            case 3 -> new double[]{parsed[0], parsed[1], parsed[2], parsed[1]};
            default -> new double[]{parsed[0], parsed[1], parsed[2], parsed[3]};
        };
    }

    private BoxLength[] parseFourSideBoxLengths(String raw) {
        if (raw == null || raw.isBlank() || "unset".equals(raw)) {
            return new BoxLength[]{BoxLength.zero(), BoxLength.zero(), BoxLength.zero(), BoxLength.zero()};
        }

        String[] parts = raw.trim().split("\\s+");
        BoxLength[] parsed = new BoxLength[Math.min(parts.length, 4)];
        for (int i = 0; i < parsed.length; i++) {
            parsed[i] = isAuto(parts[i]) ? BoxLength.autoValue() : new BoxLength(resolveBoxLength(parts[i]), false);
        }

        return switch (parsed.length) {
            case 1 -> new BoxLength[]{parsed[0], parsed[0], parsed[0], parsed[0]};
            case 2 -> new BoxLength[]{parsed[0], parsed[1], parsed[0], parsed[1]};
            case 3 -> new BoxLength[]{parsed[0], parsed[1], parsed[2], parsed[1]};
            default -> new BoxLength[]{parsed[0], parsed[1], parsed[2], parsed[3]};
        };
    }

    public double offset(String side) {
        return getBorder(side).size + getMargin(side) + getPadding(side);
    }

    public double getMarginHorizontal() {
        return getMarginLeft() + getMarginRight();
    }

    public double getMarginVertical() {
        return getMarginTop() + getMarginBottom();
    }

    public double getMarginLeft() {
        return marginLeft;
    }

    public double getMarginTop() {
        return marginTop;
    }

    public double getMarginRight() {
        return marginRight;
    }

    public double getMarginBottom() {
        return marginBottom;
    }

    public boolean isMarginAuto(String side) {
        return switch (normalizeSide(side)) {
            case "top" -> autoMarginTop;
            case "right" -> autoMarginRight;
            case "bottom" -> autoMarginBottom;
            case "left" -> autoMarginLeft;
            default -> false;
        };
    }

    public double getBorderHorizontal() {
        return getBorderLeft() + getBorderRight();
    }

    public double getBorderVertical() {
        return getBorderTop() + getBorderBottom();
    }

    public double getBorderLeft() {
        return borderLeft.size;
    }

    public double getBorderRight() {
        return borderRight.size;
    }

    public double getBorderTop() {
        return borderTop.size;
    }

    public double getBorderBottom() {
        return borderBottom.size;
    }

    public double getPaddingHorizontal() {
        return getPaddingLeft() + getPaddingRight();
    }

    public double getPaddingVertical() {
        return getPaddingTop() + getPaddingBottom();
    }

    public double getPaddingLeft() {
        return paddingLeft;
    }

    public double getPaddingRight() {
        return paddingRight;
    }

    public double getPaddingTop() {
        return paddingTop;
    }

    public double getPaddingBottom() {
        return paddingBottom;
    }

    public SideBorder getBorderSide(String side) {
        return getBorder(side);
    }

    public SideBorder getBorderTopSide() {
        return borderTop;
    }

    public SideBorder getBorderRightSide() {
        return borderRight;
    }

    public SideBorder getBorderBottomSide() {
        return borderBottom;
    }

    public SideBorder getBorderLeftSide() {
        return borderLeft;
    }

    private SideBorder getBorder(String side) {
        return switch (normalizeSide(side)) {
            case "top" -> borderTop;
            case "right" -> borderRight;
            case "bottom" -> borderBottom;
            case "left" -> borderLeft;
            default -> SideBorder.getDefault();
        };
    }

    private double getMargin(String side) {
        return switch (normalizeSide(side)) {
            case "top" -> marginTop;
            case "right" -> marginRight;
            case "bottom" -> marginBottom;
            case "left" -> marginLeft;
            default -> 0d;
        };
    }

    private double getPadding(String side) {
        return switch (normalizeSide(side)) {
            case "top" -> paddingTop;
            case "right" -> paddingRight;
            case "bottom" -> paddingBottom;
            case "left" -> paddingLeft;
            default -> 0d;
        };
    }

    private void setBorder(String side, SideBorder value) {
        SideBorder border = value == null ? SideBorder.getDefault() : value;
        switch (normalizeSide(side)) {
            case "top" -> borderTop = border;
            case "right" -> borderRight = border;
            case "bottom" -> borderBottom = border;
            case "left" -> borderLeft = border;
        }
    }

    private void setMargin(String side, double value) {
        switch (normalizeSide(side)) {
            case "top" -> marginTop = value;
            case "right" -> marginRight = value;
            case "bottom" -> marginBottom = value;
            case "left" -> marginLeft = value;
        }
        cachedSize = null;
        cachedSizeElementSize = null;
    }

    private void setAutoMargin(String side, boolean value) {
        switch (normalizeSide(side)) {
            case "top" -> autoMarginTop = value;
            case "right" -> autoMarginRight = value;
            case "bottom" -> autoMarginBottom = value;
            case "left" -> autoMarginLeft = value;
        }
    }

    private void setPadding(String side, double value) {
        switch (normalizeSide(side)) {
            case "top" -> paddingTop = value;
            case "right" -> paddingRight = value;
            case "bottom" -> paddingBottom = value;
            case "left" -> paddingLeft = value;
        }
    }

    private static String normalizeSide(String side) {
        return side == null ? "" : side.trim().toLowerCase(Locale.ROOT);
    }


    public static SideBorder parseSideBorder(String string) {
        String[] res = splitWhitespace(string, 3);
        if (res.length != 3) return SideBorder.getDefault();
        Double width = Size.parseNumber(res[0]);
        if (width == null) return SideBorder.getDefault();
        return new SideBorder(Math.max(0, width), res[1], new Color(res[2]));
    }

    public static Shadow parseShadow(String string) {
        List<Shadow> parsed = parseShadowList(string);
        return parsed.isEmpty() ? Shadow.getDefault() : parsed.get(0);
    }

    public static List<Shadow> parseShadowList(String string) {
        List<Shadow> result = new ArrayList<>();
        if (string == null || string.isBlank() || "unset".equals(string) || "none".equals(string)) {
            return result;
        }

        for (String shadowToken : Background.splitTopLevelComma(string)) {
            String[] res = splitWhitespace(shadowToken, 8);
            if (res.length < 2) continue;

            boolean inset = false;
            int valueStart = 0;
            if ("inset".equalsIgnoreCase(res[0])) {
                inset = true;
                valueStart = 1;
            } else if (res.length > 2 && "inset".equalsIgnoreCase(res[res.length - 1])) {
                inset = true;
            }
            if (res.length <= valueStart + 1) continue;
            Double x = Size.parseNumber(res[valueStart]);
            Double y = Size.parseNumber(res[valueStart + 1]);
            if (x == null || y == null) continue;

            double blur = 0;
            double spread = 0;
            int colorIndex = valueStart + 2;
            if (res.length > colorIndex) {
                Double parsedBlur = Size.parseNumber(res[colorIndex]);
                if (parsedBlur != null) {
                    blur = Math.max(0, parsedBlur);
                    colorIndex++;
                }
            }
            if (res.length > colorIndex) {
                Double parsedSpread = Size.parseNumber(res[colorIndex]);
                if (parsedSpread != null) {
                    spread = parsedSpread;
                    colorIndex++;
                }
            }
            String color = res.length > colorIndex ? res[colorIndex] : "#000";
            result.add(new Shadow(x, y, blur, spread, new Color(color), inset));
        }
        return result;
    }

    private static String[] splitWhitespace(String value, int maxTokens) {
        if (value == null || value.isBlank() || maxTokens <= 0) return new String[0];
        ArrayList<String> tokens = new ArrayList<>(Math.min(4, maxTokens));
        int index = 0;
        int depth = 0;
        while (index < value.length() && tokens.size() < maxTokens) {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
            if (index >= value.length()) break;
            int start = index;
            while (index < value.length()) {
                char current = value.charAt(index);
                if (current == '(') depth++;
                else if (current == ')' && depth > 0) depth--;
                else if (Character.isWhitespace(current) && depth == 0) break;
                index++;
            }
            tokens.add(value.substring(start, index));
        }
        return tokens.toArray(String[]::new);
    }

    public record SideBorder(double size, String type, Color color) {
        private static final SideBorder DEFAULT = new SideBorder(0, "solid", Color.BLACK);

        public static SideBorder getDefault() {
            return DEFAULT;
        }

        @Override
        public String toString() {
            return size + "px " + type + " " + color.toHexString();
        }
    }

    public record Shadow(double x, double y, double size, double spread, Color color, boolean inset) {
        private static final Shadow DEFAULT = new Shadow(0, 0, 0, 0, Color.BLACK, false);

        public static Shadow getDefault() {
            return DEFAULT;
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

        return new float[]{tl * scale, tr * scale, br * scale, bl * scale};
    }

    public static boolean matchStyleName(String name) {
        return Set.of("margin", "padding", "border-width").contains(name);
    }

    public static void createShadowTransition(Style startStyle, Style endStyle, List<Transition> result,
                                              double duration, double delay) {
        List<Shadow> start = parseShadowList(startStyle.boxShadow);
        List<Shadow> end = parseShadowList(endStyle.boxShadow);
        int count = Math.max(start.size(), end.size());
        for (int i = 0; i < count; i++) {
            Shadow startShadow = i < start.size() ? start.get(i) : transparentShadow(end.get(i));
            Shadow endShadow = i < end.size() ? end.get(i) : transparentShadow(start.get(i));
            addShadowTransition(result, i, "x", startShadow.x(), endShadow.x(), duration, delay);
            addShadowTransition(result, i, "y", startShadow.y(), endShadow.y(), duration, delay);
            addShadowTransition(result, i, "blur", startShadow.size(), endShadow.size(), duration, delay);
            addShadowTransition(result, i, "spread", startShadow.spread(), endShadow.spread(), duration, delay);
            addShadowTransition(result, i, "color", startShadow.color().getValue(), endShadow.color().getValue(), duration, delay);
        }
    }

    public static void interpolateShadow(List<Transition.Change> changes, String startValue, String endValue,
                                         double progress) {
        List<Shadow> start = parseShadowList(startValue);
        List<Shadow> end = parseShadowList(endValue);
        int count = Math.max(start.size(), end.size());
        for (int i = 0; i < count; i++) {
            Shadow startShadow = i < start.size() ? start.get(i) : transparentShadow(end.get(i));
            Shadow endShadow = i < end.size() ? end.get(i) : transparentShadow(start.get(i));
            Transition.addChange(changes, shadowTransitionName(i, "x"),
                    Transition.getOffset("x", startShadow.x(), endShadow.x(), progress));
            Transition.addChange(changes, shadowTransitionName(i, "y"),
                    Transition.getOffset("y", startShadow.y(), endShadow.y(), progress));
            Transition.addChange(changes, shadowTransitionName(i, "blur"),
                    Transition.getOffset("blur", startShadow.size(), endShadow.size(), progress));
            Transition.addChange(changes, shadowTransitionName(i, "spread"),
                    Transition.getOffset("spread", startShadow.spread(), endShadow.spread(), progress));
            Transition.addChange(changes, shadowTransitionName(i, "color"),
                    Transition.getOffset("color", startShadow.color().getValue(), endShadow.color().getValue(), progress));
        }
    }

    public static void readShadowTransition(List<Transition.Change> changes, Style style) {
        if (changes == null || changes.isEmpty() || style == null) return;
        Map<Integer, ShadowComponents> animated = new HashMap<>();
        for (Iterator<Transition.Change> iterator = changes.iterator(); iterator.hasNext(); ) {
            Transition.Change change = iterator.next();
            ShadowComponent component = parseShadowComponent(change.name());
            if (component == null) continue;
            animated.computeIfAbsent(component.index(), ignored -> new ShadowComponents())
                    .set(component.name(), change.value());
            iterator.remove();
        }
        if (animated.isEmpty()) return;

        List<Shadow> target = new ArrayList<>(parseShadowList(style.boxShadow));
        int count = Math.max(target.size(), animated.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1);
        while (target.size() < count) target.add(new Shadow(0, 0, 0, 0, new Color(0), false));
        for (Map.Entry<Integer, ShadowComponents> entry : animated.entrySet()) {
            int index = entry.getKey();
            Shadow base = target.get(index);
            target.set(index, entry.getValue().apply(base));
        }
        style.boxShadow = serializeShadows(target);
    }

    private static void addShadowTransition(List<Transition> result, int index, String component,
                                            double start, double end, double duration, double delay) {
        if (Math.abs(start - end) <= 0.0001) return;
        result.add(new Transition(shadowTransitionName(index, component), start, end,
                duration, delay, System.currentTimeMillis()));
    }

    private static String shadowTransitionName(int index, String component) {
        return "box-shadow-" + index + "-" + component;
    }

    private static Shadow transparentShadow(Shadow reference) {
        int transparentColor = reference == null ? 0 : reference.color().getValue() & 0x00FFFFFF;
        return new Shadow(0, 0, 0, 0, new Color(transparentColor), reference != null && reference.inset());
    }

    private static ShadowComponent parseShadowComponent(String name) {
        String prefix = "box-shadow-";
        if (name == null || !name.startsWith(prefix)) return null;
        int separator = name.indexOf('-', prefix.length());
        if (separator < 0) return null;
        try {
            int index = Integer.parseInt(name.substring(prefix.length(), separator));
            String component = name.substring(separator + 1);
            if (!Set.of("x", "y", "blur", "spread", "color").contains(component)) return null;
            return new ShadowComponent(index, component);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String serializeShadows(List<Shadow> shadows) {
        if (shadows == null || shadows.isEmpty()) return "none";
        ArrayList<String> values = new ArrayList<>(shadows.size());
        for (Shadow shadow : shadows) {
            Color color = shadow.color();
            values.add(String.format(Locale.ROOT,
                    "%s%.3fpx %.3fpx %.3fpx %.3fpx rgba(%d,%d,%d,%.3f)",
                    shadow.inset() ? "inset " : "",
                    shadow.x(), shadow.y(), shadow.size(), shadow.spread(),
                    color.getR(), color.getG(), color.getB(), color.getA() / 255.0));
        }
        return String.join(", ", values);
    }

    private record ShadowComponent(int index, String name) {
    }

    private static final class ShadowComponents {
        private Double x;
        private Double y;
        private Double blur;
        private Double spread;
        private Double color;

        private void set(String name, double value) {
            switch (name) {
                case "x" -> x = value;
                case "y" -> y = value;
                case "blur" -> blur = value;
                case "spread" -> spread = value;
                case "color" -> color = value;
            }
        }

        private Shadow apply(Shadow base) {
            return new Shadow(
                    x == null ? base.x() : x,
                    y == null ? base.y() : y,
                    Math.max(0, blur == null ? base.size() : blur),
                    spread == null ? base.spread() : spread,
                    color == null ? base.color() : new Color(color.intValue()),
                    base.inset()
            );
        }
    }

    public static void createTransition(Style sS, Style eS, List<Transition> res, String name, double dur, double del) {
        String[] sides = {"-top", "-right", "-bottom", "-left"};
        for (String side : sides) {
            String subProp = name + side;
            // 如果是 border-width，子属性是 border-top-width
            if (name.equals("border-width")) subProp = "border" + side + "-width";

            double s = Transition.parseStyle(subProp, sS.get(subProp));
            double e = Transition.parseStyle(subProp, eS.get(subProp));
            if (Math.abs(s - e) > 0.0001) {
                res.add(new Transition(subProp, s, e, dur, del, System.currentTimeMillis()));
            }
        }
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

    private record BoxLength(double length, boolean auto) {
        private static BoxLength zero() {
            return new BoxLength(0, false);
        }

        private static BoxLength autoValue() {
            return new BoxLength(0, true);
        }
    }
}
