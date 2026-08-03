package com.sighs.apricityui.editor.ore.persistence;

import com.sighs.apricityui.editor.ore.model.OreCanvasNode;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Imports the editable body subset emitted by Ore's clean HTML exporter. */
public final class OreEditorHtmlImporter {
    private static final Pattern DOCTYPE = Pattern.compile("(?is)<!doctype\\s+[^>]+>");
    private static final Pattern HEAD = Pattern.compile("(?is)<head\\b[^>]*>(.*?)</head\\s*>");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script\\b[^>]*>.*?</script\\s*>");
    private static final Pattern HTML_OPEN = Pattern.compile("(?is)<html\\b([^>]*)>");
    private static final Pattern BODY_OPEN = Pattern.compile("(?is)<body\\b([^>]*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("(?is)([A-Za-z_:][A-Za-z0-9:_.-]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+)))?");

    public OreEditorProject read(String source) {
        String path = "ore-editor-import/" + UUID.randomUUID() + ".html";
        String original = source == null ? "" : source;
        // The source page is data for the editor, never code to execute in its temporary parser document.
        HTML.putTemple(path, SCRIPT.matcher(original).replaceAll(""));
        Document document = Document.create(path);
        if (document == null || document.body == null) throw new IllegalArgumentException("Invalid HTML document");
        try {
            OreEditorProject project = new OreEditorProject();
            importDocumentMetadata(project, document, original);
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

    private static void importDocumentMetadata(OreEditorProject project, Document document, String source) {
        if (project == null || document == null) return;
        String original = source == null ? "" : source;
        copyRawAttributes(HTML_OPEN, original, project.documentMetadata()::setHtmlAttribute);
        copyRawAttributes(BODY_OPEN, original, project.documentMetadata()::setBodyAttribute);
        Matcher doctype = DOCTYPE.matcher(original);
        if (doctype.find()) project.documentMetadata().setDoctype(doctype.group());
        Matcher head = HEAD.matcher(original);
        int headStart = -1;
        int headEnd = -1;
        if (head.find()) {
            project.documentMetadata().setHeadContent(head.group(1));
            headStart = head.start();
            headEnd = head.end();
        }
        StringBuilder bodyScripts = new StringBuilder();
        Matcher script = SCRIPT.matcher(original);
        while (script.find()) {
            if (script.start() >= headStart && script.end() <= headEnd) continue;
            if (!bodyScripts.isEmpty()) bodyScripts.append('\n');
            bodyScripts.append(script.group());
        }
        project.documentMetadata().setBodyScriptContent(bodyScripts.toString());
    }

    private static void copyRawAttributes(Pattern tag, String source, java.util.function.BiConsumer<String, String> target) {
        Matcher opening = tag.matcher(source);
        if (!opening.find() || target == null) return;
        Matcher attributes = ATTRIBUTE.matcher(opening.group(1));
        while (attributes.find()) {
            String value = attributes.group(2) != null ? attributes.group(2)
                    : attributes.group(3) != null ? attributes.group(3)
                    : attributes.group(4) == null ? "" : attributes.group(4);
            target.accept(attributes.group(1), value);
        }
    }

    private OreCanvasNode readNode(Element element) {
        if (element == null || ignored(element.tagName)) return null;
        if (!element.children.isEmpty()) {
            OreContainerNode container = new OreContainerNode(false);
            container.setTag(safeTag(element.tagName));
            copyAttributes(element, container);
            applyContainerStyle(container, style(element));
            for (Element child : element.children) {
                OreCanvasNode node = readNode(child);
                if (node != null) container.add(node);
            }
            return container;
        }
        OreComponentNode component = new OreComponentNode(safeTag(element.tagName), element.getTextContent());
        copyAttributes(element, component);
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

    private static void copyAttributes(Element source, OreCanvasNode target) {
        if (source == null || target == null) return;
        source.getAttributes().forEach(target::setAttribute);
    }

    private static boolean ignored(String tag) {
        return tag == null || tag.equalsIgnoreCase("script") || tag.equalsIgnoreCase("style") || tag.equalsIgnoreCase("link");
    }

    private static String safeTag(String tag) {
        return tag != null && tag.matches("[A-Za-z][A-Za-z0-9-]*") ? tag.toLowerCase(Locale.ROOT) : "div";
    }
}
