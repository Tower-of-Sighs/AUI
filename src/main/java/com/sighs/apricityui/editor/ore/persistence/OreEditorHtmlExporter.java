package com.sighs.apricityui.editor.ore.persistence;

import com.sighs.apricityui.editor.ore.model.OreCanvasNode;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
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
        StringBuilder html = new StringBuilder("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
                .append("  <meta charset=\"UTF-8\">\n")
                .append("  <link rel=\"stylesheet\" href=\"apricityui/theme/ore/ore-edit.css\">\n");
        appendStateStyles(html, stateRules);
        html.append("</head>\n<body class=\"ore-theme\"");
        String theme = source.theme().toCss();
        if (!theme.isBlank()) html.append(" style=\"").append(attribute(theme)).append('\"');
        html.append(">\n");
        html.append(body);
        return html.append("\n</body>\n</html>\n").toString();
    }

    private void writeNode(StringBuilder html, OreCanvasNode node, boolean root,
                           Map<OreComponentNode.VisualState, StringBuilder> stateRules) {
        if (node instanceof OreContainerNode container) {
            html.append("<div style=\"").append(attribute(containerStyle(container, root))).append("\">");
            for (OreCanvasNode child : container.children()) writeNode(html, child, false, stateRules);
            html.append("</div>");
        } else if (node instanceof OreComponentNode component) {
            String tag = safeTag(component.type());
            String stateClass = appendStateRules(component, stateRules);
            html.append('<').append(tag);
            if (stateClass != null) html.append(" class=\"").append(stateClass).append('\"');
            html.append(" style=\"").append(attribute(style(component))).append("\">")
                    .append(text(component.content())).append("</").append(tag).append('>');
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
