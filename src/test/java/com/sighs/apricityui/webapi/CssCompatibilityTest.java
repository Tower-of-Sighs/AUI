package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Selector;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.init.StyleFrameCache;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.resource.CSS;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.style.Gradient;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.style.Transform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    void verticalAlignInitialValueMatchesBrowserBaseline() {
        Document document = TestDocumentFactory.createDocument();
        Element inline = new Element(document, "span");
        document.body.appendChild(inline);

        assertEquals("baseline", inline.getComputedStyle().verticalAlign);
        assertEquals("baseline", Text.of(inline).verticalAlign);
    }

    @Test
    void browserInlineElementsUseInlineUserAgentDisplay() {
        Document document = TestDocumentFactory.createDocument();
        String[] inlineTags = {
                "a", "abbr", "b", "bdi", "bdo", "cite", "code", "data", "del", "dfn", "em",
                "i", "ins", "kbd", "label", "mark", "q", "s", "samp", "small", "span", "strong",
                "sub", "sup", "time", "u", "var"
        };

        for (String tag : inlineTags) {
            Element element = new Element(document, tag);
            document.body.appendChild(element);
            assertEquals("inline", element.getComputedStyle().display, tag);
        }
    }

    @Test
    void hoverTextDecorationUnderlineIsResolvedAndInvalidatesText() throws Exception {
        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS(".ore-theme a { text-decoration: none; } .ore-theme a:hover { text-decoration: underline; }",
                cache, "test://hover-decoration.css");

        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        Element root = new Element(document, "div");
        root.setAttribute("class", "ore-theme");
        document.body.appendChild(root);
        Element link = new Element(document, "a");
        link.setAttribute("class", "navbar-brand");
        root.appendChild(link);

        assertEquals("none", link.getComputedStyle().textDecoration);
        assertFalse(Text.of(link).isUnderlined());

        link.setHover(true);
        document.flushPendingStyleUpdates();
        assertEquals("underline", link.getComputedStyle().textDecoration);
        assertTrue(Text.of(link).isUnderlined());
    }

    @Test
    void lineClampIsAFirstClassComputedTextProperty() {
        Style style = new Style();
        style.merge("line-clamp: 2; overflow: hidden; text-overflow: ellipsis;");
        style.finalizeComputedValues(null);

        assertEquals("2", style.lineClamp);
        assertEquals("hidden", style.overflow);
        assertEquals("ellipsis", style.textOverflow);
    }

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

        Box.Shadow spacedRgba = Box.parseShadowList("2px 4px rgba(26, 42, 58, 0.5)").get(0);
        assertEquals(0, spacedRgba.size());
        assertEquals(0x801A2A3A, spacedRgba.color().getValue());
    }

    @Test
    void insetBoxShadowsRetainKeywordAndCssLayerOrder() {
        List<Box.Shadow> shadows = Box.parseShadowList(
                "inset 0 -6px #1d4d13, inset 3px 3px rgba(255,255,255,0.22), 9px 9px rgba(0,0,0,0.36)");

        assertEquals(3, shadows.size());
        assertTrue(shadows.get(0).inset());
        assertEquals(-6, shadows.get(0).y());
        assertTrue(shadows.get(1).inset());
        assertEquals(3, shadows.get(1).x());
        assertFalse(shadows.get(2).inset());

        Box.Shadow trailingKeyword = Box.parseShadowList("2px 4px #123456 inset").get(0);
        assertTrue(trailingKeyword.inset());
        assertEquals(0xFF123456, trailingKeyword.color().getValue());
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
    void userAgentParagraphStyleDoesNotOverrideAuthorInheritedLineHeight() throws Exception {
        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        Path globalStyle = Path.of("src/main/resources/assets/apricityui/apricity/global.css");
        CSS.readCSS(Files.readString(globalStyle), cache, globalStyle.toString());
        CSS.readCSS(".theme { line-height: 1.5; }", cache, "test://author.css");

        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        document.body.setAttribute("class", "theme");
        Element paragraph = new Element(document, "p");
        document.body.appendChild(paragraph);

        assertEquals("1.5", paragraph.getComputedStyle().lineHeight);
    }

    @Test
    void userAgentDisabledSelectUsesNativeFadedAppearance() throws Exception {
        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        Path globalStyle = Path.of("src/main/resources/assets/apricityui/apricity/global.css");
        CSS.readCSS(Files.readString(globalStyle), cache, globalStyle.toString());

        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        Select select = new Select(document);
        select.setDisabled(true);
        document.body.appendChild(select);

        assertEquals("0.72", select.getComputedStyle().opacity);
        select.setAttribute("style", "opacity: 1;");
        assertEquals("1", select.getComputedStyle().opacity);
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
    void backgroundColorDefaultsToTransparent() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("span");
        document.body.appendChild(element);

        assertEquals("transparent", element.getComputedStyle().backgroundColor);

        element.setAttribute("style", "background-color: unset;");
        assertEquals("transparent", element.getComputedStyle().backgroundColor);
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
    void complexGradientStopsUseConstantSizeGeometryInsteadOfPixelSampling() {
        Gradient simple = Gradient.parse("linear-gradient(90deg, #111111 0%, #eeeeee 100%)");
        Gradient multiStop = Gradient.parse("linear-gradient(45deg, #111111 0%, #777777 50%, #eeeeee 100%)");
        Gradient hardStop = Gradient.parse("linear-gradient(45deg, #111111 50%, #eeeeee 50%)");

        assertFalse(Graph.requiresStopGeometry(simple));
        assertTrue(Graph.requiresStopGeometry(multiStop));
        assertTrue(Graph.requiresStopGeometry(hardStop));
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
                  color: #123456;
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

        assertEquals("#123456", pill.getComputedStyle().color);
        assertEquals("#123456", outer.getComputedStyle().color);
        assertEquals("#123456", inner.getComputedStyle().color);
    }

    @Test
    void generalSiblingSelectorMatchesAnyEarlierSibling() {
        Document document = TestDocumentFactory.createDocument();
        Element input = new Input(document);
        input.setAttribute("type", "radio");
        input.setAttribute("id", "page-toggle");
        input.setChecked(true);
        Element spacer = new Element(document, "nav");
        Element main = new Element(document, "main");
        Element panel = new Element(document, "section");
        panel.setAttribute("class", "panel");
        document.body.appendChild(input);
        document.body.appendChild(spacer);
        document.body.appendChild(main);
        main.appendChild(panel);

        assertTrue(Selector.matches(panel, "#page-toggle:checked~main .panel"));
        input.setChecked(false);
        assertFalse(Selector.matches(panel, "#page-toggle:checked~main .panel"));
    }

    @Test
    void checkedGeneralSiblingRuleRecalculatesFollowingSubtree() throws Exception {
        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS(".panel { display: none; } #page-toggle:checked~main .panel { display: block; }",
                cache, "test://checked-sibling.css");

        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        Input input = new Input(document);
        input.setAttribute("type", "radio");
        input.setAttribute("id", "page-toggle");
        Element main = new Element(document, "main");
        Element panel = new Element(document, "section");
        panel.setAttribute("class", "panel");
        document.body.appendChild(input);
        document.body.appendChild(main);
        main.appendChild(panel);

        assertEquals("none", panel.getComputedStyle().display);
        input.setChecked(true);
        document.flushPendingStyleUpdates();
        assertEquals("block", panel.getComputedStyle().display);
    }

    @Test
    void dynamicCompoundClassSelectorUpdatesDisplay() throws Exception {
        Map<String, Map<String, CSS.Declaration>> cache = new java.util.LinkedHashMap<>();
        CSS.readCSS(".page { display: none; } .page.active { display: block; }", cache,
                "test://dynamic-page.css");

        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        Element page = new Element(document, "section");
        page.setAttribute("class", "page");
        document.body.appendChild(page);

        assertEquals("none", page.getComputedStyle().display);
        page.getClassList().add("active");
        document.flushPendingStyleUpdates();
        assertEquals("block", page.getComputedStyle().display);
        page.getClassList().remove("active");
        document.flushPendingStyleUpdates();
        assertEquals("none", page.getComputedStyle().display);
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
    void cascadeSpecificityFollowsFunctionalPseudoAndAttributeSelectorRules() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        element.setAttribute("id", "target");
        element.setAttribute("class", "box notice");
        element.setAttribute("data-state", "ready");
        document.body.appendChild(element);

        LinkedHashMap<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS("""
                #target { color: #111111; }
                .box:where(#target) { color: #222222; }
                .box.notice { background-color: #111111; }
                .box:is(#target, .other) { background-color: #222222; }
                .box.notice { border-color: #111111; }
                .box:not(#other) { border-color: #222222; }
                div { opacity: 0.1; }
                [data-state] { opacity: 0.2; }
                """, cache, "test://cascade-specificity.css");
        document.CSSCache.putAll(cache);

        assertEquals("#111111", element.getComputedStyle().color,
                ":where() must contribute zero specificity");
        assertEquals("#222222", element.getComputedStyle().backgroundColor,
                ":is() must use its most-specific argument");
        assertEquals("#222222", element.getComputedStyle().borderColor,
                ":not() must use its most-specific argument");
        assertEquals("0.2", element.getComputedStyle().opacity,
                "attribute selectors belong to the class specificity column");
    }

    @Test
    void revertLayerUsesTheCurrentNoLayerFallbackAndTextStrokeInherits() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "span");
        document.body.appendChild(parent);
        parent.appendChild(child);

        parent.setAttribute("style", "color: #135790; text-stroke: 1px #2468ac;");
        child.setAttribute("style", "color: revert-layer; display: revert-layer;");

        assertEquals("#135790", child.getComputedStyle().color);
        assertEquals("1px #2468ac", child.getComputedStyle().textStroke);
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
    void mediaQueriesCanUseTheOwningDocumentsExplicitViewport() {
        HashMap<String, java.util.Map<String, CSS.Declaration>> cache = new HashMap<>();
        String css = """
                @media (min-width: 900px) {
                  .panel { display: flex; }
                }
                @media (max-width: 899px) {
                  .panel { display: block; }
                }
                """;

        CSS.readCSS(css, cache, "test://doc", new Size(1463, 843));

        assertEquals("flex", cache.get(".panel").get("display").value());
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
        assertEquals(32.0, com.sighs.apricityui.layout.Size.resolveLength("2rem", 0, 0));
        assertEquals(132.0, com.sighs.apricityui.layout.Size.resolveLength("calc(100% + 2rem)", 100, 0));
        assertEquals(84.0, com.sighs.apricityui.layout.Size.resolveLength("calc(100% - 16px)", 100, 0));
    }

    @Test
    void singleAxisTranslatePercentagesUseTheirOwnAxis() {
        List<Transform> transforms = Transform.parse("translateX(-50%) translateY(-50%)", 120, 20);

        assertEquals(-60, ((Transform.Translate) transforms.get(0)).x(), 0.001);
        assertEquals(-10, ((Transform.Translate) transforms.get(1)).y(), 0.001);
    }

    @Test
    void resolveLengthSupportsMinMaxAndClampFunctions() {
        assertEquals(900.0, com.sighs.apricityui.layout.Size.resolveLength("min(900px, 100%)", 1600, 0));
        assertEquals(1600.0, com.sighs.apricityui.layout.Size.resolveLength("max(900px, 100%)", 1600, 0));
        assertEquals(600.0, com.sighs.apricityui.layout.Size.resolveLength("clamp(360px, 50%, 600px)", 1600, 0));
        assertEquals(500.0, com.sighs.apricityui.layout.Size.resolveLength("min(900px, calc(100% - 20px))", 520, 0));
    }

    @Test
    void flexShorthandKeepsFixedFlexBasisAndDisablesShrink() {
        Style style = new Style();
        style.merge("flex: 0 0 250px;");
        style.finalizeComputedValues(null);

        assertEquals("0", style.flexGrow);
        assertEquals("0", style.flexShrink);
        assertEquals("250px", style.flexBasis);
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
    void appearanceAndWebkitAliasSurviveComputedStyle() {
        Document document = TestDocumentFactory.createDocument();
        Element standard = new Element(document, "select");
        Element prefixed = new Element(document, "select");
        Element cascaded = new Element(document, "select");
        document.body.appendChild(standard);
        document.body.appendChild(prefixed);
        document.body.appendChild(cascaded);

        standard.setAttribute("style", "appearance: none;");
        prefixed.setAttribute("style", "-webkit-appearance: none;");
        cascaded.setAttribute("style", "appearance: none; -webkit-appearance: auto;");

        assertEquals("none", standard.getComputedStyle().appearance);
        assertEquals("none", prefixed.getComputedStyle().appearance);
        assertEquals("auto", cascaded.getComputedStyle().appearance);
        assertEquals("none", new Window().getComputedStyle(standard).getPropertyValue("appearance"));
        assertEquals("none", new Window().getComputedStyle(prefixed).getPropertyValue("-webkit-appearance"));

        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS(".control { -webkit-appearance: auto; appearance: none; }", cache, "test://appearance.css");
        assertEquals("none", cache.get(".control").get("appearance").value());
        assertFalse(cache.get(".control").containsKey("-webkit-appearance"));
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
    void accentColorIsParsedAndInherited() {
        Document document = TestDocumentFactory.createDocument();
        Element label = new Element(document, "label");
        label.setAttribute("style", "accent-color: #3b8526;");
        Element input = new Element(document, "input");
        input.setAttribute("type", "checkbox");
        document.body.appendChild(label);
        label.appendChild(input);

        assertEquals("#3b8526", label.getComputedStyle().accentColor);
        assertEquals("#3b8526", input.getComputedStyle().accentColor);
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

    @Test
    void functionalPseudoClassesAndAttributeOperatorsMatchBrowserStyleSelectors() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Element list = new Element(document, "ul");
        Element first = new Element(document, "li");
        first.setAttribute("class", "notice active");
        first.setAttribute("data-state", "warning critical");
        first.setAttribute("lang", "en-US");
        Element divider = new Element(document, "div");
        Element second = new Element(document, "li");
        second.setAttribute("class", "notice muted");
        document.body.appendChild(list);
        list.appendChild(first);
        list.appendChild(divider);
        list.appendChild(second);

        assertTrue(Selector.matches(first, "li:first-of-type:nth-of-type(2n+1)"));
        assertTrue(Selector.matches(second, "li:last-of-type:nth-last-of-type(1)"));
        assertTrue(Selector.matches(first, ".notice:is(.active,.selected):not(.muted)"));
        assertTrue(Selector.matches(second, ":where(.notice,.card).muted"));
        assertTrue(Selector.matches(first, "[data-state~=critical][lang|=en][data-state^=warn][data-state$=critical][data-state*=ning]"));
        assertTrue(Selector.matches(first, "[lang=EN-us i]"));
        assertFalse(Selector.matches(second, "li:only-of-type"));

        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS(".notice:is(.active,.selected), .fallback { color: #123456; }", cache, "test://selector-functions.css");
        assertTrue(cache.containsKey(".notice:is(.active,.selected)"));
        assertTrue(cache.containsKey(".fallback"));
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
