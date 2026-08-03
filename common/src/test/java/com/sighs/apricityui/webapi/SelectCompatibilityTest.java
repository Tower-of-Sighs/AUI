package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Option;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SelectCompatibilityTest {
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_SPACE = 32;
    private static final int KEY_DOWN = 264;
    private static final int KEY_HOME = 268;

    @Test
    void singleSelectDefaultsToFirstOptionAndKeepsEmptyValuesDistinct() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        Option empty = option(document, "Empty", "");
        Option duplicate = option(document, "Duplicate", "");
        document.body.appendChild(select);
        select.appendChild(empty);
        select.appendChild(duplicate);

        assertEquals(0, select.getSelectedIndex());
        assertEquals("", select.getValue());
        assertTrue(empty.isSelected());
        assertFalse(duplicate.isSelected());

        duplicate.setSelected(true);
        assertEquals(1, select.getSelectedIndex());
        assertFalse(empty.isSelected());
        assertTrue(duplicate.isSelected());

        duplicate.setAttribute("disabled", "false");
        assertTrue(duplicate.isDisabled(), "Boolean attributes are true whenever present");
    }

    @Test
    void optionWithoutValueUsesNormalizedTextAndLabelUsesLabelAttribute() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        Option option = new Option(document);
        option.appendChild(document.createTextNode("  Alpha\n   Beta  "));
        option.setAttribute("label", "Visible label");
        document.body.appendChild(select);
        select.appendChild(option);

        assertEquals("Alpha Beta", option.getValue());
        assertEquals("Alpha Beta", select.getValue());
        assertEquals("Visible label", option.getOptionLabel());
        assertEquals("Alpha Beta", option.getOptionText());
        assertEquals(0, option.getOptionIndex());
        assertEquals(1, select.getSelectLength());
        assertEquals("select-one", select.getType());
    }

    @Test
    void optgroupOptionsParticipateAndDisabledGroupCannotBeChosen() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        Element group = document.createElement("optgroup");
        Option first = option(document, "First", "a");
        Option second = option(document, "Second", "b");
        group.setDisabled(true);
        group.appendChild(first);
        group.appendChild(second);
        document.body.appendChild(select);
        select.appendChild(group);

        assertEquals(List.of(first, second), select.getOptions());
        assertTrue(first.isOptionEffectivelyDisabled());
        assertTrue(second.isOptionEffectivelyDisabled());
        select.setValue("b");
        assertEquals(1, select.getSelectedIndex());
    }

    @Test
    void popupIsTopLevelAndMouseCommitDispatchesInputThenChange() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        Option first = option(document, "First", "a");
        Option disabled = option(document, "Disabled", "x");
        Option second = option(document, "Second", "b");
        disabled.setDisabled(true);
        document.body.appendChild(select);
        select.appendChild(first);
        select.appendChild(disabled);
        select.appendChild(second);
        setSelectTestGeometry(select);

        AtomicInteger input = new AtomicInteger();
        AtomicInteger change = new AtomicInteger();
        select.addEventListener("input", event -> input.incrementAndGet());
        select.addEventListener("change", event -> change.incrementAndGet());

        select.openPopup();
        Element popup = document.querySelector(".aui-select-popup");
        List<Element> rows = document.querySelectorAll(".aui-select-option");
        assertNotNull(popup);
        assertSame(document.body, popup.parentElement);
        assertNull(document.querySelector(".aui-select-backdrop"));
        assertEquals(3, rows.size());

        rows.get(1).click();
        assertEquals("a", select.getValue());
        assertTrue(select.isPopupOpen());

        rows.get(2).click();
        assertEquals("b", select.getValue());
        assertFalse(select.isPopupOpen());
        assertEquals(1, input.get());
        assertEquals(1, change.get());
    }

    @Test
    void popupUsesOwningViewportForFixedAncestorWithoutDocumentContext() throws Exception {
        Size.setViewportOverride(400, 300);
        Document document = TestDocumentFactory.createDocument();
        setViewport(document, 1600, 900);
        Element sidePanel = new Element(document, "div");
        sidePanel.setAttribute("style", "position:fixed;right:0;top:0;width:420px;height:900px;");

        Select select = new Select(document);
        select.appendChild(option(document, "First", "a"));
        select.appendChild(option(document, "Second", "b"));
        sidePanel.appendChild(select);
        document.body.appendChild(sidePanel);
        setGeometry(sidePanel, 420, 900, null);
        setGeometry(select, 200, 32, new Position(10, 10));

        try {
            try (Document.ContextScope ignored = Document.withContext(null)) {
                select.openPopup();
            }
            Element popup = document.querySelector(".aui-select-popup");
            assertNotNull(popup);
            assertTrue(popup.getAttribute("style").contains("left:1190.00px;"));
            assertTrue(popup.getAttribute("style").contains("top:42.00px;"));
        } finally {
            select.closePopup();
            document.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void popupMeasuresSelectAfterPendingLayoutInsteadOfUsingStaleWidth() throws Exception {
        Size.setViewportOverride(400, 300);
        Document document = TestDocumentFactory.createDocument();
        setViewport(document, 400, 300);
        Select select = new Select(document);
        select.appendChild(option(document, "First", "a"));
        document.body.appendChild(select);

        // Simulate the previous frame's committed geometry. The style change
        // below is still pending when the popup is opened.
        setGeometry(select, 400, 32, new Position(10, 10));
        select.setAttribute("style", "width:200px;");

        try {
            select.openPopup();
            Element popup = document.querySelector(".aui-select-popup");
            assertNotNull(popup);
            assertTrue(popup.getAttribute("style").contains("width:200.00px;"));
        } finally {
            select.closePopup();
            document.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void popupRowsInheritOptionTooltipTranslationKeys() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        Option first = option(document, "First", "a");
        Option second = option(document, "Second", "b");
        first.setAttribute("data-tooltip-key", "tooltip.first");
        second.setAttribute("data-tooltip-key", "tooltip.second");
        document.body.appendChild(select);
        select.appendChild(first);
        select.appendChild(second);
        setSelectTestGeometry(select);

        try {
            select.openPopup();
            List<Element> rows = document.querySelectorAll(".aui-select-option");
            assertEquals(2, rows.size());
            assertEquals("tooltip.first", rows.get(0).getAttribute("data-tooltip-key"));
            assertEquals("tooltip.second", rows.get(1).getAttribute("data-tooltip-key"));
        } finally {
            select.closePopup();
            document.remove();
        }
    }

    @Test
    void lightPopupDoesNotInheritSelectForegroundUnlessOptionDeclaresOne() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        select.setAttribute("style", "color:#ffffff;");
        Option selected = option(document, "Selected", "a");
        Option inherited = option(document, "Inherited", "b");
        Option authored = option(document, "Authored", "c");
        authored.setAttribute("style", "color:#c01020;");
        document.body.appendChild(select);
        select.appendChild(selected);
        select.appendChild(inherited);
        select.appendChild(authored);
        setSelectTestGeometry(select);

        try {
            select.openPopup();
            List<Element> rows = document.querySelectorAll(".aui-select-option");
            assertEquals(3, rows.size());
            assertEquals("#000000", rows.get(1).getComputedStyle().color,
                    "The native light popup must not reuse an inherited SELECT foreground");
            assertEquals("#c01020", rows.get(2).getComputedStyle().color,
                    "An OPTION's own author color remains supported");
        } finally {
            select.closePopup();
            document.remove();
        }
    }

    @Test
    void keyboardNavigationSkipsDisabledOptionsAndEscapeCancelsPopupChoice() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        Option first = option(document, "Alpha", "a");
        Option disabled = option(document, "Blocked", "b");
        Option third = option(document, "Charlie", "c");
        disabled.setDisabled(true);
        document.body.appendChild(select);
        select.appendChild(first);
        select.appendChild(disabled);
        select.appendChild(third);
        setSelectTestGeometry(select);

        assertTrue(select.handleKeyDownDefault(key(select, KEY_DOWN)));
        assertEquals("c", select.getValue());
        assertTrue(select.handleKeyDownDefault(key(select, KEY_HOME)));
        assertEquals("a", select.getValue());

        assertTrue(select.handleKeyDownDefault(key(select, KEY_SPACE)));
        assertTrue(select.isPopupOpen());
        assertTrue(select.handleKeyDownDefault(key(select, KEY_DOWN)));
        assertEquals("a", select.getValue());
        assertTrue(select.handleKeyDownDefault(key(select, KEY_ESCAPE)));
        assertFalse(select.isPopupOpen());
        assertEquals("a", select.getValue());
    }

    @Test
    void multipleSelectPreservesIndependentSelectedness() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        select.setMultiple(true);
        Option first = option(document, "First", "a");
        Option second = option(document, "Second", "b");
        document.body.appendChild(select);
        select.appendChild(first);
        select.appendChild(second);

        first.setSelected(true);
        second.setSelected(true);
        assertEquals(List.of(first, second), select.getSelectedOptions());
        assertEquals("a", select.getValue());
        assertEquals(0, select.getSelectedIndex());
    }

    @Test
    void multipleSelectPopupCommitsOneOptionWithoutClosingOrLooping() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        select.setMultiple(true);
        Option first = option(document, "First", "a");
        Option second = option(document, "Second", "b");
        Option third = option(document, "Third", "c");
        first.setSelected(true);
        second.setSelected(true);
        document.body.appendChild(select);
        select.appendChild(first);
        select.appendChild(second);
        select.appendChild(third);
        setSelectTestGeometry(select);

        AtomicInteger input = new AtomicInteger();
        AtomicInteger change = new AtomicInteger();
        select.addEventListener("input", event -> input.incrementAndGet());
        select.addEventListener("change", event -> change.incrementAndGet());

        try {
            select.openPopup();
            List<Element> rows = document.querySelectorAll(".aui-select-option");
            assertEquals(3, rows.size());
            rows.get(2).click();

            assertTrue(select.isPopupOpen());
            assertTrue(third.isSelected());
            assertEquals(List.of(first, second, third), select.getSelectedOptions());
            assertEquals(1, input.get());
            assertEquals(1, change.get());

            rows.get(0).click();
            assertFalse(first.isSelected());
            assertEquals(List.of(second, third), select.getSelectedOptions());
            assertEquals(2, input.get());
            assertEquals(2, change.get());
        } finally {
            select.closePopup();
            document.remove();
        }
    }

    @Test
    void structuralRemovalReselectsFirstOptionAndDisconnectClosesPopup() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        Option first = option(document, "First", "a");
        Option second = option(document, "Second", "b");
        document.body.appendChild(select);
        select.appendChild(first);
        select.appendChild(second);
        select.setSelectedIndex(1);

        select.removeChild(second);
        assertEquals(0, select.getSelectedIndex());
        assertEquals("a", select.getValue());

        setSelectTestGeometry(select);
        select.openPopup();
        assertTrue(select.isPopupOpen());
        document.body.removeChild(select);
        assertFalse(select.isPopupOpen());
        assertNull(document.querySelector(".aui-select-popup"));
    }

    @Test
    void optionSubtreeNeverParticipatesInSelectLayoutOrPaint() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        Option option = option(document, "Visible only through the control", "value");
        option.setAttribute("style", "display:block;padding:80px;");
        document.body.appendChild(select);
        select.appendChild(option);

        assertTrue(select.getRenderChildren().isEmpty());
        assertTrue(select.getRenderChildNodes().isEmpty());
        assertEquals("Visible only through the control", option.getOptionLabel());
        assertEquals("block", option.getComputedStyle().display,
                "Author OPTION styles remain queryable but cannot enter SELECT layout");
    }

    private static Option option(Document document, String label, String value) {
        Option option = new Option(document);
        option.setTextContent(label);
        option.setAttribute("value", value);
        return option;
    }

    private static KeyEvent key(Select select, int keyCode) {
        return new KeyEvent(select, "keydown", keyCode, 0, 0, false, KeyEvent.Source.INPUT_EVENT);
    }

    private static void setSelectTestGeometry(Select select) {
        setGeometry(select, 200, 32, new Position(10, 10));
    }

    private static void setGeometry(Element element, double width, double height, Position position) {
        Box box = new Box();
        box.element = element;
        element.getRenderer().box.set(box);
        element.getRenderer().size.set(new Size(width, height));
        element.getRenderer().position.set(position);
    }

    private static void setViewport(Document document, int width, int height) throws Exception {
        Field viewport = Document.class.getDeclaredField("viewport");
        viewport.setAccessible(true);
        viewport.set(document, new ApricityViewport(width, height, 1.0f, 1.0d));
    }

}
