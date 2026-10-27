package com.sighs.apricityui.layout;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RectFrameCache;
import com.sighs.apricityui.render.RenderBatchStats;
import com.sighs.apricityui.render.TransformFrameCache;
import com.sighs.apricityui.style.Style;
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

    @Test
    void tickAndHitTestTransformCommitsAreTargetedAndNotRepeatedAtRender() {
        Document document = create("tick-transform");
        document.commitRenderState();
        assertTrue(frame(document));
        Element target = document.getElementById("b");
        Element unrelated = document.getElementById("tail");
        Rect unchanged = unrelated.getLayout().getCommittedRectIfValid();
        RenderBatchStats.beginFrame();
        try {
            target.getStyle().setProperty("transform", "translateX(17px)");
            document.commitStyleRecalc();
            document.commitRenderState();
            assertEquals(17, target.getLayout().getCommittedWorldTransformIfValid().m30(), .001);
            assertSame(unchanged, unrelated.getLayout().getCommittedRectIfValid());
            document.getLayout().prepareRender();
        } finally {
            RenderBatchStats.endFrame();
        }
        assertEquals(0, RenderBatchStats.lastFullCommits());
        assertEquals(1, RenderBatchStats.lastTransformCommits());

        RenderBatchStats.beginFrame();
        try {
            target.getStyle().setProperty("transform", "translateX(29px)");
            document.commitStyleRecalc();
            document.hitTest(new Position(72, 5));
            assertEquals(29, target.getLayout().getCommittedWorldTransformIfValid().m30(), .001);
            document.getLayout().prepareRender();
        } finally {
            RenderBatchStats.endFrame();
        }
        assertEquals(0, RenderBatchStats.lastFullCommits());
        assertEquals(1, RenderBatchStats.lastTransformCommits());
    }

    @Test
    void fullLayoutCountsIdentifyRelayoutAndHitTestSources() {
        Document document = create("reasons");
        document.commitRenderState();
        assertTrue(frame(document));
        long relayout = RenderBatchStats.fullCommitsForReason(RenderBatchStats.FullCommitReason.RELAYOUT);
        document.getElementById("grid").getStyle().setProperty("width", "140px");
        assertTrue(frame(document));
        assertEquals(relayout + 1, RenderBatchStats.fullCommitsForReason(RenderBatchStats.FullCommitReason.RELAYOUT));
        long hitTest = RenderBatchStats.fullCommitsForReason(RenderBatchStats.FullCommitReason.HIT_TEST);
        document.getElementById("grid").getStyle().setProperty("width", "160px");
        document.commitStyleRecalc();
        document.hitTest(new Position(10, 10));
        assertEquals(hitTest + 1, RenderBatchStats.fullCommitsForReason(RenderBatchStats.FullCommitReason.HIT_TEST));
        assertTrue(frame(document));
        assertEquals(0, RenderBatchStats.lastFullCommits());
    }

    @Test
    void pendingLayoutAndActiveMotionShareOneGeometryCommit() {
        Document document = create("coalesced-motion");
        document.commitRenderState();
        assertTrue(frame(document));
        Element grid = document.getElementById("grid");
        grid.getStyle().setProperty("width", "200px");
        document.commitStyleRecalc();
        com.sighs.apricityui.style.Style end = grid.getRawComputedStyle().clone();
        end.transition = "width 1000ms linear -500ms";
        com.sighs.apricityui.style.Style start = end.clone();
        start.width = "100px";
        com.sighs.apricityui.style.Transition.create(grid, start, end);
        try {
            assertFalse(document.commitPendingStyleRecalcForRender(), "only queued geometry, not new styles, should remain");
            assertTrue(frame(document));
            assertEquals(1, RenderBatchStats.lastFullCommits(), "pending layout must not commit before motion and then again after it");
            assertEquals(150, grid.getLayout().getCommittedRectIfValid().getBodyRectSize().width(), 1);
        } finally {
            com.sighs.apricityui.style.Style stopped = end.clone();
            stopped.transition = "none";
            com.sighs.apricityui.style.Transition.create(grid, end, stopped);
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

    // ------------------------------------------------------------------
    // 开屏路径：首次全量几何提交交给渲染分片，避免 tick 单帧同步做完整页
    // ------------------------------------------------------------------

    @Test
    void openingEntryDefersInitialGeometryToRenderSlices() {
        Document document = createDeferred("deferred");
        assertTrue(document.isInitialCommitDeferred());
        document.commitRenderState();
        assertTrue(document.getLayout().needsInitialFullCommit(), "tick 不得同步完成首次全量提交");
        assertNull(document.getElementById("b").getLayout().getCommittedRectIfValid());

        boolean complete = false;
        for (int i = 0; i < 64 && !complete; i++) complete = frame(document);
        assertTrue(complete, "渲染帧必须把几何分片建完");
        assertFalse(document.getLayout().needsInitialFullCommit());
        assertNotNull(document.getElementById("b").getLayout().getCommittedRectIfValid());
    }

    @Test
    void passiveProbeDoesNotForceDeferredGeometryButRealHitTestDoes() {
        Document document = createDeferred("deferred-probe");
        document.commitRenderState();
        assertTrue(document.getLayout().needsInitialFullCommit());

        assertNull(document.hitTestIfComplete(new Position(5, 5)),
                "hover/光标探测在几何未就绪时返回空，而不是把整页同步建出来");
        assertTrue(document.getLayout().needsInitialFullCommit(), "被动探测不得触发展开几何");

        assertNotNull(document.hitTest(new Position(5, 5)), "真实输入必须按完整几何命中");
        assertFalse(document.getLayout().needsInitialFullCommit());
    }

    @Test
    void nonOpeningEntryKeepsSynchronousGeometryAfterTick() {
        String path = "test://layout-lifecycle/plain-create";
        HTML.putTemple(path, SOURCE);
        Document document = new Document(path, false);
        documents.add(document);
        document.refresh();
        assertFalse(document.isInitialCommitDeferred(), "普通创建入口保持原同步语义");
        document.commitRenderState();
        assertFalse(document.getLayout().needsInitialFullCommit());
        assertNotNull(document.getElementById("b").getLayout().getCommittedRectIfValid());
    }

    @Test
    void disabledSlicerKeepsSynchronousCommitForOpeningEntry() {
        String enabled = System.getProperty("apricityui.layout.sliceInitialCommit");
        try {
            System.setProperty("apricityui.layout.sliceInitialCommit", "false");
            Document document = createDeferred("deferred-no-slice");
            document.commitRenderState();
            assertFalse(document.getLayout().needsInitialFullCommit());
            assertNotNull(document.getElementById("b").getLayout().getCommittedRectIfValid());
        } finally {
            restore("apricityui.layout.sliceInitialCommit", enabled);
        }
    }

    private Document createDeferred(String suffix) {
        String path = "test://layout-lifecycle/" + suffix;
        HTML.putTemple(path, SOURCE);
        Document document = Document.create(path, (com.sighs.apricityui.dev.perf.PageOpenTiming) null);
        assertNotNull(document);
        documents.add(document);
        return document;
    }

    // ------------------------------------------------------------------
    // motion 布局根：out-of-flow 只提交自己的子树，in-flow 才整页回退
    // ------------------------------------------------------------------

    @Test
    void outOfFlowMotionCommitsOnlyItsSubtree() {
        Document document = create("motion-subtree");
        Element absolute = document.createElement("div");
        absolute.setAttribute("style", "position:absolute;left:0;top:0;width:20px;height:20px");
        document.body.appendChild(absolute);
        document.commitRenderState();
        for (int i = 0; i < 64 && !frame(document); i++) {
            // 初始几何分片建完
        }
        Element unrelated = document.getElementById("b");
        assertNotNull(unrelated);
        Rect untouched = unrelated.getLayout().getCommittedRectIfValid();
        assertNotNull(untouched);

        Style start = absolute.getRawComputedStyle().clone();
        Style end = start.clone();
        end.left = "40px";
        end.transition = "left 1000ms linear -500ms";
        com.sighs.apricityui.style.Transition.create(absolute, start, end);
        try {
            assertTrue(frame(document));
            assertEquals(0, RenderBatchStats.lastFullCommits(),
                    "绝对定位元素的位置动画不得整页重排");
            assertEquals(1, RenderBatchStats.lastSubtreeCommits(),
                    "只应重建该元素自己的子树");
            assertSame(untouched, unrelated.getLayout().getCommittedRectIfValid(),
                    "无关元素的已提交几何不应被重建");
        } finally {
            Style stopped = end.clone();
            stopped.transition = "none";
            com.sighs.apricityui.style.Transition.create(absolute, end, stopped);
        }
    }

    @Test
    void inFlowMotionStillFallsBackToWholeDocument() {
        Document document = create("motion-flow");
        document.commitRenderState();
        for (int i = 0; i < 64 && !frame(document); i++) {
            // 初始几何分片建完
        }
        Element grid = document.getElementById("grid");
        Style start = grid.getRawComputedStyle().clone();
        Style end = start.clone();
        end.width = "160px";
        end.transition = "width 1000ms linear -500ms";
        com.sighs.apricityui.style.Transition.create(grid, start, end);
        try {
            assertTrue(frame(document));
            assertEquals(0, RenderBatchStats.lastSubtreeCommits(),
                    "in-flow 尺寸变化会影响祖先与兄弟，不能走子树提交");
            assertTrue(RenderBatchStats.lastFullCommits() >= 1);
        } finally {
            Style stopped = end.clone();
            stopped.transition = "none";
            com.sighs.apricityui.style.Transition.create(grid, end, stopped);
        }
    }
}
