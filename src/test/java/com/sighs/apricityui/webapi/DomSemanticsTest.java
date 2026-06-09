package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DomSemanticsTest {
    @Test
    void documentApisExposeMetadataCreationAndBodyForwarding() {
        Document document = TestDocumentFactory.createDocument();

        assertEquals("test://doc", document.getURL());
        assertEquals("test://doc", document.getDocumentURI());
        assertEquals("test://doc", document.getBaseURI());
        assertEquals("test://doc", document.getLocation().getHref());
        assertEquals("test:", document.getLocation().getProtocol());
        assertEquals("loading", document.getReadyState());
        assertSame(document.body, document.getActiveElement());
        assertFalse(document.hasFocus());

        Element created = document.createElement("section");
        Element textNode = document.createTextNode("hello");
        assertEquals("SECTION", created.getNodeName());
        assertEquals(Element.ELEMENT_NODE, created.getNodeType());
        assertEquals("SPAN", textNode.getNodeName());
        assertEquals("hello", textNode.getTextContent());

        Element appended = new Element(document, "div");
        Element prepended = new Element(document, "header");
        document.appendChild(appended);
        document.prepend(prepended);

        assertEquals(List.of(prepended, appended), document.body.getChildren());

        appended.focus();
        assertSame(appended, document.getActiveElement());
        assertTrue(document.hasFocus());
        document.blur();
        assertSame(document.body, document.getActiveElement());
        assertFalse(document.hasFocus());
    }

    @Test
    void documentScrollApisForwardToBodyAndDispatchOnlyOnChange() {
        Document document = new Document("test://doc", false);
        TestBody body = new TestBody(document);
        document.body = body;

        AtomicInteger scrollCalls = new AtomicInteger();
        body.addEventListener("scroll", event -> scrollCalls.incrementAndGet());

        document.scrollTo(4, 8);
        assertEquals(4, body.getTargetScrollLeft());
        assertEquals(8, body.getTargetScrollTop());
        assertEquals(1, scrollCalls.get());

        document.scrollTo(4, 8);
        assertEquals(1, scrollCalls.get());

        document.scrollBy(3, 2);
        assertEquals(7, body.getTargetScrollLeft());
        assertEquals(10, body.getTargetScrollTop());
        assertEquals(2, scrollCalls.get());
    }

    @Test
    void classListAndDatasetStaySynchronizedWithAttributes() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");

        element.setClassName("chip active");
        assertEquals(2, element.getClassList().getLength());
        assertTrue(element.getClassList().contains("chip"));
        assertEquals("chip", element.getClassList().item(0));

        element.getClassList().add("selected", "chip");
        assertEquals("chip active selected", element.getClassName());

        assertFalse(element.getClassList().toggle("active", false));
        assertEquals("chip selected", element.getClassName());
        assertTrue(element.getClassList().toggle("active"));
        assertEquals("chip selected active", element.getClassList().toString());

        element.getDataset().set("userId", "42");
        element.setAttribute("data-theme-mode", "dark");

        assertEquals("42", element.getDataset().get("userId"));
        assertEquals("dark", element.getDataset().get("themeMode"));
        assertTrue(element.getDataset().has("themeMode"));
        assertEquals(new LinkedHashSet<>(List.of("userId", "themeMode")), element.getDataset().keys());

        element.getDataset().delete("userId");
        assertFalse(element.hasAttribute("data-user-id"));
        assertFalse(element.getDataset().has("userId"));

        element.setName("profile");
        element.setType("checkbox");
        element.setMultiple(true);
        assertEquals("profile", element.getName());
        assertEquals("checkbox", element.getType());
        assertTrue(element.isMultiple());
    }

    @Test
    void queryTraversalAndSiblingApisExposeBrowserLikeRelationships() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element first = new Element(document, "button");
        Element second = new Element(document, "input");
        Element third = new Element(document, "span");

        first.setAttribute("id", "first");
        first.setAttribute("class", "chip primary");
        second.setAttribute("name", "username");
        second.setAttribute("class", "chip");
        third.setTextContent("tail");

        document.appendChild(parent);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);

        assertSame(first, document.getElementById("first"));
        assertEquals(List.of(first, second), document.getElementsByClassName("chip"));
        assertEquals(List.of(parent), document.getElementsByTagName("div"));
        assertEquals(List.of(second), document.getElementsByName("username"));
        assertEquals(List.of(first, second, third), parent.getChildren());
        assertEquals(List.of(first, second, third), parent.getChildNodes());
        assertSame(first, parent.getFirstElementChild());
        assertSame(third, parent.getLastElementChild());
        assertSame(second, first.getNextElementSibling());
        assertSame(second, third.getPreviousElementSibling());
        assertSame(parent, second.getParentNode());
        assertTrue(second.matches("input[name=\"username\"]"));
        assertSame(parent, second.closest("div"));
        assertTrue(parent.contains(third));
    }

    @Test
    void toggleAttributeAndBoundingClientRectMatchDocumentedBehavior() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");

        assertTrue(element.toggleAttribute("hidden"));
        assertTrue(element.hasAttribute("hidden"));
        assertFalse(element.toggleAttribute("hidden"));
        assertFalse(element.hasAttribute("hidden"));

        Box box = new Box();
        box.element = element;
        element.getRenderer().box.set(box);
        element.getRenderer().size.set(new Size(30, 40));
        element.getRenderer().position.set(new Position(5, 7));

        Element.DOMRect rect = element.getBoundingClientRect();
        assertEquals(5, rect.x);
        assertEquals(7, rect.y);
        assertEquals(30, rect.width);
        assertEquals(40, rect.height);
        assertEquals(35, rect.right);
        assertEquals(47, rect.bottom);
    }

    @Test
    void elementContentCollectionsAndRemovalMatchDocumentedBehavior() {
        Document document = TestDocumentFactory.createDocument();
        Element host = new Element(document, "div");
        Element child = new Element(document, "span");
        Element select = new Element(document, "select");
        Element firstOption = new Element(document, "option");
        Element secondOption = new Element(document, "option");

        host.setTextContent("<raw&text>");
        assertEquals("&lt;raw&amp;text&gt;", host.getInnerHTML());
        assertEquals("<div>&lt;raw&amp;text&gt;</div>", host.getOuterHTML());

        child.setAttribute("title", "\"quoted\"");
        child.setTextContent("ok");
        host.appendChild(child);
        assertEquals("<span title=\"&quot;quoted&quot;\">ok</span>", host.getInnerHTML());

        firstOption.setAttribute("value", "a");
        secondOption.setAttribute("value", "b");
        secondOption.setSelected(true);
        select.appendChild(firstOption);
        select.appendChild(secondOption);

        assertEquals(List.of(firstOption, secondOption), select.getOptions());
        assertEquals(List.of(secondOption), select.getSelectedOptions());
        assertEquals(1, select.getSelectedIndex());

        document.appendChild(host);
        child.remove();
        assertTrue(host.getChildren().isEmpty());
        assertNull(child.getParentNode());
    }

    @Test
    void beforeAfterAndReplaceWithMutateTreeInExpectedOrder() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element anchor = new Element(document, "span");
        Element before = new Element(document, "p");
        Element after = new Element(document, "button");
        Element replacement = new Element(document, "section");

        document.appendChild(parent);
        parent.appendChild(anchor);

        anchor.before(before);
        anchor.after(after);
        assertEquals(List.of(before, anchor, after), parent.getChildren());

        anchor.replaceWith(replacement);
        assertEquals(List.of(before, replacement, after), parent.getChildren());
        assertNull(anchor.getParentNode());
        assertSame(parent, replacement.getParentNode());
    }

    @Test
    void mutationObserverCapturesChildListAttributesAndCharacterDataWithFilters() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "span");
        document.appendChild(parent);

        AtomicInteger callbackCalls = new AtomicInteger();
        Document.MutationObserver observer = document.createMutationObserver(records -> callbackCalls.incrementAndGet());
        observer.observe(parent, true, true, true, true, true, true, "class,data-state");

        parent.appendChild(child);
        child.setAttribute("class", "chip");
        child.setAttribute("title", "ignored");
        child.setTextContent("alpha");

        List<Document.MutationRecord> records = observer.takeRecords();
        assertEquals(3, records.size());

        Document.MutationRecord childList = records.get(0);
        assertEquals("childList", childList.type);
        assertSame(parent, childList.target);
        assertEquals(List.of(child), childList.addedNodes);

        Document.MutationRecord attribute = records.get(1);
        assertEquals("attributes", attribute.type);
        assertSame(child, attribute.target);
        assertEquals("class", attribute.attributeName);
        assertNull(attribute.oldValue);

        Document.MutationRecord characterData = records.get(2);
        assertEquals("characterData", characterData.type);
        assertSame(child, characterData.target);
        assertEquals("", characterData.oldValue);

        child.setAttribute("data-state", "ready");
        document.flushMutationObservers();
        assertEquals(1, callbackCalls.get());

        observer.disconnect();
        child.setAttribute("class", "done");
        assertTrue(observer.takeRecords().isEmpty());
    }

    @Test
    void windowComputedStyleExposesInlineComputedValues() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        element.setAttribute("style", "width: 10px; height: 20px; display: block;");

        Window.CSSStyleDeclaration style = new Window().getComputedStyle(element);
        assertEquals("10px", style.getPropertyValue("width"));
        assertEquals("20px", style.get("height"));
        assertEquals("block", style.getPropertyValue("display"));
        assertEquals("", style.getPropertyValue("missing-prop"));
    }

    private static final class TestBody extends Body {
        private double scrollLeft;
        private double scrollTop;

        private TestBody(Document document) {
            super(document);
        }

        @Override
        public void setScrollLeft(double value) {
            scrollLeft = value;
        }

        @Override
        public void setScrollTop(double value) {
            scrollTop = value;
        }

        @Override
        public double getTargetScrollLeft() {
            return scrollLeft;
        }

        @Override
        public double getTargetScrollTop() {
            return scrollTop;
        }
    }
}
