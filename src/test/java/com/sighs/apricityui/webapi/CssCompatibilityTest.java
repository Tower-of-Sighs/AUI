package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Selector;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.init.StyleFrameCache;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.resource.CSS;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Gradient;
import com.sighs.apricityui.style.Transform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CssCompatibilityTest {
    @Test
    void multipleBoxShadowsRetainCssOrderAndSpreadRadius() {
        List<Box.Shadow> shadows = Box.parseShadowList(
                "10px 10px 0 rgba(139,92,246,0.25), 10px 10px 0 3px #1a1a1a");

        assertEquals(2, shadows.size());
        assertEquals(0, shadows.get(0).size());
        assertEquals(0, shadows.get(0).spread());
        assertEquals(0x408B5CF6, shadows.get(0).color().getValue());
        assertEquals(0, shadows.get(1).size());
        assertEquals(3, shadows.get(1).spread());
        assertEquals(0xFF1A1A1A, shadows.get(1).color().getValue());

        Box.Shadow colorlessSpread = Box.parseShadowList("2px 4px 6px 8px").get(0);
        assertEquals(6, colorlessSpread.size());
        assertEquals(8, colorlessSpread.spread());
        assertEquals(0xFF000000, colorlessSpread.color().getValue());
    }

    @Test
    void inheritedPropertiesCascadeIntoChildrenByDefault() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "span");
        document.body.appendChild(parent);
        parent.appendChild(child);

        parent.setAttribute("style", "color: #123456; font-size: 20px; line-height: 2;");

        assertEquals("#123456", child.getComputedStyle().color);
        assertEquals("20px", child.getComputedStyle().fontSize);
        assertEquals("2", child.getComputedStyle().lineHeight);
    }

    @Test
    void unsetInitialAndInheritFollowCssWideKeywordSemantics() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "span");
        document.body.appendChild(parent);
        parent.appendChild(child);

        parent.setAttribute("style", "color: #abcdef; display: flex;");
        child.setAttribute("style", "color: unset; display: unset;");
        assertEquals("#abcdef", child.getComputedStyle().color);
        assertEquals("block", child.getComputedStyle().display);

        child.setAttribute("style", "color: initial; display: initial;");
        assertEquals("#000000", child.getComputedStyle().color);
        assertEquals("block", child.getComputedStyle().display);

        child.setAttribute("style", "color: inherit;");
        assertEquals("#abcdef", child.getComputedStyle().color);
    }

    @Test
    void marginPaddingAndBorderShorthandsExpandToDirectionalProperties() {
        Style style = new Style();
        style.merge("margin: 10px 20px 30px 40px; padding: 4px 8px; border: 1px solid #ff0000;");

        assertEquals("10px", style.marginTop);
        assertEquals("20px", style.marginRight);
        assertEquals("30px", style.marginBottom);
        assertEquals("40px", style.marginLeft);

        assertEquals("4px", style.paddingTop);
        assertEquals("8px", style.paddingRight);
        assertEquals("4px", style.paddingBottom);
        assertEquals("8px", style.paddingLeft);

        assertEquals("1px solid #ff0000", style.borderTop);
        assertEquals("1px solid #ff0000", style.borderRight);
        assertEquals("1px solid #ff0000", style.borderBottom);
        assertEquals("1px solid #ff0000", style.borderLeft);
    }

    @Test
    void gapShorthandSupportsSingleAndTwoValueExpansion() {
        Style style = new Style();
        style.merge("gap: 6px 2px;");

        assertEquals("6px 2px", style.gap);
        assertEquals("6px", style.rowGap);
        assertEquals("2px", style.columnGap);

        style.merge("gap: 5px;");
        assertEquals("5px", style.rowGap);
        assertEquals("5px", style.columnGap);
    }

    @Test
    void cssWideKeywordsAlsoApplyToGapAndDisplayAliases() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "div");
        document.body.appendChild(parent);
        parent.appendChild(child);

        parent.setAttribute("style", "color: #334455; gap: 9px 4px;");
        child.setAttribute("style", "gap: inherit; color: inherit;");

        assertEquals("#334455", child.getComputedStyle().color);
        assertEquals("9px 4px", child.getComputedStyle().gap);

        child.setAttribute("style", "gap: unset;");
        assertEquals("0px", child.getComputedStyle().gap);
        assertEquals("0px", child.getComputedStyle().rowGap);
        assertEquals("0px", child.getComputedStyle().columnGap);
    }

    @Test
    void gapLonghandsCanFallbackIndependentlyFromGapShorthand() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        document.body.appendChild(element);

        element.setAttribute("style", "gap: 8px 3px; row-gap: 5px;");

        assertEquals("8px 3px", element.getComputedStyle().gap);
        assertEquals("5px", element.getComputedStyle().rowGap);
        assertEquals("3px", element.getComputedStyle().columnGap);
    }

    @Test
    void backgroundShorthandSeparatesColorImageRepeatPositionAndSize() {
        Style style = new Style();
        style.merge("background: linear-gradient(#fff, #000) no-repeat center / cover #112233;");

        assertEquals("#112233", style.backgroundColor);
        assertEquals("linear-gradient(#fff, #000)", style.backgroundImage);
        assertEquals("no-repeat", style.backgroundRepeat);
        assertEquals("center", style.backgroundPosition);
        assertEquals("cover", style.backgroundSize);
    }

    @Test
    void backgroundShorthandSupportsColorAndVarOnlyForms() {
        Style solid = new Style();
        solid.merge("background: white;");
        assertEquals("white", solid.backgroundColor);
        assertEquals("unset", solid.backgroundImage);

        Style varColor = new Style();
        varColor.merge("background: var(--blue-panel);");
        assertEquals("var(--blue-panel)", varColor.backgroundColor);
        assertEquals("unset", varColor.backgroundImage);
    }

    @Test
    void linearGradientPixelStopsScaleAgainstRenderedTileSize() {
        Gradient gradient = Gradient.parse("linear-gradient(rgba(37, 99, 235, 0.07) 0.35px, transparent 0.35px)")
                .scaledTo(9.8f, 9.8f);

        int beforeStop = gradient.getColorAt(1f, 0.1f, 0f, 0f, 9.8f, 9.8f);
        int afterStop = gradient.getColorAt(1f, 1f, 0f, 0f, 9.8f, 9.8f);

        assertTrue(((beforeStop >>> 24) & 0xFF) > 0);
        assertEquals(0, (afterStop >>> 24) & 0xFF);
    }

    @Test
    void rajdhaniFallbackUsesCondensedLatinFontWhenAvailable() {
        java.awt.Font resolved = Font.resolveBaseFont("Rajdhani, sans-serif");

        assertNotNull(resolved);
        assertNotEquals("Dialog", resolved.getFamily(java.util.Locale.ROOT));
    }

    @Test
    void adjacentSiblingSelectorMatchesOnlyImmediatelyFollowingElement() {
        Document document = TestDocumentFactory.createDocument();
        Element container = new Element(document, "div");
        Element first = new Element(document, "div");
        Element second = new Element(document, "div");
        Element third = new Element(document, "span");
        document.body.appendChild(container);
        container.appendChild(first);
        container.appendChild(second);
        container.appendChild(third);
        container.setAttribute("class", "space-y-3");

        assertFalse(Selector.matches(first, ".space-y-3 > * + *"));
        assertTrue(Selector.matches(second, ".space-y-3 > * + *"));
        assertTrue(Selector.matches(third, ".space-y-3 > * + *"));
        assertFalse(Selector.matches(third, ".space-y-3 > span + *"));
    }

    @Test
    void displayAliasesSurviveComputedStyle() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        document.body.appendChild(element);

        element.setAttribute("style", "display: inline-flex;");
        assertEquals("inline-flex", element.getComputedStyle().display);

        element.setAttribute("style", "display: inline-grid;");
        assertEquals("inline-grid", element.getComputedStyle().display);

        element.setAttribute("style", "display: table;");
        assertEquals("block", element.getComputedStyle().display);

        element.setAttribute("style", "display: inline-table;");
        assertEquals("inline-block", element.getComputedStyle().display);

        element.setAttribute("style", "display: flow-root;");
        assertEquals("block", element.getComputedStyle().display);
    }

    @Test
    void userAgentDisplayDefaultsPreserveAuthorPrecedence() {
        Document document = TestDocumentFactory.createDocument();
        Element span = new Element(document, "span");
        document.body.appendChild(span);

        assertEquals("inline", span.getComputedStyle().display);

        span.setAttribute("style", "display: block;");
        assertEquals("block", span.getComputedStyle().display);
    }

    @Test
    void commaGroupedClassSelectorsApplyToEachClass() throws Exception {
        HashMap<String, java.util.Map<String, CSS.Declaration>> cache = new HashMap<>();
        String css = """
                .inline-pill,
                .inline-outer,
                .inline-inner {
                  display: inline;
                }
                """;

        CSS.readCSS(css, cache, "test://grouped.css");

        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        Element pill = new Element(document, "span");
        Element outer = new Element(document, "span");
        Element inner = new Element(document, "span");
        pill.setAttribute("class", "inline-pill warm");
        outer.setAttribute("class", "inline-outer");
        inner.setAttribute("class", "inline-inner");
        document.body.appendChild(pill);
        document.body.appendChild(outer);
        document.body.appendChild(inner);

        assertEquals("inline", pill.getComputedStyle().display);
        assertEquals("inline", outer.getComputedStyle().display);
        assertEquals("inline", inner.getComputedStyle().display);
    }

    @Test
    void shorthandsExpandBeforeSelectorCascade() {
        Document document = TestDocumentFactory.createDocument();
        Map<String, Map<String, CSS.Declaration>> cache = new java.util.LinkedHashMap<>();
        CSS.readCSS("""
                select { padding-left: 2px; }
                .dialog-select { padding: 10px 40px 10px 14px; }
                """, cache, "test://shorthand-cascade.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();

        Element select = new Element(document, "select");
        select.setAttribute("class", "dialog-select");
        document.body.appendChild(select);

        assertEquals("10px", Selector.matchCSS(select).get("padding-top"));
        assertEquals("40px", Selector.matchCSS(select).get("padding-right"));
        assertEquals("10px", Selector.matchCSS(select).get("padding-bottom"));
        assertEquals("14px", Selector.matchCSS(select).get("padding-left"));
        assertEquals("14px", select.getRawComputedStyle().paddingLeft);
    }

    @Test
    void moreSpecificLonghandOverridesLessSpecificShorthand() {
        Document document = TestDocumentFactory.createDocument();
        Map<String, Map<String, CSS.Declaration>> cache = new java.util.LinkedHashMap<>();
        CSS.readCSS("""
                select { padding: 10px 40px; }
                .dialog-select { padding-left: 14px; }
                """, cache, "test://longhand-cascade.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();

        Element select = new Element(document, "select");
        select.setAttribute("class", "dialog-select");
        document.body.appendChild(select);

        assertEquals("14px", Selector.matchCSS(select).get("padding-left"));
        assertEquals("14px", select.getRawComputedStyle().paddingLeft);
    }

    @Test
    void importantShorthandLonghandsRetainImportance() {
        Map<String, Map<String, CSS.Declaration>> cache = new java.util.LinkedHashMap<>();
        CSS.readCSS(".field { padding: 8px 12px !important; padding-left: 2px; }", cache,
                "test://important-shorthand.css");

        CSS.Declaration left = cache.get(".field").get("padding-left");
        assertEquals("12px", left.value());
        assertTrue(left.important());
    }

    @Test
    void generatedPseudoElementDoesNotRemoveHostTextFromRenderFlow() {
        Document document = TestDocumentFactory.createDocument();
        Map<String, Map<String, CSS.Declaration>> cache = new java.util.LinkedHashMap<>();
        CSS.readCSS(".label::before { content: '+'; position: absolute; }", cache,
                "test://pseudo-host-text.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();

        Element label = new Element(document, "label");
        label.setAttribute("class", "label");
        label.setTextContent("Viewport");
        document.body.appendChild(label);

        assertEquals(2, label.getRenderChildNodes().size());
        assertTrue(label.getRenderChildNodes().get(0) instanceof Element);
        assertTrue(label.getRenderChildNodes().get(1) instanceof com.sighs.apricityui.init.TextNode);
        assertEquals("Viewport", label.getRenderChildNodes().get(1).getTextContent());
        assertTrue(label.getChildNodes().isEmpty(), "generated render text must not mutate the DOM child list");
    }

    @Test
    void disabledAndFocusWithinPseudoClassesTrackControlState() throws Exception {
        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS("""
                .confirm:disabled { opacity: 0.4; transform: none !important; }
                .select-wrap { background-color: #ffffff; }
                .select-wrap:focus-within { background-color: #6d28d9; }
                """, cache, "test://dialog-states.css");

        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        Element button = new Element(document, "button");
        button.setAttribute("class", "confirm");
        Element wrap = new Element(document, "div");
        wrap.setAttribute("class", "select-wrap");
        Element select = new Element(document, "select");
        wrap.appendChild(select);
        document.body.appendChild(button);
        document.body.appendChild(wrap);

        assertFalse(button.matches(":disabled"));
        button.setAttribute("disabled", "disabled");
        document.flushPendingStyleUpdates();
        assertTrue(button.matches(":disabled"));
        assertEquals("0.4", button.getComputedStyle().opacity);

        assertFalse(wrap.matches(":focus-within"));
        document.setFocusedElement(select);
        document.flushPendingStyleUpdates();
        assertTrue(wrap.matches(":focus-within"));
        assertEquals("#6d28d9", wrap.getComputedStyle().backgroundColor);

        document.clearFocus();
        document.flushPendingStyleUpdates();
        assertFalse(wrap.matches(":focus-within"));
        assertEquals("#ffffff", wrap.getComputedStyle().backgroundColor);
    }

    @Test
    void revertFallsBackToInheritedOrInitialSemantics() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "span");
        document.body.appendChild(parent);
        parent.appendChild(child);

        parent.setAttribute("style", "color: #135790;");
        child.setAttribute("style", "color: revert; display: revert;");

        assertEquals("#135790", child.getComputedStyle().color);
        assertEquals("block", child.getComputedStyle().display);
    }

    @Test
    void mediaQueriesFilterRulesAgainstCurrentViewport() throws Exception {
        HashMap<String, java.util.Map<String, CSS.Declaration>> cache = new HashMap<>();
        String css = """
                @media (min-width: 300px) {
                  .panel { color: #111111; }
                }
                @media (max-width: 10px) {
                  .panel { color: #222222; }
                }
                """;

        withViewport(400, 300, () -> CSS.readCSS(css, cache, "test://doc"));

        assertEquals("#111111", cache.get(".panel").get("color").value());
    }

    @Test
    void mediaQueriesAlsoSupportOrientationAndHeight() throws Exception {
        HashMap<String, java.util.Map<String, CSS.Declaration>> cache = new HashMap<>();
        String css = """
                @media (orientation: landscape) and (min-height: 300px) {
                  .panel { color: #333333; }
                }
                @media (orientation: portrait) {
                  .panel { color: #999999; }
                }
                """;

        withViewport(640, 360, () -> CSS.readCSS(css, cache, "test://doc"));

        assertEquals("#333333", cache.get(".panel").get("color").value());
    }

    @Test
    void animationShorthandBackfillsLonghands() {
        Style style = new Style();
        style.merge("animation: pulse 2s ease-in 100ms infinite alternate both paused;");
        style.finalizeComputedValues(null);

        assertEquals("pulse", style.animationName);
        assertEquals("2s", style.animationDuration);
        assertEquals("100ms", style.animationDelay);
        assertEquals("infinite", style.animationIterationCount);
        assertEquals("alternate", style.animationDirection);
        assertEquals("both", style.animationFillMode);
        assertEquals("ease-in", style.animationTimingFunction);
        assertEquals("paused", style.animationPlayState);
    }

    @Test
    void animationLonghandsAlsoBuildComputedAnimationShorthand() {
        Style style = new Style();
        style.merge("""
                animation-name: pulse;
                animation-duration: 2s;
                animation-delay: 100ms;
                animation-iteration-count: infinite;
                animation-direction: alternate;
                animation-fill-mode: both;
                animation-timing-function: ease-in;
                animation-play-state: running;
                """);
        style.finalizeComputedValues(null);

        assertEquals("pulse", style.animationName);
        assertEquals("running", style.animationPlayState);
        assertTrue(style.animation.startsWith("pulse 2s ease-in 100ms infinite alternate both running"));
    }

    @Test
    void animationNoneResetsLonghandsToInitialValues() {
        Style style = new Style();
        style.merge("animation: none;");
        style.finalizeComputedValues(null);

        assertEquals("none", style.animation);
        assertEquals("none", style.animationName);
        assertEquals("0s", style.animationDuration);
        assertEquals("0s", style.animationDelay);
        assertEquals("1", style.animationIterationCount);
        assertEquals("normal", style.animationDirection);
        assertEquals("none", style.animationFillMode);
        assertEquals("ease", style.animationTimingFunction);
        assertEquals("running", style.animationPlayState);
    }

    @Test
    void animationPlayStateTokenDoesNotOverrideAnimationName() {
        String animationName = "css-compat-play-state-" + UUID.randomUUID();
        Animation.registerKeyframe(animationName, 0.0, java.util.Map.of("opacity", "0.25"));
        Animation.registerKeyframe(animationName, 100.0, java.util.Map.of("opacity", "1"));

        Style style = new Style();
        style.merge("animation: " + animationName + " 1s linear 0s infinite alternate both running;");
        style.finalizeComputedValues(null);

        assertEquals(animationName, style.animationName);
        assertEquals("running", style.animationPlayState);

        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        document.body.appendChild(element);
        element.setAttribute("style", "animation: " + animationName + " 1s linear 0s infinite alternate both running;");

        Style computed = element.getComputedStyle().clone();
        Animation.updateStyle(element, computed);

        assertTrue(Double.parseDouble(computed.opacity) >= 0.25);
    }

    @Test
    void rotatePropertyMapsIntoComputedTransform() {
        Style style = new Style();
        style.merge("rotate: 45deg;");
        style.finalizeComputedValues(null);

        assertEquals("45deg", style.rotate);
        assertEquals("rotate(45deg)", style.transform);
    }

    @Test
    void rotatePropertyAppendsAfterExistingTransform() {
        Style style = new Style();
        style.merge("transform: translateX(4px); rotate: 45deg;");
        style.finalizeComputedValues(null);

        assertEquals("translateX(4px) rotate(45deg)", style.transform);
    }

    @Test
    void resolveLengthSupportsRemAndCalcSyntax() {
        assertEquals(32.0, com.sighs.apricityui.style.Size.resolveLength("2rem", 0, 0));
        assertEquals(132.0, com.sighs.apricityui.style.Size.resolveLength("calc(100% + 2rem)", 100, 0));
        assertEquals(84.0, com.sighs.apricityui.style.Size.resolveLength("calc(100% - 16px)", 100, 0));
    }

    @Test
    void transformTranslateSupportsCalcSyntax() {
        List<Transform> transforms = Transform.parse("translateX(calc(100% + 2rem))");

        assertEquals(1, transforms.size());
        assertTrue(transforms.get(0) instanceof Transform.Translate);
        Transform.Translate translate = (Transform.Translate) transforms.get(0);
        assertTrue(translate.x() > 0);
    }

    @Test
    void objectFitAndPositionSurviveComputedStyle() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "img");
        document.body.appendChild(element);

        element.setAttribute("style", "object-fit: contain; object-position: right bottom;");

        assertEquals("contain", element.getComputedStyle().objectFit);
        assertEquals("right bottom", element.getComputedStyle().objectPosition);
    }

    @Test
    void imageObjectFitRectMatchesBrowserLikeSizingRules() {
        Style contain = new Style();
        contain.merge("object-fit: contain;");
        contain.finalizeComputedValues(null);
        ImageDrawer.ObjectFitRect containRect = ImageDrawer.resolveObjectFitRect(contain, 10, 20, 200, 100, 100, 100);
        assertEquals(60f, containRect.x());
        assertEquals(20f, containRect.y());
        assertEquals(100f, containRect.width());
        assertEquals(100f, containRect.height());

        Style cover = new Style();
        cover.merge("object-fit: cover; object-position: left top;");
        cover.finalizeComputedValues(null);
        ImageDrawer.ObjectFitRect coverRect = ImageDrawer.resolveObjectFitRect(cover, 10, 20, 200, 100, 100, 100);
        assertEquals(10f, coverRect.x());
        assertEquals(20f, coverRect.y());
        assertEquals(200f, coverRect.width());
        assertEquals(200f, coverRect.height());

        Style scaleDown = new Style();
        scaleDown.merge("object-fit: scale-down; object-position: right bottom;");
        scaleDown.finalizeComputedValues(null);
        ImageDrawer.ObjectFitRect scaleDownRect = ImageDrawer.resolveObjectFitRect(scaleDown, 10, 20, 200, 100, 40, 40);
        assertEquals(170f, scaleDownRect.x());
        assertEquals(80f, scaleDownRect.y());
        assertEquals(40f, scaleDownRect.width());
        assertEquals(40f, scaleDownRect.height());
    }

    @Test
    void keyframesSupportFromToAndCommaSeparatedSelectors() {
        String animationName = "css-compat-keyframes-" + UUID.randomUUID();
        HashMap<String, java.util.Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS("""
                @keyframes %s {
                  from, 50%% { opacity: 0.25; }
                  to { opacity: 1; }
                }
                .panel { color: #123456; }
                """.formatted(animationName), cache, "test://doc");

        assertEquals("#123456", cache.get(".panel").get("color").value());
        TreeMap<Double, Map<String, String>> timeline = readRegisteredTimeline(animationName);
        assertNotNull(timeline);
        assertEquals("0.25", timeline.get(0d).get("opacity"));
        assertEquals("0.25", timeline.get(50d).get("opacity"));
        assertEquals("1", timeline.get(100d).get("opacity"));
    }

    @SuppressWarnings("unchecked")
    private static TreeMap<Double, Map<String, String>> readRegisteredTimeline(String animationName) {
        try {
            Field field = Animation.class.getDeclaredField("KEYFRAMES");
            field.setAccessible(true);
            Map<String, TreeMap<Double, Map<String, String>>> keyframes =
                    (Map<String, TreeMap<Double, Map<String, String>>>) field.get(null);
            return keyframes.get(animationName);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }


    private static void withViewport(double width, double height, ThrowingRunnable runnable) throws Exception {
        String oldWidth = System.getProperty("aui.test.viewport.width");
        String oldHeight = System.getProperty("aui.test.viewport.height");
        System.setProperty("aui.test.viewport.width", Double.toString(width));
        System.setProperty("aui.test.viewport.height", Double.toString(height));
        try {
            runnable.run();
        } finally {
            restoreProperty("aui.test.viewport.width", oldWidth);
            restoreProperty("aui.test.viewport.height", oldHeight);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
