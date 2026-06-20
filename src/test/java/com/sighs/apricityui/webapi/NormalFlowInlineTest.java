package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.TextNode;
import com.sighs.apricityui.style.Layout;
import com.sighs.apricityui.style.NormalFlow;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalFlowInlineTest {
    @Test
    void inlineElementTextWrapsAcrossLinesInsideNormalFlow() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 36px;");
        document.body.appendChild(parent);

        Element span = new Element(document, "span");
        span.setAttribute("style", "display: inline;");
        span.appendChild(new TextNode(document, "abcd"));
        parent.appendChild(span);

        Element tail = new Element(document, "span");
        tail.setAttribute("style", "display: inline;");
        tail.appendChild(new TextNode(document, "z"));
        parent.appendChild(tail);

        assertTrue(Position.getOffset(tail).y > 0);
    }

    @Test
    void mixedInlineDescendantsAdvanceFollowingInlineSiblingAfterWrappedText() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 36px;");
        document.body.appendChild(parent);

        parent.appendChild(new TextNode(document, "ab"));

        Element span = new Element(document, "span");
        span.setAttribute("style", "display: inline;");
        span.appendChild(new TextNode(document, "cd"));
        parent.appendChild(span);

        Element tail = new Element(document, "span");
        tail.setAttribute("style", "display: inline;");
        tail.appendChild(new TextNode(document, "ef"));
        parent.appendChild(tail);

        Position tailOffset = Position.getOffset(tail);
        assertTrue(tailOffset.y > 0);
        assertTrue(tailOffset.x >= 0);
    }

    @Test
    void nestedInlineDescendantsWrapRecursivelyAcrossLines() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 36px;");
        document.body.appendChild(parent);

        Element outer = new Element(document, "span");
        outer.setAttribute("style", "display: inline;");
        Element inner = new Element(document, "span");
        inner.setAttribute("style", "display: inline; font-weight: bold;");
        inner.appendChild(new TextNode(document, "abcd"));
        outer.appendChild(inner);
        parent.appendChild(outer);

        Element tail = new Element(document, "span");
        tail.setAttribute("style", "display: inline;");
        tail.appendChild(new TextNode(document, "yz"));
        parent.appendChild(tail);

        assertTrue(Position.getOffset(tail).y > 0);
    }

    @Test
    void fragmentedInlineTextRunsUseOwningDescendantStyle() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 72px; color: #ffffff;");
        document.body.appendChild(parent);

        Element outer = new Element(document, "span");
        outer.setAttribute("style", "display: inline; color: #ffdca5;");
        outer.appendChild(new TextNode(document, "outer "));
        Element inner = new Element(document, "span");
        inner.setAttribute("style", "display: inline; color: #a8e7ff; background-color: rgba(26, 76, 105, 0.52);");
        inner.appendChild(new TextNode(document, "nested-inline-fragment-should-wrap-across-lines"));
        outer.appendChild(inner);
        parent.appendChild(outer);

        boolean sawOuter = false;
        boolean sawInner = false;
        for (NormalFlow.TextRunLayout run : NormalFlow.computeTextRuns(parent)) {
            if (run.owner() == outer) {
                sawOuter = true;
                assertEquals(Text.getFontColor(outer), run.text().color.getValue());
            }
            if (run.owner() == inner) {
                sawInner = true;
                assertEquals(Text.getFontColor(inner), run.text().color.getValue());
                assertTrue(run.lineCount() > 1);
            }
        }

        assertTrue(sawOuter);
        assertTrue(sawInner);
        assertTrue(NormalFlow.isInlineTextPaintedByAncestor(inner));
    }

    @Test
    void inlineInnerTextElementsFragmentLikeTextNodeChildren() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 72px;");
        document.body.appendChild(parent);

        Element prefix = new Element(document, "span");
        prefix.setAttribute("style", "display: inline; background-color: rgba(132, 83, 24, 0.72);");
        prefix.innerText = "prefix";
        Element outer = new Element(document, "span");
        outer.setAttribute("style", "display: inline;");
        outer.innerText = "outer";
        Element inner = new Element(document, "span");
        inner.setAttribute("style", "display: inline;");
        inner.innerText = "nested-inline-fragment-should-wrap-across-lines";
        outer.appendChild(inner);
        Element tail = new Element(document, "span");
        tail.setAttribute("style", "display: inline; background-color: rgba(33, 93, 125, 0.76);");
        tail.innerText = "tail";
        parent.appendChild(prefix);
        parent.appendChild(outer);
        parent.appendChild(tail);

        assertTrue(NormalFlow.isInlineTextPaintedByAncestor(prefix));
        assertTrue(NormalFlow.isInlineTextPaintedByAncestor(tail));
        assertTrue(Position.getOffset(tail).y > 0);
        assertTrue(Size.of(tail).width() < 72);
        for (NormalFlow.TextRunLayout run : NormalFlow.computeTextRuns(parent)) {
            if (run.owner() == prefix || run.owner() == tail) {
                assertTrue(Text.measureLine(run.text(), run.lines().get(0)) < 72);
                assertTrue(run.maxWidth() < 72);
            }
        }
    }

    @Test
    void inlineElementWithAtomicInlineChildStaysAtomic() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 36px;");
        document.body.appendChild(parent);

        Element outer = new Element(document, "span");
        outer.setAttribute("style", "display: inline;");
        Element atomicChild = new Element(document, "span");
        atomicChild.setAttribute("style", "display: inline-block; width: 40px; height: 10px;");
        outer.appendChild(atomicChild);
        parent.appendChild(outer);

        Element tail = new Element(document, "span");
        tail.setAttribute("style", "display: inline-block; width: 4px; height: 10px;");
        parent.appendChild(tail);

        assertEquals(0, Position.getOffset(outer).x);
        assertEquals(10, Position.getOffset(tail).y);
    }

    @Test
    void blockSiblingStartsAfterWrappedInlineContentHeight() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 36px;");
        document.body.appendChild(parent);

        Element span = new Element(document, "span");
        span.setAttribute("style", "display: inline;");
        span.appendChild(new TextNode(document, "abcd"));
        parent.appendChild(span);

        Element block = new Element(document, "div");
        block.setAttribute("style", "width: 10px; height: 8px;");
        parent.appendChild(block);

        assertTrue(Position.getOffset(block).y > 0);
        assertTrue(Layout.computeContentSize(parent).height() >= Position.getOffset(block).y + Size.box(block).height());
    }

    private static void assumeMinecraftClientTextRuntime() {
        Assumptions.assumeTrue(isClassPresent("net.minecraft.client.renderer.MultiBufferSource"));
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
