package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.resource.Font;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.viewport.ApricityViewport;

public record Size(double width, double height) {
    public static final double DEFAULT_LINE_HEIGHT = 16;
    public static final Size ZERO = new Size(0, 0);
    private static final ThreadLocal<Set<Element>> RESOLVING = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Integer> NATURAL_MEASURE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Map<Element, Double>> NATURAL_CONTENT_WIDTHS = ThreadLocal.withInitial(
            java.util.IdentityHashMap::new
    );
    private static final int NUMBER_CACHE_LIMIT = 4096;
    private static final Map<String, Double> NUMBER_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
            return size() > NUMBER_CACHE_LIMIT;
        }
    });
    private static volatile Size viewportOverride;
    private static volatile Double rootFontOverride;

    public Size add(Size size) {
        return new Size(width + size.width, height + size.height);
    }

    public static Size getWindowSize() {
        Size override = viewportOverride;
        if (override != null) return override;
        Document context = Document.getContextDocument();
        if (context != null && context.isActive()) {
            com.sighs.apricityui.viewport.ApricityViewport viewport = context.getViewport();
            return new Size(viewport.layoutWidth(), viewport.layoutHeight());
        }
        String widthOverride = System.getProperty("aui.test.viewport.width");
        String heightOverride = System.getProperty("aui.test.viewport.height");
        if (widthOverride != null || heightOverride != null) {
            Double parsedWidth = parseNumber(widthOverride);
            Double parsedHeight = parseNumber(heightOverride);
            double width = parsedWidth == null ? 1920 : parsedWidth;
            double height = parsedHeight == null ? 1080 : parsedHeight;
            return new Size(width, height);
        }
        try {
            return AuiServices.client().getWindowSize();
        } catch (NoClassDefFoundError | Exception ignored) {
            return new Size(1920, 1080);
        }
    }

    /**
     * Returns the deterministic viewport used when no Minecraft client window exists.
     * Unlike {@link #getWindowSize()}, this deliberately ignores the active document
     * context so one headless document cannot leak its viewport into another test.
     */
    public static Size getHeadlessWindowSize() {
        Size override = viewportOverride;
        if (override != null) return override;

        String widthOverride = System.getProperty("aui.test.viewport.width");
        String heightOverride = System.getProperty("aui.test.viewport.height");
        if (widthOverride != null || heightOverride != null) {
            Double parsedWidth = parseNumber(widthOverride);
            Double parsedHeight = parseNumber(heightOverride);
            return new Size(parsedWidth == null ? 1920 : parsedWidth,
                    parsedHeight == null ? 1080 : parsedHeight);
        }
        return new Size(1920, 1080);
    }

    public static double getWindowWidth() {
        Size override = viewportOverride;
        if (override != null) {
            return override.width;
        }
        Document context = Document.getContextDocument();
        if (context != null && context.isActive()) {
            return context.getViewport().layoutWidth();
        }
        String widthOverride = System.getProperty("aui.test.viewport.width");
        if (widthOverride != null) {
            Double parsedWidth = parseNumber(widthOverride);
            return parsedWidth == null ? 1920 : parsedWidth;
        }
        try {
            return AuiServices.client().getWindowSize().width();
        } catch (NoClassDefFoundError | Exception ignored) {
            return 1920;
        }
    }

    public static double getWindowHeight() {
        Size override = viewportOverride;
        if (override != null) {
            return override.height;
        }
        Document context = Document.getContextDocument();
        if (context != null && context.isActive()) {
            return context.getViewport().layoutHeight();
        }
        String heightOverride = System.getProperty("aui.test.viewport.height");
        if (heightOverride != null) {
            Double parsedHeight = parseNumber(heightOverride);
            return parsedHeight == null ? 1080 : parsedHeight;
        }
        try {
            return AuiServices.client().getWindowSize().height();
        } catch (NoClassDefFoundError | Exception ignored) {
            return 1080;
        }
    }

    public static void setViewportOverride(double width, double height) {
        viewportOverride = new Size(Math.max(0, width), Math.max(0, height));
    }

    public static void clearViewportOverride() {
        viewportOverride = null;
    }

    public static void setRootFontOverride(Double rootFontSize) {
        if (rootFontSize == null || rootFontSize <= 0) {
            rootFontOverride = null;
            return;
        }
        rootFontOverride = rootFontSize;
    }

    public static void clearRootFontOverride() {
        rootFontOverride = null;
    }

    public static int parse(String str) {
        if (str == null || str.isBlank()) return -1;
        Double number = parseNumber(str);
        if (number == null) return -1;
        return (int) Math.round(number);
    }

    public static Double parseNumber(String str) {
        if (str == null) return null;
        Double cached = NUMBER_CACHE.get(str);
        if (cached != null) return cached;
        int len = str.length();
        int i = 0;
        while (i < len && Character.isWhitespace(str.charAt(i))) i++;
        if (i >= len) return null;

        int start = i;
        char first = str.charAt(i);
        if (first == '+' || first == '-') i++;

        boolean hasDigit = false;
        boolean hasDot = false;
        while (i < len) {
            char c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                i++;
                continue;
            }
            if (c == '.' && !hasDot) {
                hasDot = true;
                i++;
                continue;
            }
            break;
        }
        if (!hasDigit) return null;

        try {
            Double parsed = Double.parseDouble(str.substring(start, i));
            NUMBER_CACHE.put(str, parsed);
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean isPercent(String value) {
        if (value == null) return false;
        return value.trim().endsWith("%");
    }

    public static double resolveLength(String value, double percentBasis, double fallback) {
        if (value == null || value.isBlank() || value.equals("unset")) return fallback;
        Double resolved = tryResolveLength(value, percentBasis);
        return resolved == null ? fallback : resolved;
    }

    public static Double tryResolveLength(String value, double percentBasis) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "unset".equalsIgnoreCase(trimmed) || "auto".equalsIgnoreCase(trimmed)) return null;
        if (isMathFunction(trimmed)) return resolveMathFunction(trimmed, percentBasis, getRootFontSize());
        if (trimmed.regionMatches(true, 0, "calc(", 0, 5) && trimmed.endsWith(")")) {
            return resolveCalc(trimmed.substring(5, trimmed.length() - 1), percentBasis, getRootFontSize());
        }
        return resolveSingleLength(trimmed, percentBasis, getRootFontSize());
    }

    public static Double tryResolveLength(String value, double percentBasis, double emBasis) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "unset".equalsIgnoreCase(trimmed) || "auto".equalsIgnoreCase(trimmed)) return null;
        if (isMathFunction(trimmed)) return resolveMathFunction(trimmed, percentBasis, emBasis);
        if (trimmed.regionMatches(true, 0, "calc(", 0, 5) && trimmed.endsWith(")")) {
            return resolveCalc(trimmed.substring(5, trimmed.length() - 1), percentBasis, emBasis);
        }
        return resolveSingleLength(trimmed, percentBasis, emBasis);
    }

    public static Size of(Element element) {
        Size cache = element.getRenderer().size.get();
        if (cache != null) return cache;

        Set<Element> resolving = RESOLVING.get();
        boolean firstVisit = resolving.add(element);
        try {
            return computeSize(element, firstVisit);
        } finally {
            if (firstVisit) {
                resolving.remove(element);
                if (resolving.isEmpty()) {
                    RESOLVING.remove();
                }
            }
        }
    }

    public static Size natural(Element element) {
        Double contextWidth = getNaturalMeasurementWidthContext(element);
        return measureNatural(element, LayoutMeasureCache.SIZE_NATURAL,
                contextWidth == null ? Double.NaN : contextWidth);
    }

    public static Size naturalAtContentWidth(Element element, double contentWidth) {
        if (element == null) return ZERO;
        double constrainedWidth = Math.max(0, contentWidth);
        Map<Element, Double> constraints = NATURAL_CONTENT_WIDTHS.get();
        boolean hadPrevious = constraints.containsKey(element);
        Double previous = constraints.put(element, constrainedWidth);
        try {
            return measureNatural(element, LayoutMeasureCache.SIZE_NATURAL_CONSTRAINED, constrainedWidth);
        } finally {
            if (hadPrevious) constraints.put(element, previous);
            else constraints.remove(element);
            if (constraints.isEmpty()) NATURAL_CONTENT_WIDTHS.remove();
        }
    }

    private static Size measureNatural(Element element, int cacheMode, double availableWidth) {
        if (element == null) return ZERO;
        Size cached = LayoutMeasureCache.getSize(cacheMode, element, availableWidth, Double.NaN, true);
        if (cached != null) return cached;
        int depth = NATURAL_MEASURE_DEPTH.get();
        NATURAL_MEASURE_DEPTH.set(depth + 1);
        try {
            Size result = computeSize(element, false);
            LayoutMeasureCache.putSize(cacheMode, element, availableWidth, Double.NaN, true, result);
            return result;
        } finally {
            int next = NATURAL_MEASURE_DEPTH.get() - 1;
            if (next <= 0) {
                NATURAL_MEASURE_DEPTH.remove();
            } else {
                NATURAL_MEASURE_DEPTH.set(next);
            }
        }
    }

    public static boolean isNaturalMeasurementContext() {
        return NATURAL_MEASURE_DEPTH.get() > 0;
    }

    public static boolean hasNaturalWidthConstraint(Element element) {
        if (element == null) return false;
        Map<Element, Double> constraints = NATURAL_CONTENT_WIDTHS.get();
        Element current = element;
        while (current != null) {
            if (constraints.containsKey(current)) return true;
            current = current.parentElement;
        }
        return false;
    }

    public static Double getNaturalContentWidthConstraint(Element element) {
        if (element == null) return null;
        return NATURAL_CONTENT_WIDTHS.get().get(element);
    }

    static Double getNaturalMeasurementWidthContext(Element element) {
        Map<Element, Double> constraints = NATURAL_CONTENT_WIDTHS.get();
        Element current = element;
        while (current != null) {
            Double width = constraints.get(current);
            if (width != null) return width;
            current = current.parentElement;
        }
        return null;
    }

    public static boolean isResolving(Element element) {
        return element != null && RESOLVING.get().contains(element);
    }

    private static Size computeSize(Element element, boolean allowFlexAdjustments) {
        boolean intrinsicMeasurement = isNaturalMeasurementContext();
        Size cache = intrinsicMeasurement ? null : element.getRenderer().size.get();
        if (cache != null) return cache;
        if (intrinsicMeasurement && !allowFlexAdjustments) {
            Size naturalCache = getNaturalMeasurementCache(element);
            if (naturalCache != null) return naturalCache;
        }

        Style style = element.getComputedStyle();

        if ("none".equals(style.display)) {
            return ZERO;
        }

        Size gridUsedSize = intrinsicMeasurement ? null : Grid.resolveAssignedSize(element);
        if (gridUsedSize != null
                && element.parentElement != null
                && Layout.isGridDisplay(element.parentElement.getComputedStyle().display)
                && Layout.isInFlow(style)) {
            element.getRenderer().size.set(gridUsedSize);
            return gridUsedSize;
        }

        boolean isText = element instanceof AbstractText
                || ((!element.innerText.isEmpty() || hasDirectTextNodeChildren(element)) && element.getRenderChildren().isEmpty());
        Size contentSize;
        if (element instanceof com.sighs.apricityui.element.Canvas canvas) {
            contentSize = canvas.getIntrinsicSize();
        } else if (element instanceof com.sighs.apricityui.element.Select select) {
            contentSize = select.getIntrinsicSize();
        } else {
            contentSize = isText ? getTextSize(element) : getContentSize(element);
        }
        if (isText && element instanceof AbstractText textControl
                && !textControl.isMultiline() && usesNormalLineHeight(element)) {
            Text text = Text.of(element);
            contentSize = new Size(contentSize.width(), Math.round(Text.calculateLineHeight(text.fontSize, "normal")));
        }
        Box box = Box.of(element);
        double horizontalBox = box.getBorderHorizontal() + box.getPaddingHorizontal();
        double verticalBox = box.getBorderVertical() + box.getPaddingVertical();

        boolean borderBox = box.isBorderBox();
        boolean fixedPositioned = "fixed".equals(style.position);
        boolean absolutePositioned = "absolute".equals(style.position) || fixedPositioned;
        double contentWidth = contentSize.width;
        double contentHeight = contentSize.height;
        Double cachedParentWidth = absolutePositioned ? getContainingBlockPaddingBoxWidth(element) : null;
        Double explicitParentWidth = absolutePositioned ? getExplicitContainingBlockPaddingBoxWidth(element) : null;
        Double cachedParentHeight = absolutePositioned
                ? getContainingBlockPaddingBoxHeight(element)
                : getCachedContainingBlockContentHeight(element);
        Double explicitParentHeight = absolutePositioned
                ? getExplicitContainingBlockPaddingBoxHeight(element)
                : getExplicitContainingBlockHeight(element);
        if (fixedPositioned) {
            cachedParentWidth = Double.valueOf(Math.max(0, getWindowWidth()));
            explicitParentWidth = cachedParentWidth;
            cachedParentHeight = Double.valueOf(Math.max(0, getWindowHeight()));
            explicitParentHeight = cachedParentHeight;
        }
        Double definiteParentWidth = cachedParentWidth != null ? cachedParentWidth : explicitParentWidth;
        double parentWidth = absolutePositioned && definiteParentWidth != null ? definiteParentWidth : getScaleWidth(element);
        Double definiteParentHeight = cachedParentHeight != null ? cachedParentHeight : explicitParentHeight;
        double parentHeight = definiteParentHeight != null ? definiteParentHeight : 0;
        boolean unsetWidth = tryResolveLength(style.width, parentWidth) == null;
        boolean unsetHeight = tryResolveLength(style.height, parentHeight) == null;
        boolean flexMainHeightAssigned = false;
        boolean flexCrossHeightStretched = false;
        Double naturalWidthConstraint = NATURAL_CONTENT_WIDTHS.get().get(element);
        boolean hasLeft = isInsetSet(style.left);
        boolean hasRight = isInsetSet(style.right);
        boolean hasTop = isInsetSet(style.top);
        boolean hasBottom = isInsetSet(style.bottom);
        boolean insetResolvedHeight = false;

        if (absolutePositioned && unsetWidth && hasLeft && hasRight) {
            double left = resolveLength(style.left, parentWidth, 0);
            double right = resolveLength(style.right, parentWidth, 0);
            contentWidth = Math.max(0, parentWidth - left - right - horizontalBox);
        }
        if (absolutePositioned && unsetHeight && hasTop && hasBottom && definiteParentHeight != null) {
            double top = resolveLength(style.top, parentHeight, 0);
            double bottom = resolveLength(style.bottom, parentHeight, 0);
            contentHeight = Math.max(0, parentHeight - top - bottom - verticalBox);
            insetResolvedHeight = true;
        }

        if (unsetWidth && shouldFillAvailableBlockWidth(element, style)
                && !shouldUseContentBasedAutoWidthInNaturalFlexMeasurement(element, allowFlexAdjustments)
                && !shouldUseContentBasedAutoWidthForWrappedFlex(element)) {
            double availableOuterWidth = Math.max(0, parentWidth - box.getMarginHorizontal());
            contentWidth = Math.max(0, availableOuterWidth - horizontalBox);
        }

        if (!unsetWidth) {
            double resolved = resolveLength(style.width, parentWidth, contentWidth);
            contentWidth = borderBox ? Math.max(0, resolved - horizontalBox) : Math.max(0, resolved);
        } else {
            if (naturalWidthConstraint != null) contentWidth = Math.max(0, naturalWidthConstraint);
        }
        if (!unsetHeight && (!isPercent(style.height) || definiteParentHeight != null)) {
            double resolved = resolveLength(style.height, parentHeight, contentHeight);
            contentHeight = borderBox ? Math.max(0, resolved - verticalBox) : Math.max(0, resolved);
        }

        Double aspectRatio = parseAspectRatio(style.aspectRatio);
        if (aspectRatio != null && aspectRatio > 0) {
            if ((!unsetWidth || naturalWidthConstraint != null) && unsetHeight) {
                contentHeight = aspectHeightFromWidth(contentWidth, aspectRatio, borderBox, horizontalBox, verticalBox);
            } else if (unsetWidth && !unsetHeight) {
                contentWidth = aspectWidthFromHeight(contentHeight, aspectRatio, borderBox, horizontalBox, verticalBox);
            }
        }

        Flex.ItemUsedSize flexItemSize = Flex.resolveItemUsedSize(element, box,
                contentWidth, contentHeight, unsetWidth, unsetHeight,
                horizontalBox, verticalBox, explicitParentHeight, allowFlexAdjustments);
        contentWidth = flexItemSize.contentWidth();
        contentHeight = flexItemSize.contentHeight();
        flexMainHeightAssigned = flexItemSize.mainSizeAssigned();
        flexCrossHeightStretched = flexItemSize.crossSizeStretched();

        boolean parentAssignsColumnMainSize = element.parentElement != null
                && Layout.isInFlow(style)
                && Layout.isFlexDisplay(element.parentElement.getComputedStyle().display)
                && Flex.of(element.parentElement).flexDirection.contains("column");
        if (unsetHeight && !insetResolvedHeight && !flexMainHeightAssigned && !flexCrossHeightStretched
                && !parentAssignsColumnMainSize
                && (!intrinsicMeasurement || naturalWidthConstraint != null)
                && !(element instanceof AbstractText)
                && Layout.isFlexDisplay(style.display)) {
            Flex ownFlex = Flex.of(element);
            if (ownFlex.flexDirection.contains("row") && !ownFlex.flexWrap.is("wrap")) {
                contentHeight = Flex.computeRowCrossSizeAtMainSize(element, contentWidth);
            }
        }

        double constrainedContentWidth = clampContentExtent(contentWidth, horizontalBox, style.minWidth, style.maxWidth, parentWidth, true);
        double constrainedContentHeight = clampContentExtent(contentHeight, verticalBox, style.minHeight, style.maxHeight, parentHeight, definiteParentHeight != null);
        if (aspectRatio != null && aspectRatio > 0) {
            if ((!unsetWidth || naturalWidthConstraint != null) && unsetHeight) {
                constrainedContentHeight = aspectHeightFromWidth(
                        constrainedContentWidth, aspectRatio, borderBox, horizontalBox, verticalBox);
                constrainedContentHeight = clampContentExtent(constrainedContentHeight, verticalBox, style.minHeight, style.maxHeight, parentHeight, definiteParentHeight != null);
            } else if (unsetWidth && !unsetHeight) {
                constrainedContentWidth = aspectWidthFromHeight(
                        constrainedContentHeight, aspectRatio, borderBox, horizontalBox, verticalBox);
                constrainedContentWidth = clampContentExtent(constrainedContentWidth, horizontalBox, style.minWidth, style.maxWidth, parentWidth, true);
            }
        }
        contentWidth = constrainedContentWidth;
        contentHeight = constrainedContentHeight;

        double totalWidth = contentWidth + horizontalBox;
        double totalHeight = contentHeight + verticalBox;

        Size resultSize = new Size(totalWidth, totalHeight);
        if (Boolean.getBoolean("apricityui.test.logStyles")
                && element.parentElement != null
                && element.parentElement.getClassNames().contains("compact-actions")) {
            ApricityUI.LOGGER.info(
                    "[AUI Size] tag={} class={} total={}x{} content={}x{} unsetWidth={} unsetHeight={} flex={} grow={} shrink={} basis={}",
                    element.tagName,
                    element.getClassNames(),
                    totalWidth,
                    totalHeight,
                    contentWidth,
                    contentHeight,
                    unsetWidth,
                    unsetHeight,
                    style.flex,
                    style.flexGrow,
                    style.flexShrink,
                    style.flexBasis
            );
        }
        if (!intrinsicMeasurement) {
            element.getRenderer().size.set(resultSize);
        }
        return resultSize;
    }


    private static double aspectHeightFromWidth(double contentWidth, double ratio, boolean borderBox,
                                                double horizontalBox, double verticalBox) {
        double ratioWidth = borderBox ? contentWidth + horizontalBox : contentWidth;
        double ratioHeight = ratioWidth / ratio;
        return Math.max(0, borderBox ? ratioHeight - verticalBox : ratioHeight);
    }

    private static double aspectWidthFromHeight(double contentHeight, double ratio, boolean borderBox,
                                                double horizontalBox, double verticalBox) {
        double ratioHeight = borderBox ? contentHeight + verticalBox : contentHeight;
        double ratioWidth = ratioHeight * ratio;
        return Math.max(0, borderBox ? ratioWidth - horizontalBox : ratioWidth);
    }

    private static double clampContentExtent(double contentExtent, double boxExtent,
                                             String minValue, String maxValue, double percentBasis,
                                             boolean allowPercentResolution) {
        double result = contentExtent;
        Double minParsed = parseNumber(minValue);
        if (minParsed != null) {
            if (!isPercent(minValue) || allowPercentResolution) {
                double minTotal = resolveLength(minValue, percentBasis, minParsed);
                result = Math.max(result, Math.max(0, minTotal - boxExtent));
            }
        }
        Double maxParsed = parseNumber(maxValue);
        if (maxParsed != null) {
            if (!isPercent(maxValue) || allowPercentResolution) {
                double maxTotal = resolveLength(maxValue, percentBasis, maxParsed);
                result = Math.min(result, Math.max(0, maxTotal - boxExtent));
            }
        }
        return Math.max(0, result);
    }

    private static boolean isInsetSet(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !"unset".equals(normalized) && !"auto".equals(normalized);
    }

    public static Size getTextSize(Element element) {
        Text text = Text.of(element);
        return Text.measureSize(element, text);
    }

    private static boolean hasDirectTextNodeChildren(Element element) {
        if (element == null) return false;
        for (com.sighs.apricityui.init.Node child : element.getRenderChildNodes()) {
            if (child instanceof com.sighs.apricityui.dom.TextNode textNode && !textNode.getTextContent().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static Size getContentSize(Element element) {
        return Layout.computeContentSize(element);
    }

    public static Size box(Element element) {
        return Box.of(element).size();
    }

    public static double getScaleWidth(Element element) {
        if (element == null) return getWindowWidth();

        Map<Element, Double> constraints = NATURAL_CONTENT_WIDTHS.get();
        Element[] route = element.getRouteArray();
        for (Element current : route) {
            Double constrainedWidth = constraints.get(current);
            if (constrainedWidth != null) return Math.max(0, constrainedWidth);
        }

        // The recursive implementation recalculated the entire ancestor chain
        // once for tryResolveLength() and again for resolveLength(). The route
        // is already cached by the renderer, so resolve ancestors once from
        // the root while retaining the nearest usable containing block.
        double scaleWidth = getWindowWidth();
        double nearestScaleWidth = scaleWidth;
        for (int index = route.length - 1; index > 0; index--) {
            Element current = route[index];
            Size cachedSize = current.getRenderer().size.get();
            boolean hasUsableSize = false;
            if (cachedSize != null) {
                double innerWidth = Box.of(current).innerSize().width();
                if (innerWidth > 0) {
                    scaleWidth = innerWidth;
                    hasUsableSize = true;
                }
            }

            if (!hasUsableSize) {
                Style currentStyle = current.getRawComputedStyle();
                Double resolved = tryResolveLength(currentStyle.width, scaleWidth);
                if (resolved != null) {
                    double resolvedWidth = resolved;
                    if (Box.BOX_SIZING_BORDER_BOX.equals(Box.normalizeBoxSizing(currentStyle.boxSizing))) {
                        Box currentBox = Box.of(current);
                        resolvedWidth -= currentBox.getBorderHorizontal() + currentBox.getPaddingHorizontal();
                    }
                    scaleWidth = Math.max(0, resolvedWidth);
                    hasUsableSize = true;
                }
            }

            if (hasUsableSize) nearestScaleWidth = scaleWidth;
        }
        return nearestScaleWidth;
    }

    private static Size getNaturalMeasurementCache(Element element) {
        Map<Element, Double> constraints = NATURAL_CONTENT_WIDTHS.get();
        Double availableWidth = getNaturalMeasurementWidthContext(element);
        int cacheMode = constraints.containsKey(element)
                ? LayoutMeasureCache.SIZE_NATURAL_CONSTRAINED
                : LayoutMeasureCache.SIZE_NATURAL;
        return LayoutMeasureCache.getSize(
                cacheMode,
                element,
                availableWidth == null ? Double.NaN : availableWidth,
                Double.NaN,
                true
        );
    }

    public static double getScaleHeight(Element element) {
        Element parent = element.parentElement;
        if (parent != null) {
            Size cachedParentSize = parent.getRenderer().size.get();
            if (cachedParentSize != null) {
                double innerHeight = Box.of(parent).innerSize().height();
                if (innerHeight > 0) {
                    return innerHeight;
                }
            }
            Style parentStyle = parent.getRawComputedStyle();
            if (tryResolveLength(parentStyle.height, getScaleHeight(parent)) != null) {
                double resolvedHeight = resolveLength(parentStyle.height, getScaleHeight(parent), 0);
                if (Box.BOX_SIZING_BORDER_BOX.equals(Box.normalizeBoxSizing(parentStyle.boxSizing))) {
                    Box parentBox = Box.of(parent);
                    resolvedHeight -= parentBox.getBorderVertical() + parentBox.getPaddingVertical();
                }
                return Math.max(0, resolvedHeight);
            }
            return getScaleHeight(parent);
        } else return getWindowHeight();
    }

    public static Double getExplicitContainingBlockHeight(Element element) {
        Element parent = element.parentElement;
        if (parent == null) return Math.max(0, getWindowHeight());

        Double parentOwnHeight = resolveOwnExplicitContentHeight(parent);
        if (parentOwnHeight == null) return null;
        return Math.max(0, parentOwnHeight);
    }

    private static boolean usesNormalLineHeight(Element element) {
        if (element == null) return true;
        for (Element current : element.getRouteArray()) {
            String lineHeight = current.getComputedStyle().lineHeight;
            if (lineHeight == null || lineHeight.isBlank() || "unset".equalsIgnoreCase(lineHeight)) continue;
            return "normal".equalsIgnoreCase(lineHeight);
        }
        return true;
    }

    /**
     * CSS2 §10.1：absolute 的百分比尺寸相对最近 positioned 祖先的 padding box。
     * 按轴参数化的核心：explicit 时用显式内容尺寸 + padding，否则用盒尺寸 - 边框。
     */
    private static Double containingBlockPaddingBoxExtent(Element element, boolean horizontal, boolean explicit) {
        Element cb = Position.findContainingBlock(element);
        if (cb == null) {
            Size viewport = Position.viewportContainingBlockSize(element);
            return Math.max(0, horizontal ? viewport.width() : viewport.height());
        }
        Box cbBox = Box.of(cb);
        if (explicit) {
            Double content = horizontal ? resolveOwnExplicitContentWidth(cb) : resolveOwnExplicitContentHeight(cb);
            if (content == null) return null;
            return Math.max(0, content + (horizontal ? cbBox.getPaddingHorizontal() : cbBox.getPaddingVertical()));
        }
        Size cbSize = cb.getRenderer().size.get();
        if (cbSize == null) {
            if (isResolving(cb)) return null;
            cbSize = Size.of(cb);
        }
        return Math.max(0, horizontal
                ? cbSize.width() - cbBox.getBorderHorizontal()
                : cbSize.height() - cbBox.getBorderVertical());
    }

    public static Double getContainingBlockPaddingBoxHeight(Element element) {
        return containingBlockPaddingBoxExtent(element, false, false);
    }

    private static Double getCachedContainingBlockContentHeight(Element element) {
        Element parent = element == null ? null : element.parentElement;
        if (parent == null) return Math.max(0, getWindowHeight());
        Size parentSize = parent.getRenderer().size.get();
        if (parentSize == null) return null;
        Box parentBox = Box.of(parent);
        return Math.max(0, parentSize.height() - parentBox.getBorderVertical() - parentBox.getPaddingVertical());
    }

    public static Double getContainingBlockPaddingBoxWidth(Element element) {
        return containingBlockPaddingBoxExtent(element, true, false);
    }

    private static Double getExplicitContainingBlockPaddingBoxWidth(Element element) {
        return containingBlockPaddingBoxExtent(element, true, true);
    }

    private static Double getExplicitContainingBlockPaddingBoxHeight(Element element) {
        return containingBlockPaddingBoxExtent(element, false, true);
    }

    private static Double resolveOwnExplicitContentHeight(Element element) {
        if (element == null) return null;
        Style style = element.getRawComputedStyle();

        Double containingBlockHeight = element.parentElement == null
                ? Double.valueOf(Math.max(0, getWindowHeight()))
                : getExplicitContainingBlockHeight(element);

        Double resolvedHeight = tryResolveLength(style.height, containingBlockHeight == null ? 0 : containingBlockHeight);
        if (resolvedHeight != null) {
            if (!isPercent(style.height) || containingBlockHeight != null) {
                double contentHeight = resolvedHeight;
                Box box = Box.of(element);
                if (Box.BOX_SIZING_BORDER_BOX.equals(Box.normalizeBoxSizing(style.boxSizing))) {
                    contentHeight -= box.getBorderVertical() + box.getPaddingVertical();
                }
                double parentHeight = containingBlockHeight == null ? 0 : containingBlockHeight;
                return clampContentExtent(contentHeight, box.getBorderVertical() + box.getPaddingVertical(),
                        style.minHeight, style.maxHeight, parentHeight, containingBlockHeight != null);
            }
        }

        Double aspectRatio = parseAspectRatio(style.aspectRatio);
        if (aspectRatio != null && aspectRatio > 0) {
            Double widthBasis = resolveOwnExplicitContentWidth(element);
            if (widthBasis != null) {
                return Math.max(0, widthBasis / aspectRatio);
            }
        }
        return null;
    }

    private static Double resolveOwnExplicitContentWidth(Element element) {
        if (element == null) return null;
        Style style = element.getRawComputedStyle();
        Double containingBlockWidth = element.parentElement == null ? getWindowWidth() : getScaleWidth(element);
        Double resolvedWidth = tryResolveLength(style.width, containingBlockWidth == null ? 0 : containingBlockWidth);
        if (resolvedWidth != null) {
            if (!isPercent(style.width) || containingBlockWidth != null) {
                Box box = Box.of(element);
                double horizontalBox = box.getBorderHorizontal() + box.getPaddingHorizontal();
                double parentWidth = element.parentElement == null ? getWindowWidth() : getScaleWidth(element);
                double contentWidth = box.isBorderBox() ? Math.max(0, resolvedWidth - horizontalBox) : resolvedWidth;
                return clampContentExtent(contentWidth, horizontalBox, style.minWidth, style.maxWidth, parentWidth, true);
            }
        }
        return null;
    }

    private static boolean shouldFillAvailableBlockWidth(Element element, Style style) {
        if (element == null || style == null) return false;
        if (element.parentElement == null) return true;
        if (!Layout.isInFlow(style)) return false;
        String position = style.position == null ? "static" : style.position.trim().toLowerCase(Locale.ROOT);
        if ("absolute".equals(position) || "fixed".equals(position)) {
            return false;
        }
        Element parent = element.parentElement;
        if (parent != null) {
            Style parentStyle = parent.getRawComputedStyle();
            if (isAutoWidthPositionedContainer(parent, parentStyle)) {
                // An auto-width positioned container is first measured from the
                // intrinsic contributions of its children. During that pass the
                // children must remain content-sized. Once the container has a
                // used width, normal block children resolve width:auto against it.
                return parent.getRenderer().size.get() != null && !isResolving(parent);
            }
        }
        String display = style.display == null ? "" : style.display.trim().toLowerCase(Locale.ROOT);
        if ("inline".equals(display)
                || "inline-block".equals(display)
                || "inline-flex".equals(display)
                || "inline-grid".equals(display)) {
            return false;
        }
        return !Layout.isFlexDisplay(element.parentElement.getComputedStyle().display);
    }

    private static boolean isAutoWidthPositionedContainer(Element element, Style style) {
        if (element == null || style == null) return false;
        String position = style.position == null ? "static" : style.position.trim().toLowerCase(Locale.ROOT);
        if (!"absolute".equals(position) && !"fixed".equals(position)) return false;
        return tryResolveLength(style.width, getScaleWidth(element)) == null;
    }

    public static boolean hasDefiniteAutoResolvedWidth(Element element) {
        return hasDefiniteAutoResolvedWidthInternal(element);
    }

    private static boolean shouldUseContentBasedAutoWidthInNaturalFlexMeasurement(Element element, boolean allowFlexAdjustments) {
        if (element == null || allowFlexAdjustments || !isNaturalMeasurementContext()) return false;
        Element current = element;
        while (current != null) {
            Element parent = current.parentElement;
            if (parent == null) {
                return false;
            }
            if (Layout.isFlexDisplay(parent.getComputedStyle().display) && Flex.of(parent).flexDirection.contains("row")) {
                return true;
            }
            current = parent;
        }
        return false;
    }

    private static boolean shouldUseContentBasedAutoWidthForWrappedFlex(Element element) {
        if (element == null) return false;
        Style style = element.getComputedStyle();
        return "inline-flex".equalsIgnoreCase(style.display) && Flex.of(element).flexDirection.contains("row")
                && Flex.flexWraps(Flex.of(element))
                && parseNumber(style.width) == null;
    }

    private static boolean hasDefiniteAutoResolvedWidthInternal(Element element) {
        if (element == null) return false;
        if (resolveOwnExplicitContentWidth(element) != null) return true;
        if (element.parentElement == null) return true;

        Element parent = element.parentElement;
        if (!Layout.isFlexDisplay(parent.getComputedStyle().display)
                && shouldFillAvailableBlockWidth(element, element.getComputedStyle())) {
            return true;
        }

        if (Layout.isFlexDisplay(parent.getComputedStyle().display)) {
            Flex parentFlex = Flex.of(parent);
            if (parentFlex.flexDirection.contains("column") && Flex.shouldStretchCrossAxis(element, parent)) {
                return true;
            }
        }

        return false;
    }

    public static double lerp(double current, double target) {
        return current + (target - current) * 0.2;
    }

    private static Double resolveCalc(String expression, double percentBasis) {
        return resolveCalc(expression, percentBasis, getRootFontSize());
    }

    private static Double resolveCalc(String expression, double percentBasis, double emBasis) {
        String expr = expression == null ? "" : expression.trim();
        if (expr.isEmpty()) return null;

        double result = 0;
        int sign = 1;
        int start = 0;
        for (int i = 0; i <= expr.length(); i++) {
            boolean boundary = i == expr.length();
            if (!boundary) {
                char c = expr.charAt(i);
                if ((c == '+' || c == '-') && i > start) {
                    boundary = true;
                }
            }
            if (!boundary) continue;

            String term = expr.substring(start, i).trim();
            if (!term.isEmpty()) {
                if (term.charAt(0) == '+') {
                    term = term.substring(1).trim();
                } else if (term.charAt(0) == '-') {
                    sign *= -1;
                    term = term.substring(1).trim();
                }
                Double resolved = resolveSingleLength(term, percentBasis, emBasis);
                if (resolved == null) return null;
                result += sign * resolved;
            }

            if (i < expr.length()) {
                sign = expr.charAt(i) == '-' ? -1 : 1;
            }
            start = i + 1;
        }
        return result;
    }

    private static Double resolveSingleLength(String token, double percentBasis) {
        return resolveSingleLength(token, percentBasis, getRootFontSize());
    }

    private static Double resolveSingleLength(String token, double percentBasis, double emBasis) {
        if (token == null) return null;
        String value = token.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return null;

        Double number = parseNumber(value);
        if (number == null) return null;
        if (value.endsWith("%")) return percentBasis * (number / 100d);
        if (value.endsWith("rem")) return number * getRootFontSize();
        if (value.endsWith("em")) return number * emBasis;
        if (value.endsWith("vw")) return getWindowWidth() * (number / 100d);
        if (value.endsWith("vh")) return getWindowHeight() * (number / 100d);
        return number;
    }

    public static double getRootFontSize() {
        return getRootFontSize(Document.getContextDocument());
    }

    private static boolean isMathFunction(String value) {
        return (value.regionMatches(true, 0, "min(", 0, 4)
                || value.regionMatches(true, 0, "max(", 0, 4)
                || value.regionMatches(true, 0, "clamp(", 0, 6)) && value.endsWith(")");
    }

    private static Double resolveMathFunction(String value, double percentBasis, double emBasis) {
        int opening = value.indexOf('(');
        if (opening < 0 || value.length() <= opening + 1) return null;
        String name = value.substring(0, opening).trim().toLowerCase(Locale.ROOT);
        String[] arguments = splitFunctionArguments(value.substring(opening + 1, value.length() - 1));
        if (arguments == null || arguments.length == 0) return null;

        double[] resolved = new double[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            Double length = tryResolveLength(arguments[index], percentBasis, emBasis);
            if (length == null) return null;
            resolved[index] = length;
        }
        return switch (name) {
            case "min" -> {
                double result = resolved[0];
                for (int index = 1; index < resolved.length; index++) result = Math.min(result, resolved[index]);
                yield result;
            }
            case "max" -> {
                double result = resolved[0];
                for (int index = 1; index < resolved.length; index++) result = Math.max(result, resolved[index]);
                yield result;
            }
            case "clamp" -> resolved.length == 3
                    ? Math.max(resolved[0], Math.min(resolved[1], resolved[2])) : null;
            default -> null;
        };
    }

    private static String[] splitFunctionArguments(String value) {
        ArrayList<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '(') depth++;
            else if (character == ')') {
                if (depth-- == 0) return null;
            } else if (character == ',' && depth == 0) {
                String argument = value.substring(start, index).trim();
                if (argument.isEmpty()) return null;
                arguments.add(argument);
                start = index + 1;
            }
        }
        if (depth != 0) return null;
        String argument = value.substring(start).trim();
        if (argument.isEmpty()) return null;
        arguments.add(argument);
        return arguments.toArray(String[]::new);
    }

    public static double getRootFontSize(Document preferredDocument) {
        if (preferredDocument != null) {
            Double parsed = resolveDocumentRootFontSize(preferredDocument);
            if (parsed != null && parsed > 0) {
                return parsed;
            }
            return preferredDocument.getFontMode().defaultFontSize();
        }
        Double override = rootFontOverride;
        if (override != null && override > 0) {
            return override;
        }
        for (Document document : Document.getAll()) {
            if (document == null || !document.isActive()) continue;
            Double parsed = resolveDocumentRootFontSize(document);
            if (parsed != null && parsed > 0) {
                return parsed;
            }
        }
        return 16d;
    }

    private static Double resolveDocumentRootFontSize(Document document) {
        if (document == null || document.documentElement == null) return null;
        double defaultFontSize = document.getFontMode().defaultFontSize();
        document.documentElement.getComputedStyle();
        String fontSize = document.documentElement.getStyle().fontSize;
        if (fontSize == null || fontSize.equals("unset")) {
            fontSize = document.documentElement.cssCache.get("font-size");
        }
        if (fontSize == null || fontSize.equals("unset")) {
            fontSize = document.documentElement.cssCache.get("fontSize");
        }
        return tryResolveLength(fontSize, defaultFontSize, defaultFontSize);
    }

    static Double parseAspectRatio(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || "auto".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value) || "unset".equalsIgnoreCase(value)) {
            return null;
        }

        int slash = value.indexOf('/');
        if (slash >= 0) {
            Double numerator = parseNumber(value.substring(0, slash).trim());
            Double denominator = parseNumber(value.substring(slash + 1).trim());
            if (numerator == null || denominator == null || denominator == 0) return null;
            return numerator / denominator;
        }

        Double direct = parseNumber(value);
        if (direct == null || direct <= 0) return null;
        return direct;
    }

    private static final Canvas METRICS_CANVAS = new Canvas();

    public static double measureText(Element element, String text) {
        if (text == null || text.isEmpty()) return 0;
        Text base = Text.of(element);
        Text measuring = new Text();
        measuring.fontSize = base.fontSize;
        measuring.fontWeight = base.fontWeight;
        measuring.oblique = base.oblique;
        measuring.strokeWidth = base.strokeWidth;
        measuring.strokeColor = base.strokeColor;
        measuring.color = base.color;
        measuring.fontFamily = base.fontFamily;
        measuring.lineHeight = base.lineHeight;
        measuring.direction = base.direction;
        measuring.textAlign = base.textAlign;
        measuring.verticalAlign = base.verticalAlign;
        measuring.whiteSpace = base.whiteSpace;
        measuring.fontMode = base.fontMode;
        measuring.textIndent = 0;
        measuring.letterSpacing = base.letterSpacing;
        measuring.content = text;
        return Text.measureText(measuring);
    }
}



