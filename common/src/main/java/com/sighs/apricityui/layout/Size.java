package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.resource.Font;

import java.awt.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;
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
    // 长度解析/求值的唯一实现来源已搬到 CssLength：本类只做委托，
    // 保证新类型与旧 API 行为等价（同一份代码路径）。
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
        return CssLength.parseNumber(str);
    }

    public static boolean isPercent(String value) {
        return CssLength.isPercent(value);
    }

    public static double resolveLength(String value, double percentBasis, double fallback) {
        return CssLength.parse(value).resolveOr(fallback, percentBasis);
    }

    public static Double tryResolveLength(String value, double percentBasis) {
        return CssLength.parse(value).resolve(percentBasis);
    }

    public static Double tryResolveLength(String value, double percentBasis, double emBasis) {
        return CssLength.parse(value).resolve(percentBasis, emBasis);
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
        } else if (element instanceof com.sighs.apricityui.element.Iframe iframe) {
            contentSize = iframe.getIntrinsicSize();
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

        boolean borderBox = style.isBorderBox();
        CssLength widthLength = style.widthLength();
        CssLength heightLength = style.heightLength();
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
        // CSS Sizing §5.2：固有（max-content/min-content）测量期间，如果百分比宽度的包含块尺寸本身
        // 是由内容决定的，这个百分比就是"不定"的，要按 auto 处理。否则 `.dropdown`（flex 项、
        // width:auto）里的 `.dropdown-label{width:100%}` 会把 100% 解析成 flex 容器的已用内容宽
        // 521.72，使 `.dropdown` 的 max-content 宽从 200 涨到 521.72，flex 换行行判定于是每行只放得下
        // 一个下拉框（Chromium 在同样标记下每行放两个）。
        boolean percentWidthIndefinite = intrinsicMeasurement
                && widthLength.isPercent()
                && getNaturalMeasurementWidthContext(element) == null
                && element.parentElement != null
                && !hasDefiniteAutoResolvedWidthInternal(element.parentElement);
        boolean unsetWidth = percentWidthIndefinite || widthLength.resolve(parentWidth) == null;
        boolean unsetHeight = heightLength.resolve(parentHeight) == null;
        // width:auto on a block-level box still resolves to the containing block width, so the
        // used width is definite; an aspect-ratio height may be derived from it.
        boolean widthDefinite = !unsetWidth;
        boolean flexMainHeightAssigned = false;
        boolean flexCrossHeightStretched = false;
        Double naturalWidthConstraint = NATURAL_CONTENT_WIDTHS.get().get(element);
        boolean hasLeft = isInsetSet(style.left);
        boolean hasRight = isInsetSet(style.right);
        boolean hasTop = isInsetSet(style.top);
        boolean hasBottom = isInsetSet(style.bottom);
        boolean insetResolvedHeight = false;

        if (absolutePositioned && unsetWidth && !hasIntrinsicSize(element) && hasLeft && hasRight) {
            double left = resolveLength(style.left, parentWidth, 0);
            double right = resolveLength(style.right, parentWidth, 0);
            contentWidth = Math.max(0, parentWidth - left - right - horizontalBox);
        }
        if (absolutePositioned && unsetHeight && !hasIntrinsicSize(element)
                && hasTop && hasBottom && definiteParentHeight != null) {
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
            widthDefinite = true;
        }

        if (!unsetWidth) {
            double resolved = widthLength.resolveOr(contentWidth, parentWidth);
            contentWidth = borderBox ? Math.max(0, resolved - horizontalBox) : Math.max(0, resolved);
        } else {
            if (naturalWidthConstraint != null) {
                contentWidth = Math.max(0, naturalWidthConstraint);
                widthDefinite = true;
            }
        }
        if (!unsetHeight && (!heightLength.isPercent() || definiteParentHeight != null)) {
            double resolved = heightLength.resolveOr(contentHeight, parentHeight);
            contentHeight = borderBox ? Math.max(0, resolved - verticalBox) : Math.max(0, resolved);
        }

        Double aspectRatio = parseAspectRatio(style.aspectRatio);
        if (aspectRatio != null && aspectRatio > 0) {
            if (widthDefinite && unsetHeight) {
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
        // An aspect-ratio item in a column flex container takes its width from the container's
        // cross axis, which makes the used height definite through the ratio.
        if (aspectRatio != null && aspectRatio > 0 && parentAssignsColumnMainSize
                && unsetHeight && !flexMainHeightAssigned) {
            contentHeight = aspectHeightFromWidth(contentWidth, aspectRatio, borderBox, horizontalBox, verticalBox);
        }
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

        double constrainedContentWidth = clampContentExtent(contentWidth, horizontalBox, style.minWidthLength(), style.maxWidthLength(), parentWidth, true);
        double constrainedContentHeight = clampContentExtent(contentHeight, verticalBox, style.minHeightLength(), style.maxHeightLength(), parentHeight, definiteParentHeight != null);
        if (aspectRatio != null && aspectRatio > 0) {
            if (widthDefinite && unsetHeight) {
                constrainedContentHeight = aspectHeightFromWidth(
                        constrainedContentWidth, aspectRatio, borderBox, horizontalBox, verticalBox);
                constrainedContentHeight = clampContentExtent(constrainedContentHeight, verticalBox, style.minHeightLength(), style.maxHeightLength(), parentHeight, definiteParentHeight != null);
            } else if (unsetWidth && !unsetHeight) {
                constrainedContentWidth = aspectWidthFromHeight(
                        constrainedContentHeight, aspectRatio, borderBox, horizontalBox, verticalBox);
                constrainedContentWidth = clampContentExtent(constrainedContentWidth, horizontalBox, style.minWidthLength(), style.maxWidthLength(), parentWidth, true);
            }
        }
        contentWidth = constrainedContentWidth;
        contentHeight = constrainedContentHeight;

        double totalWidth = contentWidth + horizontalBox;
        double totalHeight = contentHeight + verticalBox;

        Size resultSize = new Size(totalWidth, totalHeight);
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
                                             CssLength minValue, CssLength maxValue, double percentBasis,
                                             boolean allowPercentResolution) {
        double result = contentExtent;
        // CSS2.1 §10.4/§10.7：min/max 冲突时 min 胜出——先钳 max 再钳 min。
        Double maxParsed = maxValue.numberValue();
        if (maxParsed != null) {
            if (!maxValue.isPercent() || allowPercentResolution) {
                double maxTotal = maxValue.resolveOr(maxParsed, percentBasis);
                result = Math.min(result, Math.max(0, maxTotal - boxExtent));
            }
        }
        Double minParsed = minValue.numberValue();
        if (minParsed != null) {
            if (!minValue.isPercent() || allowPercentResolution) {
                double minTotal = minValue.resolveOr(minParsed, percentBasis);
                result = Math.max(result, Math.max(0, minTotal - boxExtent));
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
        // 索引循环：该判定在自然测量路径逐帧高频调用，for-each 的迭代器分配
        // 在 JFR 里累计约 22MB。
        // 纯空白文本节点（white-space 折叠模式下会在行首/行尾被移除）不算文本内容，
        // 否则只含换行缩进的容器会被测成一行高。
        List<com.sighs.apricityui.init.Node> children = element.getRenderChildNodes();
        Boolean preLike = null;
        for (int i = 0; i < children.size(); i++) {
            if (!(children.get(i) instanceof com.sighs.apricityui.dom.TextNode textNode)) continue;
            String content = textNode.getTextContent();
            if (content.isEmpty()) continue;
            if (preLike == null) {
                String whiteSpace = Text.getWhiteSpace(element);
                preLike = "pre".equals(whiteSpace) || "pre-wrap".equals(whiteSpace) || "break-spaces".equals(whiteSpace);
            }
            if (preLike || !content.isBlank()) return true;
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
                double containingWidth = scaleWidth;
                Double resolved = currentStyle.widthLength().resolve(scaleWidth);
                if (resolved != null) {
                    double resolvedWidth = resolved;
                    if (currentStyle.isBorderBox()) {
                        Box currentBox = Box.of(current);
                        resolvedWidth -= currentBox.getBorderHorizontal() + currentBox.getPaddingHorizontal();
                    }
                    scaleWidth = Math.max(0, resolvedWidth);
                    hasUsableSize = true;
                } else {
                    // width:auto 且这一层还没有可用的已用尺寸：它是撑满上层的块，它的内容盒要在
                    // 上层内容盒基础上再让出它自己的 padding/border。否则更近的子级会按上层内容盒
                    // 排版——实测 .detail-cover 因此在部分 pass 按 383.33 而不是 351.33 算宽，
                    // 16:9 高度多出 18px，整张详情卡可见地偏高。
                    Box currentBox = Box.of(current);
                    scaleWidth = Math.max(0, scaleWidth
                            - currentBox.getBorderHorizontal() - currentBox.getPaddingHorizontal());
                    hasUsableSize = true;
                }
                scaleWidth = clampScaleWidth(current, currentStyle, scaleWidth, containingWidth);
            }

            if (hasUsableSize) nearestScaleWidth = scaleWidth;
        }
        return nearestScaleWidth;
    }

    /**
     * CSS 2.1 §10.4：已用宽度受 min-/max-width 钳制。{@link #getScaleWidth} 在这里推导的是"祖先
     * 自身还没有可用的已用尺寸时"的包含块宽度；不钳制的话 {@code width:100%;max-width:1240px} 的
     * 祖先贡献的是未钳制的百分数（1458.43−40 = 1418.43，而不是 1200），于是同一次 pass 里量出来的
     * 子级宽了 218px，带 aspect-ratio 的内容（16:9 的 .detail-cover）跟着算高 ~70px，
     * `.container` 的已用高度因此在 821.05 与 750.22 之间摇摆。
     */
    private static double clampScaleWidth(Element element, Style style, double contentWidth, double containingWidth) {
        double result = contentWidth;
        Box box = Box.of(element);
        boolean borderBox = style.isBorderBox();
        double boxExtent = box.getBorderHorizontal() + box.getPaddingHorizontal();
        CssLength maxLength = style.maxWidthLength();
        Double maxValue = maxLength.numberValue();
        if (maxValue != null && (!maxLength.isPercent() || containingWidth > 0)) {
            double max = maxLength.resolveOr(maxValue, containingWidth);
            result = Math.min(result, Math.max(0, borderBox ? max - boxExtent : max));
        }
        CssLength minLength = style.minWidthLength();
        Double minValue = minLength.numberValue();
        if (minValue != null && (!minLength.isPercent() || containingWidth > 0)) {
            double min = minLength.resolveOr(minValue, containingWidth);
            result = Math.max(result, Math.max(0, borderBox ? min - boxExtent : min));
        }
        return Math.max(0, result);
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
        if (element == null) return getWindowHeight();

        // Resolve from the root towards the nearest ancestor so percentage
        // heights use the same containing-block chain without recursive calls.
        Element[] route = element.getRouteArray();
        double scaleHeight = getWindowHeight();
        double nearestScaleHeight = scaleHeight;
        for (int index = route.length - 1; index > 0; index--) {
            Element current = route[index];
            Size cachedSize = current.getRenderer().size.get();
            boolean hasUsableSize = false;
            if (cachedSize != null) {
                double innerHeight = Box.of(current).innerSize().height();
                if (innerHeight > 0) {
                    scaleHeight = innerHeight;
                    hasUsableSize = true;
                }
            }

            if (!hasUsableSize) {
                Style currentStyle = current.getRawComputedStyle();
                Double resolved = currentStyle.heightLength().resolve(scaleHeight);
                if (resolved != null) {
                    double resolvedHeight = resolved;
                    if (currentStyle.isBorderBox()) {
                        Box currentBox = Box.of(current);
                        resolvedHeight -= currentBox.getBorderVertical() + currentBox.getPaddingVertical();
                    }
                    scaleHeight = Math.max(0, resolvedHeight);
                    hasUsableSize = true;
                }
            }

            if (hasUsableSize) nearestScaleHeight = scaleHeight;
        }
        return nearestScaleHeight;
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

    /**
     * 样式/动画阶段使用的非强制变体：containing block 尺寸已缓存时直接返回，
     * 尚未布局过时返回 null，绝不触发 {@link #of} 的整树布局计算。
     */
    public static Double getCachedContainingBlockPaddingBoxHeight(Element element) {
        return cachedContainingBlockPaddingBoxExtent(element, false);
    }

    public static Double getCachedContainingBlockPaddingBoxWidth(Element element) {
        return cachedContainingBlockPaddingBoxExtent(element, true);
    }

    private static Double cachedContainingBlockPaddingBoxExtent(Element element, boolean horizontal) {
        Element cb = Position.findContainingBlock(element);
        if (cb == null) {
            Size viewport = Position.viewportContainingBlockSize(element);
            return Math.max(0, horizontal ? viewport.width() : viewport.height());
        }
        Size cbSize = cb.getRenderer().size.get();
        if (cbSize == null) return null;
        Box cbBox = Box.of(cb);
        return Math.max(0, horizontal
                ? cbSize.width() - cbBox.getBorderHorizontal()
                : cbSize.height() - cbBox.getBorderVertical());
    }

    private static Double getExplicitContainingBlockPaddingBoxWidth(Element element) {
        return containingBlockPaddingBoxExtent(element, true, true);
    }

    private static Double getExplicitContainingBlockPaddingBoxHeight(Element element) {
        return containingBlockPaddingBoxExtent(element, false, true);
    }

    private static Double resolveOwnExplicitContentHeight(Element element) {
        if (element == null) return null;
        com.sighs.apricityui.dom.RenderElement renderer = element.getRenderer();
        long memoDep = renderer.layoutDependency();
        if (renderer.explicitHeightMemoDep == memoDep) {
            double memo = renderer.explicitHeightMemoValue;
            return Double.isNaN(memo) ? null : memo;
        }
        Double result = resolveOwnExplicitContentHeightUncached(element);
        renderer.explicitHeightMemoDep = memoDep;
        renderer.explicitHeightMemoValue = result == null ? Double.NaN : result;
        return result;
    }

    private static Double resolveOwnExplicitContentHeightUncached(Element element) {
        Style style = element.getRawComputedStyle();

        Double containingBlockHeight = element.parentElement == null
                ? Double.valueOf(Math.max(0, getWindowHeight()))
                : getExplicitContainingBlockHeight(element);

        CssLength heightLength = style.heightLength();
        Double resolvedHeight = heightLength.resolve(containingBlockHeight == null ? 0 : containingBlockHeight);
        if (resolvedHeight != null) {
            if (!heightLength.isPercent() || containingBlockHeight != null) {
                double contentHeight = resolvedHeight;
                Box box = Box.of(element);
                if (style.isBorderBox()) {
                    contentHeight -= box.getBorderVertical() + box.getPaddingVertical();
                }
                double parentHeight = containingBlockHeight == null ? 0 : containingBlockHeight;
                return clampContentExtent(contentHeight, box.getBorderVertical() + box.getPaddingVertical(),
                        style.minHeightLength(), style.maxHeightLength(), parentHeight, containingBlockHeight != null);
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
        CssLength widthLength = style.widthLength();
        Double resolvedWidth = widthLength.resolve(containingBlockWidth == null ? 0 : containingBlockWidth);
        if (resolvedWidth != null) {
            if (!widthLength.isPercent() || containingBlockWidth != null) {
                Box box = Box.of(element);
                double horizontalBox = box.getBorderHorizontal() + box.getPaddingHorizontal();
                double parentWidth = element.parentElement == null ? getWindowWidth() : getScaleWidth(element);
                double contentWidth = style.isBorderBox() ? Math.max(0, resolvedWidth - horizontalBox) : resolvedWidth;
                return clampContentExtent(contentWidth, horizontalBox, style.minWidthLength(), style.maxWidthLength(), parentWidth, true);
            }
        }
        return null;
    }

    /**
     * 有确定固有尺寸的替换元素——正是 {@link #computeSize} 里那几个自己提供 contentSize 的类。
     *
     * <p>CSS 2.1 §10.3.4 与 §10.3.8：这类元素的 {@code width/height:auto} 取固有尺寸，
     * 既不撑满包含块，也不被 left/right（或 inset）拉伸。浏览器里的 {@code <iframe>} 正是
     * 如此：不加宽度就是 300×150，写 {@code inset:0} 也不会被撑开。</p>
     */
    private static boolean hasIntrinsicSize(Element element) {
        return element instanceof com.sighs.apricityui.element.Canvas
                || element instanceof com.sighs.apricityui.element.Iframe
                || element instanceof com.sighs.apricityui.element.Select;
    }

    /** Public form of {@link #shouldFillAvailableBlockWidth} for the flex layout. */
    public static boolean fillsAvailableBlockWidth(Element element) {
        return element != null && shouldFillAvailableBlockWidth(element, element.getComputedStyle());
    }

    /**
     * CSS 2.1 §10.3.7/§10.3.8：绝对/固定定位、且该轴两侧 inset 都是数值时，{@code auto} 尺寸由包含块
     * 解析出来，是**确定值**——{@code position:fixed; inset:0} 的宽度就是视口宽。
     *
     * <p>{@link #fillsAvailableBlockWidth} 回答的是"块级在流内撑满父级"，对这类元素返回 false；
     * 只拿它判断"容器是不是内容自适应"会把明明有剩余空间的容器当成收缩包裹，
     * {@code justify-content} 于是不生效（实测 {@code position:fixed;inset:0;display:flex;
     * justify-content:center} 里的子项贴在内容盒左边而不是居中）。</p>
     */
    public static boolean hasInsetResolvedSize(Element element, boolean horizontal) {
        if (element == null) return false;
        Style style = element.getComputedStyle();
        String position = style.position == null ? "static" : style.position.trim().toLowerCase(Locale.ROOT);
        if (!"absolute".equals(position) && !"fixed".equals(position)) return false;
        return isInsetSet(horizontal ? style.left : style.top)
                && isInsetSet(horizontal ? style.right : style.bottom);
    }

    private static boolean shouldFillAvailableBlockWidth(Element element, Style style) {
        if (element == null || style == null) return false;
        if (element.parentElement == null) return true;
        if (!Layout.isInFlow(style)) return false;
        // CSS 2.1 §10.3.4：块级替换元素的 width:auto 取固有宽度，不撑满包含块。
        // 浏览器里 <iframe style="display:block"> 不加宽度就是 300px 宽，这里对齐它。
        if (hasIntrinsicSize(element)) return false;
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
        return style.widthLength().resolve(getScaleWidth(element)) == null;
    }

    public static boolean hasDefiniteAutoResolvedWidth(Element element) {
        return hasDefiniteAutoResolvedWidthInternal(element);
    }

    /**
     * 行向 flex 容器是否已经能把主轴尺寸分给子项。
     *
     * <p>{@link #hasDefiniteAutoResolvedWidth} 只覆盖"显式宽度 / 块级撑满 / 列向拉伸"三种情况，
     * 行向 flex 里 {@code width:auto} 的容器不在其中——它的宽度由外层 flex 行分配，同样是确定的。
     * 少了这一条，这种容器里 {@code flex:1 1 auto} 的子项永远拿不到分配值，停在内容宽：
     * rewind_screen 的 .tree-head 宽 232，里面的 .tree-title 却是 0 宽。</p>
     */
    public static boolean hasDefiniteMainSizeForFlexItems(Element parent) {
        if (parent == null) return false;
        if (hasDefiniteAutoResolvedWidthInternal(parent)) return true;
        if (isNaturalMeasurementContext()) return false;
        Size used = parent.getRenderer().size.get();
        return used != null && used.width() > 0;
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
                && !style.widthLength().hasNumber();
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

    public static double getRootFontSize() {
        return getRootFontSize(Document.getContextDocument());
    }

    public static double getRootFontSize(Document preferredDocument) {
        if (preferredDocument != null) {
            Double parsed = resolveDocumentRootFontSize(preferredDocument);
            if (parsed != null && parsed > 0) {
                return parsed;
            }
            return Text.DEFAULT_FONT_SIZE;
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
        double defaultFontSize = Text.DEFAULT_FONT_SIZE;
        document.documentElement.getComputedStyle();
        String fontSize = document.documentElement.getInlineStylePropertyValue("font-size");
        if (fontSize == null || fontSize.isBlank() || fontSize.equals("unset")) {
            CSS.Declaration declared = document.documentElement.cssCache.get("font-size");
            fontSize = declared == null ? null : declared.value();
        }
        if (fontSize == null || fontSize.equals("unset")) {
            CSS.Declaration declared = document.documentElement.cssCache.get("fontSize");
            fontSize = declared == null ? null : declared.value();
        }
        return tryResolveLength(fontSize, defaultFontSize, defaultFontSize);
    }

    static Double parseAspectRatio(String raw) {
        return CssLength.parseAspectRatio(raw);
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
        measuring.wordBreak = base.wordBreak;
        measuring.textIndent = 0;
        measuring.letterSpacing = base.letterSpacing;
        measuring.content = text;
        return Text.measureText(measuring);
    }
}

