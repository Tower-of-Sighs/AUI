package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptDomBridgeTest {
    @Test
    void directInlineStyleFieldWritesSynchronizeAttributeAndComputedStyle() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);

        Style style = element.getStyle();
        assertNotNull(style);
        assertEquals("", style.width);

        style.width = "120px";

        assertEquals("120px", element.getComputedStyle().width);
        assertEquals("width: 120px;", element.getAttribute("style"));
        assertSame(style, element.getStyle());
        assertTrue(document.getDirtyElements().contains(element));
    }

    @Test
    void inlineStyleDeclarationApiIsBidirectionalAndKeepsObjectIdentity() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);
        Style style = element.getStyle();

        element.setInlineStyleProperty("display", "block");
        element.setInlineStyleProperty("backgroundColor", "#123456", "important");
        element.setInlineStyleProperty("--accent", "rgb(1, 2, 3)");

        assertEquals("display: block; background-color: #123456 !important; --accent: rgb(1, 2, 3);",
                element.getAttribute("style"));
        assertEquals("block", element.getInlineStylePropertyValue("display"));
        assertEquals("#123456", element.getInlineStylePropertyValue("background-color"));
        assertEquals("important", element.getInlineStylePropertyPriority("backgroundColor"));
        assertEquals("rgb(1, 2, 3)", element.getInlineStylePropertyValue("--accent"));
        assertEquals("#123456", element.getComputedStyle().backgroundColor);

        element.setAttribute("style", "width: 48px; margin: 2px 4px;");
        assertSame(style, element.getStyle());
        assertEquals("48px", style.width);
        assertEquals("2px", element.getInlineStylePropertyValue("margin-top"));
        assertEquals("4px", element.getInlineStylePropertyValue("margin-left"));
        assertEquals("48px", element.removeInlineStyleProperty("width"));
        assertEquals("margin: 2px 4px;", element.getAttribute("style"));
    }

    @Test
    void duplicateInlineDeclarationsFollowBrowserCssomSemantics() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);

        element.setAttribute("style", "color: red !important; color: blue;");

        // cssText 保留完整声明列表（含重复属性与优先级）。
        assertEquals("color: red !important; color: blue;", element.getInlineStyleCssText());
        // getPropertyValue/priority 取最后一条声明。
        assertEquals("blue", element.getInlineStylePropertyValue("color"));
        assertEquals("", element.getInlineStylePropertyPriority("color"));
        // 层叠仍是 important 优先。
        assertEquals("red", element.getComputedStyle().color);

        // 同一重要性下后者胜出。
        element.setAttribute("style", "color: red; color: blue");
        assertEquals("blue", element.getComputedStyle().color);

        // setProperty 移除该属性全部声明后追加到末尾（浏览器行为）。
        element.setAttribute("style", "color: red; width: 10px;");
        element.setInlineStyleProperty("color", "green");
        assertEquals("width: 10px; color: green;", element.getInlineStyleCssText());

        // removeProperty 移除该属性的全部声明，返回最后一条的值。
        element.setAttribute("style", "width: 10px; color: red; color: blue;");
        assertEquals("width: 10px; color: red; color: blue;", element.getInlineStyleCssText());
        assertEquals("blue", element.removeInlineStyleProperty("color"));
        assertEquals("width: 10px;", element.getAttribute("style"));
        assertEquals("width: 10px;", element.getInlineStyleCssText());
    }

    @Test
    void inlineStyleMutationQueuesOneStyleAttributeRecord() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);
        var observer = document.createMutationObserver(ignored -> {
        });
        observer.observe(element, false, true, false, false, true, false, "style");

        element.setInlineStyleProperty("height", "32px");

        var records = observer.takeRecords();
        assertEquals(1, records.size());
        assertEquals("style", records.get(0).attributeName);
        assertEquals(null, records.get(0).oldValue);

        element.setInlineStyleProperty("height", "32px");
        assertTrue(observer.takeRecords().isEmpty());
    }

    @Test
    void parsedUpgradedAndClonedElementsKeepInlineStyleSynchronized() {
        Document document = TestDocumentFactory.createDocument();
        Element parsed = document.createHTML("<textarea style='width: 42px; color: red'></textarea>");

        assertEquals("42px", parsed.getStyle().width);
        parsed.getStyle().width = "54px";

        Element cloned = parsed.cloneNode(true);
        assertEquals("54px", parsed.getComputedStyle().width);
        assertEquals("54px", cloned.getStyle().width);
        assertEquals("red", cloned.getInlineStylePropertyValue("color"));
        cloned.setAttribute("style", "height: 18px;");
        assertEquals("", cloned.getStyle().width);
        assertEquals("18px", cloned.getStyle().height);
    }

    @Test
    void legacyFieldAndDeclarationMethodWritesComposeWithoutLosingChanges() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");

        element.getStyle().width = "70px";
        element.setInlineStyleProperty("height", "80px");

        assertEquals("width: 70px; height: 80px;", element.getAttribute("style"));
        assertEquals("70px", element.getComputedStyle().width);
        assertEquals("80px", element.getComputedStyle().height);
    }

    @Test
    void declarationParsingPreservesComplexValuesAndCssomRemovalRules() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");

        assertEquals("", element.getInlineStylePropertyValue("display"));
        element.setInlineStyleCssText("display: block; --payload: 'a;b:c'; background-image: url('x;y.png');");

        assertEquals("block", element.getInlineStylePropertyValue("display"));
        assertEquals("'a;b:c'", element.getInlineStylePropertyValue("--payload"));
        assertEquals("url('x;y.png')", element.getInlineStylePropertyValue("background-image"));
        element.setInlineStyleCssText("content: '!important'; --literal: fn(!important);");
        assertEquals("'!important'", element.getInlineStylePropertyValue("content"));
        assertEquals("", element.getInlineStylePropertyPriority("content"));
        assertEquals("fn(!important)", element.getInlineStylePropertyValue("--literal"));
        element.setInlineStyleProperty("display", "", "important");
        assertEquals("", element.getInlineStylePropertyValue("display"));
        String beforeInvalidPriority = element.getInlineStyleCssText();
        element.setInlineStyleProperty("color", "red", "urgent");
        assertEquals(beforeInvalidPriority, element.getInlineStyleCssText());
    }

    @Test
    void legacyJavaStyleFieldAssignmentSynchronizesOnFrameTick() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);

        Style style = element.getStyle();
        style.height = "77px";
        document.tickFrame();

        assertEquals("77px", element.getComputedStyle().height);
        assertEquals("height: 77px;", element.getAttribute("style"));
        assertSame(style, element.getStyle());
    }

    @Test
    void inheritedInlineStyleMutationRecomputesDescendants() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = document.createElement("div");
        Element child = document.createElement("span");
        document.body.appendChild(parent);
        parent.appendChild(child);
        parent.setInlineStyleProperty("color", "red");
        document.tickFrame();
        assertEquals("red", child.getComputedStyle().color);

        parent.getStyle().color = "blue";
        document.commitStyleRecalc();

        assertEquals("blue", child.getComputedStyle().color);
    }

    @Test
    void directAttributeMapChangesRefreshTheExistingStyleObject() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);
        Style style = element.getStyle();

        element.setInlineStyleProperty("width", "10px");
        assertEquals("10px", element.getComputedStyle().width);
        var observer = document.createMutationObserver(ignored -> {
        });
        observer.observe(element, false, true, false, false, true, false, "style");

        element.getAttributes().put("style", "width: 25px;");

        assertSame(style, element.getStyle());
        assertEquals("25px", element.getInlineStylePropertyValue("width"));
        assertEquals("25px", style.width);
        assertEquals("25px", element.getComputedStyle().width);
        var records = observer.takeRecords();
        assertEquals(1, records.size());
        assertEquals("width: 10px;", records.get(0).oldValue);

        Element fresh = document.createElement("div");
        document.body.appendChild(fresh);
        fresh.getStyle();
        observer.observe(fresh, false, true, false, false, true, false, "style");
        fresh.getAttributes().put("style", "color: red;");
        assertEquals("red", fresh.getComputedStyle().color);
        records = observer.takeRecords();
        assertEquals(1, records.size());
        assertEquals(null, records.get(0).oldValue);
    }
}
