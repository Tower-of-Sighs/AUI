package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.NormalFlow;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalFlowInlineTest {
    @Test
    void negativeLengthVerticalAlignLowersAtomicInlineBox() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "font-size:16px;line-height:24px;width:120px;");
        document.body.appendChild(parent);

        Element baseline = new Element(document, "span");
        baseline.setAttribute("style", "display:inline-block;vertical-align:baseline;width:24px;height:24px;");
        Element lowered = new Element(document, "span");
        lowered.setAttribute("style", "display:inline-block;vertical-align:-0.125em;width:24px;height:24px;");
        parent.appendChild(baseline);
        parent.appendChild(lowered);

        assertEquals(2.0d, Position.getOffset(lowered).y - Position.getOffset(baseline).y, 0.01d);
    }

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
        parent.setAttribute("style", "width: 36px; overflow-wrap: anywhere;");
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
        parent.setAttribute("style", "width: 36px; overflow-wrap: anywhere;");
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
    void nestedStyleOnlyInlineWrappersArePaintedOnlyByTheirBlockAncestor() throws Exception {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");
        Path globalStyle = Path.of("../../common/src/main/resources/assets/apricityui/apricity/global.css");
        CSS.readCSS(Files.readString(globalStyle), document.CSSCache, globalStyle.toString());
        document.rebuildSelectorIndex();

        Element paragraph = new Element(document, "p");
        document.body.appendChild(paragraph);

        Element underline = new Element(document, "u");
        Element strong = new Element(document, "strong");
        strong.appendChild(new TextNode(document, "Ctrl/B"));
        underline.appendChild(strong);
        paragraph.appendChild(underline);

        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(paragraph);
        assertEquals(1, runs.size());
        assertEquals("Ctrl/B", runs.get(0).text().content);
        assertEquals(strong, runs.get(0).owner());
        assertTrue(runs.get(0).text().isBold());
        assertTrue(runs.get(0).text().isUnderlined());
        assertTrue(NormalFlow.isInlineTextPaintedByAncestor(underline),
                "the outer underline wrapper must not repaint its nested text run");
        assertTrue(NormalFlow.isInlineTextPaintedByAncestor(strong),
                "the innermost style wrapper must not repaint its text run");
    }

    @Test
    void fragmentedInlineTextRunsUseOwningDescendantStyle() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 72px; color: #ffffff; overflow-wrap: anywhere;");
        document.body.appendChild(parent);

        Element outer = new Element(document, "span");
        outer.setAttribute("style", "display: inline; color: #ffdca5;");
        outer.appendChild(new TextNode(document, "outer "));
        Element inner = new Element(document, "span");
        inner.setAttribute("style", "display: inline; color: #a8e7ff; background-color: rgba(26, 76, 105, 0.52);");
        inner.appendChild(new TextNode(document, "nested-inline-fragment-should-wrap-across-lines"));
        outer.appendChild(inner);
        parent.appendChild(outer);

        List<NormalFlow.TextRunLayout> parentRuns = NormalFlow.computeTextRuns(parent);
        boolean sawOuter = false;
        for (NormalFlow.TextRunLayout run : parentRuns) {
            if (run.owner() == outer) {
                sawOuter = true;
                assertEquals(Text.getFontColor(outer), run.text().color.getValue());
            }
        }

        assertTrue(sawOuter);
        List<NormalFlow.TextRunLayout> innerRuns = NormalFlow.computeTextRuns(inner);
        assertFalse(innerRuns.isEmpty());
        assertTrue(innerRuns.stream().allMatch(run -> run.owner() == inner));
        assertTrue(innerRuns.stream().allMatch(run -> run.text().color.getValue() == Text.getFontColor(inner)));
        assertEquals("anywhere", Text.of(inner).overflowWrap);
        assertTrue(Text.wrap(Text.of(inner), 72).lines().size() > 1);
        assertTrue(innerRuns.stream().anyMatch(run -> run.lineCount() > 1));
        assertFalse(NormalFlow.isInlineTextPaintedByAncestor(inner));
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

        assertFalse(NormalFlow.isInlineTextPaintedByAncestor(prefix));
        assertFalse(NormalFlow.isInlineTextPaintedByAncestor(tail));
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
        atomicChild.setAttribute("style", "display: inline-block; vertical-align: top; width: 40px; height: 10px;");
        outer.appendChild(atomicChild);
        parent.appendChild(outer);

        Element tail = new Element(document, "span");
        tail.setAttribute("style", "display: inline-block; vertical-align: top; width: 4px; height: 10px;");
        parent.appendChild(tail);

        assertEquals(0, Position.getOffset(outer).x);
        assertTrue(Position.getOffset(tail).y > 10,
                "the atomic inline child must force the following item onto a later line");
    }

    @Test
    void positionedInlineTextPaintsInItsOwnStackingLayer() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "button");
        parent.setAttribute("style", "display: block; width: 120px;");
        document.body.appendChild(parent);

        Element label = new Element(document, "span");
        label.setAttribute("style", "display: inline; position: relative; z-index: 1;");
        label.appendChild(new TextNode(document, "COPY"));
        parent.appendChild(label);

        assertFalse(NormalFlow.isInlineTextPaintedByAncestor(label));
        assertEquals(0, NormalFlow.computeTextRuns(parent).stream()
                .filter(run -> run.owner() == label)
                .count());
        assertEquals(1, NormalFlow.computeTextRuns(label).stream()
                .filter(run -> run.owner() == label)
                .count());
        assertTrue(Size.box(label).width() > 0);
    }

    @Test
    void nowrapKeepsMultipleInlineFragmentsOnOneLine() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element content = new Element(document, "span");
        content.setAttribute("style", "display: block; width: 72px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;");
        document.body.appendChild(content);

        Element tag = new Element(document, "span");
        tag.appendChild(new TextNode(document, "<div"));
        Element name = new Element(document, "span");
        name.appendChild(new TextNode(document, " class="));
        Element value = new Element(document, "span");
        value.appendChild(new TextNode(document, "\"cursor-layer cursor-normal\">"));
        content.appendChild(tag);
        content.appendChild(name);
        content.appendChild(value);

        assertEquals(0, Position.getOffset(tag).y);
        assertEquals(0, Position.getOffset(name).y);
        assertEquals(0, Position.getOffset(value).y);
        assertEquals(1, NormalFlow.computeTextRuns(content).stream()
                .mapToInt(NormalFlow.TextRunLayout::lineCount)
                .max().orElse(0));
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

    @Test
    void whitespaceTextBetweenInlineBlocksCollapsesToOneSpaceGap() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        // 对照组：无空白节点，两个 inline-block 紧贴。
        Element tight = new Element(document, "div");
        tight.setAttribute("style", "width: 200px;");
        document.body.appendChild(tight);
        Element tightA = inlineBlock(document);
        Element tightB = inlineBlock(document);
        tight.appendChild(tightA);
        tight.appendChild(tightB);

        // 实验组：中间夹换行+缩进的纯空白文本节点，浏览器折叠成一个空格。
        Element gapped = new Element(document, "div");
        gapped.setAttribute("style", "width: 200px;");
        document.body.appendChild(gapped);
        Element gapA = inlineBlock(document);
        gapped.appendChild(gapA);
        gapped.appendChild(new TextNode(document, "\n    "));
        Element gapB = inlineBlock(document);
        gapped.appendChild(gapB);
        gapped.appendChild(new TextNode(document, "\n"));

        double tightX = Position.getOffset(tightB).x;
        double gapX = Position.getOffset(gapB).x;
        assertTrue(gapX > tightX,
                "whitespace between inline-level boxes must collapse to a rendered space");

        // 中间的空白产生一个空格 run；容器尾部的换行被边界规则移除，不产生 run。
        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(gapped);
        assertEquals(1, runs.size());
        assertEquals(" ", runs.get(0).text().content);
    }

    @Test
    void boundaryWhitespaceTextNodesRenderNothing() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        // 容器首尾的纯空白被移除：首个 inline-block 仍在行首，且不产生任何文本 run。
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 200px;");
        document.body.appendChild(parent);
        parent.appendChild(new TextNode(document, "\n  "));
        Element first = inlineBlock(document);
        parent.appendChild(first);
        parent.appendChild(new TextNode(document, "\n  "));
        assertEquals(0, Position.getOffset(first).x);
        assertTrue(NormalFlow.computeTextRuns(parent).isEmpty());

        // 块级兄弟之间的空白不会挤出新行。
        Element blocks = new Element(document, "div");
        blocks.setAttribute("style", "width: 200px;");
        document.body.appendChild(blocks);
        Element tightBlocks = new Element(document, "div");
        tightBlocks.setAttribute("style", "width: 200px;");
        document.body.appendChild(tightBlocks);

        Element b1 = block(document);
        blocks.appendChild(b1);
        blocks.appendChild(new TextNode(document, "\n    "));
        Element b2 = block(document);
        blocks.appendChild(b2);

        Element c1 = block(document);
        tightBlocks.appendChild(c1);
        Element c2 = block(document);
        tightBlocks.appendChild(c2);

        assertEquals(Position.getOffset(c2).y - Position.getOffset(c1).y,
                Position.getOffset(b2).y - Position.getOffset(b1).y,
                "whitespace between block-level boxes must not create a line");
    }

    @Test
    void leadingSpaceAtLineStartIsRemoved() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 200px;");
        document.body.appendChild(parent);
        parent.appendChild(new TextNode(document, "\n   hello"));

        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(parent);
        assertEquals(1, runs.size());
        assertEquals("hello", runs.get(0).text().content);
        assertEquals(0, runs.get(0).x());
    }

    @Test
    void parsedWhitespaceBetweenInlineBlocksProducesSpaceGap() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        // 走真实解析管线（tokenizer + buildDocument）：标签间的换行缩进
        // 必须像浏览器一样折叠成一个渲染空格。
        Element footer = com.sighs.apricityui.parser.HTML.createElement(document,
                "<div style=\"width: 200px;\">\n"
                        + "  <span style=\"display: inline-block; vertical-align: top; width: 10px; height: 6px;\"></span>\n"
                        + "  <span style=\"display: inline-block; vertical-align: top; width: 10px; height: 6px;\"></span>\n"
                        + "</div>");
        document.body.appendChild(footer);

        // DOM 里保留空白文本节点（与浏览器一致）。
        assertTrue(footer.childNodes.stream().anyMatch(node -> node instanceof TextNode),
                "parser must keep whitespace-only text nodes");

        Element second = footer.children.get(1);
        assertTrue(Position.getOffset(second).x > 10,
                "whitespace between inline-blocks must collapse to a rendered space");
    }

    private static Element inlineBlock(Document document) {
        Element element = new Element(document, "span");
        element.setAttribute("style", "display: inline-block; vertical-align: top; width: 10px; height: 6px;");
        return element;
    }

    private static Element block(Document document) {
        Element element = new Element(document, "div");
        element.setAttribute("style", "width: 10px; height: 10px;");
        return element;
    }

    private static void assumeMinecraftClientTextRuntime() {
        // Inline flow tests are pure geometry checks and do not require a live
        // Minecraft client when the viewport and font fallback are deterministic.
    }
}
