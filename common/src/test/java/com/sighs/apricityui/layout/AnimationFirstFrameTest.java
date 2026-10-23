package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.render.RectFrameCache;
import com.sighs.apricityui.render.RenderBatchStats;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.render.TransformFrameCache;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The first painted frame of an element with a backwards-filled entrance animation must already be at its start value. */
class AnimationFirstFrameTest {
    @Test
    void entranceAnimationAppliesOnTheFirstPreparedFrame() {
        Document document = TestDocumentFactory.createDocument();
        applyStylesheet(document, """
                .card { width:100px; height:100px; animation: cardIn 0.4s linear backwards; }
                @keyframes cardIn { from { opacity: 0; } to { opacity: 1; } }
                """);
        document.body.setAttribute("style", "width:400px;height:300px;");

        Element card = new Element(document, "div");
        card.setAttribute("class", "card");
        document.body.appendChild(card);

        assertEquals(0.0, firstFrameOpacity(document, card), 0.001,
                "first frame must already sample the animation start value");
    }

    @Test
    void lazilyComputedStyleStartsAnimationWithoutWaitingForAStyleRecalc() {
        Document document = TestDocumentFactory.createDocument();
        applyStylesheet(document, """
                .card { width:100px; height:100px; animation: cardIn 0.4s linear backwards; }
                @keyframes cardIn { from { opacity: 0; } to { opacity: 1; } }
                """);
        document.body.setAttribute("style", "width:400px;height:300px;");

        Element card = new Element(document, "div");
        card.setAttribute("class", "card");
        document.body.appendChild(card);
        // Layout/geometry may build the style before the queued recalc runs.
        card.getComputedStyle();

        StyleFrameCache.begin();
        try {
            document.stepMotionRender();
            assertEquals(0.0, Double.parseDouble(card.getComputedStyle().opacity), 0.001,
                    "motion must track an element as soon as its style is built");
        } finally {
            StyleFrameCache.end();
        }
    }

    @Test
    void replacingChildrenDropsOldCardsFromTheFirstNewFrame() {
        Document document = TestDocumentFactory.createDocument();
        applyStylesheet(document, """
                .card { width:100px; height:100px; }
                .card.entering { animation: cardIn 0.4s linear backwards; }
                @keyframes cardIn { from { opacity: 0; } to { opacity: 1; } }
                """);
        document.body.setAttribute("style", "width:400px;height:300px;");

        Element grid = new Element(document, "div");
        document.body.appendChild(grid);
        Element oldCard = new Element(document, "div");
        oldCard.setAttribute("class", "card");
        grid.appendChild(oldCard);
        frame(document);

        grid.clearChildren();
        Element newCard = new Element(document, "div");
        newCard.setAttribute("class", "card entering");
        grid.appendChild(newCard);

        frame(document);

        assertTrue(paints(document, newCard), "the new card must be in the first frame");
        assertFalse(paints(document, oldCard), "the removed card must not survive into the first frame");
    }

    @Test
    void folderSwapBetweenTicksSamplesStaggeredCardsBeforePainting() {
        Document document = TestDocumentFactory.createDocument();
        applyStylesheet(document, """
                .file-card { width:100px; height:100px; }
                .file-card.entering { animation: folderCardIn 0.4s cubic-bezier(0.16,1,0.3,1) backwards; }
                @keyframes folderCardIn {
                    from { opacity:0; transform:translateY(20px) scale(0.95); }
                    to { opacity:1; transform:translateY(0) scale(1); }
                }
                """);
        document.body.setAttribute("style", "width:400px;height:300px;");
        Element grid = new Element(document, "div");
        document.body.append(grid);
        Element oldCard = new Element(document, "div");
        oldCard.setAttribute("class", "file-card");
        grid.append(oldCard);
        document.tickFrame();
        frame(document);

        // ResourceManager builds detached cards (including their icon/name children)
        // and replaces the grid from an input callback, before the next client tick.
        grid.clearChildren();
        Element[] cards = new Element[3];
        for (int i = 0; i < cards.length; i++) {
            Element card = new Element(document, "div");
            card.setAttribute("class", "file-card entering");
            card.setAttribute("style", "animation-delay:" + (i * 0.05d) + "s;");
            Element icon = new Element(document, "div");
            icon.setAttribute("class", "file-icon");
            card.append(icon);
            card.append(new Element(document, "div"));
            grid.append(card);
            cards[i] = card;
        }

        RenderBatchStats.beginFrame();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        LayoutMeasureCache.begin();
        StyleFrameCache.begin();
        try {
            assertTrue(document.getLayout().prepareRender());
            assertFalse(paints(document, oldCard));
            for (Element card : cards) {
                assertEquals(0.0, Double.parseDouble(card.getComputedStyle().opacity), 0.001);
                assertEquals(0.0, com.sighs.apricityui.style.Filter.getFilterOf(card).opacity(), 0.001);
                assertFalse(com.sighs.apricityui.style.Filter.isDisabled(card));
                assertTrue(card.getComputedStyle().transform.contains("20"), card.getComputedStyle().transform);
                assertTrue(card.getComputedStyle().transform.contains("0.95"), card.getComputedStyle().transform);
                var paintList = document.getPaintList();
                int push = paintList.indexOf(new RenderNode.FilterPushNode(card));
                int pop = paintList.indexOf(new RenderNode.FilterPopNode(card));
                assertTrue(push >= 0 && pop > push, "first frame must composite the entire card");
                assertTrue(paintList.subList(push + 1, pop).stream().anyMatch(node ->
                        RenderNode.getRenderNodeTarget(node) == card.getChildren().get(0)),
                        "icon must be inside the card opacity group");
            }
        } finally {
            StyleFrameCache.end();
            LayoutMeasureCache.end();
            TransformFrameCache.end();
            RectFrameCache.end();
            RenderBatchStats.endFrame();
        }
    }

    private static boolean paints(Document document, Element element) {
        for (RenderNode node : document.getPaintList()) {
            if (RenderNode.getRenderNodeTarget(node) == element) return true;
        }
        return false;
    }

    private static void frame(Document document) {
        RenderBatchStats.beginFrame();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        LayoutMeasureCache.begin();
        StyleFrameCache.begin();
        try {
            document.getLayout().prepareRender();
        } finally {
            StyleFrameCache.end();
            LayoutMeasureCache.end();
            TransformFrameCache.end();
            RectFrameCache.end();
            RenderBatchStats.endFrame();
        }
    }

    private static double firstFrameOpacity(Document document, Element card) {
        RenderBatchStats.beginFrame();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        LayoutMeasureCache.begin();
        StyleFrameCache.begin();
        try {
            document.getLayout().prepareRender();
            return Double.parseDouble(card.getComputedStyle().opacity);
        } finally {
            StyleFrameCache.end();
            LayoutMeasureCache.end();
            TransformFrameCache.end();
            RectFrameCache.end();
            RenderBatchStats.endFrame();
        }
    }

    private static void applyStylesheet(Document document, String css) {
        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS(css, cache, "test://animation-first-frame.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
    }
}
