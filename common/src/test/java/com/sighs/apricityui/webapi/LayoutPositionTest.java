package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Flex;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.parser.CSS;
import org.junit.jupiter.api.Test;

import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sighs.apricityui.style.Style;

class LayoutPositionTest {

    @Test
    void layoutMeasurementCacheRejectsEntriesFromAnOlderLayoutDependency() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element element = new Element(document, "div");
        document.body.appendChild(parent);
        parent.appendChild(element);

        LayoutMeasureCache.begin();
        try {
            Size measured = new Size(24, 12);
            LayoutMeasureCache.putSize(LayoutMeasureCache.SIZE_NATURAL, element,
                    Double.NaN, Double.NaN, true, measured);
            assertEquals(measured, LayoutMeasureCache.getSize(LayoutMeasureCache.SIZE_NATURAL, element,
                    Double.NaN, Double.NaN, true));

            element.getRenderer().invalidateLayoutVersion();
            assertNull(LayoutMeasureCache.getSize(LayoutMeasureCache.SIZE_NATURAL, element,
                    Double.NaN, Double.NaN, true));

            LayoutMeasureCache.putSize(LayoutMeasureCache.SIZE_NATURAL, element,
                    Double.NaN, Double.NaN, true, measured);
            parent.getRenderer().size.set(new Size(320, 200));
            assertNull(LayoutMeasureCache.getSize(LayoutMeasureCache.SIZE_NATURAL, element,
                    Double.NaN, Double.NaN, true));
        } finally {
            LayoutMeasureCache.end();
        }
    }

    @Test
    void layoutMeasurementCacheReusesStableEntriesAcrossFrames() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        document.body.appendChild(element);
        Size measured = new Size(24, 12);

        LayoutMeasureCache.begin();
        LayoutMeasureCache.putSize(LayoutMeasureCache.SIZE_NATURAL, element,
                Double.NaN, Double.NaN, true, measured);
        LayoutMeasureCache.end();

        LayoutMeasureCache.begin();
        try {
            assertEquals(measured, LayoutMeasureCache.getSize(LayoutMeasureCache.SIZE_NATURAL, element,
                    Double.NaN, Double.NaN, true));
        } finally {
            LayoutMeasureCache.end();
        }
    }
    @Test
    void inlineFlexDirectTextKeepsItsContentHeightAboveMinHeight() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");
        Element button = new Element(document, "button");
        button.innerText = "1";
        button.setAttribute("style", "box-sizing: border-box; display: inline-flex; font-size: 16px; line-height: 16px; min-height: 38px; padding: 7px 10px 11px; border: 3px solid black;");
        document.body.appendChild(button);

        assertEquals(40, Size.of(button).height(), 0.01);
    }

    @Test
    void nativeSelectUsesNormalIntrinsicLineHeight() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");
        Select select = new Select(document);
        select.setAttribute("style", "box-sizing: border-box; width: 200px; min-height: 42px; padding: 8px 10px; border: 3px solid black; font-size: 16px; line-height: 1.35;");
        Element option = new Element(document, "option");
        option.innerText = "Unavailable";
        select.appendChild(option);
        document.body.appendChild(select);

        assertEquals(42, Size.of(select).height(), 0.01);
    }

    @Test
    void baselineAlignedInlineFlexExpandsTheContainingLineBox() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");
        Element cell = new Element(document, "div");
        cell.setAttribute("style", "font-family: sans-serif; font-size: 16px; line-height: 24px;");
        Element badge = new Element(document, "span");
        badge.innerText = "Stable";
        badge.setAttribute("style", "box-sizing: border-box; display: inline-flex; align-items: center; min-height: 24px; padding: 3px 8px; border: 2px solid black; font-family: sans-serif; font-size: 12px; line-height: 14.4px;");
        cell.appendChild(badge);
        document.body.appendChild(cell);

        double badgeHeight = Size.of(badge).height();
        assertTrue(Layout.computeContentSize(cell).height() > badgeHeight);
        assertTrue(Position.getOffset(badge).y > Position.getOffset(cell).y);
    }

    @Test
    void textUsesResolvedComputedFontSizeFromCustomProperty() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "--large-text: 20px; font-size: 16px;");
        Element label = new Element(document, "p");
        label.innerText = "Computed font size";
        label.setAttribute("style", "font-family: sans-serif; font-size: var(--large-text);");
        document.body.appendChild(label);

        assertEquals("20px", label.getComputedStyle().fontSize);
        assertEquals(20, Text.of(label).fontSize);
    }

    @Test
    void rowFlexShrinkRelayoutsTextAgainstFinalItemWidth() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 600px; height: 200px;");

        Element row = new Element(document, "div");
        row.setAttribute("style", "display: flex; align-items: center; width: 340px; gap: 20px;");
        Element copy = new Element(document, "div");
        copy.setAttribute("style", "min-width: 0;");
        Element paragraph = new Element(document, "p");
        paragraph.innerText = "Alpha bravo charlie delta echo";
        paragraph.setAttribute("style", "font-family: sans-serif; font-size: 20px; line-height: 20px;");
        Element fixed = new Element(document, "div");
        fixed.setAttribute("style", "width: 60px; height: 20px; flex-shrink: 0;");
        copy.appendChild(paragraph);
        row.appendChild(copy);
        row.appendChild(fixed);
        document.body.appendChild(row);

        Size.of(paragraph);
        assertEquals(1, Text.wrap(paragraph).lines().size());
        assertEquals(260, Size.of(copy).width(), 0.01);
        assertTrue(Text.wrap(paragraph).lines().size() > 1);
        assertTrue(Size.of(paragraph).height() >= 40);
        assertTrue(Size.of(copy).height() >= 40);
        assertTrue(Size.of(row).height() >= Size.of(copy).height());
    }

    @Test
    void replacingARegisteredFontInvalidatesLineMeasurements() throws IOException {
        String family = "font-revision-test-" + UUID.randomUUID();
        Text text = new Text();
        text.fontFamily = family;
        text.fontSize = 20;

        try (InputStream regular = getClass().getResourceAsStream(
                "/assets/apricityui/apricity/apricityui/theme/ore/fonts/minecraft-regular.otf")) {
            assertNotNull(regular);
            assertTrue(Font.registerFont(family, regular));
        }
        double regularWidth = Text.measureLine(text, "MMMMMMMMMMMM");

        try (InputStream display = getClass().getResourceAsStream(
                "/assets/apricityui/apricity/apricityui/theme/ore/fonts/minecraft-ten.ttf")) {
            assertNotNull(display);
            assertTrue(Font.registerFont(family, display));
        }
        double displayWidth = Text.measureLine(text, "MMMMMMMMMMMM");

        assertNotEquals(regularWidth, displayWidth, 0.01);
    }

    @Test
    void normalWhiteSpaceBreaksAfterVisibleHyphensBeforeEmergencyCharacterBreaking() {
        Text text = new Text();
        text.fontFamily = "sans-serif";
        text.fontSize = 20;
        text.whiteSpace = "normal";
        text.content = "alpha-beta";

        double wrapWidth = Text.measureLine(text, "alpha-") + 0.1;
        Text.WrappedText wrapped = Text.wrap(text, wrapWidth);

        assertEquals(List.of("alpha-", "beta"), wrapped.lines());
    }

    @Test
    void fontMetricInvalidationClearsTextAndIntrinsicLayoutCaches() {
        Document document = TestDocumentFactory.createDocument();
        Element label = new Element(document, "span");
        label.innerText = "Font metrics";
        label.setAttribute("style", "font-family: sans-serif; font-size: 20px;");
        document.body.appendChild(label);

        Text.of(label);
        Size.of(label);
        assertNotNull(label.getRenderer().text.get());
        assertNotNull(label.getRenderer().size.get());

        document.invalidateFontMetrics();

        assertNull(label.getRenderer().text.get());
        assertNull(label.getRenderer().wrappedText.get());
        assertNull(label.getRenderer().size.get());
        assertTrue(document.hasPendingRenderState());
    }

    @Test
    void fixedPercentageSizeUsesViewportInsteadOfParentContentSize() {
        Size.setViewportOverride(640, 360);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "width:0;height:0;");
            Element fixed = new Element(document, "div");
            fixed.setAttribute("style", "position:fixed;width:100%;height:100%;");
            document.body.appendChild(fixed);

            assertEquals(640, Math.round(Size.of(fixed).width()));
            assertEquals(360, Math.round(Size.of(fixed).height()));
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void fixedInsetFlexContainerCentersChildInViewport() {
        Size.setViewportOverride(640, 360);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "width: 320px; height: 180px;");

            Element backdrop = new Element(document, "div");
            backdrop.setAttribute("style", "position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; padding: 20px;");
            Element modal = new Element(document, "div");
            modal.setAttribute("style", "width: 200px; height: 100px;");
            document.body.appendChild(backdrop);
            backdrop.appendChild(modal);

            assertEquals(640, Size.of(backdrop).width(), 0.01);
            assertEquals(360, Size.of(backdrop).height(), 0.01);
            assertEquals(220, Position.getOffset(modal).x, 0.01);
            assertEquals(130, Position.getOffset(modal).y, 0.01);
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void autoHeightFlexFooterKeepsIntrinsicCrossSizeInsideCenteredModal() {
        Size.setViewportOverride(640, 360);
        try {
            Document document = TestDocumentFactory.createDocument();
            Element backdrop = new Element(document, "div");
            backdrop.setAttribute("style", "position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; padding: 20px;");
            Element modal = new Element(document, "div");
            modal.setAttribute("style", "width: 300px;");
            Element header = new Element(document, "div");
            header.setAttribute("style", "height: 50px;");
            Element body = new Element(document, "div");
            body.setAttribute("style", "height: 100px;");
            Element footer = new Element(document, "div");
            footer.setAttribute("style", "display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-top: 3px solid #000;");
            Element cancel = new Element(document, "button");
            cancel.setAttribute("style", "width: 80px; height: 42px;");
            Element apply = new Element(document, "button");
            apply.setAttribute("style", "width: 80px; height: 42px;");
            document.body.appendChild(backdrop);
            backdrop.appendChild(modal);
            modal.appendChild(header);
            modal.appendChild(body);
            modal.appendChild(footer);
            footer.appendChild(cancel);
            footer.appendChild(apply);

            Size.of(backdrop);
            Position.getOffset(modal);
            assertEquals(219, Size.of(modal).height(), 0.01);
            assertEquals(69, Size.of(footer).height(), 0.01);
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void flexCrossSizeRemeasureUsesAssignedContentBoxWidthOnlyOnce() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 640px; height: 360px;");

        Element footer = new Element(document, "div");
        footer.setAttribute("style", "display: flex; align-items: center; justify-content: space-between; "
                + "width: 480px; padding: 12px 16px; border-top: 3px solid #000;");
        Element cancel = new Element(document, "button");
        cancel.setAttribute("style", "min-height: 42px; padding: 7px 16px 13px; border: 3px solid #000; "
                + "font-size: 16px; line-height: 1; display: inline-flex; align-items: center; justify-content: center;");
        cancel.setTextContent("Cancel");
        Element apply = new Element(document, "button");
        apply.setAttribute("style", cancel.getAttribute("style"));
        apply.setTextContent("Apply");
        document.body.appendChild(footer);
        footer.appendChild(cancel);
        footer.appendChild(apply);

        assertEquals(42, Size.of(cancel).height(), 0.01);
        assertEquals(42, Size.of(apply).height(), 0.01);
        assertEquals(69, Size.of(footer).height(), 0.01);
    }

    @Test
    void constrainedNaturalFlowWrapsFullWidthAtomicInlineChildren() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 640px;");
        Element wrapper = new Element(document, "div");
        Element body = new Element(document, "div");
        body.setAttribute("style", "padding: 16px;");
        Element first = new Element(document, "span");
        first.setAttribute("style", "display: inline-block; vertical-align: top; width: 100%; height: 42px; margin-bottom: 8px;");
        Element second = new Element(document, "span");
        second.setAttribute("style", "display: inline-block; vertical-align: top; width: 100%; height: 42px;");
        document.body.appendChild(wrapper);
        wrapper.appendChild(body);
        body.appendChild(first);
        body.appendChild(second);

        Size.of(wrapper); // Populate a wider used-size cache before the intrinsic pass.
        Size measured = Size.naturalAtContentWidth(wrapper, 462);
        assertEquals(462, measured.width(), 0.01);
        assertEquals(124, measured.height(), 0.01);
    }

    @Test
    void columnFlexNaturalContentCacheVariesWithWidthContext() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 640px;");
        Element stack = new Element(document, "div");
        stack.setAttribute("style", "display: flex; flex-direction: column;");
        Element card = new Element(document, "div");
        Element body = new Element(document, "div");
        body.setAttribute("style", "padding: 16px;");
        Element first = new Element(document, "span");
        first.setAttribute("style", "display: inline-block; vertical-align: top; width: 200px; height: 42px; margin-bottom: 8px;");
        Element second = new Element(document, "span");
        second.setAttribute("style", "display: inline-block; vertical-align: top; width: 200px; height: 42px;");
        document.body.appendChild(stack);
        stack.appendChild(card);
        card.appendChild(body);
        body.appendChild(first);
        body.appendChild(second);

        Size wide = Size.naturalAtContentWidth(stack, 500);
        Size narrow = Size.naturalAtContentWidth(stack, 300);
        assertTrue(narrow.height() > wide.height());
        assertEquals(124, narrow.height(), 0.01);
    }

    @Test
    void descendantNaturalSizeCacheVariesWithAncestorWidthContextInOneLayoutPass() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 640px;");
        Element stack = new Element(document, "div");
        stack.setAttribute("style", "display: flex; flex-direction: column;");
        Element card = new Element(document, "div");
        Element body = new Element(document, "div");
        body.setAttribute("style", "padding: 16px;");
        Element first = new Element(document, "span");
        first.setAttribute("style", "display: inline-block; vertical-align: top; width: 100%; height: 42px; margin-bottom: 8px;");
        Element second = new Element(document, "span");
        second.setAttribute("style", "display: inline-block; vertical-align: top; width: 100%; height: 42px;");
        document.body.appendChild(stack);
        stack.appendChild(card);
        card.appendChild(body);
        body.appendChild(first);
        body.appendChild(second);

        LayoutMeasureCache.begin();
        try {
            Size.naturalAtContentWidth(stack, 600);
            Size narrow = Size.naturalAtContentWidth(stack, 462);
            assertEquals(462, narrow.width(), 0.01);
            assertEquals(124, narrow.height(), 0.01);
        } finally {
            LayoutMeasureCache.end();
        }
    }

    @Test
    void constrainedColumnFlexRemeasuresChildrenInsteadOfReusingUsedSizeCache() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 640px;");
        Element stack = new Element(document, "div");
        stack.setAttribute("style", "display: flex; flex-direction: column;");
        Element card = new Element(document, "div");
        Element body = new Element(document, "div");
        body.setAttribute("style", "padding: 16px;");
        Element first = new Element(document, "span");
        first.setAttribute("style", "display: inline-block; vertical-align: top; width: 200px; height: 42px; margin-bottom: 8px;");
        Element second = new Element(document, "span");
        second.setAttribute("style", "display: inline-block; vertical-align: top; width: 200px; height: 42px;");
        document.body.appendChild(stack);
        stack.appendChild(card);
        card.appendChild(body);
        body.appendChild(first);
        body.appendChild(second);

        assertEquals(82, Size.of(card).height(), 0.01);
        Size constrained = Size.naturalAtContentWidth(stack, 300);
        assertEquals(124, constrained.height(), 0.01);
    }

    @Test
    void gridAutoRowUsesColumnFlexHeightMeasuredAtAssignedTrackWidth() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 1140px;");
        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: grid; width: 1140px; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 16px;");
        Element left = new Element(document, "div");
        left.setAttribute("style", "grid-column: span 7; height: 300px;");
        Element stack = new Element(document, "div");
        stack.setAttribute("style", "grid-column: span 5; display: flex; flex-direction: column; gap: 12px;");
        Element firstCard = new Element(document, "div");
        firstCard.setAttribute("style", "height: 158px;");
        Element secondCard = new Element(document, "div");
        Element header = new Element(document, "div");
        header.setAttribute("style", "height: 46px;");
        Element body = new Element(document, "div");
        body.setAttribute("style", "padding: 16px;");
        Element first = new Element(document, "span");
        first.setAttribute("style", "display: inline-block; vertical-align: top; width: 100%; height: 42px; margin-bottom: 8px;");
        Element second = new Element(document, "span");
        second.setAttribute("style", "display: inline-block; vertical-align: top; width: 100%; height: 42px;");
        document.body.appendChild(grid);
        grid.appendChild(left);
        grid.appendChild(stack);
        stack.appendChild(firstCard);
        stack.appendChild(secondCard);
        secondCard.appendChild(header);
        secondCard.appendChild(body);
        body.appendChild(first);
        body.appendChild(second);

        assertEquals(340, Layout.computeContentSize(grid).height(), 0.01);
        assertEquals(340, Size.naturalAtContentWidth(stack, 468).height(), 0.01);
        Position.getOffset(secondCard);
        assertEquals(170, Size.of(secondCard).height(), 0.01);
    }

    @Test
    void gridStretchFillsTheAreaWithTheItemsMarginBoxForContentBoxSizing() {
        Document document = TestDocumentFactory.createDocument();
        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: grid; width: 200px; height: 200px; "
                + "grid-template-columns: 200px; grid-template-rows: 200px;");
        Element item = new Element(document, "div");
        item.setAttribute("style", "box-sizing: content-box; margin: 10px; "
                + "padding: 12px; border: 2px solid black;");
        document.body.appendChild(grid);
        grid.appendChild(item);

        assertEquals(180, Size.of(item).width(), 0.01);
        assertEquals(180, Size.of(item).height(), 0.01);
        assertEquals(200, Box.of(item).size().width(), 0.01);
        assertEquals(200, Box.of(item).size().height(), 0.01);
    }

    @Test
    void oreThemeContractRowsRemainInsideTheStretchedGridCard() throws IOException {
        Document document = TestDocumentFactory.createDocument();
        Path stylesheet = Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore.css");
        assertTrue(Font.registerFont("OreRegular", Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/fonts/minecraft-regular.otf")));
        assertTrue(Font.registerFont("OreDisplay", Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/fonts/minecraft-ten.ttf")));
        document.body.setAttribute("class", "ore-theme");
        document.body.setAttribute("style", "width: 1150px; font-family: OreRegular;");

        Element grid = new Element(document, "div");
        grid.setAttribute("class", "grid");
        Element shortCard = createOreContractCard(document, 3);
        Element contractCard = createOreContractCard(document, 5);
        document.body.appendChild(grid);
        grid.appendChild(shortCard);
        grid.appendChild(contractCard);

        Size.of(grid);
        CSS.readCSS(Files.readString(stylesheet), document.CSSCache, stylesheet.toString());
        document.rebuildSelectorIndex();
        document.reapplyStylesFromCache();
        document.commitStyleRecalc();

        Element license = contractCard.querySelector("li:last-child");
        double licenseBottom = Position.of(license).y + Box.of(license).size().height();
        double cardPaddingBottom = Position.of(contractCard).y + Size.of(contractCard).height()
                - Box.of(contractCard).getBorderBottom();

        assertTrue(licenseBottom <= cardPaddingBottom + 0.01,
                "the auto grid row must include the complete intrinsic block contribution");
        assertEquals(Size.of(contractCard).height(), Size.of(shortCard).height(), 0.01);
        List<Element> rows = contractCard.querySelectorAll(".list-group-item");
        assertEquals(52, Size.of(rows.get(0)).height(), 0.01);
        assertEquals(52, Size.of(rows.get(1)).height(), 0.01);
        assertEquals(52, Size.of(rows.get(2)).height(), 0.01);
        assertEquals(52, Size.of(rows.get(3)).height(), 0.01);
        assertEquals(50, Size.of(rows.get(4)).height(), 0.01);
    }

    private static Element createOreContractCard(Document document, int rowCount) {
        Element card = new Element(document, "div");
        card.setAttribute("class", "col-6 card card-accent-purple");
        Element header = new Element(document, "div");
        header.setAttribute("class", "card-header");
        header.setTextContent("Theme contract");
        Element body = new Element(document, "div");
        body.setAttribute("class", "card-body");
        Element list = new Element(document, "ul");
        list.setAttribute("class", "list-group");
        card.appendChild(header);
        card.appendChild(body);
        body.appendChild(list);
        String[] labels = {"Theme scope", "Variables", "Display font", "Body font", "License"};
        String[] values = {".ore-theme", "--ore-*", "OreDisplay", "OreRegular", "MPL-2.0"};
        for (int i = 0; i < rowCount; i++) {
            Element row = new Element(document, "li");
            row.setAttribute("class", "list-group-item");
            Element label = new Element(document, "span");
            label.setTextContent(labels[i]);
            Element value = new Element(document, "code");
            value.setTextContent(values[i]);
            row.appendChild(label);
            row.appendChild(value);
            list.appendChild(row);
        }
        return card;
    }

    @Test
    void blockLevelWrappedFlexWithAutoWidthFillsContainingBlock() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 500px; height: 200px;");

        Element footer = new Element(document, "nav");
        footer.setAttribute("style", "width: 400px;");
        Element pagination = new Element(document, "ul");
        pagination.setAttribute("style", "display: flex; flex-wrap: wrap; justify-content: center; gap: 6px;");
        document.body.appendChild(footer);
        footer.appendChild(pagination);

        for (int i = 0; i < 3; i++) {
            Element item = new Element(document, "li");
            item.setAttribute("style", "width: 30px; height: 20px;");
            pagination.appendChild(item);
        }

        assertEquals(400, Size.of(pagination).width(), 0.01);
        assertEquals(149, Position.getOffset(pagination.getFirstElementChild()).x, 0.01);
    }

    @Test
    void smoothScrollAdvancesOnRenderFramesInsteadOfClientTicks() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");
        Element scroller = new Element(document, "div");
        scroller.setAttribute("style", "width: 100px; height: 50px; overflow-y: auto;");
        Element content = new Element(document, "div");
        content.setAttribute("style", "width: 100px; height: 300px;");
        document.body.appendChild(scroller);
        scroller.appendChild(content);
        document.commitRenderState();

        scroller.setScrollTop(100);
        document.tickFrame();
        assertEquals(0, scroller.getScrollTop(), 0.001);

        assertTrue(document.stepScrollRender());
        assertTrue(scroller.getScrollTop() > 0);
        assertTrue(scroller.getScrollTop() < scroller.getTargetScrollTop());
    }

    @Test
    void nativeScrollbarReservesEightPixelsAndSupportsThumbDragging() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");
        Element scroller = new Element(document, "div");
        scroller.setAttribute("style", "width: 100px; height: 60px; overflow-y: auto;");
        Element content = new Element(document, "div");
        content.setAttribute("style", "width: 100px; height: 300px;");
        document.body.appendChild(scroller);
        scroller.appendChild(content);

        assertTrue(scroller.hasVerticalScrollRange());
        assertEquals(8.0, scroller.getVerticalScrollbarGutter(), 0.001);
        document.commitRenderState();

        Rect rect = Rect.of(scroller);
        Position body = rect.getBodyRectPosition();
        Size size = rect.getBodyRectSize();
        double x = body.x + size.width() - 4;
        double thumbY = body.y + 4;
        double bottomY = body.y + size.height() - 6;

        assertTrue(MouseEvent.tiggerEvent(new MouseEvent("mousedown", new Position(x, thumbY), 0, false), document));
        assertTrue(MouseEvent.tiggerEvent(new MouseEvent("mousemove", new Position(x, bottomY), -1, false), document));
        assertTrue(MouseEvent.tiggerEvent(new MouseEvent("mouseup", new Position(x, bottomY), 0, false), document));
        assertTrue(scroller.getScrollTop() > 150);
        assertEquals(scroller.getScrollTop(), scroller.getTargetScrollTop(), 0.001);
    }

    @Test
    void nativeScrollbarGutterIsMeasuredInDevicePixelsAcrossViewports() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        setViewport(document, 300, 200, 2.0d);
        document.body.setAttribute("style", "width: 300px; height: 200px;");
        Element scroller = new Element(document, "div");
        scroller.setAttribute("style", "width: 100px; height: 60px; overflow-y: auto;");
        Element content = new Element(document, "div");
        content.setAttribute("style", "width: 100px; height: 300px;");
        document.body.appendChild(scroller);
        scroller.appendChild(content);

        assertTrue(scroller.hasVerticalScrollRange());
        assertEquals(4.0, scroller.getVerticalScrollbarGutter(), 0.001);
    }

    @Test
    void webFontMeasurementUsesBrowserFractionalAdvances() throws Exception {
        java.nio.file.Path fontPath = java.nio.file.Path.of(
                "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/fonts/minecraft-ten.ttf");
        assertTrue(Font.registerFont("OreDisplayFractionalTest", fontPath));
        Text text = new Text();
        text.fontFamily = "OreDisplayFractionalTest";
        text.fontSize = 25;
        text.fontWeight = 400;
        assertEquals(71.7503, Text.measureLine(text, "ORE UI"), 0.001);
    }

    @Test
    void fixedAutoWidthContainerShrinkFitsBeforeStretchingBlockChildren() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 300px;");

        Element menu = new Element(document, "div");
        menu.setAttribute("style", "position: fixed; left: 20px; top: 20px; min-width: 200px; padding: 4px 0; border: 2px solid #000;");
        document.body.appendChild(menu);

        Element header = new Element(document, "div");
        header.setAttribute("style", "padding: 6px 16px 8px; border-bottom: 1px solid #ddd;");
        Element headerContent = new Element(document, "span");
        headerContent.setAttribute("style", "display: inline-block; width: 20px; height: 10px;");
        header.appendChild(headerContent);
        menu.appendChild(header);

        Element item = new Element(document, "div");
        item.setAttribute("style", "position: relative; padding: 8px 16px;");
        Element itemContent = new Element(document, "span");
        itemContent.setAttribute("style", "display: inline-block; width: 60px; height: 10px;");
        item.appendChild(itemContent);
        Element fill = new Element(document, "div");
        fill.setAttribute("style", "position: absolute; left: 0; top: 0; width: 100%; height: 100%;");
        item.appendChild(fill);
        menu.appendChild(item);

        double menuWidth = Size.of(menu).width();
        double menuContentWidth = Box.of(menu).innerSize().width();

        assertEquals(200, Math.round(menuWidth));
        assertEquals(Math.round(menuContentWidth), Math.round(Size.of(header).width()));
        assertEquals(Math.round(menuContentWidth), Math.round(Size.of(item).width()));
        assertEquals(Math.round(item.getBoundingClientRect().width), Math.round(Size.of(fill).width()));
        assertEquals(200, Math.round(Size.of(menu).width()), "percentage hover fill must not expand the auto-width menu");
    }

    @Test
    void parseSignedNumberAcceptsNegativeAndDecimalLengths() {
        assertEquals(-12.5, Position.parseSignedNumber("-12.5px"));
        assertEquals(7.25, Position.parseSignedNumber("translate(7.25px)"));
        assertEquals(0, Position.parseSignedNumber("auto"));
    }

    @Test
    void absoluteRightAnchorsAgainstParentPaddingBoxWhenLeftIsAuto() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 120px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "position: relative; width: 200px; height: 80px; padding: 10px; box-sizing: border-box;");
        document.body.appendChild(parent);

        Element absoluteChild = new Element(document, "div");
        absoluteChild.setAttribute("style", "position: absolute; right: 0; top: 0; width: 40px; height: 20px;");
        parent.appendChild(absoluteChild);

        double parentPaddingBoxWidth = Size.of(parent).width() - Box.of(parent).getBorderHorizontal();
        double childWidth = Size.box(absoluteChild).width();

        assertEquals(parentPaddingBoxWidth - childWidth, Position.getOffset(absoluteChild).x);
        assertEquals(0, Position.getOffset(absoluteChild).y);
    }

    @Test
    void absoluteBottomAnchorsAgainstParentPaddingBoxWhenTopIsAuto() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "position: relative; width: 120px; height: 90px; padding: 10px; box-sizing: border-box;");
        document.body.appendChild(parent);

        Element absoluteChild = new Element(document, "div");
        absoluteChild.setAttribute("style", "position: absolute; bottom: 0; left: 0; width: 20px; height: 15px;");
        parent.appendChild(absoluteChild);

        double parentPaddingBoxHeight = Size.of(parent).height() - Box.of(parent).getBorderVertical();
        double childHeight = Size.box(absoluteChild).height();

        assertEquals(0, Position.getOffset(absoluteChild).x);
        assertEquals(parentPaddingBoxHeight - childHeight, Position.getOffset(absoluteChild).y);
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
    void fixedOffsetsUseOwningDocumentViewportWithoutContext() throws Exception {
        Size.setViewportOverride(400, 300);
        Document document = TestDocumentFactory.createDocument();
        setViewport(document, 1600, 900);
        Element fixed = new Element(document, "div");
        fixed.setAttribute("style", "position:fixed;right:0;bottom:0;width:420px;height:100px;");
        document.body.appendChild(fixed);

        try (Document.ContextScope ignored = Document.withContext(null)) {
            assertEquals(1180, Position.getOffset(fixed).x, 0.01);
            assertEquals(800, Position.getOffset(fixed).y, 0.01);
        } finally {
            document.remove();
            Size.clearViewportOverride();
        }
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
    void autoHeightFlexRowStretchesAutoHeightItemsToTheLineCrossSize() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element row = new Element(document, "div");
        row.setAttribute("style", "display:flex;align-items:stretch;width:240px;");
        document.body.appendChild(row);

        Element tall = new Element(document, "div");
        tall.setAttribute("style", "width:120px;height:44px;");
        row.appendChild(tall);

        Element stretched = new Element(document, "div");
        stretched.setAttribute("style", "width:120px;min-height:42px;");
        row.appendChild(stretched);

        assertEquals(44, Size.of(row).height());
        assertEquals(44, Size.of(stretched).height(),
                "A single-line flex item with an auto cross size must stretch to the line cross size");
    }

    @Test
    void autoHeightFlexRowStretchesShorterButtonToTextInputCrossSize() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:600px;height:300px;");

        Element row = new Element(document, "div");
        row.setAttribute("style", "display:flex;width:428px;box-sizing:border-box;");
        document.body.appendChild(row);

        Input input = new Input(document);
        input.setValue("/locate structure");
        input.setAttribute("style", "box-sizing:border-box;min-height:42px;padding:8px 10px;"
                + "border:3px solid #1e1e1f;font-size:16px;line-height:1.35;flex:1;min-width:0;");
        row.appendChild(input);

        Element button = new Element(document, "button");
        button.setTextContent("Run");
        button.setAttribute("style", "box-sizing:border-box;display:inline-flex;min-width:96px;min-height:42px;"
                + "padding:7px 16px 13px;border:3px solid #1e1e1f;font-size:16px;line-height:1;");
        row.appendChild(button);

        // Simulate the provisional used size produced when the button is
        // visited before the flex line's auto height has been resolved.
        button.getRenderer().size.set(new Size(96, 42));
        assertEquals(42, Size.of(button).height(), 0.0001);
        double rowHeight = Size.of(row).height();
        double inputHeight = Size.of(input).height();
        assertEquals(inputHeight, Size.natural(button).height(), 0.0001,
                "The renderer's natural measurement must honor the resolved flex line cross size");
        double buttonHeight = Size.of(button).height();
        assertEquals(inputHeight, rowHeight, 0.0001);
        assertEquals(inputHeight, buttonHeight, 0.0001,
                "The auto-height button must stretch to the flex line's input-defined cross size");
    }

    @Test
    void ancestorResizeInvalidatesDependentDescendantLayoutCaches() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 1000px; height: 800px;");

        Element window = new Element(document, "div");
        window.setAttribute("style", "display: flex; flex-direction: column; width: 800px; height: 600px;");
        document.body.appendChild(window);

        Element heading = new Element(document, "div");
        heading.setAttribute("style", "height: 50px;");
        window.appendChild(heading);

        Element viewport = new Element(document, "div");
        viewport.setAttribute("style", "position: relative; flex: 1 1 0%; min-height: 0;");
        window.appendChild(viewport);

        Element preview = new Element(document, "div");
        preview.setAttribute("style", "position: absolute; inset: 0; width: 100%; height: 100%;");
        viewport.appendChild(preview);

        assertEquals(800, Math.round(Size.of(heading).width()));
        assertEquals(550, Math.round(Size.of(viewport).height()));
        assertEquals(800, Math.round(Size.of(preview).width()));
        assertEquals(550, Math.round(Size.of(preview).height()));

        window.setAttribute("style", "display: flex; flex-direction: column; width: 400px; height: 300px;");

        assertEquals(400, Math.round(Size.of(heading).width()));
        assertEquals(250, Math.round(Size.of(viewport).height()));
        assertEquals(400, Math.round(Size.of(preview).width()));
        assertEquals(250, Math.round(Size.of(preview).height()));
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
    void percentageChildUsesFinalNestedFlexItemContentBox() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 1600px; height: 900px;");

        Element main = new Element(document, "div");
        main.setAttribute("style", "display: flex; width: 1600px; height: 852px;");
        document.body.appendChild(main);

        Element previewPanel = new Element(document, "div");
        previewPanel.setAttribute("style", "display: flex; flex-direction: column; flex: 1; min-width: 0;");
        main.appendChild(previewPanel);

        Element toolbar = new Element(document, "div");
        toolbar.setAttribute("style", "height: 34px;");
        previewPanel.appendChild(toolbar);

        Element previewFrame = new Element(document, "div");
        previewFrame.setAttribute("style", "display: flex; align-items: flex-start; flex: 1; padding: 24px;");
        previewPanel.appendChild(previewFrame);

        Element previewPage = new Element(document, "div");
        previewPage.setAttribute("style", "width: 100%; min-height: 100%;");
        previewFrame.appendChild(previewPage);

        Element sidePanel = new Element(document, "div");
        sidePanel.setAttribute("style", "width: 420px; flex-shrink: 0;");
        main.appendChild(sidePanel);

        assertEquals(1180, Math.round(Size.of(previewPanel).width()));
        assertEquals(1180, Math.round(Size.of(previewFrame).width()));
        assertEquals(1132, Math.round(Size.of(previewPage).width()));
        assertEquals(818, Math.round(Size.of(previewFrame).height()));
        assertEquals(770, Math.round(Size.of(previewPage).height()));
    }

    @Test
    void flexColumnVisibleOverflowItemsKeepContentBasedMinimumHeight() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; width: 120px; height: 60px; gap: 4px;");
        document.body.appendChild(parent);

        Element first = new Element(document, "div");
        first.setAttribute("style", "display: flex; flex-direction: column;");
        Element firstContent = new Element(document, "div");
        firstContent.setAttribute("style", "height: 50px;");
        first.appendChild(firstContent);
        parent.appendChild(first);

        Element second = new Element(document, "div");
        second.setAttribute("style", "display: flex; flex-direction: column;");
        Element secondContent = new Element(document, "div");
        secondContent.setAttribute("style", "height: 50px;");
        second.appendChild(secondContent);
        parent.appendChild(second);

        assertEquals(50, Math.round(Size.of(first).height()));
        assertEquals(50, Math.round(Size.of(second).height()));
        assertTrue(Position.getOffset(second).y >= Position.getOffset(first).y + Size.of(first).height());
    }

    @Test
    void autoHeightFlexParentDoesNotReenterThroughFlexibleChildSizing() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column;");
        document.body.appendChild(parent);

        Element flexible = new Element(document, "div");
        flexible.setAttribute("style", "display: flex; flex-direction: row; flex: 1 1 0%; gap: 4px;");
        parent.appendChild(flexible);

        Element first = new Element(document, "div");
        first.setAttribute("style", "width: 20px; height: 10px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "width: 30px; height: 10px;");
        flexible.appendChild(first);
        flexible.appendChild(second);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            Size parentSize = Size.of(parent);
            assertTrue(parentSize.width() >= 0);
            assertTrue(parentSize.height() >= 0);
        });
    }

    @Test
    void percentHeightUsesAspectRatioParentContentHeightWithoutDoubleSubtractingBox() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "box-sizing: border-box; width: 200px; aspect-ratio: 4 / 3; padding: 10px; border: 2px solid #000;");
        document.body.appendChild(parent);

        Element child = new Element(document, "div");
        child.setAttribute("style", "height: 100%; width: 100%;");
        parent.appendChild(child);

        assertEquals(Math.round(Box.of(parent).innerSize().height()), Math.round(Size.of(child).height()));
    }

    @Test
    void flexRowGrowItemKeepsPositiveShareWhenFixedSiblingsOverflow() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element row = new Element(document, "div");
        row.setAttribute("style", "display: flex; width: 160px; gap: 16px;");
        document.body.appendChild(row);

        Element flexible = new Element(document, "button");
        flexible.setAttribute("style", "flex: 1 1 0%; width: 100px; padding: 16px;");
        row.appendChild(flexible);

        Element reset = new Element(document, "button");
        reset.setAttribute("style", "width: 80px; padding: 16px 32px;");
        row.appendChild(reset);

        Element exit = new Element(document, "button");
        exit.setAttribute("style", "width: 80px; padding: 16px 32px;");
        row.appendChild(exit);

        assertTrue(Size.of(flexible).width() > 0);
        assertTrue(Size.of(flexible).width() < 100);
    }

    @Test
    void flexRowChildrenShrinkInsideAutoWidthBlockContainer() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 220px; height: 200px;");

        Element column = new Element(document, "div");
        column.setAttribute("style", "width: 180px;");
        document.body.appendChild(column);

        Element card = new Element(document, "div");
        card.setAttribute("style", "display: flex; justify-content: space-between; padding: 8px; box-sizing: border-box;");
        column.appendChild(card);

        Element left = new Element(document, "div");
        left.setAttribute("style", "display: flex; gap: 8px;");
        Element icon = new Element(document, "div");
        icon.setAttribute("style", "width: 24px; height: 24px; flex-shrink: 0;");
        Element label = new Element(document, "div");
        label.setAttribute("style", "width: 140px; height: 20px;");
        left.appendChild(icon);
        left.appendChild(label);
        card.appendChild(left);

        Element right = new Element(document, "div");
        right.setAttribute("style", "display: flex; gap: 8px;");
        Element defaultLabel = new Element(document, "div");
        defaultLabel.setAttribute("style", "width: 50px; height: 20px;");
        Element toggle = new Element(document, "div");
        toggle.setAttribute("style", "width: 20px; height: 20px; flex-shrink: 0;");
        right.appendChild(defaultLabel);
        right.appendChild(toggle);
        card.appendChild(right);

        double cardRight = Position.getOffset(card).x + Size.of(card).width();
        double rightEdge = Position.getOffset(right).x + Size.of(right).width();

        assertEquals(180, Math.round(Size.of(card).width()));
        assertTrue(rightEdge <= cardRight, "right-side controls should remain inside the auto-width flex card");
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
    void singleLineInputValueDoesNotWrapAtItsCssWidth() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:300px;height:200px;");

        Input input = new Input(document);
        input.setAttribute("type", "text");
        input.setAttribute("style", "width:64px;padding:2px 4px;border:1px solid transparent;font-size:11px;");
        input.setValue("group-title");
        document.body.appendChild(input);

        Text text = Text.of(input);
        assertEquals(1, Text.wrap(input).lines().size());
        assertEquals(Math.round(Text.calculateLineHeight(text.fontSize, "normal")) + 6, Size.of(input).height(), 0.01);
    }

    @Test
    void fittingTextInputDoesNotCreateCaretOverscrollOnMousePlacement() throws IOException {
        String family = "input-scroll-test-" + UUID.randomUUID();
        try (InputStream font = getClass().getResourceAsStream(
                "/assets/apricityui/apricity/apricityui/theme/ore/fonts/minecraft-regular.otf")) {
            assertNotNull(font);
            assertTrue(Font.registerFont(family, font));
        }

        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:300px;height:200px;");
        CaretTestInput input = new CaretTestInput(document);
        input.setAttribute("style", "box-sizing:border-box;width:180px;padding:8px 10px;border:3px solid black;"
                + "font-family:'" + family + "';font-size:16px;");
        input.setValue("short value");
        document.body.appendChild(input);

        input.placeCaretAtEnd();

        assertTrue(Size.measureText(input, input.getValue()) < Box.of(input).innerSize().width());
        assertEquals(0, input.scrollLeft, 0.001);
        assertEquals(0, input.targetScrollLeft, 0.001);
    }

    @Test
    void letterSpacingIncludesTheTrailingCharacterAdvance() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        Element plain = new Element(document, "span");
        plain.setAttribute("style", "font-size:11px;letter-spacing:0;");
        Element spaced = new Element(document, "span");
        spaced.setAttribute("style", "font-size:11px;letter-spacing:1.5px;");

        double plainWidth = Text.measureLine(Text.of(plain), "INSPECT");
        double spacedWidth = Text.measureLine(Text.of(spaced), "INSPECT");
        assertEquals(7 * 1.5, spacedWidth - plainWidth, 0.01);
    }

    @Test
    void spacedTextMeasurementMatchesPerGlyphRasterAdvanceThroughFinalGlyph() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        Element title = new Element(document, "span");
        title.setAttribute("style", "font-family:Dialog;font-size:14px;font-weight:600;letter-spacing:3px;");

        String content = "TEST/LXGWMARKERGOTHIC-REGULAR.TTF";
        Text text = Text.of(title);
        int style = text.isBold() ? java.awt.Font.BOLD : java.awt.Font.PLAIN;
        var runs = Font.planFontRuns(text.fontFamily, style, Font.getBaseFontSize(), content);
        double scale = text.renderedFontSize() / Font.getBaseFontSize();
        FontRenderContext browserMetrics = new FontRenderContext(new AffineTransform(), true, true);
        double expected = Font.measureFontRuns(runs, browserMetrics, text.letterSpacing / scale, true) * scale;

        assertEquals(expected, Text.measureLine(text, content), 0.01);
        double withoutTrailingSpacing = Font.measureFontRuns(
                runs, browserMetrics, text.letterSpacing / scale, false) * scale;
        assertEquals(text.letterSpacing, expected - withoutTrailingSpacing, 0.01);
    }

    @Test
    void flexDirectTextPaintDoesNotApplyContentOriginTwice() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:300px;height:200px;");

        Element button = new Element(document, "button");
        button.setAttribute("style", "display:flex;width:109px;height:30px;padding:0 14px;border:1px solid transparent;gap:8px;align-items:center;");
        Element icon = new Element(document, "span");
        icon.setAttribute("style", "width:14px;height:14px;flex-shrink:0;");
        button.appendChild(icon);
        button.appendChild(new TextNode(document, "INSPECT"));
        document.body.appendChild(button);

        Flex.DirectTextLayout textLayout = Flex.computeDirectTextLayouts(button).get(0);
        Position paint = readFlexDirectTextPaintPosition(button, textLayout);
        assertEquals(37, textLayout.position().x, 0.01);
        assertEquals(Position.of(button).x + 37, paint.x, 0.01);
    }

    @Test
    void nestedFlexItemCentersItsContentsWithinAssignedUsedWidth() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 500px; height: 200px;");

        Element tabs = new Element(document, "div");
        tabs.setAttribute("style", "display:flex;width:420px;height:32px;");
        document.body.appendChild(tabs);

        Element firstTab = null;
        Element firstIcon = null;
        for (String label : new String[]{"ATTRS", "STYLES", "BOX"}) {
            Element tab = new Element(document, "div");
            tab.setAttribute("style", "display:flex;flex:1;align-items:center;justify-content:center;gap:6px;");
            Element icon = new Element(document, "span");
            icon.setAttribute("style", "width:12px;height:12px;flex-shrink:0;");
            tab.appendChild(icon);
            tab.appendChild(new TextNode(document, label));
            tabs.appendChild(tab);
            if (firstTab == null) {
                firstTab = tab;
                firstIcon = icon;
            }
        }

        assertEquals(140, Math.round(Size.of(firstTab).width()));
        double textWidth = Text.measureLine(Text.of(firstTab), "ATTRS");
        double expectedGroupLeft = (140 - 12 - 6 - textWidth) / 2.0;
        assertEquals(expectedGroupLeft, Position.getOffset(firstIcon).x, 0.01);
        Flex.DirectTextLayout textLayout = Flex.computeDirectTextLayouts(firstTab).get(0);
        assertEquals(expectedGroupLeft + 12 + 6, textLayout.position().x, 0.01);
        assertTrue(textLayout.position().x + textWidth <= 140);
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
    void autoWidthBlockFlexFillsContainingBlockPerBrowserStandard() {
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
        assertEquals(140, Size.of(topbar).width());
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
    void autoHeightWrappedFlexUsesItsLineHeightBeforeStretchingItems() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element card = new Element(document, "div");
        card.setAttribute("style", "width: 240px; padding: 10px;");
        document.body.appendChild(card);

        Element tabs = new Element(document, "div");
        tabs.setAttribute("style", "display: flex; flex-wrap: wrap; gap: 4px;");
        card.appendChild(tabs);

        for (int i = 0; i < 3; i++) {
            Element tab = new Element(document, "button");
            tab.setAttribute("style", "width: 60px; min-height: 38px;");
            tabs.appendChild(tab);
        }

        assertEquals(38, Size.of(tabs).height());
        assertEquals(38, Size.of(tabs.children.get(0)).height());
        assertEquals(58, Size.of(card).height());
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
        assertTrue(Layout.isInFlow(new com.sighs.apricityui.style.Style()));

        com.sighs.apricityui.style.Style absolute = new com.sighs.apricityui.style.Style();
        absolute.position = "absolute";
        assertTrue(!Layout.isInFlow(absolute));

        com.sighs.apricityui.style.Style hidden = new com.sighs.apricityui.style.Style();
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
    void constrainedBorderBoxAspectRatioUsesTheOuterBox() {
        Document document = TestDocumentFactory.createDocument();
        Element child = new Element(document, "div");
        child.setAttribute("style", "box-sizing: border-box; padding: 20px 16px; border: 2px solid #000; aspect-ratio: 1 / 1;");
        document.body.appendChild(child);

        Size measured = Size.naturalAtContentWidth(child, 115);

        assertEquals(151, measured.width());
        assertEquals(151, measured.height());
    }

    @Test
    void aspectRatioHeightUsesWidthAfterMaxWidthClamp() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element child = new Element(document, "div");
        child.setAttribute("style", "width: 100%; max-width: 120px; aspect-ratio: 4 / 3;");
        document.body.appendChild(child);

        assertEquals(120, Size.of(child).width());
        assertEquals(90, Size.of(child).height());
    }

    @Test
    void rowFlexStretchDoesNotOverrideAspectRatioHeightFromDefiniteWidth() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element row = new Element(document, "div");
        row.setAttribute("style", "display: flex; align-items: stretch; width: 220px; height: 180px;");
        document.body.appendChild(row);

        Element child = new Element(document, "div");
        child.setAttribute("style", "width: 100%; max-width: 120px; aspect-ratio: 4 / 3;");
        row.appendChild(child);

        assertEquals(120, Size.of(child).width());
        assertEquals(90, Size.of(child).height());
    }

    @Test
    void percentHeightChildRecomputesAfterAspectRatioParentClamp() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "width: 100%; max-width: 120px; aspect-ratio: 4 / 3;");
        document.body.appendChild(parent);

        Element child = new Element(document, "div");
        child.setAttribute("style", "width: 100%; height: 100%;");
        parent.appendChild(child);

        assertEquals(90, Size.of(parent).height());
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
    void zeroMinFractionalGridTracksRetainSubpixelWidths() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: grid; width: 101px; column-gap: 2px; "
                + "grid-template-columns: repeat(3, minmax(0, 1fr));");
        document.body.appendChild(grid);

        Element first = new Element(document, "div");
        Element second = new Element(document, "div");
        Element third = new Element(document, "div");
        first.setAttribute("style", "height: 10px;");
        second.setAttribute("style", "height: 10px;");
        third.setAttribute("style", "height: 10px;");
        grid.appendChild(first);
        grid.appendChild(second);
        grid.appendChild(third);

        double trackWidth = 97.0 / 3.0;
        Position.getOffset(first);
        Position.getOffset(second);
        Position.getOffset(third);
        assertEquals(trackWidth, Size.of(first).width(), 0.0001);
        assertEquals(trackWidth + 2, Position.getOffset(second).x, 0.0001);
        assertEquals((trackWidth + 2) * 2, Position.getOffset(third).x, 0.0001);
        assertEquals(101, Layout.computeContentSize(grid).width(), 0.0001);
    }

    @Test
    void autoWidthGridInsideHalfWidthFlexColumnUsesColumnContentWidth() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "display: flex; width: 512px; height: 266px;");

        Element section = new Element(document, "section");
        section.setAttribute("style", "display: flex; flex-direction: column; width: 50%; padding-right: 8px; box-sizing: border-box;");
        document.body.appendChild(section);

        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 14px;");
        section.appendChild(grid);

        Element first = new Element(document, "div");
        first.setAttribute("style", "height: 10px;");
        Element second = new Element(document, "div");
        second.setAttribute("style", "height: 10px;");
        grid.appendChild(first);
        grid.appendChild(second);

        double secondX = Position.of(second).x;
        assertTrue(secondX < 150, "second grid column should stay inside the left section, x=" + secondX
                + " sectionWidth=" + section.getComputedStyle().width
                + " sectionBox=" + section.getComputedStyle().boxSizing
                + " gridWidth=" + grid.getComputedStyle().width
                + " template=" + grid.getComputedStyle().gridTemplateColumns
                + " gridContent=" + Layout.computeContentSize(grid).width()
                + " gridScale=" + Size.getScaleWidth(grid)
                + " sectionScale=" + Size.getScaleWidth(section));
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
    void autoHeightFractionalRowRemeasuresContentAfterCollapsedAssignment() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element host = new Element(document, "div");
        document.body.appendChild(host);

        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display: grid; width: 100px; grid-template-rows: 0fr;");
        Element inner = new Element(document, "div");
        inner.setAttribute("style", "overflow: hidden;");
        grid.appendChild(inner);
        host.appendChild(grid);

        // The initial collapsed layout assigns a zero-height grid area.
        Position.getOffset(inner);
        assertEquals(0, Size.of(inner).height());

        Element child = new Element(document, "div");
        child.setAttribute("style", "height: 40px;");
        inner.appendChild(child);
        grid.setAttribute("style", "display: grid; width: 100px; grid-template-rows: 1fr;");
        document.flushPendingStyleUpdates();

        assertEquals(40, Size.of(grid).height());
        Position.getOffset(inner);
        assertEquals(40, Size.of(inner).height());
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

    private static Position readFlexDirectTextPaintPosition(Element element, Flex.DirectTextLayout layout) {
        try {
            java.lang.reflect.Method method = Element.class.getDeclaredMethod(
                    "getFlexDirectTextPaintPosition", Flex.DirectTextLayout.class);
            method.setAccessible(true);
            return (Position) method.invoke(element, layout);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void assumeMinecraftClientTextRuntime() {
        // Text/layout assertions use the deterministic AWT font fallback in the
        // headless JVM test task; Minecraft font rendering is covered by the
        // client integration smoke suite.
    }

    private static void setViewport(Document document, int width, int height) throws Exception {
        setViewport(document, width, height, 1.0d);
    }

    private static void setViewport(Document document, int width, int height, double scissorScale) throws Exception {
        Field viewport = Document.class.getDeclaredField("viewport");
        viewport.setAccessible(true);
        viewport.set(document, new ApricityViewport(width, height, 1.0f, scissorScale));
    }

    private static final class CaretTestInput extends Input {
        private CaretTestInput(Document document) {
            super(document);
        }

        private void placeCaretAtEnd() {
            cursor = getValue().length();
            clampScroll();
        }
    }

    @Test
    void nestedFlowPositionsDoNotDoubleCountAncestorMargins() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 300px;");

        Element outer = new Element(document, "div");
        outer.setAttribute("style", "box-sizing: border-box;"
                + " width: 200px; height: 150px; padding: 10px; border: 2px solid #000; margin-left: 7px;");
        document.body.appendChild(outer);

        Element middle = new Element(document, "div");
        middle.setAttribute("style", "margin-left: 30px;");
        outer.appendChild(middle);

        Element inner = new Element(document, "div");
        inner.setAttribute("style", "margin-left: 25px;");
        middle.appendChild(inner);

        // 中间祖先的 margin 只能计入一次：middle = 7 + border2 + padding10 + margin30
        assertEquals(7, Position.of(outer).x, 0.01);
        assertEquals(49, Position.of(middle).x, 0.01);
        assertEquals(74, Position.of(inner).x, 0.01);
    }

}
