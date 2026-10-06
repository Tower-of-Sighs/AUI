package com.sighs.apricityui.render;

import com.sighs.apricityui.element.Img;
import com.sighs.apricityui.element.Svg;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterImageSvgRenderingTest {
    @Test
    void filteredImgIsRenderedInsideItsOwnCompositeLayer() {
        Document document = TestDocumentFactory.createDocument();
        Img image = new Img(document);
        image.setAttribute("style", "filter: grayscale(100%) brightness(50%);");
        document.body.appendChild(image);

        assertHasFilterBoundary(Drawer.createPaintList(document.body), image);
    }

    @Test
    void filteredSvgIsRenderedInsideItsOwnCompositeLayer() {
        Document document = TestDocumentFactory.createDocument();
        Svg svg = new Svg(document);
        svg.setAttribute("style", "filter: grayscale(100%) brightness(50%);");
        document.body.appendChild(svg);

        assertHasFilterBoundary(Drawer.createPaintList(document.body), svg);
    }

    @Test
    void descendantImageFilterAppearingAfterParentStateChangeRebuildsItsLayer() {
        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS(".disabled img { filter: grayscale(100%) brightness(50%); }",
                cache, "test://image-filter.css");

        Document document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        Element parent = new Element(document, "div");
        parent.setAttribute("class", "custom-checkbox enabled");
        Img image = new Img(document);
        parent.appendChild(image);
        document.body.appendChild(parent);
        document.flushPendingStyleUpdates();
        assertEquals(1.0f, Filter.getFilterOf(image).brightness(), 0.0001f);

        parent.setAttribute("class", "custom-checkbox disabled");
        document.flushPendingStyleUpdates();

        assertTrue(image.getComputedStyle().filter.contains("grayscale"),
                "the descendant image must receive the state-dependent filter");
        assertEquals(0.5f, Filter.getFilterOf(image).brightness(), 0.0001f,
                "the cached image filter must follow the parent state change");
        assertHasFilterBoundary(Drawer.createPaintList(document.body), image);
    }

    @Test
    void filterPopFlushesSharedTextureBatchesBeforeSamplingTheLayer() {
        AtomicInteger sharedBufferFlushes = new AtomicInteger();
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender(recordingRenderService(sharedBufferFlushes));
        try {
            FilterRenderer.pushFilter();
            FilterRenderer.popFilter(Filter.parse("grayscale(100%) brightness(50%)", 1.0f), 1.0f);
            assertEquals(2, sharedBufferFlushes.get(),
                    "both filter FBO boundaries must submit deferred image/SVG batches");
        } finally {
            AuiServices.setRender(previous);
        }
    }

    private static AuiRenderService recordingRenderService(AtomicInteger sharedBufferFlushes) {
        FboHandle main = FboHandle.of(new Object(), 1920, 1080);
        return (AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMainRenderTarget" -> main;
                    case "createOffscreenTarget" -> FboHandle.of(new Object(), 1920, 1080);
                    case "getProjectionMatrix" -> method.getReturnType().getConstructor().newInstance();
                    case "getFilterShader" -> new Object();
                    case "beginMesh" -> MeshBuilder.of(new Object());
                    case "flushSharedBuffers" -> {
                        sharedBufferFlushes.incrementAndGet();
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        if (type == long.class) return 0L;
        return null;
    }

    private static void assertHasFilterBoundary(List<RenderNode> paintList, Object target) {
        assertTrue(paintList.stream().anyMatch(node -> node instanceof RenderNode.FilterPushNode push
                        && push.target() == target),
                "filtered image/SVG must push an offscreen composite layer");
        assertTrue(paintList.stream().anyMatch(node -> node instanceof RenderNode.FilterPopNode pop
                        && pop.target() == target),
                "filtered image/SVG must pop an offscreen composite layer");
    }
}
