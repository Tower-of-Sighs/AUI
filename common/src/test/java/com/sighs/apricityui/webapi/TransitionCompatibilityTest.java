package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.style.Transition;
import com.sighs.apricityui.layout.CssLength;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.style.StyleFrameCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionCompatibilityTest {
    @Test
    void transformTransitionPadsNoneWithIdentityRotation() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("button");
        document.body.appendChild(element);

        Style start = new Style();
        start.transform = "none";
        Style end = start.clone();
        end.transform = "rotate(90deg)";
        end.transition = "transform 150ms ease";

        Transition.create(element, start, end);
        assertTrue(Transition.isActive(element));

        Style entering = end.clone();
        Transition.updateStyle(element, entering);
        assertEquals("rotateX(0.00deg) rotateY(0.00deg) rotateZ(0.00deg)", entering.transform);

        Style reset = end.clone();
        reset.transition = "none";
        Transition.create(element, end, reset);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void transformTransitionPadsRotationWithIdentityWhenReturningToNone() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("button");
        document.body.appendChild(element);

        Style start = new Style();
        start.transform = "rotate(90deg)";
        Style end = start.clone();
        end.transform = "none";
        end.transition = "transform 150ms ease";

        Transition.create(element, start, end);
        assertTrue(Transition.isActive(element));

        Style leaving = end.clone();
        Transition.updateStyle(element, leaving);
        assertEquals("rotateX(0.00deg) rotateY(0.00deg) rotateZ(90.00deg)", leaving.transform);

        Style reset = end.clone();
        reset.transition = "none";
        Transition.create(element, end, reset);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void rapidScaleReversalDoesNotRestoreAnUnchangedAxis() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);

        Style collapsed = new Style();
        collapsed.transform = "scaleY(0)";
        collapsed.transition = "transform 250ms ease";
        Style expanded = collapsed.clone();
        expanded.transform = "scaleY(1)";

        Transition.create(element, collapsed, expanded);
        Transition.create(element, expanded, collapsed);

        Style firstFrame = collapsed.clone();
        Transition.updateStyle(element, firstFrame);
        assertEquals("scale(1.00, 0.00)", firstFrame.transform);

        Style followingFrame = collapsed.clone();
        Transition.updateStyle(element, followingFrame);
        assertEquals("scaleY(0)", followingFrame.transform);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void backgroundTransitionReturnsToTransparentInsteadOfOpaqueBlack() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("span");
        document.body.appendChild(element);

        Style hovered = element.getComputedStyle().clone();
        hovered.backgroundColor = "rgba(139, 92, 246, 0.1)";
        Style resting = element.getComputedStyle().clone();
        resting.transition = "background-color 0ms linear";

        Transition.create(element, hovered, resting);
        Style sampled = resting.clone();
        Transition.updateStyle(element, sampled);

        assertEquals("rgba(0, 0, 0, 0.000)", sampled.backgroundColor);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void autoDimensionsRemainDiscreteWhileNumericLengthsTransition() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("select");
        document.body.appendChild(element);

        Style start = new Style();
        start.height = "14px";
        start.paddingLeft = "2px";

        Style end = start.clone();
        end.height = "auto";
        end.paddingLeft = "14px";
        end.transition = "all 200ms ease";

        Transition.create(element, start, end);
        assertTrue(Transition.isActive(element), "The numeric padding change should still transition");

        Style sampled = end.clone();
        Transition.updateStyle(element, sampled);
        assertEquals("auto", sampled.height, "auto must not be coerced into a pixel transition");
        assertEquals("2.00px", sampled.paddingLeft);

        Style settled = end.clone();
        settled.transition = "none";
        Transition.create(element, end, settled);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void keywordOnlyChangeDoesNotCreateNumericTransition() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("select");
        document.body.appendChild(element);

        Style start = new Style();
        start.height = "14px";
        Style end = start.clone();
        end.height = "auto";
        end.transition = "all 200ms ease";

        Transition.create(element, start, end);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void boxShadowTransitionsFromNoneInsteadOfAppearingAtItsFinalOffset() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);

        Style start = new Style();
        start.boxShadow = "none";
        Style end = start.clone();
        end.boxShadow = "6px 6px 0 #1a1a1a";
        end.transition = "all 10000ms linear";

        Transition.create(element, start, end);
        assertTrue(Transition.isActive(element));

        Style sampled = end.clone();
        Transition.updateStyle(element, sampled);
        Box.Shadow shadow = Box.parseShadowList(sampled.boxShadow).get(0);
        assertTrue(shadow.x() < 1, "the first frame must start near the zero-offset shadow");
        assertTrue(shadow.y() < 1, "the first frame must start near the zero-offset shadow");
        assertTrue(shadow.color().getA() < 64, "the shadow must fade in from transparent");

        Style settled = end.clone();
        settled.transition = "none";
        Transition.create(element, end, settled);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void gridFractionTracksTransitionBetweenNumericLists() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);

        Style collapsed = new Style();
        collapsed.gridTemplateRows = "0fr";
        collapsed.transition = "grid-template-rows 2000ms linear -1000ms";
        Style expanded = collapsed.clone();
        expanded.gridTemplateRows = "1fr";

        Transition.create(element, collapsed, expanded);
        Style middle = expanded.clone();
        Transition.updateStyle(element, middle);

        double value = Double.parseDouble(middle.gridTemplateRows.replace("fr", ""));
        assertEquals(.5, value, .00001);
        assertTrue(Transition.isActive(element));
    }

    @Test
    void unsupportedGridTrackStructureRemainsDiscrete() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);

        Style start = new Style();
        start.gridTemplateRows = "0fr";
        Style end = start.clone();
        end.gridTemplateRows = "minmax(0, 1fr)";
        end.transition = "grid-template-rows 200ms linear";

        Transition.create(element, start, end);
        assertFalse(Transition.isActive(element));
    }
    @Test
    void numericTracksAndGapKeepPercentagesAtTheMidpoint() {
        Element element = TestDocumentFactory.createDocument().createElement("div");
        Style start = new Style();
        start.gridTemplateColumns = "20px 10% 1fr";
        start.update("gap", "4px 10%");
        Style end = start.clone();
        end.gridTemplateColumns = "40px calc(20px + 30%) 3fr";
        end.update("gap", "12px 30%");
        end.transition = "all 1000ms linear -500ms";
        Transition.create(element, start, end);
        Style sampled = end.clone();
        Transition.updateStyle(element, sampled);
        var tracks = com.sighs.apricityui.style.ShorthandParser.splitGridTokens(sampled.gridTemplateColumns);
        assertEquals(30, CssLength.parse(tracks.get(0)).resolve(200), .001);
        assertEquals(50, CssLength.parse(tracks.get(1)).resolve(200), .001);
        assertEquals(90, CssLength.parse(tracks.get(1)).resolve(400), .001);
        assertEquals("2.000000fr", tracks.get(2));
        assertEquals(8, CssLength.parse(sampled.rowGap).resolve(200), .001);
        assertEquals(40, CssLength.parse(sampled.columnGap).resolve(200), .001);
        assertTrue(Transition.affectsLayout(element));
    }

    @Test
    void gapShorthandAndDelayKeepTheirStartValues() {
        Element element = TestDocumentFactory.createDocument().createElement("div");
        Style start = new Style();
        start.update("gap", "2px 6px");
        Style end = start.clone();
        end.update("gap", "10px 14px");
        end.transition = "gap 1000ms linear 200ms";
        Transition.create(element, start, end);
        Style sampled = end.clone();
        Transition.updateStyle(element, sampled);
        assertEquals(2, CssLength.parse(sampled.rowGap).resolve(100), .001);
        assertEquals(6, CssLength.parse(sampled.columnGap).resolve(100), .001);
    }

    @Test
    void reversalStartsAtCurrentListAndZeroDurationSettles() {
        Element element = TestDocumentFactory.createDocument().createElement("div");
        Style start = new Style();
        start.gridTemplateColumns = "20px 1fr";
        Style end = start.clone();
        end.gridTemplateColumns = "100px 3fr";
        end.transition = "grid-template-columns 1000000ms linear -500000ms";
        Transition.create(element, start, end);
        Transition.updateStyle(element, end.clone());
        Style reversed = start.clone();
        reversed.transition = "grid-template-columns 1000000ms linear";
        Transition.create(element, end, reversed);
        Style sampled = reversed.clone();
        Transition.updateStyle(element, sampled);
        var tracks = com.sighs.apricityui.style.ShorthandParser.splitGridTokens(sampled.gridTemplateColumns);
        assertEquals(60, CssLength.parse(tracks.get(0)).resolve(100), .1);
        assertEquals(2, Double.parseDouble(tracks.get(1).replace("fr", "")), .01);
        Style instant = end.clone();
        instant.transition = "grid-template-columns 0ms linear";
        Transition.create(element, reversed, instant);
        assertFalse(Transition.updateStyle(element, instant));
        assertEquals(100, CssLength.parse(instant.gridTemplateColumns.split(" ")[0]).resolve(100), .001);
    }

    @Test
    void structuresAndIntrinsicKeywordsCancelAnActiveTrackTransition() {
        Element element = TestDocumentFactory.createDocument().createElement("div");
        for (String target : new String[]{"auto", "none", "subgrid", "1fr 2fr", "minmax(0,1fr)",
                "repeat(2,20px)", "[a] 1fr", "fit-content(30px)", "40px"}) {
            Style start = new Style();
            start.gridTemplateRows = "0fr";
            Style end = start.clone();
            end.gridTemplateRows = "1fr";
            end.transition = "all 1000ms linear";
            Transition.create(element, start, end);
            Style discrete = end.clone();
            discrete.gridTemplateRows = target;
            Transition.create(element, end, discrete);
            assertFalse(Transition.isActive(element), target);
            assertEquals(target, discrete.gridTemplateRows);
        }
    }

    @Test
    void gridMotionInvalidatesDescendantsAndPreservesSelection() {
        Document document = TestDocumentFactory.createDocument();
        Element grid = document.createElement("div");
        grid.setAttribute("style", "display:grid;width:200px;grid-template:40px/40px 160px");
        document.body.appendChild(grid);
        Element first = document.createElement("div"), text = document.createElement("div");
        text.setAttribute("style", "font-size:12px;line-height:14px;user-select:text");
        text.setTextContent("selected text wraps as the columns move");
        grid.appendChild(first); grid.appendChild(text);
        document.commitRenderState();
        Style before = grid.getComputedStyle().clone();
        document.getDocumentSelection().collapse(text, 0);
        document.getDocumentSelection().extendTo(text, 8);
        grid.getStyle().setProperty("grid-template-columns", "140px 60px");
        document.flushPendingStyleUpdates();
        Style after = grid.getComputedStyle().clone();
        after.transition = "grid-template-columns 1000ms linear -500ms";
        Transition.create(grid, before, after);
        StyleFrameCache.begin();
        try {
            assertTrue(document.stepMotionRender());
            assertTrue(document.drainMotionLayoutRoots().contains(grid));
            document.commitRenderState();
            var rect = text.getBoundingClientRect();
            assertEquals(90, rect.x - grid.getBoundingClientRect().x, .1);
            assertEquals(110, Size.of(text).width(), .1);
            assertEquals("selected", document.getDocumentSelection().getSelectedText(document));
            assertEquals(text, document.hitTest(new Position(rect.x + 2, rect.y + 2)));
        } finally { StyleFrameCache.end(); }
        Style stopped = after.clone();
        stopped.transition = "none";
        Transition.create(grid, stopped, stopped);
        document.commitRenderState();
        assertFalse(Transition.isActive(grid));
        assertEquals(140, Position.getOffset(text).x, .001);
        assertEquals(60, Size.of(text).width(), .001);
    }

}
