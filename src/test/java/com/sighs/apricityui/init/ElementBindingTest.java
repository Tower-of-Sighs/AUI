package com.sighs.apricityui.init;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Option;
import com.sighs.apricityui.element.Select;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ElementBindingTest {
    @Test
    void valueAndDefaultValueBehaveLikeSeparateCurrentAndDefaultState() {
        Document document = createDocument();
        Input input = new Input(document);

        input.setDefaultValue("seed");
        assertEquals("seed", input.getDefaultValue());
        assertEquals("seed", input.getAttribute("value"));
        assertEquals("seed", input.getValue());

        input.setValue("alpha");
        assertEquals("alpha", input.getValue());
        assertEquals("seed", input.getDefaultValue());
        assertEquals("seed", input.getAttribute("value"));

        input.setAttribute("value", "beta");
        assertEquals("alpha", input.getValue());
        assertEquals("beta", input.getDefaultValue());

        input.setPlaceholder("hint");
        assertEquals("hint", input.getPlaceholder());
        assertEquals("hint", input.getAttribute("placeholder"));

        input.setAttribute("placeholder", "other");
        assertEquals("other", input.getPlaceholder());
    }

    @Test
    void datasetAndToggleAttributeStayInSync() {
        Document document = createDocument();
        Element element = new Element(document, "div");

        element.getDataset().set("userId", "42");
        assertEquals("42", element.getDataset().get("userId"));
        assertEquals("42", element.getAttribute("data-user-id"));

        element.setAttribute("data-theme-mode", "dark");
        assertEquals("dark", element.getDataset().get("themeMode"));
        assertTrue(element.getDataset().has("themeMode"));

        assertTrue(element.toggleAttribute("disabled", true));
        assertTrue(element.isDisabled());
        assertTrue(element.hasAttribute("disabled"));

        assertFalse(element.toggleAttribute("disabled", false));
        assertFalse(element.isDisabled());
        assertFalse(element.hasAttribute("disabled"));
    }

    @Test
    void radioCheckedStateIsMutuallyExclusiveAcrossGroup() {
        Document document = createDocument();
        Input first = new Input(document);
        Input second = new Input(document);
        first.setAttribute("type", "radio");
        second.setAttribute("type", "radio");
        first.setAttribute("name", "mode");
        second.setAttribute("name", "mode");

        document.appendChild(first);
        document.appendChild(second);

        first.setChecked(true);
        assertTrue(first.isChecked());
        assertFalse(second.isChecked());

        second.setAttribute("checked", "");
        assertFalse(second.isChecked());
        assertTrue(first.isChecked());
        assertTrue(second.isDefaultChecked());
        assertFalse(first.isDefaultChecked());
    }

    @Test
    void selectOptionStateStaysInSyncAcrossValueSelectedAndSelectedIndex() {
        Document document = createDocument();
        Select select = new Select(document);
        Option first = new Option(document);
        Option second = new Option(document);

        first.innerText = "First";
        first.setAttribute("value", "a");
        second.innerText = "Second";
        second.setAttribute("value", "b");

        document.appendChild(select);
        select.appendChild(first);
        select.appendChild(second);

        first.setAttribute("selected", "");
        assertEquals("a", select.getValue());
        assertTrue(first.isSelected());
        assertFalse(second.isSelected());
        assertEquals(0, select.getSelectedIndex());

        select.setValue("b");
        assertFalse(first.isSelected());
        assertTrue(second.isSelected());
        assertEquals(1, select.getSelectedIndex());

        select.setSelectedIndex(0);
        assertEquals("a", select.getValue());
        assertTrue(first.isSelected());
        assertFalse(second.isSelected());
    }

    @Test
    void defaultCheckedAndDefaultSelectedRemainSeparateFromCurrentState() {
        Document document = createDocument();
        Input input = new Input(document);
        input.setAttribute("type", "checkbox");

        input.setDefaultChecked(true);
        assertTrue(input.isDefaultChecked());
        assertTrue(input.isChecked());

        input.setChecked(false);
        assertFalse(input.isChecked());
        assertTrue(input.isDefaultChecked());
        assertTrue(input.hasAttribute("checked"));

        Select select = new Select(document);
        Option option = new Option(document);
        option.innerText = "One";
        option.setAttribute("value", "1");
        option.setDefaultSelected(true);
        document.appendChild(select);
        select.appendChild(option);

        assertTrue(option.isDefaultSelected());
        assertTrue(option.isSelected());

        option.setSelected(false);
        assertFalse(option.isSelected());
        assertTrue(option.isDefaultSelected());
        assertTrue(option.hasAttribute("selected"));
    }

    @Test
    void disabledBlocksClickDispatch() {
        Document document = createDocument();
        Element element = new Element(document, "button");
        AtomicInteger clicks = new AtomicInteger();
        element.addEventListener("click", event -> clicks.incrementAndGet());

        element.click();
        assertEquals(1, clicks.get());

        element.setDisabled(true);
        element.click();
        assertEquals(1, clicks.get());
    }

    @Test
    void documentEventApisForwardToBodyAndDispatchCorrectly() {
        Document document = createDocument();
        AtomicInteger customEvents = new AtomicInteger();
        Event seedEvent = new Event(null, "custom", null, false);

        document.addEventListener("custom", event -> customEvents.incrementAndGet());
        assertTrue(document.dispatchEvent(seedEvent));
        assertEquals(1, customEvents.get());
        assertSame(document.body, seedEvent.target);
        assertSame(document.body, seedEvent.currentTarget);

        document.removeEventListener("custom", event -> customEvents.incrementAndGet());
        Event secondEvent = new Event(null, "custom", null, false);
        assertTrue(document.dispatchEvent(secondEvent));
        assertEquals(2, customEvents.get());
    }

    @Test
    void documentCanRemoveRegisteredListener() {
        Document document = createDocument();
        AtomicInteger calls = new AtomicInteger();
        java.util.function.Consumer<Event> listener = event -> calls.incrementAndGet();

        document.addEventListener("custom", listener);
        assertTrue(document.dispatchEvent(new Event(null, "custom", null, false)));
        assertEquals(1, calls.get());

        document.removeEventListener("custom", listener);
        assertFalse(document.dispatchEvent(new Event(null, "custom", null, false)));
        assertEquals(1, calls.get());
    }

    @Test
    void movingAndReplacingNodesPreservesBindings() {
        Document document = createDocument();
        Element firstParent = new Element(document, "div");
        Element secondParent = new Element(document, "div");
        Element child = new Element(document, "div");

        document.appendChild(firstParent);
        document.appendChild(secondParent);

        child.setClassName("chip active");
        child.getDataset().set("state", "ready");
        firstParent.appendChild(child);

        secondParent.appendChild(child);
        assertSame(secondParent, child.getParentNode());
        assertEquals("chip active", child.getClassName());
        assertEquals("ready", child.getDataset().get("state"));
        assertTrue(child.getClassList().contains("active"));

        Element replacement = new Element(document, "div");
        replacement.setClassName("replacement");
        replacement.getDataset().set("state", "next");
        secondParent.replaceChild(replacement, child);

        assertSame(secondParent, replacement.getParentNode());
        assertEquals("replacement", replacement.getClassName());
        assertEquals("next", replacement.getDataset().get("state"));
        assertFalse(secondParent.contains(child));
    }

    @Test
    void selectStateSurvivesStructuralMoves() {
        Document document = createDocument();
        Select firstSelect = new Select(document);
        Select secondSelect = new Select(document);
        Option option = new Option(document);

        option.innerText = "Only";
        option.setAttribute("value", "only");
        option.setSelected(true);

        document.appendChild(firstSelect);
        document.appendChild(secondSelect);
        firstSelect.appendChild(option);

        assertEquals("only", firstSelect.getValue());
        assertTrue(option.isSelected());

        secondSelect.appendChild(option);
        assertSame(secondSelect, option.getParentNode());
        assertEquals("only", secondSelect.getValue());
        assertTrue(option.isSelected());
        assertEquals(0, secondSelect.getSelectedIndex());
    }

    private static Document createDocument() {
        Document document = new Document("test://doc", false);
        document.body = new Body(document);
        return document;
    }
}
