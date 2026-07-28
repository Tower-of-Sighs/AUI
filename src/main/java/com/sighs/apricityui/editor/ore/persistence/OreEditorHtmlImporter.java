package com.sighs.apricityui.editor.ore.persistence;

import com.sighs.apricityui.editor.ore.model.OreCanvasNode;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Imports the editable body subset emitted by Ore's clean HTML exporter. */
public final class OreEditorHtmlImporter {
    public OreEditorProject read(String source) {
        String path = "ore-editor-import/" + UUID.randomUUID() + ".html";
        HTML.putTemple(path, source == null ? "" : source);
        Document document = Document.create(path);
        if (document == null || document.body == null) throw new IllegalArgumentException("Invalid HTML document");
        try {
            OreEditorProject project = new OreEditorProject();
            Map<String, String> bodyStyle = style(document.body);
            bodyStyle.forEach((key, value) -> {
                if (key.startsWith("--ore-")) project.theme().set(key, value);
            });
            for (Element child : document.body.children) {
                OreCanvasNode node = readNode(child);
                if (node != null) project.root().add(node);
            }
            return project;
        } finally {
            document.remove();
        }
    }

    private OreCanvasNode readNode(Element element) {
        if (element == null || ignored(element.tagName)) return null;
        if (!element.children.isEmpty()) {
            OreContainerNode container = new OreContainerNode(false);
            applyContainerStyle(container, style(element));
            for (Element child : element.children) {
                OreCanvasNode node = readNode(child);
                if (node != null) container.add(node);
            }
            return container;
        }
        OreComponentNode component = new OreComponentNode(safeTag(element.tagName), element.getTextContent());
        Map<String, String> values = style(element);
        values.forEach(component.style()::set);
        if ("absolute".equalsIgnoreCase(values.get("position"))) component.enterAbsolute(0);
        return component;
    }

    private void applyContainerStyle(OreContainerNode container, Map<String, String> values) {
        String direction = values.remove("flex-direction");
        String wrap = values.remove("flex-wrap");
        String justify = values.remove("justify-content");
        String items = values.remove("align-items");
        String content = values.remove("align-content");
        String gap = values.remove("gap");
        String rowGap = values.remove("row-gap");
        String columnGap = values.remove("column-gap");
        values.remove("display");
        if (direction != null) container.flex().setDirection(direction);
        if (wrap != null) container.flex().setWrap(wrap);
        if (justify != null) container.flex().setJustifyContent(justify);
        if (items != null) container.flex().setAlignItems(items);
        if (content != null) container.flex().setAlignContent(content);
        if (gap != null) container.flex().setGap(gap);
        if (rowGap != null) container.flex().setRowGap(rowGap);
        if (columnGap != null) container.flex().setColumnGap(columnGap);
        values.forEach(container.style()::set);
    }

    private static Map<String, String> style(Element element) {
        Map<String, String> values = new LinkedHashMap<>();
        if (element == null) return values;
        String source = element.getAttribute("style");
        if (source == null) return values;
        for (String declaration : source.split(";")) {
            int separator = declaration.indexOf(':');
            if (separator <= 0) continue;
            String name = declaration.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(separator + 1).trim();
            if (!name.isBlank() && !value.isBlank()) values.put(name, value);
        }
        return values;
    }

    private static boolean ignored(String tag) {
        return tag == null || tag.equalsIgnoreCase("script") || tag.equalsIgnoreCase("style") || tag.equalsIgnoreCase("link");
    }

    private static String safeTag(String tag) {
        return tag != null && tag.matches("[A-Za-z][A-Za-z0-9-]*") ? tag.toLowerCase(Locale.ROOT) : "div";
    }
}
