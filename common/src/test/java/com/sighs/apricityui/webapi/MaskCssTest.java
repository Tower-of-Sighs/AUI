package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.MaskImage;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ore 主题 mask 测试页（apricity/tests/mask-test.html）的资源契约 + 用页面
 * 真实内联 CSS 驱动的 mask 解析回归：长写逐层列表、简写逐层分配、
 * match-source 消解与 luminance 判定。
 */
class MaskCssTest {

    private static final String RESOURCE = "assets/apricityui/apricity/tests/mask-test.html";

    private static String readResource() throws Exception {
        try (InputStream input = MaskCssTest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "resource on classpath: " + RESOURCE);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String inlineStyle(String html) {
        int start = html.indexOf("<style>");
        int end = html.indexOf("</style>", start);
        assertTrue(start >= 0 && end > start, "page contains an inline stylesheet");
        return html.substring(start + "<style>".length(), end);
    }

    private static Document documentWithPageCss() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(inlineStyle(readResource()), cache, "test://mask-test.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        return document;
    }

    private static Element maskedDiv(Document document, String classes) {
        Element element = new Element(document, "div");
        element.setAttribute("class", classes);
        document.body.appendChild(element);
        return element;
    }

    @Test
    void pageLinksOreThemeAndCoversEveryMaskFeature() throws Exception {
        String html = readResource();

        assertTrue(html.contains("../apricityui/theme/ore/ore.css"), "ORE public theme is linked");
        assertTrue(html.contains("class=\"ore-theme\""), "ORE scope is present");
        assertTrue(html.contains("var(--canvas)"), "page consumes ORE tokens");

        assertTrue(html.contains("mask-mode: luminance"));
        assertTrue(html.contains("mask-composite: add"));
        assertTrue(html.contains("mask-composite: subtract"));
        assertTrue(html.contains("mask-composite: intersect"));
        assertTrue(html.contains("mask-composite: exclude"));
        assertTrue(html.contains("mask-clip: padding-box"));
        assertTrue(html.contains("mask-clip: no-clip"));
        assertTrue(html.contains("mask-origin: content-box"));
        assertTrue(html.contains("url(../devtools/bear.png)"), "url() mask layer is exercised");
        assertTrue(html.contains("missing-mask.png"), "fail-open case is exercised");
    }

    @Test
    void longhandMaskPropertiesFromPageCssResolve() throws Exception {
        Document document = documentWithPageCss();

        assertEquals("luminance", maskedDiv(document, "subject mode-luminance").getComputedStyle().maskMode);
        assertEquals("intersect", maskedDiv(document, "subject comp-intersect").getComputedStyle().maskComposite);
        assertEquals("subtract", maskedDiv(document, "subject comp-subtract").getComputedStyle().maskComposite);
        assertEquals("exclude", maskedDiv(document, "subject comp-exclude").getComputedStyle().maskComposite);
        assertEquals("padding-box", maskedDiv(document, "geo geo-clip-padding").getComputedStyle().maskClip);

        Style noclip = maskedDiv(document, "geo geo-noclip").getComputedStyle();
        assertEquals("no-clip", noclip.maskClip);
        assertEquals("border-box", noclip.maskOrigin);
        assertEquals("180% 180%", noclip.maskSize);

        assertEquals("content-box", maskedDiv(document, "origin-box origin-content").getComputedStyle().maskOrigin);
    }

    @Test
    void shorthandDistributesCompositeAndGeometryPerLayer() throws Exception {
        Style style = maskedDiv(documentWithPageCss(), "subject comp-shorthand").getComputedStyle();

        assertEquals(2, Background.splitTopLevelComma(style.maskImage).size());
        assertEquals("add, intersect", style.maskComposite);
        assertEquals("border-box, border-box", style.maskClip);
        assertEquals("border-box, border-box", style.maskOrigin);
        assertEquals("match-source, match-source", style.maskMode);
    }

    @Test
    void resolvedLayersApplyCyclicMatchSourceAndLuminanceRules() throws Exception {
        Document document = documentWithPageCss();

        List<MaskImage.ResolvedLayer> subtract = MaskImage.layersOf(maskedDiv(document, "subject comp-subtract"));
        assertEquals(2, subtract.size());
        assertEquals("subtract", subtract.get(0).composite(), "single composite value cycles to every layer");
        assertEquals("subtract", subtract.get(1).composite());
        assertEquals("alpha", subtract.get(0).mode(), "match-source resolves to alpha for CSS image sources");
        assertFalse(MaskImage.effectiveLuminance(subtract));

        List<MaskImage.ResolvedLayer> luminance = MaskImage.layersOf(maskedDiv(document, "subject mode-luminance"));
        assertEquals("luminance", luminance.get(0).mode());
        assertTrue(MaskImage.effectiveLuminance(luminance));

        List<MaskImage.ResolvedLayer> noclip = MaskImage.layersOf(maskedDiv(document, "geo geo-noclip"));
        assertEquals("no-clip", noclip.get(0).clip());
        assertEquals("border-box", noclip.get(0).origin());
    }
}
