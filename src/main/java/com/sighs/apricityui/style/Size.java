package com.sighs.apricityui.style;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.resource.Font;

import java.awt.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record Size(double width, double height) {
    public static final double DEFAULT_LINE_HEIGHT = 16;
    public static final Size ZERO = new Size(0, 0);
    private static final ThreadLocal<Set<Element>> RESOLVING = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Integer> NATURAL_MEASURE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Set<Element>> NATURAL_NO_CACHE = ThreadLocal.withInitial(HashSet::new);
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
        if (override != null) {
            return override;
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
            return Client.getWindowSize();
        } catch (NoClassDefFoundError | Exception ignored) {
            return new Size(1920, 1080);
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
        if (trimmed.regionMatches(true, 0, "calc(", 0, 5) && trimmed.endsWith(")")) {
            return resolveCalc(trimmed.substring(5, trimmed.length() - 1), percentBasis, getRootFontSize());
        }
        return resolveSingleLength(trimmed, percentBasis, getRootFontSize());
    }

    public static Double tryResolveLength(String value, double percentBasis, double emBasis) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "unset".equalsIgnoreCase(trimmed) || "auto".equalsIgnoreCase(trimmed)) return null;
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
        if (element == null) return ZERO;
        Size cached = LayoutMeasureCache.getSize(LayoutMeasureCache.SIZE_NATURAL, element, Double.NaN, Double.NaN, true);
        if (cached != null) return cached;
        int depth = NATURAL_MEASURE_DEPTH.get();
        NATURAL_MEASURE_DEPTH.set(depth + 1);
        NATURAL_NO_CACHE.get().add(element);
        try {
            Size result = computeSize(element, false);
            LayoutMeasureCache.putSize(LayoutMeasureCache.SIZE_NATURAL, element, Double.NaN, Double.NaN, true, result);
            return result;
        } finally {
            Set<Element> noCache = NATURAL_NO_CACHE.get();
            noCache.remove(element);
            if (noCache.isEmpty()) {
                NATURAL_NO_CACHE.remove();
            }
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

    public static boolean isResolving(Element element) {
        return element != null && RESOLVING.get().contains(element);
    }

    private static Size computeSize(Element element, boolean allowFlexAdjustments) {
        boolean noCacheForElement = NATURAL_NO_CACHE.get().contains(element);
        Size cache = noCacheForElement ? null : element.getRenderer().size.get();
        if (cache != null) return cache;

        Style style = element.getComputedStyle();

        if ("none".equals(style.display)) {
            return ZERO;
        }

        Size gridAssignedSize = noCacheForElement ? null : element.getRenderer().gridAssignedSize.get();
        if (gridAssignedSize != null
                && element.parentElement != null
                && Layout.isGridDisplay(element.parentElement.getComputedStyle().display)
                && Layout.isInFlow(style)) {
            element.getRenderer().size.set(gridAssignedSize);
            return gridAssignedSize;
        }

        boolean isText = element instanceof AbstractText
                || ((!element.innerText.isEmpty() || hasDirectTextNodeChildren(element)) && element.getRenderChildren().isEmpty());
        Size contentSize;
        if (element instanceof com.sighs.apricityui.element.Canvas canvas) {
            contentSize = canvas.getIntrinsicSize();
        } else {
            contentSize = isText ? getTextSize(element) : getContentSize(element);
        }
        Box box = Box.of(element);
        double horizontalBox = box.getBorderHorizontal() + box.getPaddingHorizontal();
        double verticalBox = box.getBorderVertical() + box.getPaddingVertical();

        boolean borderBox = box.isBorderBox();
        boolean absolutePositioned = "absolute".equals(style.position) || "fixed".equals(style.position);
        double contentWidth = contentSize.width;
        double contentHeight = contentSize.height;
        Double cachedParentWidth = absolutePositioned ? getContainingBlockPaddingBoxWidth(element) : null;
        Double explicitParentWidth = absolutePositioned ? getExplicitContainingBlockPaddingBoxWidth(element) : null;
        Double definiteParentWidth = cachedParentWidth != null ? cachedParentWidth : explicitParentWidth;
        double parentWidth = absolutePositioned && definiteParentWidth != null ? definiteParentWidth : getScaleWidth(element);
        Double explicitParentHeight = absolutePositioned ? getExplicitContainingBlockPaddingBoxHeight(element) : getExplicitContainingBlockHeight(element);
        Double cachedParentHeight = absolutePositioned ? getContainingBlockPaddingBoxHeight(element) : null;
        Double definiteParentHeight = cachedParentHeight != null ? cachedParentHeight : explicitParentHeight;
        double parentHeight = definiteParentHeight != null ? definiteParentHeight : 0;
        boolean unsetWidth = tryResolveLength(style.width, parentWidth) == null;
        boolean unsetHeight = tryResolveLength(style.height, parentHeight) == null;
        boolean hasLeft = isInsetSet(style.left);
        boolean hasRight = isInsetSet(style.right);
        boolean hasTop = isInsetSet(style.top);
        boolean hasBottom = isInsetSet(style.bottom);

        if (absolutePositioned && unsetWidth && hasLeft && hasRight) {
            double left = resolveLength(style.left, parentWidth, 0);
            double right = resolveLength(style.right, parentWidth, 0);
            contentWidth = Math.max(0, parentWidth - left - right - horizontalBox);
        }
        if (absolutePositioned && unsetHeight && hasTop && hasBottom && definiteParentHeight != null) {
            double top = resolveLength(style.top, parentHeight, 0);
            double bottom = resolveLength(style.bottom, parentHeight, 0);
            contentHeight = Math.max(0, parentHeight - top - bottom - verticalBox);
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
        }
        if (!unsetHeight && (!isPercent(style.height) || definiteParentHeight != null)) {
            double resolved = resolveLength(style.height, parentHeight, contentHeight);
            contentHeight = borderBox ? Math.max(0, resolved - verticalBox) : Math.max(0, resolved);
        }

        Double aspectRatio = parseAspectRatio(style.aspectRatio);
        if (aspectRatio != null && aspectRatio > 0) {
            if (!unsetWidth && unsetHeight) {
                contentHeight = contentWidth / aspectRatio;
            } else if (unsetWidth && !unsetHeight) {
                contentWidth = contentHeight * aspectRatio;
            }
        }

        if (allowFlexAdjustments && element.parentElement != null && Layout.isInFlow(style)
                && Layout.isFlexDisplay(element.parentElement.getComputedStyle().display)) {
            Element parent = element.parentElement;
            Flex parentFlex = Flex.of(parent);
            boolean parentResolving = isResolving(parent);
            Size parentContentSize = parentResolving ? ZERO : Box.of(parent).innerSize();

            if (parentFlex.flexDirection.isColumn()) {
                if (unsetWidth && Flex.shouldStretchCrossAxis(element, parent)) {
                    double parentCrossWidth = parentContentSize.width() > 0
                            ? parentContentSize.width()
                            : getScaleWidth(element);
                    double stretchedOuterWidth = Math.max(0, parentCrossWidth - box.getMarginHorizontal());
                    contentWidth = Math.max(0, stretchedOuterWidth - horizontalBox);
                }
            } else {
                if (unsetHeight && Flex.shouldStretchCrossAxis(element, parent)) {
                    double parentCrossHeight = parentContentSize.height() > 0
                            ? parentContentSize.height()
                            : getScaleHeight(element);
                    double stretchedOuterHeight = Math.max(0, parentCrossHeight - box.getMarginVertical());
                    contentHeight = Math.max(0, stretchedOuterHeight - verticalBox);
                }
            }

            double totalWidth = contentWidth + horizontalBox;
            double totalHeight = contentHeight + verticalBox;
            if (!parentResolving && parentFlex.flexDirection.isColumn() && unsetHeight) {
                double assignedOuterHeight = Flex.resolveAssignedMainSize(element, parent, totalHeight + box.getMarginVertical());
                double usableOuterHeight = Math.max(0, assignedOuterHeight - box.getMarginVertical());
                contentHeight = Math.max(0, usableOuterHeight - verticalBox);
            }
            if (!parentResolving && parentFlex.flexDirection.isRow()) {
                if (hasDefiniteAutoResolvedWidth(parent)) {
                    double assignedOuterWidth = Flex.resolveAssignedMainSize(element, parent, totalWidth + box.getMarginHorizontal());
                    double usableOuterWidth = Math.max(0, assignedOuterWidth - box.getMarginHorizontal());
                    contentWidth = Math.max(0, usableOuterWidth - horizontalBox);
                }
            }
        }

        double constrainedContentWidth = clampContentExtent(contentWidth, horizontalBox, style.minWidth, style.maxWidth, parentWidth, true);
        double constrainedContentHeight = clampContentExtent(contentHeight, verticalBox, style.minHeight, style.maxHeight, parentHeight, definiteParentHeight != null);
        if (aspectRatio != null && aspectRatio > 0) {
            if (!unsetWidth && unsetHeight) {
                constrainedContentHeight = constrainedContentWidth / aspectRatio;
                constrainedContentHeight = clampContentExtent(constrainedContentHeight, verticalBox, style.minHeight, style.maxHeight, parentHeight, definiteParentHeight != null);
            } else if (unsetWidth && !unsetHeight) {
                constrainedContentWidth = constrainedContentHeight * aspectRatio;
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
        if (!noCacheForElement && !shouldDeferSizeCache(element, style, unsetWidth, unsetHeight)) {
            element.getRenderer().size.set(resultSize);
        }
        return resultSize;
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
        return Text.of(element).size;
    }

    private static boolean hasDirectTextNodeChildren(Element element) {
        if (element == null) return false;
        for (com.sighs.apricityui.init.Node child : element.getRenderChildNodes()) {
            if (child instanceof com.sighs.apricityui.init.TextNode textNode && !textNode.getTextContent().isEmpty()) {
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
        Element parent = element.parentElement;
        if (parent != null) {
            Size cachedParentSize = parent.getRenderer().size.get();
            if (cachedParentSize != null) {
                double innerWidth = Box.of(parent).innerSize().width();
                if (innerWidth > 0) {
                    return innerWidth;
                }
            }
            Style parentStyle = parent.getRawComputedStyle();
            if (tryResolveLength(parentStyle.width, getScaleWidth(parent)) != null) {
                double resolvedWidth = resolveLength(parentStyle.width, getScaleWidth(parent), 0);
                if (Box.BOX_SIZING_BORDER_BOX.equals(Box.normalizeBoxSizing(parentStyle.boxSizing))) {
                    Box parentBox = Box.of(parent);
                    resolvedWidth -= parentBox.getBorderHorizontal() + parentBox.getPaddingHorizontal();
                }
                return Math.max(0, resolvedWidth);
            }
            return getScaleWidth(parent);
        } else return getWindowSize().width;
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
        } else return getWindowSize().height;
    }

    public static Double getExplicitContainingBlockHeight(Element element) {
        Element parent = element.parentElement;
        if (parent == null) return Math.max(0, getWindowSize().height());

        Double parentOwnHeight = resolveOwnExplicitContentHeight(parent);
        if (parentOwnHeight == null) return null;
        return Math.max(0, parentOwnHeight);
    }

    private static Double getContainingBlockPaddingBoxHeight(Element element) {
        Element parent = element == null ? null : element.parentElement;
        if (parent == null) return Math.max(0, getWindowSize().height());
        Size cachedParentSize = parent.getRenderer().size.get();
        Size parentSize = cachedParentSize;
        if (parentSize == null) {
            if (isResolving(parent)) return null;
            parentSize = Size.of(parent);
        }
        Box parentBox = Box.of(parent);
        return Math.max(0, parentSize.height() - parentBox.getBorderVertical());
    }

    private static Double getContainingBlockPaddingBoxWidth(Element element) {
        Element parent = element == null ? null : element.parentElement;
        if (parent == null) return Math.max(0, getWindowSize().width());
        Size cachedParentSize = parent.getRenderer().size.get();
        Size parentSize = cachedParentSize;
        if (parentSize == null) {
            if (isResolving(parent)) return null;
            parentSize = Size.of(parent);
        }
        Box parentBox = Box.of(parent);
        return Math.max(0, parentSize.width() - parentBox.getBorderHorizontal());
    }

    private static Double getExplicitContainingBlockPaddingBoxWidth(Element element) {
        Element parent = element == null ? null : element.parentElement;
        if (parent == null) return Math.max(0, getWindowSize().width());
        Double contentWidth = resolveOwnExplicitContentWidth(parent);
        if (contentWidth == null) return null;
        Box parentBox = Box.of(parent);
        return Math.max(0, contentWidth + parentBox.getPaddingHorizontal());
    }

    private static Double getExplicitContainingBlockPaddingBoxHeight(Element element) {
        Element parent = element == null ? null : element.parentElement;
        if (parent == null) return Math.max(0, getWindowSize().height());
        Double contentHeight = resolveOwnExplicitContentHeight(parent);
        if (contentHeight == null) return null;
        Box parentBox = Box.of(parent);
        return Math.max(0, contentHeight + parentBox.getPaddingVertical());
    }

    private static Double resolveOwnExplicitContentHeight(Element element) {
        if (element == null) return null;
        Style style = element.getRawComputedStyle();

        Double containingBlockHeight = element.parentElement == null
                ? Double.valueOf(Math.max(0, getWindowSize().height()))
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
        Double containingBlockWidth = element.parentElement == null ? getWindowSize().width() : getScaleWidth(element);
        Double resolvedWidth = tryResolveLength(style.width, containingBlockWidth == null ? 0 : containingBlockWidth);
        if (resolvedWidth != null) {
            if (!isPercent(style.width) || containingBlockWidth != null) {
                Box box = Box.of(element);
                double horizontalBox = box.getBorderHorizontal() + box.getPaddingHorizontal();
                double parentWidth = element.parentElement == null ? getWindowSize().width() : getScaleWidth(element);
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
            String parentPosition = parentStyle.position == null ? "static" : parentStyle.position.trim().toLowerCase(Locale.ROOT);
            boolean parentAutoWidth = tryResolveLength(parentStyle.width, getScaleWidth(parent)) == null;
            if (parentAutoWidth && ("absolute".equals(parentPosition) || "fixed".equals(parentPosition))) {
                return false;
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
            if (Layout.isFlexDisplay(parent.getComputedStyle().display) && Flex.of(parent).flexDirection.isRow()) {
                return true;
            }
            current = parent;
        }
        return false;
    }

    private static boolean shouldUseContentBasedAutoWidthForWrappedFlex(Element element) {
        if (element == null) return false;
        Style style = element.getComputedStyle();
        return Layout.isFlexDisplay(style.display) && Flex.of(element).flexDirection.isRow()
                && Flex.of(element).flexWrap.canWrap()
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
            if (parentFlex.flexDirection.isColumn() && Flex.shouldStretchCrossAxis(element, parent)) {
                return true;
            }
        }

        return false;
    }

    private static boolean shouldDeferSizeCache(Element element, Style style, boolean unsetWidth, boolean unsetHeight) {
        if (element == null || style == null) return false;
        Element parent = element.parentElement;
        if (parent == null) return false;

        Set<Element> resolving = RESOLVING.get();
        if (!resolving.contains(parent)) return false;

        if (unsetWidth && shouldFillAvailableBlockWidth(element, style)) {
            return true;
        }

        if (isPercent(style.width) || isPercent(style.height)) {
            return true;
        }

        if (!Layout.isFlexDisplay(parent.getComputedStyle().display)) {
            return false;
        }

        Flex parentFlex = Flex.of(parent);
        if (parentFlex.flexDirection.isColumn() && unsetHeight) {
            return true;
        }
        if (parentFlex.flexDirection.isRow()) {
            return true;
        }
        if (parentFlex.flexDirection.isColumn() && unsetWidth && Flex.shouldStretchCrossAxis(element, parent)) {
            return true;
        }
        if (parentFlex.flexDirection.isRow() && unsetHeight && Flex.shouldStretchCrossAxis(element, parent)) {
            return true;
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
        if (value.endsWith("vw")) return getWindowSize().width() * (number / 100d);
        if (value.endsWith("vh")) return getWindowSize().height() * (number / 100d);
        return number;
    }

    public static double getRootFontSize() {
        return getRootFontSize(null);
    }

    public static double getRootFontSize(Document preferredDocument) {
        Double override = rootFontOverride;
        if (override != null && override > 0) {
            return override;
        }
        if (preferredDocument != null) {
            Double parsed = resolveDocumentRootFontSize(preferredDocument);
            if (parsed != null && parsed > 0) {
                return parsed;
            }
            return preferredDocument.getFontMode().defaultFontSize();
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



