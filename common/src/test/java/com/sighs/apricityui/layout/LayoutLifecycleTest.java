package com.sighs.apricityui.layout;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RectFrameCache;
import com.sighs.apricityui.render.RenderBatchStats;
import com.sighs.apricityui.render.TransformFrameCache;
import com.sighs.apricityui.style.StyleFrameCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LayoutLifecycleTest {
    private final List<Document> documents = new ArrayList<>();
    private static final String SOURCE = """
            <html><head><style>
              body { margin:0; width:320px; }
              #row { display:flex; width:240px; align-items:flex-start; }
              #grid { display:grid; flex:none; width:120px;
                      grid-template-columns:40px 80px; grid-auto-rows:20px; }
              table { width:120px; border-spacing:0; flex:none; }
              td { padding:0; } td div { height:20px; }
              #tail { height:10px; }
              #scroller { overflow:scroll; width:100px; height:40px; }
              #content { height:200px; }
            </style></head><body>
              <div id="row"><div id="grid"><div id="a"></div><div id="b"></div></div>
                <table><tr><td><div></div></td><td><div></div></td></tr></table></div>
              <div id="tail"></div>
              <div id="scroller"><div id="content"></div></div>
            </body></html>
            """;

    @AfterEach
    void cleanup() {
        documents.forEach(Document::remove);
    }

    @Test
    void mixedLayoutsRefreshQueriesAndCommittedGeometryAfterStyleAndTreeChanges() {
        Document document = create("mixed");
        document.commitRenderState();
        Element grid = document.getElementById("grid");
        Element b = document.getElementById("b");
        Element tail = document.getElementById("tail");
        assertEquals(40, Position.getOffset(b).x, 0.001);
        assertEquals(20, Position.getOffset(tail).y, 0.001);

        grid.getStyle().setProperty("grid-template-columns", "60px 60px");
        document.flushPendingStyleUpdates();
        assertEquals(60, Position.getOffset(b).x, 0.001);
        assertEquals(60, Size.of(b).width(), 0.001);
        assertTrue(frame(document));
        assertEquals(60, b.getLayout().getCommittedRectIfValid().getBodyRectPosition().x, 0.001);

        Element extra = document.createElement("div");
        grid.appendChild(extra);
        assertEquals(40, Size.of(grid).height(), 0.001);
        assertTrue(frame(document));
        assertEquals(40, tail.getLayout().getCommittedRectIfValid().getBodyRectPosition().y, 0.001);
        grid.removeChild(extra);
        assertEquals(20, Size.of(grid).height(), 0.001);
        assertTrue(frame(document));
        assertEquals(20, tail.getLayout().getCommittedRectIfValid().getBodyRectPosition().y, 0.001);
    }

    @Test
    void textNodeMutationInvalidatesMeasuredAndCommittedContent() {
        Document document = create("text");
        Element text = document.createElement("div");
        text.setAttribute("style", "width:60px;font-size:16px;line-height:20px;overflow-wrap:anywhere");
        TextNode node = new TextNode(document, "one");
        text.appendChild(node);
        document.body.appendChild(text);
        document.commitRenderState();
        double before = Size.of(text).height();
        node.setData("one two three four five six seven eight nine ten");
        assertTrue(frame(document));
        assertTrue(Size.of(text).height() > before);
        assertEquals(Size.of(text).height(), text.getLayout().getCommittedRectIfValid().getBodyRectSize().height(), 0.001);
    }

    @Test
    void unchangedPaintAndTransformFramesKeepMeasurementAndAvoidFullCommits() {
        Document document = create("paths");
        document.commitRenderState();
        assertTrue(frame(document)); // Scrollbar gutters settle after the first metrics commit.
        Element element = document.getElementById("b");
        Size size = Size.of(element);
        Rect rect = element.getLayout().getCommittedRectIfValid();
        assertTrue(frame(document));
        assertEquals(0, RenderBatchStats.lastFullCommits());
        assertEquals(0, RenderBatchStats.lastTransformCommits());

        element.getStyle().setProperty("color", "red");
        assertTrue(frame(document));
        assertEquals(0, RenderBatchStats.lastFullCommits());
        assertSame(size, Size.of(element));

        element.getStyle().setProperty("transform", "translateX(10px)");
        assertTrue(frame(document));
        assertEquals(0, RenderBatchStats.lastFullCommits());
        assertEquals(1, RenderBatchStats.lastTransformCommits());
        assertSame(size, Size.of(element));
        assertEquals(10, element.getLayout().getCommittedWorldTransformIfValid().m30(), 0.001);
        Position after = element.getLayout().getCommittedRectIfValid().getBodyRectPosition();
        assertEquals(rect.getBodyRectPosition().x, after.x, 0.001);
        assertEquals(rect.getBodyRectPosition().y, after.y, 0.001);

        document.getElementById("grid").getStyle().setProperty("width", "140px");
        assertTrue(frame(document));
        assertEquals(1, RenderBatchStats.lastFullCommits());
    }

    @Test
    void scrollTranslationUpdatesGeometryWithoutFullLayout() {
        Document document = create("scroll");
        document.commitRenderState();
        assertTrue(frame(document));
        Element scroller = document.getElementById("scroller");
        Element content = document.getElementById("content");
        double before = content.getLayout().getCommittedRectIfValid().getBodyRectPosition().y;
        scroller.setScrollTop(15);
        assertTrue(frame(document));
        assertEquals(0, RenderBatchStats.lastFullCommits());
        assertTrue(content.getLayout().getCommittedRectIfValid().getBodyRectPosition().y < before);
    }

    @Test
    void renderLifecycleCompletesInitialSlicesAndReleasesFrameScopes() {
        String enabled = System.getProperty("apricityui.layout.sliceInitialCommit");
        String budget = System.getProperty("apricityui.layout.initialCommitSliceMs");
        try {
            System.setProperty("apricityui.layout.sliceInitialCommit", "true");
            System.setProperty("apricityui.layout.initialCommitSliceMs", "0.000001");
            Document document = create("sliced");
            int frames = 1;
            while (!frame(document)) assertTrue(++frames < 500);
            assertTrue(frames > 1);
            assertFalse(document.getLayout().needsInitialFullCommit());
            assertFalse(document.getLayout().isInitialCommitSlicing());
            assertFalse(LayoutMeasureCache.isActive());
            assertNotNull(document.getElementById("b").getLayout().getCommittedRectIfValid());
            assertTrue(frame(document));
            assertEquals(0, RenderBatchStats.lastFullCommits());
        } finally {
            restore("apricityui.layout.sliceInitialCommit", enabled);
            restore("apricityui.layout.initialCommitSliceMs", budget);
        }
    }

    private Document create(String suffix) {
        String path = "test://layout-lifecycle/" + suffix;
        HTML.putTemple(path, SOURCE);
        Document document = new Document(path, false);
        documents.add(document);
        document.refresh();
        return document;
    }

    private static boolean frame(Document document) {
        RenderBatchStats.beginFrame();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        LayoutMeasureCache.begin();
        StyleFrameCache.begin();
        try {
            return document.getLayout().prepareRender();
        } finally {
            StyleFrameCache.end();
            LayoutMeasureCache.end();
            TransformFrameCache.end();
            RectFrameCache.end();
            RenderBatchStats.endFrame();
        }
    }

    private static void restore(String property, String value) {
        if (value == null) System.clearProperty(property); else System.setProperty(property, value);
    }
}
