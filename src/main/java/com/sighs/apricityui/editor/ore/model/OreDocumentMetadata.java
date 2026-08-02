package com.sighs.apricityui.editor.ore.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.sighs.apricityui.parser.HTML;

/** Non-canvas document information retained when an existing HTML file is opened in Ore. */
public final class OreDocumentMetadata {
    private final Map<String, String> htmlAttributes = new LinkedHashMap<>();
    private final Map<String, String> bodyAttributes = new LinkedHashMap<>();
    private String doctype = "<!DOCTYPE html>";
    private String headContent = "";
    private String bodyScriptContent = "";

    public Map<String, String> htmlAttributes() { return Collections.unmodifiableMap(new LinkedHashMap<>(htmlAttributes)); }
    public Map<String, String> bodyAttributes() { return Collections.unmodifiableMap(new LinkedHashMap<>(bodyAttributes)); }
    public String doctype() { return doctype; }
    public String headContent() { return headContent; }
    public String bodyScriptContent() { return bodyScriptContent; }

    public void setHtmlAttribute(String name, String value) { put(htmlAttributes, name, value); }
    public void setBodyAttribute(String name, String value) { put(bodyAttributes, name, value); }
    public void setDoctype(String doctype) {
        if (doctype != null && doctype.matches("(?is)\\s*<!doctype\\s+[^>]+>\\s*")) this.doctype = doctype.trim();
    }
    public void setHeadContent(String headContent) { this.headContent = headContent == null ? "" : headContent.trim(); }
    public void setBodyScriptContent(String bodyScriptContent) {
        this.bodyScriptContent = bodyScriptContent == null ? "" : bodyScriptContent.trim();
    }

    private static void put(Map<String, String> target, String name, String value) {
        if (name == null || !name.matches("[A-Za-z_:][A-Za-z0-9:_.-]*")) return;
        target.put(name.toLowerCase(java.util.Locale.ROOT), value == null ? "" : value);
    }
}
