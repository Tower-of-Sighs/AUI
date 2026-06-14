package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.TextNode;
import com.sighs.apricityui.style.Layout;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutPositionTest {
    @Test
    void parseSignedNumberAcceptsNegativeAndDecimalLengths() {
        assertEquals(-12.5, Position.parseSignedNumber("-12.5px"));
        assertEquals(7.25, Position.parseSignedNumber("translate(7.25px)"));
        assertEquals(0, Position.parseSignedNumber("auto"));
    }

    @Test
    void absoluteRightAnchorsAgainstParentContentBoxWhenLeftIsAuto() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 120px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "position: relative; width: 200px; height: 80px; padding: 10px; box-sizing: border-box;");
        document.body.appendChild(parent);

        Element absoluteChild = new Element(document, "div");
        absoluteChild.setAttribute("style", "position: absolute; right: 0; top: 0; width: 40px; height: 20px;");
        parent.appendChild(absoluteChild);

        double parentInnerWidth = com.sighs.apricityui.style.Box.of(parent).innerSize().width();
        double childWidth = Size.box(absoluteChild).width();

        assertEquals(parentInnerWidth - childWidth, Position.getOffset(absoluteChild).x);
        assertEquals(0, Position.getOffset(absoluteChild).y);
    }

    @Test
    void absoluteBottomAnchorsAgainstParentContentBoxWhenTopIsAuto() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "position: relative; width: 120px; height: 90px; padding: 10px; box-sizing: border-box;");
        document.body.appendChild(parent);

        Element absoluteChild = new Element(document, "div");
        absoluteChild.setAttribute("style", "position: absolute; bottom: 0; left: 0; width: 20px; height: 15px;");
        parent.appendChild(absoluteChild);

        double parentInnerHeight = Box.of(parent).innerSize().height();
        double childHeight = Size.box(absoluteChild).height();

        assertEquals(0, Position.getOffset(absoluteChild).x);
        assertEquals(parentInnerHeight - childHeight, Position.getOffset(absoluteChild).y);
    }

    @Test
    void fixedOffsetsResolveAgainstViewportInsteadOfParentContentBox() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "position: relative; width: 120px; height: 90px; padding: 10px; box-sizing: border-box;");
        document.body.appendChild(parent);

        Element fixedChild = new Element(document, "div");
        fixedChild.setAttribute("style", "position: fixed; right: 12px; bottom: 8px; width: 20px; height: 15px;");
        parent.appendChild(fixedChild);

        Size window = Size.getWindowSize();
        assertEquals(window.width() - 20 - 12, Position.getOffset(fixedChild).x);
        assertEquals(window.height() - 15 - 8, Position.getOffset(fixedChild).y);
    }

    @Test
    void flexColumnChildrenStretchAcrossCrossAxisWhenAlignSelfIsAuto() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; width: 120px; height: 100px; padding: 10px; box-sizing: border-box;");
        document.body.appendChild(parent);

        Element child = new Element(document, "div");
        child.setAttribute("style", "height: 20px;");
        parent.appendChild(child);

        double parentInnerWidth = Box.of(parent).innerSize().width();
        assertEquals(parentInnerWidth, Size.box(child).width());
    }

    @Test
    void flexColumnGrowItemCanShrinkWithinExplicitParentHeight() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; width: 120px; height: 100px;");
        document.body.appendChild(parent);

        Element header = new Element(document, "div");
        header.setAttribute("style", "height: 40px;");
        parent.appendChild(header);

        Element main = new Element(document, "div");
        main.setAttribute("style", "flex: 1 1 0%;");
        Element content = new Element(document, "div");
        content.setAttribute("style", "height: 90px;");
        main.appendChild(content);
        parent.appendChild(main);

        assertEquals(60, Math.round(Size.of(main).height()));
        assertEquals(100, Math.round(Size.of(parent).height()));
    }

    @Test
    void flexColumnChildrenRespectExplicitAlignSelfOverride() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; width: 120px; height: 100px; padding: 10px; box-sizing: border-box;");
        document.body.appendChild(parent);

        Element child = new Element(document, "div");
        child.innerText = "Pen";
        child.setAttribute("style", "height: 20px; align-self: flex-start;");
        parent.appendChild(child);

        double parentInnerWidth = Box.of(parent).innerSize().width();
        double childWidth = Size.box(child).width();

        assertEquals(Box.of(parent).offset("left"), Position.getOffset(child).x);
        assertTrue(childWidth < parentInnerWidth);
    }

    @Test
    void relativeOffsetsDoNotAffectFollowingSiblingFlowPlacement() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 120px;");
        document.body.appendChild(parent);

        Element first = new Element(document, "div");
        first.setAttribute("style", "position: relative; left: 7px; top: 5px; width: 20px; height: 10px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 20px; height: 10px;");
        parent.appendChild(first);
        parent.appendChild(second);

        assertEquals(7, Position.getOffset(first).x);
        assertEquals(5, Position.getOffset(first).y);
        assertEquals(10, Position.getOffset(second).y);
    }

    @Test
    void flexButtonTextUsesContainerAlignmentInsteadOfTopLeftFlow() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element button = new Element(document, "div");
        button.setAttribute("style", "display: flex; align-items: center; justify-content: center; width: 80px; height: 20px; padding: 2px;");
        button.appendChild(new TextNode(document, "Pen"));
        document.body.appendChild(button);

        Text text = Text.of(button);
        double contentWidth = Box.of(button).innerSize().width();
        double contentHeight = Box.of(button).innerSize().height();
        double lineWidth = Text.measureLine(text, "Pen");
        double expectedTextX = (contentWidth - lineWidth) / 2.0;
        double expectedTextY = (contentHeight - text.lineHeight) / 2.0;

        Position flexTextOffset = readFlexTextOffset(button);
        assertEquals(expectedTextX, flexTextOffset.x);
        assertEquals(expectedTextY, flexTextOffset.y);
    }

    @Test
    void cssFontSizeRemainsInCssPixelUnits() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        Element label = new Element(document, "span");
        label.innerText = "HUD";
        label.setAttribute("style", "font-size: 16px;");
        document.body.appendChild(label);

        assertEquals(16.0, Text.of(label).fontSize);
        assertEquals(16.0, Text.getFontSize(label));
    }

    @Test
    void flexWrapMovesOverflowingItemsOntoNextLine() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element topbar = new Element(document, "div");
        topbar.setAttribute("style", "display: flex; flex-wrap: wrap; width: 120px; column-gap: 4px; row-gap: 6px;");
        document.body.appendChild(topbar);

        Element first = new Element(document, "div");
        first.setAttribute("style", "width: 70px; height: 20px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 70px; height: 20px;");
        topbar.appendChild(first);
        topbar.appendChild(second);

        assertEquals(0, Position.getOffset(first).y);
        assertEquals(26, Position.getOffset(second).y);
    }

    @Test
    void autoWidthFlexWrapUsesContainingBlockWithoutRecursiveSizing() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 140px; height: 200px;");

        Element topbar = new Element(document, "div");
        topbar.setAttribute("style", "display: flex; flex-wrap: wrap; column-gap: 4px; row-gap: 6px;");
        document.body.appendChild(topbar);

        Element first = new Element(document, "div");
        first.setAttribute("style", "width: 70px; height: 20px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 70px; height: 20px;");
        topbar.appendChild(first);
        topbar.appendChild(second);

        assertEquals(0, Position.getOffset(first).y);
        assertEquals(26, Position.getOffset(second).y);
        assertEquals(70, Size.of(topbar).width());
        assertEquals(46, Size.of(topbar).height());
    }

    @Test
    void flexGapShorthandExpandsToBothAxes() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element column = new Element(document, "div");
        column.setAttribute("style", "display: flex; flex-direction: column; gap: 3px;");
        document.body.appendChild(column);

        Element first = new Element(document, "div");
        first.setAttribute("style", "width: 10px; height: 10px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 10px; height: 10px;");
        column.appendChild(first);
        column.appendChild(second);

        assertEquals(13, Position.getOffset(second).y);
        assertEquals("3px", column.getComputedStyle().rowGap);
        assertEquals("3px", column.getComputedStyle().columnGap);
    }

    @Test
    void flexGapTwoValueSyntaxSeparatesRowAndColumnSpacing() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element wrapped = new Element(document, "div");
        wrapped.setAttribute("style", "display: flex; flex-wrap: wrap; width: 30px; gap: 7px 5px;");
        document.body.appendChild(wrapped);

        Element first = new Element(document, "div");
        first.setAttribute("style", "width: 20px; height: 10px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 20px; height: 10px;");
        wrapped.appendChild(first);
        wrapped.appendChild(second);

        assertEquals(0, Position.getOffset(first).y);
        assertEquals(17, Position.getOffset(second).y);
    }

    @Test
    void wrappedRowsHonorCrossAxisAlignmentWithinLineHeight() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element wrapped = new Element(document, "div");
        wrapped.setAttribute("style", "display: flex; flex-wrap: wrap; width: 40px; align-items: center; row-gap: 5px;");
        document.body.appendChild(wrapped);

        Element tall = new Element(document, "div");
        tall.setAttribute("style", "width: 20px; height: 20px;");
        Element shortItem = new Element(document, "div");
        shortItem.setAttribute("style", "width: 20px; height: 10px;");
        Element nextLine = new Element(document, "div");
        nextLine.setAttribute("style", "width: 20px; height: 10px;");
        wrapped.appendChild(tall);
        wrapped.appendChild(shortItem);
        wrapped.appendChild(nextLine);

        assertEquals(0, Position.getOffset(tall).y);
        assertEquals(5, Position.getOffset(shortItem).y);
        assertEquals(25, Position.getOffset(nextLine).y);
    }

    @Test
    void inlineFlexAndInlineGridUseTheirSpecializedLayoutEngines() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element flex = new Element(document, "div");
        flex.setAttribute("style", "display: inline-flex; gap: 4px;");
        document.body.appendChild(flex);
        Element flexFirst = new Element(document, "div");
        flexFirst.setAttribute("style", "width: 10px; height: 10px;");
        Element flexSecond = new Element(document, "div");
        flexSecond.setAttribute("style", "width: 10px; height: 10px;");
        flex.appendChild(flexFirst);
        flex.appendChild(flexSecond);

        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: inline-grid; grid-template-columns: 2; gap: 3px;");
        document.body.appendChild(grid);
        Element gridFirst = new Element(document, "div");
        gridFirst.setAttribute("style", "width: 10px; height: 10px;");
        Element gridSecond = new Element(document, "div");
        gridSecond.setAttribute("style", "width: 10px; height: 10px;");
        grid.appendChild(gridFirst);
        grid.appendChild(gridSecond);

        assertEquals(14, Position.getOffset(flexSecond).x);
        assertEquals(13, Position.getOffset(gridSecond).x);
        assertEquals(24, Layout.computeContentSize(flex).width());
        assertEquals(23, Layout.computeContentSize(grid).width());
    }

    @Test
    void layoutDisplayHelpersRecognizeInlineVariantsAndInFlowRules() {
        assertTrue(Layout.isFlexDisplay("inline-flex"));
        assertTrue(Layout.isGridDisplay("inline-grid"));
        assertTrue(Layout.isInFlow(new com.sighs.apricityui.init.Style()));

        com.sighs.apricityui.init.Style absolute = new com.sighs.apricityui.init.Style();
        absolute.position = "absolute";
        assertTrue(!Layout.isInFlow(absolute));

        com.sighs.apricityui.init.Style hidden = new com.sighs.apricityui.init.Style();
        hidden.display = "none";
        assertTrue(!Layout.isInFlow(hidden));
    }

    @Test
    void borderBoxPercentageSizingUsesParentContentBoxAsBasis() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 200px; height: 100px; padding: 10px; border: 5px solid #000; box-sizing: border-box;");
        document.body.appendChild(parent);

        Element child = new Element(document, "div");
        child.setAttribute("style", "width: 50%; height: 50%;");
        parent.appendChild(child);

        assertEquals(85, Size.of(child).width());
        assertEquals(35, Size.of(child).height());
    }

    @Test
    void aspectRatioDerivesHeightFromExplicitWidth() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element child = new Element(document, "div");
        child.setAttribute("style", "width: 120px; aspect-ratio: 4 / 3;");
        document.body.appendChild(child);

        assertEquals(120, Size.of(child).width());
        assertEquals(90, Size.of(child).height());
    }

    @Test
    void aspectRatioDerivesWidthFromExplicitHeight() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element child = new Element(document, "div");
        child.setAttribute("style", "height: 90px; aspect-ratio: 4 / 3;");
        document.body.appendChild(child);

        assertEquals(120, Size.of(child).width());
        assertEquals(90, Size.of(child).height());
    }

    @Test
    void siblingBlockVerticalMarginsCollapseToLargestMargin() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 100px;");
        document.body.appendChild(parent);

        Element first = new Element(document, "div");
        first.setAttribute("style", "width: 20px; height: 10px; margin-bottom: 12px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 20px; height: 10px; margin-top: 8px;");
        parent.appendChild(first);
        parent.appendChild(second);

        assertEquals(14, Position.getOffset(second).y);
        assertEquals(32, Layout.computeContentSize(parent).height());
    }

    @Test
    void percentHeightFallsBackToAutoWhenParentHeightIsNotExplicit() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 100px;");
        document.body.appendChild(parent);

        Element child = new Element(document, "div");
        child.setAttribute("style", "width: 20px; height: 50%;");
        parent.appendChild(child);

        assertEquals(0, Size.of(child).height());
    }

    @Test
    void percentHeightResolvesThroughExplicitPercentageParentChain() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element grandParent = new Element(document, "div");
        grandParent.setAttribute("style", "width: 100px; height: 120px;");
        document.body.appendChild(grandParent);

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 100px; height: 50%;");
        grandParent.appendChild(parent);

        Element child = new Element(document, "div");
        child.setAttribute("style", "width: 20px; height: 50%;");
        parent.appendChild(child);

        assertEquals(60, Size.of(parent).height());
        assertEquals(30, Size.of(child).height());
    }

    @Test
    void gridFrTracksConsumeRemainingSpace() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: grid; width: 120px; grid-template-columns: 20px 1fr 2fr;");
        document.body.appendChild(grid);

        Element first = new Element(document, "div");
        first.setAttribute("style", "width: 5px; height: 10px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 5px; height: 10px;");
        Element third = new Element(document, "div");
        third.setAttribute("style", "width: 5px; height: 10px;");
        grid.appendChild(first);
        grid.appendChild(second);
        grid.appendChild(third);

        assertEquals(20, Position.getOffset(second).x);
        assertEquals(55, Position.getOffset(third).x);
        assertEquals(120, Layout.computeContentSize(grid).width());
    }

    @Test
    void gridRepeatAndMinmaxExpandTracks() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: grid; width: 100px; grid-template-columns: repeat(2, minmax(10px, 1fr));");
        document.body.appendChild(grid);

        Element first = new Element(document, "div");
        first.setAttribute("style", "width: 5px; height: 10px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 5px; height: 10px;");
        grid.appendChild(first);
        grid.appendChild(second);

        assertEquals(50, Position.getOffset(second).x);
        assertEquals(100, Layout.computeContentSize(grid).width());
    }

    @Test
    void gridAutoFillRepeatsFixedTracksWithinAvailableWidth() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: grid; width: 95px; column-gap: 5px; grid-template-columns: repeat(auto-fill, 20px);");
        document.body.appendChild(grid);

        Element first = new Element(document, "div");
        first.setAttribute("style", "width: 10px; height: 10px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 10px; height: 10px;");
        Element third = new Element(document, "div");
        third.setAttribute("style", "width: 10px; height: 10px;");
        Element fourth = new Element(document, "div");
        fourth.setAttribute("style", "width: 10px; height: 10px;");
        grid.appendChild(first);
        grid.appendChild(second);
        grid.appendChild(third);
        grid.appendChild(fourth);

        assertEquals(25, Position.getOffset(second).x);
        assertEquals(50, Position.getOffset(third).x);
        assertEquals(75, Position.getOffset(fourth).x);
        assertEquals(95, Layout.computeContentSize(grid).width());
    }

    @Test
    void gridItemSelfAlignmentOffsetsWithinExplicitCell() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: grid; grid-template-columns: 40px; grid-template-rows: 30px;");
        document.body.appendChild(grid);

        Element child = new Element(document, "div");
        child.setAttribute("style", "width: 10px; height: 10px; justify-self: center; align-self: end;");
        grid.appendChild(child);

        assertEquals(15, Position.getOffset(child).x);
        assertEquals(20, Position.getOffset(child).y);
    }

    @Test
    void outOfFlowChildrenDoNotContributeToParentContentHeight() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 100px;");
        document.body.appendChild(parent);

        Element inFlow = new Element(document, "div");
        inFlow.setAttribute("style", "width: 20px; height: 10px;");
        Element absoluteChild = new Element(document, "div");
        absoluteChild.setAttribute("style", "position: absolute; width: 20px; height: 50px;");
        parent.appendChild(inFlow);
        parent.appendChild(absoluteChild);

        assertEquals(10, Layout.computeContentSize(parent).height());
    }

    private static Position readFlexTextOffset(Element element) {
        try {
            java.lang.reflect.Method method = Element.class.getDeclaredMethod("getFlexTextOffset");
            method.setAccessible(true);
            return (Position) method.invoke(element);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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
