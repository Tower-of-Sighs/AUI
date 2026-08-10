package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.NormalFlow;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.dom.DocumentFragment;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.style.Text;
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
        assertSame(document.documentElement, document.getDocumentElement());
        assertSame(document.head, document.getHead());
        assertSame(document.documentElement, document.body.getParentNode());
        assertSame(document.documentElement, document.head.getParentNode());
        assertSame(document.body, document.getActiveElement());
        assertFalse(document.hasFocus());

        Element created = document.createElement("section");
        TextNode textNode = document.createTextNode("hello");
        CommentNode commentNode = document.createComment("anchor");
        assertEquals("SECTION", created.getNodeName());
        assertEquals(Element.ELEMENT_NODE, created.getNodeType());
        assertEquals("#text", textNode.getNodeName());
        assertEquals(Node.TEXT_NODE, textNode.getNodeType());
        assertEquals("hello", textNode.getTextContent());
        assertEquals("#comment", commentNode.getNodeName());
        assertEquals(Node.COMMENT_NODE, commentNode.getNodeType());
        assertEquals("anchor", commentNode.getTextContent());

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
    void disconnectedAttributeWritesDoNotQueueRenderWorkUntilMounted() {
        Document document = TestDocumentFactory.createDocument();
        document.getDirtyElements().clear();
        Element row = new Element(document, "div");
        Element cell = new Element(document, "span");

        row.setAttribute("class", "row");
        row.setAttribute("style", "display:flex;width:100px;");
        cell.setAttribute("class", "cell");
        cell.setAttribute("style", "width:50px;");
        row.appendChild(cell);

        assertFalse(row.isConnected());
        assertFalse(cell.isConnected());
        assertTrue(document.getDirtyElements().isEmpty(), () -> "dirty=" + document.getDirtyElements());

        document.body.appendChild(row);

        assertTrue(row.isConnected());
        assertTrue(cell.isConnected());
        assertTrue(document.getDirtyElements().contains(row));
    }

    @Test
    void clearChildrenRemovesSubtreesInOneDomOperation() {
        Document document = TestDocumentFactory.createDocument();
        Element host = document.createElement("div");
        Element first = document.createElement("span");
        Element nested = document.createElement("b");
        Element second = document.createElement("section");
        first.setAttribute("id", "first");
        nested.setAttribute("id", "nested");
        second.setAttribute("id", "second");

        document.body.appendChild(host);
        host.appendChild(first);
        first.appendChild(nested);
        host.appendChild(second);

        AtomicInteger callbackCalls = new AtomicInteger();
        document.createMutationObserver(records -> {
            callbackCalls.incrementAndGet();
            @SuppressWarnings("unchecked")
            List<Document.MutationRecord> snapshot = (List<Document.MutationRecord>) records;
            assertEquals(1, snapshot.size());
            assertSame(host, snapshot.get(0).target);
            assertEquals(List.of(first, second), snapshot.get(0).removedNodes);
        }).observe(host, true, false, false, false, false, false, "");

        host.clearChildren();
        document.flushMutationObservers();

        assertTrue(host.childNodes.isEmpty());
        assertTrue(host.children.isEmpty());
        assertNull(first.getParentNode());
        assertNull(second.getParentNode());
        assertNull(document.getElementById("first"));
        assertNull(document.getElementById("nested"));
        assertNull(document.getElementById("second"));
        assertFalse(document.getElements().contains(first));
        assertFalse(document.getElements().contains(nested));
        assertFalse(document.getElements().contains(second));
        assertEquals(1, callbackCalls.get());
    }

    @Test
    void queryTraversalAndSiblingApisExposeBrowserLikeRelationships() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element first = new Element(document, "button");
        Element second = new Element(document, "input");
        Element third = new Element(document, "span");
        TextNode text = document.createTextNode("tail-text");
        CommentNode comment = document.createComment("tail-comment");

        first.setAttribute("id", "first");
        first.setAttribute("class", "chip primary");
        second.setAttribute("name", "username");
        second.setAttribute("class", "chip");
        third.setTextContent("tail");

        document.appendChild(parent);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);
        parent.appendChild(text);
        parent.appendChild(comment);
        Element mountedSecond = parent.getChildren().get(1);

        assertSame(first, document.getElementById("first"));
        assertIterableEquals(List.of(first, mountedSecond), document.getElementsByClassName("chip"));
        assertIterableEquals(List.of(parent), document.getElementsByTagName("div"));
        assertIterableEquals(List.of(mountedSecond), document.getElementsByName("username"));
        assertIterableEquals(List.of(first, mountedSecond, third), parent.getChildren());
        assertIterableEquals(List.of(first, mountedSecond, third, text, comment), parent.getChildNodes());
        assertSame(first, parent.getFirstChild());
        assertSame(comment, parent.getLastChild());
        assertSame(parent, text.getParentNode());
        assertSame(comment, text.getNextSibling());
        assertSame(text, comment.getPreviousSibling());
        assertTrue(text.isConnected());
        assertTrue(comment.isConnected());
        assertSame(document, text.getOwnerDocument());
        assertSame(first, parent.getFirstElementChild());
        assertSame(third, parent.getLastElementChild());
        assertSame(mountedSecond, first.getNextElementSibling());
        assertSame(mountedSecond, third.getPreviousElementSibling());
        assertSame(parent, mountedSecond.getParentNode());
        assertTrue(mountedSecond.matches("input[name=\"username\"]"));
        assertSame(parent, mountedSecond.closest("div"));
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
        TextNode textNode = document.createTextNode("text-child");
        CommentNode commentNode = document.createComment("comment-child");

        host.setTextContent("<raw&text>");
        assertEquals("&lt;raw&amp;text&gt;", host.getInnerHTML());
        assertEquals("<div>&lt;raw&amp;text&gt;</div>", host.getOuterHTML());

        child.setAttribute("title", "\"quoted\"");
        child.setTextContent("ok");
        host.appendChild(child);
        assertEquals("<span title=\"&quot;quoted&quot;\">ok</span>", host.getInnerHTML());

        host.removeChild(child);
        host.appendChild(textNode);
        host.appendChild(commentNode);
        assertEquals("text-childcomment-child", host.getTextContent());
        assertEquals("text-child<!--comment-child-->", host.getInnerHTML());
        assertEquals("<div>text-child<!--comment-child--></div>", host.getOuterHTML());
        assertEquals(List.of(textNode, commentNode), host.getChildNodes());
        assertTrue(host.hasChildNodes());
        assertSame(textNode, host.getFirstChild());
        assertSame(commentNode, host.getLastChild());

        firstOption.setAttribute("value", "a");
        secondOption.setAttribute("value", "b");
        secondOption.setSelected(true);
        select.appendChild(firstOption);
        select.appendChild(secondOption);

        assertEquals(List.of(firstOption, secondOption), select.getOptions());
        assertEquals(List.of(secondOption), select.getSelectedOptions());
        assertEquals(1, select.getSelectedIndex());

        document.appendChild(host);
        textNode.remove();
        assertEquals(List.of(commentNode), host.getChildNodes());
        assertNull(textNode.getParentNode());
        assertFalse(textNode.isConnected());

        commentNode.remove();
        assertTrue(host.getChildNodes().isEmpty());
        assertNull(commentNode.getParentNode());
        assertFalse(commentNode.isConnected());
    }

    @Test
    void documentFragmentAppendsAndInsertsChildrenWithoutBecomingParentNode() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element anchor = new Element(document, "span");
        Element first = new Element(document, "b");
        TextNode text = document.createTextNode("txt");
        CommentNode comment = document.createComment("marker");
        DocumentFragment fragment = document.createDocumentFragment();

        document.body.appendChild(parent);
        parent.appendChild(anchor);
        fragment.appendChild(first);
        fragment.appendChild(text);
        fragment.appendChild(comment);

        parent.insertBefore(fragment, anchor);

        assertEquals(List.of(first, text, comment, anchor), parent.getChildNodes());
        assertSame(parent, first.getParentNode());
        assertSame(parent, text.getParentNode());
        assertSame(parent, comment.getParentNode());
        assertTrue(fragment.getChildNodes().isEmpty());
    }

    @Test
    void documentFragmentSetTextContentReplacesChildrenWithSingleTextNode() {
        Document document = TestDocumentFactory.createDocument();
        DocumentFragment fragment = document.createDocumentFragment();
        Element element = new Element(document, "span");
        TextNode textNode = document.createTextNode("before");

        fragment.appendChild(element);
        fragment.appendChild(textNode);
        fragment.setTextContent("after");

        assertEquals(1, fragment.getChildNodes().size());
        assertTrue(fragment.getFirstChild() instanceof TextNode);
        assertEquals("after", fragment.getTextContent());
        assertNull(element.getParentNode());
        assertNull(textNode.getParentNode());
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
        TextNode textNode = document.createTextNode("seed");
        CommentNode commentNode = document.createComment("marker");
        document.appendChild(parent);

        AtomicInteger callbackCalls = new AtomicInteger();
        Document.MutationObserver observer = document.createMutationObserver(records -> callbackCalls.incrementAndGet());
        observer.observe(parent, true, true, true, true, true, true, "class,data-state");

        parent.appendChild(child);
        parent.appendChild(textNode);
        parent.appendChild(commentNode);
        child.setAttribute("class", "chip");
        child.setAttribute("title", "ignored");
        child.setTextContent("alpha");
        textNode.setTextContent("updated");

        List<Document.MutationRecord> records = observer.takeRecords();
        assertEquals(6, records.size());

        Document.MutationRecord childList = records.get(0);
        assertEquals("childList", childList.type);
        assertSame(parent, childList.target);
        assertEquals(List.of(child), childList.addedNodes);

        Document.MutationRecord textAppend = records.get(1);
        assertEquals("childList", textAppend.type);
        assertSame(parent, textAppend.target);
        assertEquals(List.of(textNode), textAppend.addedNodes);

        Document.MutationRecord commentAppend = records.get(2);
        assertEquals("childList", commentAppend.type);
        assertSame(parent, commentAppend.target);
        assertEquals(List.of(commentNode), commentAppend.addedNodes);

        Document.MutationRecord attribute = records.get(3);
        assertEquals("attributes", attribute.type);
        assertSame(child, attribute.target);
        assertEquals("class", attribute.attributeName);
        assertNull(attribute.oldValue);

        Document.MutationRecord characterData = records.get(4);
        assertEquals("characterData", characterData.type);
        assertSame(child, characterData.target);
        assertEquals("", characterData.oldValue);

        Document.MutationRecord textMutation = records.get(5);
        assertEquals("characterData", textMutation.type);
        assertSame(textNode, textMutation.target);
        assertEquals("seed", textMutation.oldValue);

        child.setAttribute("data-state", "ready");
        document.flushMutationObservers();
        assertEquals(1, callbackCalls.get());

        observer.disconnect();
        child.setAttribute("class", "done");
        textNode.setTextContent("ignored");
        assertTrue(observer.takeRecords().isEmpty());
    }

    @Test
    void setTextContentReplacesExistingChildNodesWithPlainText() {
        Document document = TestDocumentFactory.createDocument();
        Element host = new Element(document, "div");
        Element nested = new Element(document, "span");
        TextNode textNode = document.createTextNode("before");
        CommentNode commentNode = document.createComment("marker");

        host.appendChild(nested);
        host.appendChild(textNode);
        host.appendChild(commentNode);
        assertEquals("beforemarker", host.getTextContent());

        host.setTextContent("reset");
        assertEquals("reset", host.getTextContent());
        assertTrue(host.getChildNodes().isEmpty());
        assertNull(nested.getParentNode());
        assertNull(textNode.getParentNode());
        assertNull(commentNode.getParentNode());
        assertFalse(textNode.isConnected());
        assertFalse(commentNode.isConnected());
    }

    @Test
    void innerAndOuterHtmlPreserveTextAndCommentNodesFromParsedMarkup() {
        Document document = TestDocumentFactory.createDocument();
        Element host = new Element(document, "div");
        Element wrapper = new Element(document, "section");

        host.setInnerHTML("hello<span>mid</span><!--tail-->");
        assertEquals(3, host.getChildNodes().size());
        assertTrue(host.getChildNodes().get(0) instanceof TextNode);
        assertTrue(host.getChildNodes().get(1) instanceof Element);
        assertTrue(host.getChildNodes().get(2) instanceof CommentNode);
        assertEquals("hello<span>mid</span><!--tail-->", host.getInnerHTML());

        document.body.appendChild(wrapper);
        wrapper.appendChild(host);
        host.setOuterHTML("before<strong>swap</strong><!--after-->");

        assertEquals(3, wrapper.getChildNodes().size());
        assertTrue(wrapper.getChildNodes().get(0) instanceof TextNode);
        assertTrue(wrapper.getChildNodes().get(1) instanceof Element);
        assertTrue(wrapper.getChildNodes().get(2) instanceof CommentNode);
        assertEquals("before<strong>swap</strong><!--after-->", wrapper.getInnerHTML());
    }

    @Test
    void innerHtmlParsedElementsInitializeDerivedAttributeState() {
        Document document = TestDocumentFactory.createDocument();
        Element host = new Element(document, "div");

        host.setInnerHTML("<div id=\"slot\" class=\"slot-card flex\" style=\"display:flex;background:white;border:2px solid #123456\"></div>");

        Element slot = host.querySelector("#slot");
        assertNotNull(slot);
        assertTrue(slot.getClassNames().contains("slot-card"));
        assertTrue(slot.getClassNames().contains("flex"));
        assertEquals("flex", slot.getComputedStyle().display);
        assertEquals("white", slot.getComputedStyle().backgroundColor);
        assertEquals("2px solid #123456", slot.getComputedStyle().border);
    }

    @Test
    void disconnectedInnerHtmlBuildsLocalSubtreeUntilHostIsMounted() {
        Document document = TestDocumentFactory.createDocument();
        Element host = new Element(document, "div");

        host.setInnerHTML("<div class=\"card\"><span>alpha</span></div>");

        assertEquals(1, host.getChildNodes().size());
        assertSame(host, host.getFirstChild().getParentNode());
        assertFalse(host.getFirstChild().isConnected());

        document.body.appendChild(host);

        Element card = host.querySelector(".card");
        assertNotNull(card);
        assertTrue(card.isConnected());
        assertEquals("alpha", card.getTextContent());
    }

    @Test
    void cloneNodeSupportsShallowAndDeepCopiesAcrossNodeTypes() {
        Document document = TestDocumentFactory.createDocument();
        Element host = new Element(document, "div");
        Element child = new Element(document, "span");
        TextNode textNode = document.createTextNode("hello");
        CommentNode commentNode = document.createComment("marker");

        child.setAttribute("data-role", "value");
        child.appendChild(document.createTextNode("nested"));
        host.appendChild(textNode);
        host.appendChild(child);
        host.appendChild(commentNode);

        Element shallow = host.cloneNode(false);
        Element deep = host.cloneNode(true);
        DocumentFragment fragment = document.createDocumentFragment();
        fragment.appendChild(host.cloneNode(true));
        DocumentFragment fragmentClone = (DocumentFragment) fragment.cloneNode(true);

        assertTrue(shallow.getChildNodes().isEmpty());
        assertEquals(host.getAttributes(), shallow.getAttributes());

        assertEquals(3, deep.getChildNodes().size());
        assertTrue(deep.getFirstChild() instanceof TextNode);
        assertTrue(deep.getChildNodes().get(1) instanceof Element);
        assertTrue(deep.getLastChild() instanceof CommentNode);
        assertEquals("hello", deep.getFirstChild().getTextContent());
        assertEquals("nested", deep.getChildNodes().get(1).getTextContent());
        assertEquals("marker", deep.getLastChild().getTextContent());

        assertEquals(1, fragmentClone.getChildNodes().size());
        assertNotSame(fragment.getFirstChild(), fragmentClone.getFirstChild());
    }

    @Test
    void textNodesParticipateInContentModelAlongsideInlineElements() {
        Document document = TestDocumentFactory.createDocument();
        Element paragraph = new Element(document, "p");
        TextNode prefix = document.createTextNode("点击");
        Element link = new Element(document, "a");
        TextNode suffix = document.createTextNode("继续");

        link.setTextContent("这里");
        paragraph.appendChild(prefix);
        paragraph.appendChild(link);
        paragraph.appendChild(suffix);

        assertEquals(List.of(prefix, link, suffix), paragraph.getChildNodes());
        assertEquals("点击这里继续", paragraph.getTextContent());
        assertEquals("点击<a>这里</a>继续", paragraph.getInnerHTML());
        prefix.setTextContent("请点击");
        assertEquals("请点击这里继续", paragraph.getTextContent());
    }

    @Test
    void textNodeRunsPreserveWhitespaceAroundInlineElements() {
        assertEquals("Hello ", normalizeInlineFragment("Hello ", "normal"));
        assertEquals(" !", normalizeInlineFragment(" !", "normal"));
        assertEquals("line \n next", normalizeInlineFragment("line  \n  next", "pre-line"));
    }

    @Test
    void textNodeOnlyElementsRemainSelectableAndOptionFallbackUsesTextNodes() {
        Document document = TestDocumentFactory.createDocument();
        Element label = new Element(document, "div");
        Element select = new Element(document, "select");
        Element option = new Element(document, "option");

        label.appendChild(document.createTextNode("alpha "));
        label.appendChild(document.createTextNode("beta"));

        assertTrue(label.canSelectInnerText());

        option.appendChild(document.createTextNode("visible"));
        select.appendChild(option);
        option.setSelected(true);
        assertEquals("visible", select.getValue());
    }

    @Test
    void richTextElementsWithInlineChildrenRemainSelectable() {
        Document document = TestDocumentFactory.createDocument();
        Element label = new Element(document, "div");
        document.body.appendChild(label);

        label.innerText = "alpha beta";
        assertTrue(label.canSelectInnerText());
        label.appendChild(new Element(document, "span"));
        // 富文本单元: 元素带内联子元素 (即使是空 span) 仍可选中, 不再只限叶子元素。
        assertTrue(label.canSelectInnerText());
    }

    @Test
    void textNodeMutationInvalidatesParentSizeCaches() {
        Document document = TestDocumentFactory.createDocument();
        Element host = new Element(document, "div");
        TextNode textNode = document.createTextNode("a");
        host.appendChild(textNode);
        document.body.appendChild(host);

        host.getRenderer().text.set(new Text());
        host.getRenderer().wrappedText.set(new Text.WrappedTextCache(1, 2, 3, 4L, new Text.WrappedText(List.of("a"), new int[]{0}, 1)));
        host.getRenderer().size.set(new Size(10, 10));

        textNode.setTextContent("alphabet");

        assertNull(host.getRenderer().size.get());
        assertNull(host.getRenderer().text.get());
        assertNull(host.getRenderer().wrappedText.get());
        assertTrue(document.getDirtyElements().contains(host));
    }

    @Test
    void commentNodeCharacterDataMutationQueuesMutationRecord() {
        Document document = TestDocumentFactory.createDocument();
        var comment = document.createComment("before");
        var observer = document.createMutationObserver(ignored -> {
        });
        observer.observe(comment, false, false, true, false, false, true, null);

        comment.setTextContent("after");

        assertEquals("after", comment.getTextContent());
        assertEquals("#comment", comment.getNodeName());
        assertEquals(Node.COMMENT_NODE, comment.getNodeType());
        assertEquals("<!--after-->", comment.toString());
        assertEquals(1, observer.takeRecords().size());
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

    private static String normalizeInlineFragment(String content, String whiteSpace) {
        try {
            java.lang.reflect.Method method = NormalFlow.class.getDeclaredMethod("normalizeInlineTextFragment", String.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, content, whiteSpace);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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
