package com.sighs.apricityui.webapi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(script.contains("__auiInstallValueBridge(el, 'name', () => el.getAttribute('name')"));
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
