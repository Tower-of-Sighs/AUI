package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.LayoutCommit;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InitialLayoutRevisionTest {
    private static Document document(String name) {
        String path = "test://layout-revision/" + name;
        HTML.putTemple(path, """
                <html><body style="margin:0"><div style="overflow:scroll;width:100px;height:20px">
                <div style="width:60px;height:40px;position:relative;z-index:1"></div>
                <div style="width:80px;height:30px;transform:translate(2px,3px)"></div>
                </div></body></html>
                """);
        Document document = new Document(path, false);
        document.refresh();
        return document;
    }

    private static void finish(Document document) {
        int calls = 0;
        while (!LayoutCommit.commitInitialSlice(document, 1)) {
            assertTrue(++calls < 1000, "stable geometry must finish");
        }
    }

    private static Map<Element, String> geometry(Document document) {
        Map<Element, String> result = new java.util.LinkedHashMap<>();
        for (Element target : document.getLayout().getCommitTargets()) {
            Rect rect = target.getLayout().getCommittedRect();
            if (rect == null) continue;
            result.put(target, rect.position.x + "," + rect.position.y + ":"
                    + rect.getElementSize().width() + "," + rect.getElementSize().height());
        }
        return result;
    }

    @Test
    void inPlaceReorderPublishesRevisionAndRestartsUniqueSequence() {
        Size.setViewportOverride(640, 480);
        try {
            Document document = document("reorder");
            assertFalse(LayoutCommit.commitInitialSlice(document, 1));
            var oldSlice = document.getLayout().getInitialCommitSlice();
            var originalPaintList = document.getPaintList();
            var targets = document.getLayout().getCommitTargets();
            assertSame(targets, document.getLayout().getCommitTargets(), "dedupe once per revision");
            assertTrue(targets.size() < originalPaintList.size(), "paint phases must share geometry targets");
            document.markDirty(document.body, Drawer.REORDER);
            document.commitRenderStateForMotion();
            assertSame(originalPaintList, document.getPaintList(), "exercise in-place splice, not replacement");
            assertTrue(document.getLayout().getPaintListRevision() > oldSlice.paintListRevision);
            assertFalse(LayoutCommit.commitInitialSlice(document, 1));
            assertNotSame(oldSlice, document.getLayout().getInitialCommitSlice());
            finish(document);
            Map<Element, String> sliced = geometry(document);
            LayoutCommit.commitNow(document, RenderBatchStats.FullCommitReason.EXPLICIT);
            assertEquals(sliced, geometry(document));
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void alreadyVisitedGeometryInvalidatedBetweenSlicesIsRevalidated() {
        Size.setViewportOverride(640, 480);
        try {
            Document document = document("dependency");
            assertFalse(LayoutCommit.commitInitialSlice(document, 1));
            var slice = document.getLayout().getInitialCommitSlice();
            Element visited = slice.targets.get(0);
            visited.getLayout().invalidateLayoutSubtree();
            finish(document);
            assertTrue(visited.getLayout().hasCommittedRect(visited.getLayout().rectDependency(document)));
            Map<Element, String> sliced = geometry(document);
            LayoutCommit.commitNow(document, RenderBatchStats.FullCommitReason.EXPLICIT);
            assertEquals(sliced, geometry(document));
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void insertionDuringSameGenerationIncludesNewTargets() {
        Size.setViewportOverride(640, 480);
        try {
            Document document = document("insert");
            long generation = document.getRefreshGeneration();
            assertFalse(LayoutCommit.commitInitialSlice(document, 1));
            Element inserted = new Element(document, "div");
            document.body.appendChild(inserted);
            document.commitRenderStateForMotion();
            assertEquals(generation, document.getRefreshGeneration());
            finish(document);
            assertTrue(document.getLayout().getCommitTargets().contains(inserted));
            assertNotNull(inserted.getLayout().getCommittedRect());
            Map<Element, String> sliced = geometry(document);
            LayoutCommit.commitNow(document, RenderBatchStats.FullCommitReason.EXPLICIT);
            assertEquals(sliced, geometry(document));
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void scrollbarTailRemainsBehindCompletePaintGate() {
        Size.setViewportOverride(640, 480);
        try {
            Document document = document("scroll-tail");
            assertFalse(LayoutCommit.commitInitialSlice(document, 1));
            var slice = document.getLayout().getInitialCommitSlice();
            while (slice.cursor < slice.targets.size()) {
                assertFalse(LayoutCommit.commitInitialSlice(document, 1));
            }
            assertTrue(document.getLayout().needsInitialFullCommit());
            assertEquals(0, slice.scrollCursor);
            assertFalse(LayoutCommit.commitInitialSlice(document, 1));
            assertEquals(1, slice.scrollCursor, "scroll metrics also consume a bounded slice");
            assertTrue(document.getLayout().needsInitialFullCommit());
            finish(document);
            assertFalse(document.getLayout().needsInitialFullCommit());
            assertFalse(document.getLayout().isInitialCommitSlicing());
        } finally {
            Size.clearViewportOverride();
        }
    }
}
