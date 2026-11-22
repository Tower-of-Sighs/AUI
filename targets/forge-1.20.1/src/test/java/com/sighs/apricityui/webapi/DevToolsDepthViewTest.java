package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.dev.devtools.DevToolsController;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.task.FrameTaskScheduler;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.StyleFrameCache;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DevToolsDepthViewTest {
    @Test
    void rightDragPansWithoutRotatingOrSelecting() throws Exception {
        Size.setViewportOverride(1600, 900);
        Element.register("CANVAS", (document, tag) -> new Canvas(document));
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        String path = "test://depth-view-pan";
        HTML.putTemple(path, """
                <html><body><div id="page" style="position:fixed;left:400px;top:200px;width:800px;height:500px"></div></body></html>
                """);
        Document target = Document.create(path);
        try {
            target.markDirty(target.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            target.commitRenderState();
            assertTrue(DevTools.selectDocument(target));
            Document tool = DevTools.getToolDocument();
            tool.querySelector("#depthReset").click();
            openDepthView(tool);
            Canvas canvas = (Canvas) tool.querySelector("#depthCanvas");
            int width = canvas.getBitmapWidth();
            int height = canvas.getBitmapHeight();
            int[] original = canvas.getContext("2d").getImageData(0, 0, width, height).data;
            Element.DOMRect rect = canvas.getBoundingClientRect();
            Position start = new Position(rect.x + rect.width / 2, rect.y + rect.height / 2);
            Position end = new Position(start.x + 24 * rect.width / width, start.y + 16 * rect.height / height);
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", start, 1, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", end, -1, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", end, 1, false), tool, canvas);
            assertTrue(DevTools.prepareRender(tool), "right drag must repaint the translated view");
            int[] translated = canvas.getContext("2d").getImageData(0, 0, width, height).data;
            assertFalse(Arrays.equals(original, translated), "right drag must move the layers");
            int changedPixels = 0;
            int paintedPixels = 0;
            for (int y = 16; y < height; y++) {
                for (int x = 24; x < width; x++) {
                    int source = ((y - 16) * width + x - 24) * 4;
                    if (original[source + 3] != 0) paintedPixels++;
                    for (int channel = 0; channel < 4; channel++) {
                        if (Math.abs(original[source + channel] - translated[(y * width + x) * 4 + channel]) > 12) {
                            changedPixels++;
                            break;
                        }
                    }
                }
            }
            assertTrue(changedPixels < Math.max(8, paintedPixels / 100),
                    "panning must preserve the layer shapes apart from antialiasing: " + changedPixels + "/" + paintedPixels);
            assertEquals("true", tool.querySelector("#depthViewBtn").getAttribute("aria-pressed"),
                    "releasing the right button must not select a DOM node");
            MouseEvent contextMenu = new MouseEvent("contextmenu", end, 1, false);
            contextMenu.cancelable = true;
            MouseEvent.dispatchToTarget(contextMenu, tool, canvas);
            assertTrue(contextMenu.defaultPrevented, "right drag must not open a context menu");
            tool.querySelector("#depthReset").click();
            assertTrue(DevTools.prepareRender(tool));
            assertArrayEquals(original, canvas.getContext("2d").getImageData(0, 0, width, height).data,
                    "reset must also restore panning");
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", start, 1, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", start, 1, false), tool, canvas);
            assertEquals("true", tool.querySelector("#depthViewBtn").getAttribute("aria-pressed"),
                    "a right click must not select a DOM node");
            for (String id : List.of("depthRotateLeft", "depthRotateRight", "depthTiltUp", "depthTiltDown",
                    "depthZoomIn", "depthZoomOut", "depthFacing")) assertNull(tool.querySelector("#" + id));
            assertNull(tool.querySelector(".depth-legend"));
            dragView(tool, canvas, 1, 30, 20);
            assertTrue(DevTools.prepareRender(tool));
            Position center = overlapPoint(canvas, target.getViewportSize(), target.getViewportSize().height() * 0.225, -0.65, 0.15);
            Position panned = new Position(center.x + 30, center.y + 20);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", panned, -1, false), tool, canvas);
            assertTrue(DevTools.prepareRender(tool));
            assertTrue(tool.querySelector("#depthSelection").getTextContent().contains("div#page"),
                    "hover hit testing must follow the panned layers");
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", panned, 0, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", panned, 0, false), tool, tool.body);
            assertDomSelection(tool, target.querySelector("#page"));
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            if (target != null) target.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void hoverHighlightsLayersWithoutChangingDomSelection() throws Exception {
        Size.setViewportOverride(1600, 900);
        Element.register("CANVAS", (document, tag) -> new Canvas(document));
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        String path = "test://depth-view-hover";
        HTML.putTemple(path, """
                <html><body>
                <div id="left" style="position:fixed;left:200px;top:200px;width:500px;height:500px;z-index:1"></div>
                <div id="right" style="position:fixed;left:900px;top:200px;width:500px;height:500px;z-index:2"></div>
                </body></html>
                """);
        Document target = Document.create(path);
        try {
            target.markDirty(target.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            target.commitRenderState();
            Element left = target.querySelector("#left");
            Element right = target.querySelector("#right");
            assertTrue(DevTools.selectElement(left));
            Document tool = DevTools.getToolDocument();
            tool.querySelector("#depthReset").click();
            openDepthView(tool);
            Canvas canvas = (Canvas) tool.querySelector("#depthCanvas");
            assertEquals(0, selectedHeight(canvas), "entering 3D must not highlight the selected DOM node");
            String idle = tool.querySelector("#depthSelection").getTextContent();
            assertEquals("", idle);
            int[] original = canvas.getContext("2d").getImageData(0, 0,
                    canvas.getBitmapWidth(), canvas.getBitmapHeight()).data;
            Size viewport = target.getViewportSize();
            double scale = Math.min(canvas.getWidth() / (viewport.width() + viewport.height()),
                    canvas.getHeight() / (viewport.width() + viewport.height()));
            Element.DOMRect rect = canvas.getBoundingClientRect();
            Position point = null;
            for (Element element : List.of(left, right)) {
                double x = canvas.getWidth() / 2.0
                        + (element.getBoundingClientRect().x + 250 - viewport.width() / 2) * Math.cos(-0.65) * scale;
                point = new Position(rect.x + x * rect.width / canvas.getWidth(), rect.y + rect.height / 2);
                MouseEvent.dispatchToTarget(new MouseEvent("mousemove", point, -1, false), tool, canvas);
                assertTrue(DevTools.prepareRender(tool), "hovering a different layer must redraw its highlight");
                assertTrue(selectedHeight(canvas) > 0, "the hovered layer must turn orange without clicking");
                assertTrue(tool.querySelector("#depthSelection").getTextContent().contains("div#" + element.id),
                        "hover " + element.id + ": " + tool.querySelector("#depthSelection").getTextContent());
                assertTrue(tool.querySelector("#domTree [data-node-id=\"" + left.uuid + "\"]")
                        .getClassNames().contains("selected"), "hovering must preserve the DOM selection");
                MouseEvent.dispatchToTarget(new MouseEvent("mousemove", point, -1, false), tool, canvas);
                assertFalse(DevTools.prepareRender(tool), "moving within the same layer needs no redraw");
            }
            Position blank = new Position(rect.x + rect.width / 2, rect.y + 2);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", blank, -1, false), tool, canvas);
            assertTrue(DevTools.prepareRender(tool), "moving to empty space must clear the highlight");
            assertArrayEquals(original, canvas.getContext("2d").getImageData(0, 0,
                    canvas.getBitmapWidth(), canvas.getBitmapHeight()).data);
            assertEquals(idle, tool.querySelector("#depthSelection").getTextContent());
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", point, -1, false), tool, canvas);
            assertTrue(DevTools.prepareRender(tool), "hovering again after clearing must redraw");
            MouseEvent.dispatchToTarget(new MouseEvent("mouseleave", point, -1, false), tool, canvas);
            assertTrue(DevTools.prepareRender(tool), "leaving the canvas must clear the highlight");
            assertArrayEquals(original, canvas.getContext("2d").getImageData(0, 0,
                    canvas.getBitmapWidth(), canvas.getBitmapHeight()).data);
            tool.querySelector("#depthViewBtn").click();
            openDepthView(tool);
            assertArrayEquals(original, canvas.getContext("2d").getImageData(0, 0,
                    canvas.getBitmapWidth(), canvas.getBitmapHeight()).data, "reopening starts without a highlight");
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            if (target != null) target.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void generatedDecorationsDoNotAddDomLayers() throws Exception {
        Size.setViewportOverride(1600, 900);
        Element.register("CANVAS", (document, tag) -> new Canvas(document));
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        String path = "test://depth-view-generated-decorations";
        HTML.putTemple(path, """
                <html><body><div class="header" style="position:fixed;left:400px;top:200px;
                width:800px;height:300px"><div class="logo"></div>
                <div id="navPath" class="nav-path"></div><div class="header-actions"></div></div></body></html>
                """);
        Document target = Document.create(path);
        try {
            target.markDirty(target.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            target.commitRenderState();
            assertTrue(DevTools.selectElement(target.querySelector(".header")));
            Document tool = DevTools.getToolDocument();
            tool.querySelector("#depthReset").click();
            openDepthView(tool);
            Canvas canvas = (Canvas) tool.querySelector("#depthCanvas");
            int[] original = canvas.getContext("2d").getImageData(0, 0,
                    canvas.getBitmapWidth(), canvas.getBitmapHeight()).data;
            target.registerStylesheet("""
                    .header::before, .header::after {
                        content:'';position:absolute;left:0;top:0;width:100%;height:100%;background:red;
                    }
                    .header::after { z-index:9; }
                    """, path + "#style", 0);
            target.commitStyleRecalc();
            target.commitRenderState();
            assertEquals(2, target.querySelector(".header").getRenderChildren().stream()
                    .filter(Element::isPseudoElement).count(), "the fixture must generate both decorations");
            assertTrue(target.getPaintList().stream().map(RenderNode::getRenderNodeTarget)
                    .anyMatch(element -> element != null && element.isPseudoElement()),
                    "the renderer's paint list must contain generated elements");
            tool.querySelector("#depthViewBtn").click();
            openDepthView(tool);
            assertArrayEquals(original, canvas.getContext("2d").getImageData(0, 0,
                    canvas.getBitmapWidth(), canvas.getBitmapHeight()).data,
                    "generated decorations must not become additional selectable DOM layers");
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            if (target != null) target.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void snapshotStaysStillUntilReopened() throws Exception {
        Size.setViewportOverride(1600, 900);
        Element.register("CANVAS", (document, tag) -> new Canvas(document));
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        String path = "test://depth-view-snapshot";
        Animation.registerKeyframe("depthMotion", 0, Map.of("left", "600px"));
        Animation.registerKeyframe("depthMotion", 100, Map.of("left", "1000px"));
        HTML.putTemple(path, """
                <html><body><div id="moving" style="position:fixed;left:600px;top:300px;
                width:400px;height:300px;z-index:42;animation:depthMotion 1s linear infinite paused"></div></body></html>
                """);
        Document target = Document.create(path);
        try {
            target.markDirty(target.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            target.commitRenderState();
            assertTrue(DevTools.selectDocument(target));
            Document tool = DevTools.getToolDocument();
            tool.querySelector("#depthViewBtn").click();
            tool.querySelector("#depthReset").click();
            tool.commitStyleRecalc();
            tool.markDirty(tool.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            tool.commitRenderState();
            assertTrue(DevTools.prepareRender(tool));
            Canvas canvas = (Canvas) tool.querySelector("#depthCanvas");
            int[] snapshot = canvas.getContext("2d").getImageData(0, 0,
                    canvas.getBitmapWidth(), canvas.getBitmapHeight()).data;
            Element moving = target.querySelector("#moving");
            double initialX = moving.getBoundingClientRect().x;
            moving.setAttribute("style", "position:fixed;left:600px;top:300px;width:400px;height:300px;"
                    + "animation:depthMotion 1s linear -0.8s infinite paused;z-index:99");
            target.commitStyleRecalc();
            StyleFrameCache.begin();
            target.prepareRender();
            assertTrue(moving.getBoundingClientRect().x > initialX + 200,
                    "the source animation must advance independently of the snapshot, x="
                            + moving.getBoundingClientRect().x);
            DevTools.refresh();
            dragView(tool, canvas, 0, 20, 0);
            DevTools.prepareRender(tool);
            tool.querySelector("#depthReset").click();
            assertTrue(DevTools.prepareRender(tool));
            assertArrayEquals(snapshot, canvas.getContext("2d").getImageData(0, 0,
                    canvas.getBitmapWidth(), canvas.getBitmapHeight()).data,
                    "redraws must keep captured bounds and z-index labels");
            assertFalse(DevTools.prepareRender(tool), "an unchanged snapshot needs no redraw");
            tool.querySelector("#depthViewBtn").click();
            tool.querySelector("#depthViewBtn").click();
            tool.commitStyleRecalc();
            tool.commitRenderState();
            assertTrue(DevTools.prepareRender(tool));
            assertFalse(Arrays.equals(snapshot, canvas.getContext("2d").getImageData(0, 0,
                    canvas.getBitmapWidth(), canvas.getBitmapHeight()).data),
                    "reopening the view must capture the current document");
        } finally {
            StyleFrameCache.end();
            if (DevTools.isOpen()) DevTools.toggle();
            if (target != null) target.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void clickingLayerOpensAndRevealsItsDomNode() throws Exception {
        Size.setViewportOverride(1600, 900);
        Element.register("CANVAS", (document, tag) -> new Canvas(document));
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        String path = "test://depth-view-dom-reveal";
        HTML.putTemple(path, "<html><body>" + "<div></div>".repeat(80) + """
                <section id="branch"><div id="layer" style="position:fixed;left:0;top:0;
                width:100vw;height:100vh;z-index:42"></div></section></body></html>
                """);
        Document target = Document.create(path);
        try {
            target.markDirty(target.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            target.commitRenderState();
            assertTrue(DevTools.selectDocument(target));
            Document tool = DevTools.getToolDocument();
            Element layer = target.querySelector("#layer");
            assertNull(tool.querySelector("#domTree [data-node-id=\"" + layer.uuid + "\"]"));
            tool.querySelector("#depthViewBtn").click();
            tool.querySelector("#depthReset").click();
            tool.commitStyleRecalc();
            tool.markDirty(tool.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            tool.commitRenderState();
            assertTrue(DevTools.prepareRender(tool));
            Canvas canvas = (Canvas) tool.querySelector("#depthCanvas");
            LinkedHashSet<Element> painted = new LinkedHashSet<>();
            for (RenderNode node : target.getPaintList()) {
                if (node instanceof RenderNode.ElementPhaseNode phase && phase.phase() == Base.RenderPhase.BODY
                        && !RenderNode.shouldSkip(phase.target())) painted.add(phase.target());
                else if (node instanceof RenderNode.ElementBackgroundNode background
                        && !RenderNode.shouldSkip(background.target())) painted.add(background.target());
            }
            Size viewport = target.getViewportSize();
            double scale = Math.min(canvas.getWidth() / (viewport.width() + viewport.height()),
                    canvas.getHeight() / (viewport.width() + viewport.height()));
            double spacing = Math.max(viewport.height() * 0.45 / Math.max(1, painted.size() - 1), 3.5 / scale);
            double depth = (new ArrayList<>(painted).indexOf(layer) - (painted.size() - 1) / 2.0) * spacing;
            layer.remove();
            DevTools.refresh();
            clickOverlap(tool, canvas, viewport, depth, -0.65, 0.15);
            assertEquals("true", tool.querySelector("#depthViewBtn").getAttribute("aria-pressed"),
                    "a removed snapshot node must not be opened in the DOM inspector");
            target.querySelector("#branch").append(layer);
            DevTools.refresh();
            clickOverlap(tool, canvas, viewport, depth, -0.65, 0.15);
            assertEquals("false", tool.querySelector("#depthViewBtn").getAttribute("aria-pressed"),
                    "clicking the layer should switch to DOM inspection");
            assertTrue(tool.querySelector("#depthView").getClassNames().contains("hidden"));
            assertFalse(tool.querySelector("#domSection").getClassNames().contains("hidden"));
            Element row = tool.querySelector("#domTree [data-node-id=\"" + layer.uuid + "\"]");
            assertNotNull(row, "the previously collapsed ancestor must be expanded");
            assertTrue(row.getClassNames().contains("selected"));
            for (int frame = 0; frame < 16; frame++) {
                FrameTaskScheduler.tick();
                tool.commitStyleRecalc();
                tool.commitRenderState();
            }
            assertTrue(tool.querySelector("#domTree").getTargetScrollTop() > 0,
                    "the selected node must be revealed outside the initial DOM viewport");
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            if (target != null) target.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void denseLayersRemainDistinctFromAbove() throws Exception {
        Size.setViewportOverride(1600, 900);
        Element.register("CANVAS", (document, tag) -> new Canvas(document));
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        StringBuilder html = new StringBuilder("<html><body>");
        for (int i = 0; i < 60; i++) {
            html.append("<div style=\"position:fixed;left:500px;top:200px;width:700px;height:600px;background:red\"></div>");
        }
        html.append("</body></html>");
        String path = "test://depth-view-dense";
        HTML.putTemple(path, html.toString());
        Document target = Document.create(path);
        try {
            target.markDirty(target.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            target.commitRenderState();
            assertTrue(DevTools.selectDocument(target));
            Document tool = DevTools.getToolDocument();
            tool.querySelector("#depthViewBtn").click();
            tool.querySelector("#depthReset").click();
            tool.commitStyleRecalc();
            tool.markDirty(tool.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            tool.commitRenderState();
            assertTrue(DevTools.prepareRender(tool));
            Canvas canvas = (Canvas) tool.querySelector("#depthCanvas");
            Element.DOMRect rect = canvas.getBoundingClientRect();
            Position start = new Position(rect.x + rect.width / 2, rect.y + rect.height / 2);
            Position end = new Position(start.x + 65, start.y + (Math.PI / 2 - 0.15) * 100);
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", start, 0, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", end, -1, false), tool, tool.body);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", end, 0, false), tool, tool.body);
            assertTrue(DevTools.prepareRender(tool));
            int width = canvas.getBitmapWidth(), height = canvas.getBitmapHeight();
            int[] data = canvas.getContext("2d").getImageData(0, 0, width, height).data;
            int bands = 0;
            HashSet<Integer> colors = new HashSet<>();
            boolean previous = false;
            for (int y = 0; y < height; y++) {
                int offset = (y * width + width / 2) * 4;
                boolean painted = data[offset + 3] != 0;
                if (painted) colors.add(data[offset] << 16 | data[offset + 1] << 8 | data[offset + 2]);
                if (painted && !previous) bands++;
                previous = painted;
            }
            assertTrue(bands >= 30, "dense edge-on layers must retain visible gaps, got " + bands);
            assertTrue(colors.size() >= 6, "depth layers should have distinguishable colors");
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            if (target != null) target.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void verticalDragKeepsPageReadable() throws Exception {
        Size.setViewportOverride(1600, 900);
        Element.register("CANVAS", (document, tag) -> new Canvas(document));
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        String path = "test://depth-view-upright";
        HTML.putTemple(path, """
                <html><body><div id="page" style="position:fixed;left:500px;top:200px;
                width:700px;height:600px;background:red"></div></body></html>
                """);
        Document target = Document.create(path);
        try {
            target.markDirty(target.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            target.commitRenderState();
            assertTrue(DevTools.selectElement(target.querySelector("#page")));
            Document tool = DevTools.getToolDocument();
            tool.querySelector("#depthViewBtn").click();
            tool.querySelector("#depthReset").click();
            tool.commitStyleRecalc();
            tool.markDirty(tool.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            tool.commitRenderState();
            assertTrue(DevTools.prepareRender(tool));
            Canvas canvas = (Canvas) tool.querySelector("#depthCanvas");
            hoverOverlap(tool, canvas, target.getViewportSize(), target.getViewportSize().height() * 0.225, -0.65, 0.15);
            int initialHeight = selectedHeight(canvas);
            assertTrue(initialHeight > 30, "the selected page must be visible");

            Element.DOMRect rect = canvas.getBoundingClientRect();
            Position start = new Position(rect.x + rect.width / 2, rect.y + rect.height / 2);
            Position end = new Position(start.x, start.y + 57);
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", start, 0, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", end, -1, false), tool, tool.body);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", end, 0, false), tool, tool.body);
            assertTrue(DevTools.prepareRender(tool));
            assertEquals("", tool.querySelector("#depthSelection").getTextContent(),
                    "rotation must clear the previous hover highlight");
            hoverOverlap(tool, canvas, target.getViewportSize(), target.getViewportSize().height() * 0.225, -0.65, 0.72);
            assertTrue(selectedHeight(canvas) >= initialHeight * 0.75, "vertical drag must not flatten the page");

            tool.querySelector("#depthReset").click();
            assertTrue(DevTools.prepareRender(tool));
            end = new Position(start.x + 65, start.y + (Math.PI / 2 - 0.15) * 100);
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", start, 0, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", end, -1, false), tool, tool.body);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", end, 0, false), tool, tool.body);
            assertTrue(DevTools.prepareRender(tool));
            assertTrue(DevTools.selectElement(target.body), "select body before side click");
            assertTrue(DevTools.prepareRender(tool), "redraw after DOM selection");
            LinkedHashSet<Element> painted = new LinkedHashSet<>();
            for (RenderNode node : target.getPaintList()) {
                if (node instanceof RenderNode.ElementPhaseNode phase && phase.phase() == Base.RenderPhase.BODY
                        && !RenderNode.shouldSkip(phase.target())) painted.add(phase.target());
                else if (node instanceof RenderNode.ElementBackgroundNode background
                        && !RenderNode.shouldSkip(background.target())) painted.add(background.target());
            }
            Size viewport = target.getViewportSize();
            double scale = Math.min(canvas.getWidth() / (viewport.width() + viewport.height()),
                    canvas.getHeight() / (viewport.width() + viewport.height()));
            double depth = viewport.height() * (0.45 * new ArrayList<>(painted).indexOf(target.querySelector("#page"))
                    / Math.max(1, painted.size() - 1) - 0.225);
            double sideX = canvas.getWidth() / 2.0 + (850 - viewport.width() / 2.0) * scale;
            double sideY = canvas.getHeight() / 2.0 - depth * scale;
            Position side = new Position(rect.x + sideX * rect.width / canvas.getWidth(),
                    rect.y + sideY * rect.height / canvas.getHeight());
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", side, -1, false), tool, canvas);
            assertTrue(DevTools.prepareRender(tool), "hover the edge-on layer");
            assertTrue(selectedHeight(canvas) >= 3 && selectedHeight(canvas) <= 12,
                    "an edge-on layer should show its thickness");
            assertTrue(tool.querySelector("#inspectHighlight").getClassNames().contains("show"),
                    "hovering a layer highlights its element");
            MouseEvent.dispatchToTarget(new MouseEvent("mouseleave", side, -1, false), tool, canvas);
            assertFalse(tool.querySelector("#inspectHighlight").getClassNames().contains("show"),
                    "leaving the view clears the highlight");
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", side, 0, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", side, 0, false), tool, tool.body);
            assertDomSelection(tool, target.querySelector("#page"));
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            if (target != null) target.remove();
            Size.clearViewportOverride();
        }
    }

    private static int selectedHeight(Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        int[] data = canvas.getContext("2d").getImageData(0, 0, width, height).data;
        int top = height;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 4;
                if (data[i] > 130 && data[i + 1] < 150 && data[i + 2] < 60 && data[i + 3] > 0) {
                    top = Math.min(top, y);
                    bottom = y;
                }
            }
        }
        return Math.max(0, bottom - top + 1);
    }

    @Test
    void selectionFollowsVisibleFaceAfterTurningOver() throws Exception {
        Size.setViewportOverride(1600, 900);
        Element.register("CANVAS", (document, tag) -> new Canvas(document));
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        String path = "test://depth-view-back-face";
        HTML.putTemple(path, """
                <html><body>
                <div id="back" style="position:fixed;left:0;top:0;width:100vw;height:100vh;z-index:1"></div>
                <div id="front" style="position:fixed;left:0;top:0;width:100vw;height:100vh;z-index:2"></div>
                </body></html>
                """);
        Document target = Document.create(path);
        try {
            target.markDirty(target.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            target.commitRenderState();
            assertTrue(DevTools.selectDocument(target));
            Document tool = DevTools.getToolDocument();
            tool.querySelector("#depthViewBtn").click();
            tool.querySelector("#depthReset").click();
            tool.commitStyleRecalc();
            tool.markDirty(tool.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            tool.commitRenderState();
            assertTrue(DevTools.prepareRender(tool));
            Canvas canvas = (Canvas) tool.querySelector("#depthCanvas");
            Element back = target.querySelector("#back");
            Element front = target.querySelector("#front");
            LinkedHashSet<Element> ordered = new LinkedHashSet<>();
            for (RenderNode node : target.getPaintList()) {
                if (node instanceof RenderNode.ElementPhaseNode phase && phase.phase() == Base.RenderPhase.BODY
                        && !RenderNode.shouldSkip(phase.target())) ordered.add(phase.target());
                else if (node instanceof RenderNode.ElementBackgroundNode background
                        && !RenderNode.shouldSkip(background.target())) ordered.add(background.target());
            }
            List<Element> order = new ArrayList<>(ordered);
            assertTrue(order.indexOf(back) >= 0 && order.indexOf(front) > order.indexOf(back));
            Size viewport = target.getViewportSize();
            double depth = viewport.height() * (0.45 * (order.indexOf(back) + order.indexOf(front))
                    / (2.0 * Math.max(1, order.size() - 1)) - 0.225);
            hoverOverlap(tool, canvas, viewport, depth, -0.65, 0.15);
            double scale = Math.min(canvas.getWidth() / (viewport.width() + viewport.height()),
                    canvas.getHeight() / (viewport.width() + viewport.height()));
            int pixelX = (int) ((canvas.getWidth() / 2.0 + depth * Math.sin(-0.65) * scale)
                    * canvas.getBitmapWidth() / canvas.getWidth());
            int pixelY = (int) ((canvas.getHeight() / 2.0 - depth * Math.cos(-0.65) * Math.sin(0.15) * scale)
                    * canvas.getBitmapHeight() / canvas.getHeight());
            int[] selectedPixel = canvas.getContext("2d").getImageData(pixelX, pixelY, 1, 1).data;
            assertArrayEquals(new int[] {255, 174, 70, 255}, selectedPixel,
                    "the selected front layer must be opaque");
            clickOverlap(tool, canvas, viewport, depth, -0.65, 0.15);
            assertDomSelection(tool, front);
            openDepthView(tool);

            Element.DOMRect rect = canvas.getBoundingClientRect();
            Position start = new Position(rect.x + rect.width / 2, rect.y + rect.height / 2);
            Position end = new Position(start.x + Math.PI * 100, start.y);
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", start, 0, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", end, -1, false), tool, tool.body);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", end, 0, false), tool, tool.body);
            assertTrue(DevTools.prepareRender(tool));
            clickOverlap(tool, canvas, viewport, depth, -0.65 + Math.PI, 0.15);
            assertDomSelection(tool, back);
            openDepthView(tool);

            tool.querySelector("#depthReset").click();
            assertTrue(DevTools.prepareRender(tool));
            end = new Position(start.x, start.y + Math.PI * 100);
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", start, 0, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", end, -1, false), tool, tool.body);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", end, 0, false), tool, tool.body);
            assertTrue(DevTools.prepareRender(tool));
            clickOverlap(tool, canvas, viewport, depth, -0.65, 0.15 + Math.PI);
            assertDomSelection(tool, back);
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            if (target != null) target.remove();
            Size.clearViewportOverride();
        }
    }

    private static void clickOverlap(Document tool, Canvas canvas, Size viewport, double depth, double yaw, double pitch) {
        Position point = overlapPoint(canvas, viewport, depth, yaw, pitch);
        MouseEvent.dispatchToTarget(new MouseEvent("mousedown", point, 0, false), tool, canvas);
        MouseEvent.dispatchToTarget(new MouseEvent("mouseup", point, 0, false), tool, tool.body);
    }

    private static void dragView(Document tool, Canvas canvas, int button, double deltaX, double deltaY) {
        Element.DOMRect rect = canvas.getBoundingClientRect();
        Position start = new Position(rect.x + rect.width / 2, rect.y + rect.height / 2);
        Position end = new Position(start.x + deltaX, start.y + deltaY);
        MouseEvent.dispatchToTarget(new MouseEvent("mousedown", start, button, false), tool, canvas);
        MouseEvent.dispatchToTarget(new MouseEvent("mousemove", end, -1, false), tool, tool.body);
        MouseEvent.dispatchToTarget(new MouseEvent("mouseup", end, button, false), tool, tool.body);
    }

    private static void hoverOverlap(Document tool, Canvas canvas, Size viewport, double depth, double yaw, double pitch) {
        MouseEvent.dispatchToTarget(new MouseEvent("mousemove", overlapPoint(canvas, viewport, depth, yaw, pitch), -1, false),
                tool, canvas);
        assertTrue(DevTools.prepareRender(tool));
    }

    private static Position overlapPoint(Canvas canvas, Size viewport, double depth, double yaw, double pitch) {
        double scale = Math.min(canvas.getWidth() / (viewport.width() + viewport.height()),
                canvas.getHeight() / (viewport.width() + viewport.height()));
        double x = canvas.getWidth() / 2.0 + depth * Math.sin(yaw) * scale;
        double y = canvas.getHeight() / 2.0 - depth * Math.cos(yaw) * Math.sin(pitch) * scale;
        Element.DOMRect rect = canvas.getBoundingClientRect();
        return new Position(rect.x + x * rect.width / canvas.getWidth(),
                rect.y + y * rect.height / canvas.getHeight());
    }

    private static void assertDomSelection(Document tool, Element target) {
        assertEquals("false", tool.querySelector("#depthViewBtn").getAttribute("aria-pressed"));
        assertTrue(tool.querySelector("#depthView").getClassNames().contains("hidden"));
        assertFalse(tool.querySelector("#domSection").getClassNames().contains("hidden"));
        Element row = tool.querySelector("#domTree [data-node-id=\"" + target.uuid + "\"]");
        assertNotNull(row, "the clicked layer must have a DOM row");
        assertTrue(row.getClassNames().contains("selected"));
    }

    private static void openDepthView(Document tool) {
        tool.querySelector("#depthViewBtn").click();
        tool.commitStyleRecalc();
        tool.markDirty(tool.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        tool.commitRenderState();
        assertTrue(DevTools.prepareRender(tool));
    }

    @Test
    void drawsAndSelectsLayersAndSwitchesModes() throws Exception {
        Size.setViewportOverride(1600, 900);
        Element.register("CANVAS", (document, tag) -> new Canvas(document));
        HTML.putTemple(DevToolsController.PATH, Files.readString(Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/devtools/devtools.html")));
        String path = "test://depth-view";
        HTML.putTemple(path, """
                <html><body><div id="back" style="position:fixed;left:100px;top:100px;
                width:80px;height:80px;z-index:-1"></div><div id="layer" style="position:fixed;left:600px;top:300px;
                width:400px;height:300px;z-index:42;background:red"></div></body></html>
                """);
        Document target = Document.create(path);
        try {
            target.markDirty(target.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            target.commitRenderState();
            assertTrue(DevTools.selectDocument(target), "select target");
            Document tool = DevTools.getToolDocument();
            Element button = tool.querySelector("#depthViewBtn");
            button.click();
            tool.querySelector("#depthReset").click();
            assertEquals("true", button.getAttribute("aria-pressed"));
            assertTrue(tool.querySelector("#domSection").getClassNames().contains("hidden"), "hide inspector");
            tool.commitStyleRecalc();
            tool.markDirty(tool.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            tool.commitRenderState();
            assertTrue(DevTools.prepareRender(tool), "prepare 3D view");
            Canvas canvas = (Canvas) tool.querySelector("#depthCanvas");
            int[] pixels = canvas.getContext("2d").getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data;
            boolean painted = false;
            for (int i = 3; i < pixels.length; i += 4) painted |= pixels[i] != 0;
            assertTrue(painted, "3D view should draw visible layers");

            Element.DOMRect rect = canvas.getBoundingClientRect();
            Size viewport = target.getViewportSize();
            clickOverlap(tool, canvas, viewport, viewport.height() * 0.225, -0.65, 0.15);
            assertDomSelection(tool, target.querySelector("#layer"));
            openDepthView(tool);
            assertEquals(0, selectedHeight(canvas), "the clicked DOM node must not remain highlighted in 3D");
            hoverOverlap(tool, canvas, viewport, viewport.height() * 0.225, -0.65, 0.15);
            assertTrue(tool.querySelector("#depthSelection").getTextContent().contains("div#layer"),
                    "selection: " + tool.querySelector("#depthSelection").getTextContent());
            assertTrue(tool.querySelector("#depthSelection").getTextContent().contains("z-index: 42"), "selected z-index");
            assertNull(tool.querySelector("#depthNext"));
            assertNull(tool.querySelector("#depthPrevious"));
            pixels = canvas.getContext("2d").getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data;
            dragView(tool, canvas, 0, 20, 0);
            assertTrue(DevTools.prepareRender(tool));
            assertFalse(Arrays.equals(pixels, canvas.getContext("2d")
                    .getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data), "rotation changes the view");

            pixels = canvas.getContext("2d").getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data;
            Position dragStart = new Position(rect.x + rect.width / 2, rect.y + rect.height / 2);
            Position dragEnd = new Position(dragStart.x, dragStart.y + 80);
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", dragStart, 0, false), tool, canvas);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", dragEnd, -1, false), tool, tool.body);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", dragEnd, 0, false), tool, tool.body);
            assertTrue(DevTools.prepareRender(tool));
            assertFalse(Arrays.equals(pixels, canvas.getContext("2d")
                    .getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data), "vertical drag changes the view");
            tool.querySelector("#depthReset").click();
            assertTrue(DevTools.prepareRender(tool));
            int[] resetPixels = canvas.getContext("2d").getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data;
            dragView(tool, canvas, 0, 0, 20);
            assertTrue(DevTools.prepareRender(tool));
            assertFalse(Arrays.equals(resetPixels, canvas.getContext("2d")
                    .getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data), "vertical drag changes the view");
            tool.querySelector("#depthReset").click();
            assertTrue(DevTools.prepareRender(tool));
            assertArrayEquals(resetPixels, canvas.getContext("2d")
                    .getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data, "reset restores both axes");
            MouseEvent wheel = new MouseEvent("wheel", dragStart, -1, false);
            wheel.deltaY = -1;
            wheel.cancelable = true;
            MouseEvent.dispatchToTarget(wheel, tool, canvas);
            assertTrue(wheel.defaultPrevented, "zooming must not scroll the surrounding panel");
            assertTrue(DevTools.prepareRender(tool));
            assertFalse(Arrays.equals(resetPixels, canvas.getContext("2d")
                    .getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data), "the wheel must still zoom");
            tool.querySelector("#depthReset").click();
            assertTrue(DevTools.prepareRender(tool));
            assertArrayEquals(resetPixels, canvas.getContext("2d")
                    .getImageData(0, 0, canvas.getWidth(), canvas.getHeight()).data, "reset restores zoom");

            tool.querySelector(".console-btn").click();
            assertTrue(tool.querySelector("#depthView").getClassNames().contains("hidden"), "hide 3D for console");
            assertFalse(tool.querySelector("#consoleContent").getClassNames().contains("hidden"));
            button.click();
            assertFalse(tool.querySelector("#depthView").getClassNames().contains("hidden"));
            assertTrue(tool.querySelector("#consoleContent").getClassNames().contains("hidden"), "hide console for 3D");
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            if (target != null) target.remove();
            Size.clearViewportOverride();
        }
    }
}
