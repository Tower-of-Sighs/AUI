package com.sighs.apricityui.editor.ore.persistence;

import com.sighs.apricityui.editor.ore.model.OreCanvasNode;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.editor.ore.model.OreDocumentMetadata;
import com.sighs.apricityui.editor.ore.model.OreNodeStyle;

import java.util.EnumMap;
import java.util.Map;

/** Produces a normal standalone AUI document without editor IDs, overlays or helper nodes. */
public final class OreEditorHtmlExporter {
    public String export(OreEditorProject project) {
        OreEditorProject source = project == null ? new OreEditorProject() : project;
        Map<OreComponentNode.VisualState, StringBuilder> stateRules = new EnumMap<>(OreComponentNode.VisualState.class);
        StringBuilder body = new StringBuilder();
        writeNode(body, source.root(), true, stateRules);
        OreDocumentMetadata metadata = source.documentMetadata();
        StringBuilder html = new StringBuilder(metadata.doctype()).append("\n<html");
        appendRawAttributes(html, metadata.htmlAttributes(), null);
        if (!metadata.htmlAttributes().containsKey("lang")) html.append(" lang=\"en\"");
        html.append(">\n<head>\n");
        String head = metadata.headContent();
        if (head.isBlank()) html.append("  <meta charset=\"UTF-8\">\n");
        else html.append(head).append('\n');
        if (!head.toLowerCase(java.util.Locale.ROOT).contains("ore-edit.css")) {
            html.append("  <link rel=\"stylesheet\" href=\"apricityui/theme/ore/ore-edit.css\">\n");
        }
        appendStateStyles(html, stateRules);
        html.append("</head>\n<body");
        appendRawAttributes(html, metadata.bodyAttributes(), "class");
        String bodyClass = metadata.bodyAttributes().get("class");
        html.append(" class=\"").append(attribute(bodyClass == null || bodyClass.isBlank() ? "ore-theme" : bodyClass + " ore-theme"))
                .append('\"');
        String theme = source.theme().toCss();
        String sourceStyle = metadata.bodyAttributes().get("style");
        String normalizedSourceStyle = sourceStyle == null ? "" : sourceStyle.trim();
        String bodyStyle = normalizedSourceStyle + (normalizedSourceStyle.isBlank() || normalizedSourceStyle.endsWith(";") ? "" : ";") + theme;
        if (!bodyStyle.isBlank()) html.append(" style=\"").append(attribute(bodyStyle)).append('\"');
        html.append(">\n");
        html.append(body);
        if (!metadata.bodyScriptContent().isBlank()) html.append('\n').append(metadata.bodyScriptContent());
        return html.append("\n</body>\n</html>\n").toString();
    }

    private void writeNode(StringBuilder html, OreCanvasNode node, boolean root,
                           Map<OreComponentNode.VisualState, StringBuilder> stateRules) {
        if (node instanceof OreContainerNode container) {
            html.append('<').append(safeTag(container.tag()));
            appendAttributes(html, container, null);
            html.append(" style=\"").append(attribute(containerStyle(container, root))).append("\">");
            for (OreCanvasNode child : container.children()) writeNode(html, child, false, stateRules);
            html.append("</").append(safeTag(container.tag())).append('>');
        } else if (node instanceof OreComponentNode component) {
            String tag = safeTag(component.type());
            String stateClass = appendStateRules(component, stateRules);
            html.append('<').append(tag);
            appendAttributes(html, component, stateClass);
            html.append(" style=\"").append(attribute(style(component))).append("\">")
                    .append(text(component.content())).append("</").append(tag).append('>');
        }
    }

    private void appendAttributes(StringBuilder html, OreCanvasNode node, String appendedClass) {
        boolean wroteClass = false;
        for (Map.Entry<String, String> entry : node.attributes().entrySet()) {
            String name = entry.getKey();
            if ("class".equals(name)) {
                wroteClass = true;
                String value = entry.getValue();
                if (appendedClass != null) value = (value == null || value.isBlank()) ? appendedClass : value + " " + appendedClass;
                html.append(" class=\"").append(attribute(value)).append('\"');
            } else {
                html.append(' ').append(name).append("=\"").append(attribute(entry.getValue())).append('\"');
            }
        }
        if (!wroteClass && appendedClass != null) html.append(" class=\"").append(appendedClass).append('\"');
    }

    private void appendRawAttributes(StringBuilder html, Map<String, String> attributes, String skipped) {
        if (attributes == null) return;
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank() || name.equals(skipped)
                    || (skipped != null && "style".equals(name))) continue;
            html.append(' ').append(name).append("=\"").append(attribute(entry.getValue())).append('\"');
        }
    }

    private String appendStateRules(OreComponentNode component,
                                    Map<OreComponentNode.VisualState, StringBuilder> stateRules) {
        String stateClass = "ore-state-" + component.id().toString().replace("-", "");
        boolean hasRules = false;
        for (OreComponentNode.VisualState state : OreComponentNode.VisualState.values()) {
            if (state == OreComponentNode.VisualState.DEFAULT) continue;
            OreNodeStyle style = component.stateStyles().get(state);
            if (style == null || style.properties().isEmpty()) continue;
            stateRules.computeIfAbsent(state, ignored -> new StringBuilder())
                    .append('.').append(stateClass).append(pseudoClass(state)).append(" {")
                    .append(style(style)).append("}\n");
            hasRules = true;
        }
        return hasRules ? stateClass : null;
    }

    private void appendStateStyles(StringBuilder html, Map<OreComponentNode.VisualState, StringBuilder> stateRules) {
        if (stateRules.isEmpty()) return;
        html.append("  <style>\n");
        for (OreComponentNode.VisualState state : OreComponentNode.VisualState.values()) {
            StringBuilder rules = stateRules.get(state);
            if (rules != null) html.append("    ").append(rules);
        }
        html.append("  </style>\n");
    }

    private String pseudoClass(OreComponentNode.VisualState state) {
        return switch (state) {
            case HOVER -> ":hover";
            case ACTIVE -> ":active";
            case FOCUS -> ":focus";
            case DISABLED -> ":disabled";
            case DEFAULT -> "";
        };
    }

    private String containerStyle(OreContainerNode container, boolean root) {
        StringBuilder style = new StringBuilder("display:flex;position:relative;")
                .append("flex-direction:").append(container.flex().direction()).append(';')
                .append("flex-wrap:").append(container.flex().wrap()).append(';')
                .append("justify-content:").append(container.flex().justifyContent()).append(';')
                .append("align-items:").append(container.flex().alignItems()).append(';')
                .append("align-content:").append(container.flex().alignContent()).append(';')
                .append("gap:").append(container.flex().gap()).append(';')
                .append("row-gap:").append(container.flex().rowGap()).append(';')
                .append("column-gap:").append(container.flex().columnGap()).append(';');
        if (root) style.append("min-height:100%;width:100%;");
        container.style().properties().forEach((key, value) -> style.append(key).append(':').append(value).append(';'));
        return style.toString();
    }

    private String style(OreCanvasNode node) {
        StringBuilder style = new StringBuilder();
        node.style().properties().forEach((key, value) -> style.append(key).append(':').append(value).append(';'));
        return style.toString();
    }

    private String style(OreNodeStyle nodeStyle) {
        StringBuilder style = new StringBuilder();
        nodeStyle.properties().forEach((key, value) -> style.append(key).append(':').append(value).append(';'));
        return style.toString();
    }

    private String safeTag(String tag) {
        return tag != null && tag.matches("[A-Za-z][A-Za-z0-9-]*") ? tag.toLowerCase() : "div";
    }
    private String text(String value) { return attribute(value); }
    private String attribute(String value) {
        return (value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
