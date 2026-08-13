package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
    void globalJsStyleProxyExecutesFieldMethodsAndCssTextThroughRhino() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);
        String script = globalFunction("__auiDefineProperty")
                + globalFunction("__auiInstallValueBridge")
                + globalFunction("__auiDecorateStyle");

        dev.latvian.mods.rhino.Context context = RhinoTestSupport.enterContext();
        dev.latvian.mods.rhino.Scriptable scope = context.initStandardObjects();
        scope.put(context, "el", scope, RhinoTestSupport.wrap(context, scope, element));
        Object result = context.evaluateString(scope,
                script
                        + "__auiInstallValueBridge(el, 'style', function() { return __auiDecorateStyle(el); },"
                        + " function(v) { el.setInlineStyleCssText(v == null ? '' : String(v.cssText || v)); });"
                        + "var s = el.style;"
                        + "s.width = '90px';"
                        + "s['background-color'] = '#112233';"
                        + "s['--direct'] = 'ok';"
                        + "s.setProperty('--tone', '#abc', 'important');"
                        + "var before = s.width + '|' + s.getPropertyValue('--tone') + '|'"
                        + " + s.getPropertyPriority('--tone') + '|' + s['background-color'] + '|'"
                        + " + s.getPropertyValue('--direct') + '|' + s.length;"
                        + "s.cssText = 'height: 33px; display: block;';"
                        + "before + '|' + s.height + '|' + s.item(0) + '|' + s[0] + '|'"
                        + " + s.removeProperty('display') + '|' + el.getAttribute('style') + '|'"
                        + " + (s === el.style);",
                "global-inline-style", 1, null);

        assertEquals("90px|#abc|important|#112233|ok|4|33px|height|height|block|height: 33px;|true", result);
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

    private static String globalFunction(String name) throws IOException {
        String script;
        try (InputStream stream = ScriptDomBridgeTest.class.getClassLoader()
                .getResourceAsStream("assets/apricityui/apricity/global.js")) {
            assertNotNull(stream);
            script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String marker = "function " + name + "(";
        int start = script.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing global function " + name);
        int brace = script.indexOf('{', start);
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = brace; index < script.length(); index++) {
            char current = script.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && quote != 0) {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) quote = 0;
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                continue;
            }
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return script.substring(start, index + 1) + "\n";
        }
        throw new AssertionError("Unterminated global function " + name);
    }
}
