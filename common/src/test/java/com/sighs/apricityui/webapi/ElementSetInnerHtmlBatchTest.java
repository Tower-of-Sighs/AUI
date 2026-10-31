package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dom.DocumentExpander;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ElementSetInnerHtmlBatchTest {
    private Document document;
    private final DocumentExpander originalExpander = AuiServices.expander();

    @AfterEach
    void cleanup() {
        AuiServices.setExpander(originalExpander);
        if (document != null) document.remove();
    }

    @Test
    void unknownExpanderKeepsSequentialRemovalAndInsertion() {
        Document document = createDocumentWithHost("<i></i><b></b>");
        Element host = document.getElementById("host");
        RequiredContentSpy spy = new RequiredContentSpy(false);
        AuiServices.setExpander(spy);
        Document.MutationObserver observer = document.createMutationObserver(records -> {});
        observer.observe(host, true, false, false, false, false, false, null);

        host.setInnerHTML("<small></small><em></em>");

        assertEquals(4, observer.takeRecords().size());
        assertEquals(2, spy.hostRestores);
        assertEquals(4, spy.hostValidations);
        assertEquals(0, spy.normalizations);
    }

    @Test
    void specialParentsRetainRequiredContentAndSequentialSiblingValidationEvenWithOptIn() {
        Document document = createDocumentWithHost("");
        for (String tag : List.of("slot", "ingredient")) {
            Element parent = new Element(document, tag);
            document.body.appendChild(parent);
            parent.appendChild(new Element(document, "item"));
            RequiredContentSpy spy = new RequiredContentSpy(true);
            AuiServices.setExpander(spy);

            parent.setInnerHTML("<item id='first'></item><item id='last'></item>");

            assertEquals(1, parent.children.size());
            assertEquals("last", parent.children.get(0).id);
            assertNull(document.getElementById("first"));
            assertTrue(spy.requiredRestores > 0);
            assertTrue(spy.replacedItems >= 2, "placeholder and earlier inserted item must both be replaced");
            assertEquals(0, spy.normalizations);
            parent.setInnerHTML("");
            assertEquals(1, parent.children.size());
            assertEquals("ITEM", parent.children.get(0).tagName);
            assertTrue(parent.children.get(0).hasAttribute("data-restored"));
            AuiServices.setExpander(originalExpander);
            parent.remove();
        }
    }

    @Test
    void invalidSpecialInsertionPreservesEarlierInsertionAndDoesNotAttachLaterRoots() {
        Document document = createDocumentWithHost("");
        Element parent = new Element(document, "slot");
        document.body.appendChild(parent);
        parent.appendChild(new Element(document, "item"));
        RequiredContentSpy spy = new RequiredContentSpy(true);
        AuiServices.setExpander(spy);

        assertThrows(IllegalArgumentException.class, () -> parent.setInnerHTML(
                "<item id='valid'></item><input id='illegal' required><item id='unreached'></item>"));

        assertEquals(List.of(document.getElementById("valid")), parent.getChildNodes());
        assertFalse(document.getElementById("illegal").isConnected());
        assertFalse(parent.getChildren().stream().anyMatch(child -> "unreached".equals(child.id)));
        assertEquals(0, spy.normalizations);
    }

    @Test
    void parseInitializationFailureStillClearsOldChildrenBeforePropagating() {
        Document document = createDocumentWithHost("<b id='old'></b>");
        Element host = document.getElementById("host");
        Element old = document.getElementById("old");
        Element.register("batch-parse-failure", (owner, tag) -> {
            throw new IllegalStateException("parse init failed");
        });
        try {
            assertThrows(IllegalStateException.class, () -> host.setInnerHTML(
                    "<b id='uncommitted'></b><batch-parse-failure></batch-parse-failure>"));
            assertTrue(host.childNodes.isEmpty());
            assertFalse(old.isConnected());
            assertNull(document.getElementById("old"));
            assertFalse(document.getElementById("uncommitted").isConnected());
        } finally {
            Element.register("batch-parse-failure", Element::new);
        }
    }

    @Test
    void nullDocumentRetainsLegacyNoOpRemovalButClearsLegacyText() {
        Element host = new Element(null, "div");
        Element child = new Element(null, "b");
        host.childNodes.add(child);
        host.children.add(child);
        child.parentNode = host;
        child.parentElement = host;
        host.innerText = "legacy";

        host.setInnerHTML("<i></i>");

        assertEquals("", host.innerText);
        assertEquals(List.of(child), host.childNodes);
        assertSame(host, child.parentNode);
    }

    @Test
    void detachedReplacementKeepsInitializedMixedNodesDetachedAndObserverSilent() throws ReflectiveOperationException {
        // Headless tests do not run the loader's @ElementRegister scan. Install
        // the real Input factory locally and restore the exact previous mapping.
        java.lang.reflect.Field field = Element.class.getDeclaredField("REGISTRY");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, java.util.function.BiFunction<Document, String, ? extends Element>> registry =
                (java.util.Map<String, java.util.function.BiFunction<Document, String, ? extends Element>>) field.get(null);
        var previous = registry.get("INPUT");
        Element.register("INPUT", (owner, tag) -> new com.sighs.apricityui.element.Input(owner));
        try {
            Document document = createDocumentWithHost("");
            Element host = document.createHTML("<div><b>old</b></div>");
            Document.MutationObserver observer = document.createMutationObserver(records -> {});
            observer.observe(document.body, true, true, false, true, false, false, null);

            host.setInnerHTML("text<!--note--><input id='detached-input' required placeholder='value'><span><b>nested</b></span>");

            assertEquals(4, host.childNodes.size());
            assertEquals(Node.TEXT_NODE, host.childNodes.get(0).getNodeType());
            assertEquals(Node.COMMENT_NODE, host.childNodes.get(1).getNodeType());
            assertTrue(host.children.get(0) instanceof com.sighs.apricityui.element.Input);
            assertTrue(host.children.get(0).hasAttribute("required"));
            assertEquals("value", host.children.get(0).getPlaceholder());
            assertFalse(document.getElementById("detached-input").isConnected());
            for (Node child : host.childNodes) {
                assertSame(host, child.parentNode);
                assertFalse(child.isConnected());
            }
            assertSame(host.children.get(1), host.children.get(1).children.get(0).parentNode);
            assertTrue(observer.takeRecords().isEmpty());
            document.body.appendChild(host);
            assertSame(host.children.get(0), document.getElementById("detached-input"));
        } finally {
            if (previous == null) registry.remove("INPUT");
            else registry.put("INPUT", previous);
        }
    }

    @Test
    void batchReplacementRefreshesSelectionAndRuntimeStyleResources() {
        Document document = createDocumentWithHost("<style>#target { color: red; }</style><span id='target'>alpha</span>");
        Element host = document.getElementById("host");
        // Template STYLE is extracted by the parser; install runtime styles here.
        host.setInnerHTML("<style>#target { color: red; }</style><span id='target'>alpha</span>");
        Element oldTarget = document.getElementById("target");
        document.flushPendingStyleUpdates();
        assertEquals("red", oldTarget.getComputedStyle().color);
        assertEquals("alpha", document.getCachedFlattened(host));

        host.setInnerHTML("<style>#target { color: blue; }</style><span id='target'>beta</span>");
        document.flushPendingStyleUpdates();
        assertEquals("blue", document.getElementById("target").getComputedStyle().color);
        assertEquals("beta", document.getCachedFlattened(host));
        assertFalse(oldTarget.isConnected());
        host.setInnerHTML("<span id='target'>plain</span>");
        document.flushPendingStyleUpdates();
        assertNotEquals("blue", document.getElementById("target").getComputedStyle().color);
    }

    @Test
    void wrapperAndFragmentPreparationInitializeEachReplacementOnlyOnce() {
        Document document = createDocumentWithHost("");
        Element.register("batch-init-probe", InitProbe::new);
        try {
            Element connected = document.getElementById("host");
            Element detached = document.createHTML("<div></div>");
            for (Element host : List.of(connected, detached)) {
                host.setInnerHTML("<batch-init-probe><batch-init-probe></batch-init-probe></batch-init-probe>");
                InitProbe root = assertInstanceOf(InitProbe.class, host.children.get(0));
                InitProbe nested = assertInstanceOf(InitProbe.class, root.children.get(0));
                assertEquals(1, root.initCount);
                assertEquals(1, nested.initCount);
                assertSame(host, root.parentNode);
                assertSame(root, nested.parentNode);
                assertEquals(host.isConnected(), root.isConnected());
            }
        } finally {
            Element.register("batch-init-probe", Element::new);
        }
    }

    private static final class InitProbe extends Element {
        private int initCount;

        private InitProbe(Document document, String tagName) { super(document, tagName); }

        @Override
        protected void onInitFromDom(Element origin) { initCount++; }
    }

    private static final class RequiredContentSpy implements DocumentExpander {
        private final boolean optIn;
        private boolean adjusting;
        private int requiredRestores;
        private int replacedItems;
        private int hostRestores;
        private int hostValidations;
        private int normalizations;

        private RequiredContentSpy(boolean optIn) { this.optIn = optIn; }

        @Override
        public void apply(Document document) {}

        @Override
        public boolean supportsInnerHtmlBatch(Document document, Node parent) { return optIn; }

        private boolean special(Node parent) {
            return parent instanceof Element element
                    && ("SLOT".equals(element.tagName) || "INGREDIENT".equals(element.tagName));
        }

        @Override
        public void validateRuntimeInsertion(Document document, Node parent, Node child) {
            if (parent instanceof Element element && "host".equals(element.id)) hostValidations++;
            if (adjusting || !special(parent) || !(child instanceof Element element)) return;
            if (!"ITEM".equals(element.tagName)) throw new IllegalArgumentException("Only ITEM is allowed");
            adjusting = true;
            try {
                for (Node sibling : new ArrayList<>(parent.childNodes)) {
                    if (sibling instanceof Element item && "ITEM".equals(item.tagName)) {
                        sibling.remove();
                        replacedItems++;
                    }
                }
            } finally {
                adjusting = false;
            }
        }

        @Override
        public void restoreRequiredContent(Document document, Node parent) {
            if (parent instanceof Element element && "host".equals(element.id)) hostRestores++;
            if (adjusting || !special(parent) || !parent.childNodes.isEmpty()) return;
            requiredRestores++;
            Element placeholder = new Element(document, "item");
            placeholder.setAttribute("data-restored", "");
            adjusting = true;
            try {
                parent.appendChild(placeholder);
            } finally {
                adjusting = false;
            }
        }

        @Override
        public void normalizeRuntimeChildren(Document document, Node parent) { normalizations++; }
    }

    @Test
    void replacesConnectedSubtreeInOrderAndMovesFocusOutOfRemovedInput() {
        Document document = createDocumentWithHost("<section id='old'><input id='focus-target'></section><b id='old-tail'></b>");
        Element host = document.getElementById("host");
        Element oldSection = document.getElementById("old");
        Element oldInput = document.getElementById("focus-target");
        oldInput.focus();

        host.setInnerHTML("<p id='first'>one</p><input id='focus-target'><span id='last'>three</span>");

        assertFalse(oldSection.isConnected());
        assertFalse(oldInput.isConnected());
        assertFalse(host.contains(oldSection));
        assertFalse(host.contains(oldInput));
        assertNull(document.getElementById("old"));
        assertNull(document.getElementById("old-tail"));
        assertEquals(List.of("P", "INPUT", "SPAN"), host.getChildren().stream().map(Element::getNodeName).toList());
        assertSame(host.getChildren().get(0), document.getElementById("first"));
        assertSame(host.getChildren().get(2), document.getElementById("last"));
        assertNotSame(oldInput, document.getElementById("focus-target"));
        assertNotSame(oldInput, document.getActiveElement());
    }

    @Test
    void connectedReplacementBatchesChildListRecordsWithoutLosingNodeOrder() {
        Document document = createDocumentWithHost("<i id='left'></i><b id='removed'></b><u id='right'></u>");
        Element host = document.getElementById("host");
        Element left = document.getElementById("left");
        Element removed = document.getElementById("removed");
        Element right = document.getElementById("right");
        List<Document.MutationRecord> observed = new ArrayList<>();
        document.createMutationObserver(records -> {
            @SuppressWarnings("unchecked")
            List<Document.MutationRecord> batch = (List<Document.MutationRecord>) records;
            observed.addAll(batch);
        }).observe(host, true, false, false, false, false, false, null);

        host.setInnerHTML("<small id='new-a'></small><small id='new-b'></small>");
        document.flushMutationObservers();

        assertEquals(2, observed.size());
        assertEquals(List.of(left, removed, right), observed.get(0).removedNodes);
        assertNull(observed.get(0).previousSibling);
        assertNull(observed.get(0).nextSibling);
        assertEquals(List.of(document.getElementById("new-a"), document.getElementById("new-b")), observed.get(1).addedNodes);
        assertNull(observed.get(1).previousSibling);
        assertNull(observed.get(1).nextSibling);
    }

    @Test
    void connectedStyleLinksAreDetachedAndAttachedAndRequiredPlaceholderSurvivesParsing() {
        Document document = createDocumentWithHost("<link id='old-style' rel='stylesheet' href='old.css'><input id='old-field'>");
        Element host = document.getElementById("host");
        Element oldLink = document.getElementById("old-style");

        host.setInnerHTML("<link id='new-style' rel='stylesheet' href='new.css'><input id='required' required placeholder='enter value'>");

        assertFalse(oldLink.isConnected());
        assertTrue(document.getElementById("new-style").isConnected());
        Element required = document.getElementById("required");
        assertNotNull(required);
        assertTrue(required.hasAttribute("required"));
        assertEquals("enter value", required.getPlaceholder());
    }

    @Test
    void nullOrEmptyMarkupClearsExistingDomConsistently() {
        Document document = createDocumentWithHost("<section id='preserved'><b>old</b></section>");
        Element host = document.getElementById("host");

        host.setInnerHTML(null);
        assertTrue(host.getChildNodes().isEmpty());
        assertNull(document.getElementById("preserved"));

        host.setInnerHTML("<section id='again'></section>");
        host.setInnerHTML("");
        assertTrue(host.getChildNodes().isEmpty());
        assertNull(document.getElementById("again"));
    }

    private Document createDocumentWithHost(String initialChildren) {
        String path = "test://inner-html-batch/" + java.util.UUID.randomUUID();
        HTML.putTemple(path, "<html><head></head><body><div id='host'>" + initialChildren + "</div></body></html>");
        document = Document.create(path);
        return document;
    }
}
