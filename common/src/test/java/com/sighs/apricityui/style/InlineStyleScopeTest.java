package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InlineStyleScopeTest {
    private final List<Document> documents = new ArrayList<>();

    private static class CountedElement extends Element {
        int recalculations;

        CountedElement(Document document) {
            super(document, "div");
        }

        @Override
        public boolean recomputeStyleSelf() {
            recalculations++;
            return super.recomputeStyleSelf();
        }
    }

    @AfterEach
    void cleanup() {
        documents.forEach(Document::remove);
    }

    @Test
    void firstStyleAttributeAfterStylelessInitializationKeepsPreviousSnapshot() {
        Document document = create();
        Element element = new Element(document, "div");
        document.body.appendChild(element);
        document.commitStyleRecalc();

        element.setAttribute("style", "color:red");
        document.commitStyleRecalc();

        assertEquals(0xffff0000, Color.parse(element.getRawComputedStyle().color));
    }

    @Test
    void stylelessElementDefersInlineStyleAllocationUntilRequested() throws Exception {
        Element element = new Element(null, "div");
        java.lang.reflect.Field inlineStyleField = Element.class.getDeclaredField("inlineStyle");
        inlineStyleField.setAccessible(true);

        element.getInlineStylePropertyValue("color");
        assertNull(inlineStyleField.get(element));

        element.getStyle();
        assertNotNull(inlineStyleField.get(element));
    }

    @Test
    void emptyInlineStyleInitializationStillAcceptsCssomWrites() {
        Document document = create();
        Element element = new Element(document, "div");
        element.setAttribute("style", "");
        document.body.appendChild(element);

        element.getStyle().setProperty("color", "red");
        document.commitStyleRecalc();

        assertEquals("color: red;", element.getAttribute("style"));
        assertEquals(0xffff0000, Color.parse(element.getRawComputedStyle().color));
    }

    @Test
    void paintOnlyInlineWritesDoNotRecomputeDescendantStyles() {
        Document document = create();
        CountedElement parent = new CountedElement(document), child = new CountedElement(document);
        document.body.appendChild(parent);
        parent.appendChild(child);
        document.commitStyleRecalc();
        Style previous = child.getRawComputedStyle();
        child.recalculations = 0;
        parent.getStyle().setProperty("background-color", "red");
        parent.getStyle().setProperty("opacity", "0.5");
        document.commitStyleRecalc();
        assertEquals(0, child.recalculations);
        assertSame(previous, child.getRawComputedStyle());

        parent.getStyle().backgroundColor = "blue";
        document.commitStyleRecalc();
        assertEquals(0, child.recalculations, "legacy mutable fields must use the same narrow scope");
        assertSame(previous, child.getRawComputedStyle());
    }

    @Test
    void batchedInheritedWritesUseTheFirstSnapshotAndPropagate() {
        Document document = create();
        CountedElement parent = new CountedElement(document), child = new CountedElement(document);
        document.body.appendChild(parent);
        parent.appendChild(child);
        parent.setAttribute("style", "color:black");
        document.commitStyleRecalc();
        child.getRawComputedStyle();
        child.recalculations = 0;
        parent.getStyle().setProperty("color", "red");
        parent.getStyle().setProperty("background-color", "blue");
        document.commitStyleRecalc();
        assertTrue(child.recalculations > 0);
        assertEquals(0xffff0000, Color.parse(child.getRawComputedStyle().color));
        parent.removeAttribute("style");
        document.commitStyleRecalc();
        assertEquals(Color.parse(parent.getRawComputedStyle().color), Color.parse(child.getRawComputedStyle().color));
    }

    @Test
    void customPropertiesAndComputedDisplayStillPropagate() {
        Document document = create();
        CountedElement parent = new CountedElement(document), child = new CountedElement(document);
        document.body.appendChild(parent);
        parent.appendChild(child);
        parent.setAttribute("style", "--paint:red");
        child.setAttribute("style", "display:inline;background-color:var(--paint)");
        document.commitStyleRecalc();
        assertEquals(0xffff0000, Color.parse(child.getRawComputedStyle().backgroundColor));
        child.recalculations = 0;
        parent.getStyle().setProperty("--paint", "blue");
        parent.getStyle().setProperty("display", "flex");
        document.commitStyleRecalc();
        assertTrue(child.recalculations > 0);
        assertEquals(0xff0000ff, Color.parse(child.getRawComputedStyle().backgroundColor));
        assertEquals("block", child.getRawComputedStyle().display);
    }

    @Test
    void ancestorStyleAttributeSelectorsRetainSubtreeInvalidation() throws Exception {
        Document document = create();
        rules(document, ".leaf{color:blue} [style*=red] .leaf{color:red}");
        CountedElement parent = new CountedElement(document), child = new CountedElement(document);
        document.body.appendChild(parent);
        parent.appendChild(child);
        child.setAttribute("class", "leaf");
        document.commitStyleRecalc();
        assertEquals(0xff0000ff, Color.parse(child.getRawComputedStyle().color));
        parent.setAttribute("style", "background:red");
        document.commitStyleRecalc();
        assertEquals(0xffff0000, Color.parse(child.getRawComputedStyle().color));
    }

    @Test
    void functionalStyleSelectorsAlsoRefreshFollowingSiblings() throws Exception {
        for (String function : List.of("is", "where", "not")) {
            Document document = create();
            String selector = function.equals("not") ? ":not([style*=red])" : ":" + function + "([style*=red])";
            rules(document, "div{color:blue} " + selector + " + .leaf{color:red}");
            CountedElement changed = new CountedElement(document), sibling = new CountedElement(document);
            document.body.appendChild(changed);
            document.body.appendChild(sibling);
            sibling.setAttribute("class", "leaf");
            document.commitStyleRecalc();
            int before = Color.parse(sibling.getRawComputedStyle().color);
            changed.getRenderer().computedStyle.clear(); // Also cover a known index with no local baseline.
            changed.setAttribute("style", "background:red");
            document.commitStyleRecalc();
            assertNotEquals(before, Color.parse(sibling.getRawComputedStyle().color), function);
        }
    }

    @Test
    void inheritedParentChangeCoalescesAnAlreadyQueuedChildRequest() {
        Document document = create();
        CountedElement parent = new CountedElement(document), child = new CountedElement(document);
        document.body.appendChild(parent);
        parent.appendChild(child);
        document.commitStyleRecalc();
        child.getRawComputedStyle();
        child.recalculations = 0;
        parent.getStyle().setProperty("color", "red");
        child.getStyle().setProperty("background-color", "blue");
        document.commitStyleRecalc();
        assertEquals(1, child.recalculations, "parent propagation already covers the child's pending inline write");
        assertEquals(0xffff0000, Color.parse(child.getRawComputedStyle().color));
        assertEquals(0xff0000ff, Color.parse(child.getRawComputedStyle().backgroundColor));
    }

    private Document create() {
        Document document = TestDocumentFactory.createDocument();
        documents.add(document);
        return document;
    }

    private static void rules(Document document, String source) throws Exception {
        HashMap<String, Map<String, CSS.Declaration>> css = new HashMap<>();
        CSS.readCSS(source, css, "test://inline-style-scope.css");
        document.CSSCache.putAll(css);
        document.rebuildSelectorIndex();
    }
}
