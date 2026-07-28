package com.sighs.apricityui.editor.ore.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sighs.apricityui.editor.ore.model.OreCanvasNode;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Versioned, structured project codec. Editor decoration is never serialized. */
public final class OreEditorProjectCodec {
    public static final int FORMAT_VERSION = 1;

    public String write(OreEditorProject project) {
        JsonObject document = new JsonObject();
        document.addProperty("format", "ore-editor-project");
        document.addProperty("version", FORMAT_VERSION);
        document.add("root", writeNode(project == null ? new OreEditorProject().root() : project.root()));
        JsonObject theme = new JsonObject();
        if (project != null) project.theme().overrides().forEach(theme::addProperty);
        document.add("theme", theme);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("doctype", project == null ? "<!DOCTYPE html>" : project.documentMetadata().doctype());
        metadata.addProperty("head", project == null ? "" : project.documentMetadata().headContent());
        metadata.addProperty("bodyScripts", project == null ? "" : project.documentMetadata().bodyScriptContent());
        JsonObject htmlAttributes = new JsonObject();
        JsonObject bodyAttributes = new JsonObject();
        if (project != null) {
            project.documentMetadata().htmlAttributes().forEach(htmlAttributes::addProperty);
            project.documentMetadata().bodyAttributes().forEach(bodyAttributes::addProperty);
        }
        metadata.add("htmlAttributes", htmlAttributes);
        metadata.add("bodyAttributes", bodyAttributes);
        document.add("documentMetadata", metadata);
        return document.toString();
    }

    public OreEditorProject read(String source) {
        JsonObject document = JsonParser.parseString(source == null ? "" : source).getAsJsonObject();
        if (!"ore-editor-project".equals(string(document, "format"))) throw new IllegalArgumentException("Not an Ore editor project");
        if (document.get("version") == null || document.get("version").getAsInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported Ore editor project version");
        }
        OreCanvasNode decoded = readNode(requiredObject(document, "root"), true);
        if (!(decoded instanceof OreContainerNode root)) throw new IllegalArgumentException("Project root must be a container");
        OreEditorProject project = new OreEditorProject(root);
        JsonObject theme = object(document, "theme");
        if (theme != null) for (Map.Entry<String, JsonElement> entry : theme.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) project.theme().set(entry.getKey(), entry.getValue().getAsString());
        }
        JsonObject metadata = object(document, "documentMetadata");
        if (metadata != null) {
            project.documentMetadata().setDoctype(string(metadata, "doctype"));
            project.documentMetadata().setHeadContent(string(metadata, "head"));
            project.documentMetadata().setBodyScriptContent(string(metadata, "bodyScripts"));
            JsonObject htmlAttributes = object(metadata, "htmlAttributes");
            if (htmlAttributes != null) for (Map.Entry<String, JsonElement> entry : htmlAttributes.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) project.documentMetadata().setHtmlAttribute(entry.getKey(), entry.getValue().getAsString());
            }
            JsonObject bodyAttributes = object(metadata, "bodyAttributes");
            if (bodyAttributes != null) for (Map.Entry<String, JsonElement> entry : bodyAttributes.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) project.documentMetadata().setBodyAttribute(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return project;
    }

    private JsonObject writeNode(OreCanvasNode node) {
        JsonObject value = new JsonObject();
        value.addProperty("id", node.id().toString());
        value.addProperty("kind", node instanceof OreContainerNode ? "container" : "component");
        value.addProperty("locked", node.locked());
        JsonObject style = new JsonObject();
        node.style().properties().forEach(style::addProperty);
        value.add("style", style);
        JsonObject attributes = new JsonObject();
        node.attributes().forEach(attributes::addProperty);
        value.add("attributes", attributes);
        if (node instanceof OreContainerNode container) {
            value.addProperty("tag", container.tag());
            JsonObject flex = new JsonObject();
            flex.addProperty("direction", container.flex().direction());
            flex.addProperty("wrap", container.flex().wrap());
            flex.addProperty("justifyContent", container.flex().justifyContent());
            flex.addProperty("alignItems", container.flex().alignItems());
            flex.addProperty("alignContent", container.flex().alignContent());
            flex.addProperty("gap", container.flex().gap());
            flex.addProperty("rowGap", container.flex().rowGap());
            flex.addProperty("columnGap", container.flex().columnGap());
            value.add("flex", flex);
            JsonArray children = new JsonArray();
            for (OreCanvasNode child : container.children()) children.add(writeNode(child));
            value.add("children", children);
        } else if (node instanceof OreComponentNode component) {
            value.addProperty("type", component.type());
            value.addProperty("content", component.content());
            value.addProperty("absolute", component.absolute());
            value.addProperty("flowIndex", component.flowIndex());
            if (component.hasFlowStyleSnapshot()) {
                JsonObject flowStyles = new JsonObject();
                component.flowStyleSnapshot().forEach(flowStyles::addProperty);
                value.add("flowStyles", flowStyles);
            }
            JsonObject states = new JsonObject();
            component.stateStyles().forEach((state, stateStyle) -> {
                JsonObject properties = new JsonObject();
                stateStyle.properties().forEach(properties::addProperty);
                states.add(state.name(), properties);
            });
            value.add("states", states);
        }
        return value;
    }

    private OreCanvasNode readNode(JsonObject value, boolean root) {
        UUID id;
        try { id = UUID.fromString(string(value, "id")); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid node UUID", exception); }
        OreCanvasNode node;
        if ("container".equals(string(value, "kind"))) {
            OreContainerNode container = new OreContainerNode(root, id);
            container.setTag(string(value, "tag"));
            JsonObject flex = object(value, "flex");
            if (flex != null) {
                container.flex().setDirection(string(flex, "direction"));
                container.flex().setWrap(string(flex, "wrap"));
                container.flex().setJustifyContent(string(flex, "justifyContent"));
                container.flex().setAlignItems(string(flex, "alignItems"));
                container.flex().setAlignContent(string(flex, "alignContent"));
                container.flex().setGap(string(flex, "gap"));
                container.flex().setRowGap(string(flex, "rowGap"));
                container.flex().setColumnGap(string(flex, "columnGap"));
            }
            JsonArray children = array(value, "children");
            if (children != null) for (JsonElement child : children) {
                if (!child.isJsonObject()) throw new IllegalArgumentException("Invalid child node");
                container.add(readNode(child.getAsJsonObject(), false));
            }
            node = container;
        } else if ("component".equals(string(value, "kind"))) {
            OreComponentNode component = new OreComponentNode(string(value, "type"), string(value, "content"), id);
            if (value.has("absolute") && value.get("absolute").getAsBoolean()) {
                component.enterAbsolute(value.has("flowIndex") ? value.get("flowIndex").getAsInt() : 0);
            }
            node = component;
        } else throw new IllegalArgumentException("Unknown Ore node kind");
        if (value.has("locked") && value.get("locked").isJsonPrimitive()) node.setLocked(value.get("locked").getAsBoolean());
        JsonObject attributes = object(value, "attributes");
        if (attributes != null) for (Map.Entry<String, JsonElement> entry : attributes.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) node.setAttribute(entry.getKey(), entry.getValue().getAsString());
        }
        JsonObject style = object(value, "style");
        if (style != null) for (Map.Entry<String, JsonElement> entry : style.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) node.style().set(entry.getKey(), entry.getValue().getAsString());
        }
        if (node instanceof OreComponentNode component && "absolute".equals(component.style().get("position")) && !component.absolute()) {
            component.enterAbsolute(0);
        }
        if (node instanceof OreComponentNode component) {
            JsonObject flowStyles = object(value, "flowStyles");
            if (flowStyles != null) {
                Map<String, String> snapshot = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : flowStyles.entrySet()) {
                    snapshot.put(entry.getKey(), entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : null);
                }
                component.setFlowStyleSnapshot(snapshot);
            }
            JsonObject states = object(value, "states");
            if (states != null) for (Map.Entry<String, JsonElement> entry : states.entrySet()) {
                try {
                    OreComponentNode.VisualState state = OreComponentNode.VisualState.valueOf(entry.getKey());
                    if (!entry.getValue().isJsonObject()) continue;
                    for (Map.Entry<String, JsonElement> property : entry.getValue().getAsJsonObject().entrySet()) {
                        if (property.getValue().isJsonPrimitive()) component.stateStyle(state).set(property.getKey(), property.getValue().getAsString());
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return node;
    }

    private static JsonObject requiredObject(JsonObject object, String name) {
        JsonObject result = object(object, name);
        if (result == null) throw new IllegalArgumentException("Missing " + name);
        return result;
    }
    private static JsonObject object(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
    private static JsonArray array(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }
    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }
}
