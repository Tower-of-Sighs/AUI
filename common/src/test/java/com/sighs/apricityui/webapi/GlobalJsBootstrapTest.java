package com.sighs.apricityui.webapi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sighs.apricityui.form.FormData;

class GlobalJsBootstrapTest {
    @Test
    void globalJsIncludesUrlSearchParamsBootstrap() {
        String script = readGlobalJs();

        assertNotNull(script);
        assertTrue(script.contains("ApricityUI.getDocumentByUUID(\"__AUI_DOCUMENT_UUID__\")"));
        assertTrue(script.contains("let window = ApricityUI.getWindow();"));
        assertTrue(script.contains("function URLSearchParams(init)"));
        assertTrue(script.contains("decodeURIComponent(key.replace("));
        assertTrue(script.contains("decodeURIComponent(value.replace("));
        assertTrue(script.contains("append: function(key, value)"));
        assertTrue(script.contains("getAll: function(key)"));
        assertTrue(script.contains("sort: function()"));
        assertTrue(script.contains("forEach: function(callback, thisArg)"));
        assertTrue(script.contains("toString: function()"));
    }

    @Test
    void globalJsIncludesLocationBootstrap() {
        String script = readGlobalJs();

        assertNotNull(script);
        assertTrue(script.contains("function __auiCreateLocation(href)"));
        assertTrue(script.contains("function __auiDefineProperty(target, name, getter, setter)"));
        assertTrue(script.contains("function __auiInstallValueBridge(target, name, getter, setter)"));
        assertTrue(script.contains("protocol: protocol"));
        assertTrue(script.contains("host: host"));
        assertTrue(script.contains("hostname: hostname"));
        assertTrue(script.contains("port: port"));
        assertTrue(script.contains("origin: origin"));
        assertTrue(script.contains("pathname: pathname"));
        assertTrue(script.contains("search: search"));
        assertTrue(script.contains("hash: hash"));
        assertTrue(script.contains("assign: function() {}"));
        assertTrue(script.contains("replace: function() {}"));
        assertTrue(script.contains("reload: function() {}"));
        assertTrue(script.contains("location.searchParams = new URLSearchParams(search);"));
        assertTrue(script.contains("__auiInstallValueBridge(window, 'location', () => __auiLocation);"));
        assertTrue(script.contains("__auiInstallValueBridge(document, 'location', () => __auiLocation);"));
        assertTrue(script.contains("globalThis.location = __auiLocation;"));
    }

    @Test
    void globalJsIncludesFormDataBootstrap() {
        String script = readGlobalJs();

        assertNotNull(script);
        assertTrue(script.contains("function FormData(form)"));
        assertTrue(script.contains("let inputFields = form.getElementsByTagName ? form.getElementsByTagName('input') : form.querySelectorAll('input');"));
        assertTrue(script.contains("let selectFields = form.getElementsByTagName ? form.getElementsByTagName('select') : form.querySelectorAll('select');"));
        assertTrue(script.contains("let textareaFields = form.getElementsByTagName ? form.getElementsByTagName('textarea') : form.querySelectorAll('textarea');"));
        assertTrue(script.contains("field.hasAttribute && field.hasAttribute('disabled')"));
        assertTrue(script.contains("type === 'checkbox' || type === 'radio'"));
        assertTrue(script.contains("field.value || 'on'"));
        assertTrue(script.contains("let options = field.getElementsByTagName ? field.getElementsByTagName('option') : (field.options || []);"));
        assertTrue(script.contains("option.hasAttribute('selected')"));
        assertTrue(script.contains("__auiInstallValueBridge(el, 'name', () => el.getAttribute('name') || ''"));
        assertTrue(script.contains("__auiInstallValueBridge(el, 'type', () => el.getType()"));
        assertTrue(script.contains("__auiInstallValueBridge(el, 'multiple', () => !!el.hasAttribute('multiple')"));
        assertTrue(script.contains("__auiInstallValueBridge(el, 'selected', () => el.isSelected()"));
        assertTrue(script.contains("__auiInstallValueBridge(el, 'defaultSelected', () => el.isDefaultSelected()"));
        assertTrue(script.contains("__auiInstallValueBridge(el, 'label', () => el.getOptionLabel()"));
        assertTrue(script.contains("__auiInstallValueBridge(el, 'index', () => el.getOptionIndex()"));
        assertTrue(script.contains("append: function(key, value)"));
        assertTrue(script.contains("getAll: function(key)"));
        assertTrue(script.contains("forEach: function(callback, thisArg)"));
        assertTrue(script.contains("toString: function()"));
        assertTrue(script.contains("function __auiDecorateElement(el)"));
        assertTrue(script.contains("function MutationObserver(callback)"));
        assertTrue(script.contains("document.querySelector = function(sel)"));
    }

    @Test
    void globalJsBridgesInnerTextToItsLayoutAwareApi() {
        String script = readGlobalJs();

        assertTrue(script.contains("return el.getInnerText();"));
        assertTrue(script.contains("el.setInnerText(typeof v === 'undefined' ? 'undefined' : formatText(v));"));
        assertFalse(script.contains("'innerText', { get: function() { return el.getTextContent();"));
    }

    @Test
    void globalJsInstallsLiveCssStyleDeclarationBridge() {
        String script = readGlobalJs();

        assertTrue(script.contains("function __auiDecorateStyle(el)"));
        assertTrue(script.contains("style.getPropertyValue = function(name)"));
        assertTrue(script.contains("style.setProperty = function(name, value, priority)"));
        assertTrue(script.contains("style.removeProperty = function(name)"));
        assertTrue(script.contains("Object.defineProperty(style, 'cssText'"));
        assertTrue(script.contains("__auiInstallValueBridge(el, 'style', () => __auiDecorateStyle(el)"));
    }

    @Test
    void globalJsIncludesIntersectionObserverBootstrap() {
        String script = readGlobalJs();

        assertNotNull(script);
        assertTrue(script.contains("function __auiDecorateIntersectionEntries(list)"));
        assertTrue(script.contains("function IntersectionObserver(callback, options)"));
        assertTrue(script.contains("window.createIntersectionObserver(function(entries)"));
        assertTrue(script.contains("IntersectionObserver root must be an Element, Document, or null"));
        assertTrue(script.contains("takeRecords: function()"));
        assertTrue(script.contains("__auiDecorateIntersectionEntries(this.__auiNativeObserver.takeRecords())"));
        assertTrue(script.contains("isVisible: !!entry.isVisible"));
        assertTrue(script.contains("scrollMargin = options.scrollMargin"));
        assertTrue(script.contains("trackVisibility = !!options.trackVisibility"));
        assertTrue(script.contains("__auiInstallValueBridge(observer, 'scrollMargin'"));
        assertTrue(script.contains("__auiInstallValueBridge(observer, 'delay'"));
        assertTrue(script.contains("__auiInstallValueBridge(observer, 'trackVisibility'"));
        assertTrue(script.contains("callback.call(observer"));
        assertTrue(script.contains("__auiInstallValueBridge(observer, 'thresholds'"));
    }

    @Test
    void intersectionObserverBootstrapUsesStandardizedOptionsAndPrototypeMethods() throws Exception {
        String script = globalFunction("__auiDefineProperty")
                + globalFunction("__auiInstallValueBridge")
                + globalFunction("__auiDecorateIntersectionEntries")
                + globalFunction("__auiDecorateIntersectionThresholds")
                + "function __auiDecorateElement(el) { return el; }\n"
                + "var document = {};\n"
                + "var lastNativeCallback = null;\n"
                + "var lastNativeArgs = null;\n"
                + "var window = { createIntersectionObserver: function(cb, root, rm, sm, th, delay, tv) {"
                + " lastNativeCallback = cb; lastNativeArgs = [root, rm, sm, th, delay, tv];"
                + " return { getRoot: function() { return root; },"
                + " getRootMargin: function() { return '96px 96px 96px 96px'; },"
                + " getScrollMargin: function() { return '2px 2px 2px 2px'; },"
                + " getThresholds: function() { return [0.5]; },"
                + " getDelay: function() { return tv && delay < 100 ? 100 : delay; },"
                + " getTrackVisibility: function() { return !!tv; },"
                + " observe: function() {}, unobserve: function() {}, disconnect: function() {},"
                + " takeRecords: function() { return []; } }; } };\n"
                + globalSection("function __auiNormalizeIntersectionMargin", "function MutationObserver")
                + "var rootElement = { getNodeType: function() { return 1; } };\n"
                + "var callbackThis = null; var callbackObserver = null; var callbackEntry = null;\n"
                + "var observer = new IntersectionObserver(function(entries, ob) {"
                + " callbackThis = this; callbackObserver = ob; callbackEntry = entries[0];"
                + "}, { root: rootElement, rootMargin: '1in', scrollMargin: '2px', threshold: [0.5], delay: 25, trackVisibility: true });\n"
                + "lastNativeCallback([{ target: rootElement, time: 7, rootBounds: null, boundingClientRect: {}, intersectionRect: {}, isIntersecting: true, isVisible: true, intersectionRatio: 1 }]);\n"
                + "var badMargin = false; try { new IntersectionObserver(function() {}, { rootMargin: 'bad' }); } catch (e) { badMargin = e instanceof SyntaxError; }\n"
                + "[observer instanceof IntersectionObserver, observer.observe === IntersectionObserver.prototype.observe,"
                + " lastNativeArgs[1], lastNativeArgs[2], observer.rootMargin, observer.scrollMargin, observer.delay,"
                + " observer.trackVisibility, callbackThis === observer, callbackObserver === observer, callbackEntry.isVisible, badMargin].join('|');";

        dev.latvian.mods.rhino.Context context = RhinoTestSupport.enterContext();
        dev.latvian.mods.rhino.Scriptable scope = context.initStandardObjects();
        Object result = context.evaluateString(scope, script, "intersection-observer-bootstrap", 1, null);

        assertEquals("true|true|1in|2px|96px 96px 96px 96px|2px 2px 2px 2px|100|true|true|true|true|true", result);
    }

    private static String globalFunction(String name) {
        String script = readGlobalJs();
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
            if (current == 39 || current == '"' || current == '`') {
                quote = current;
                continue;
            }
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return script.substring(start, index + 1) + "\n";
        }
        throw new AssertionError("Unterminated global function " + name);
    }

    private static String globalSection(String startMarker, String endMarker) {
        String script = readGlobalJs();
        int start = script.indexOf(startMarker);
        int end = script.indexOf(endMarker, start < 0 ? 0 : start);
        if (start < 0 || end < 0) throw new AssertionError("Missing global section markers");
        return script.substring(start, end);
    }

    private static String readGlobalJs() {
        try (InputStream stream = GlobalJsBootstrapTest.class.getClassLoader()
                .getResourceAsStream("assets/apricityui/apricity/global.js")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
