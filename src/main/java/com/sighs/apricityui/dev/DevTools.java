package com.sighs.apricityui.dev;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.init.Selector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DevTools {
    private static final String PATH = "devtools/index.html";

    private static Document toolDocument = null;
    private static String selectedDocumentUuid = null;
    private static String selectedElementUuid = null;
    private static final Set<String> collapsedNodeUuids = new LinkedHashSet<>();
    private static final Set<String> collapseInitializedDocUuids = new LinkedHashSet<>();

    private DevTools() {}

    public static void toggle() {
        if (Document.get(PATH).isEmpty()) {
            toolDocument = Document.create(PATH);
            refresh();
            return;
        }
        toolDocument = null;
        selectedDocumentUuid = null;
        selectedElementUuid = null;
        collapsedNodeUuids.clear();
        collapseInitializedDocUuids.clear();
        Document.remove(PATH);
    }

    public static void refresh() {
        if (toolDocument == null || toolDocument.body == null) return;

        Element title = toolDocument.querySelector(".title");
        Element docSwitch = toolDocument.querySelector(".doc-switch");
        Element tree = toolDocument.querySelector(".tree");
        Element inspector = toolDocument.querySelector(".inspector");
        if (title == null || docSwitch == null || tree == null || inspector == null) return;

        List<Document> docs = getDebuggableDocuments();
        if (docs.isEmpty()) {
            title.innerText = "DevTools";
            clearChildren(docSwitch);
            clearChildren(tree);
            clearChildren(inspector);
            inspector.append(span("No debuggable document."));
            markDirty(toolDocument);
            return;
        }

        Document selectedDocument = resolveSelectedDocument(docs);
        if (selectedDocument == null || selectedDocument.body == null) return;
        ensureDefaultCollapsedState(selectedDocument);
        title.innerText = "DevTools - " + selectedDocument.getPath();

        buildDocumentSwitcher(docSwitch, docs, selectedDocument);

        Element selectedElement = resolveSelectedElement(selectedDocument);
        if (selectedElement == null) {
            selectedElement = selectedDocument.body;
            selectedElementUuid = selectedElement.uuid.toString();
        }

        clearChildren(tree);
        buildTree(tree, selectedDocument.body, 0);

        clearChildren(inspector);
        buildStyleInspector(inspector, selectedElement);

        markDirty(toolDocument);
    }

    private static List<Document> getDebuggableDocuments() {
        ArrayList<Document> result = new ArrayList<>();
        for (Document doc : Document.getAll()) {
            if (doc == null) continue;
            if (PATH.equals(doc.getPath())) continue;
            if (doc.body == null) continue;
            result.add(doc);
        }
        return result;
    }

    private static Document resolveSelectedDocument(List<Document> candidates) {
        if (selectedDocumentUuid != null) {
            for (Document candidate : candidates) {
                if (candidate.getUuid().toString().equals(selectedDocumentUuid)) {
                    return candidate;
                }
            }
        }
        Document fallback = candidates.get(candidates.size() - 1);
        selectedDocumentUuid = fallback.getUuid().toString();
        selectedElementUuid = fallback.body.uuid.toString();
        return fallback;
    }

    private static Element resolveSelectedElement(Document doc) {
        if (doc == null || selectedElementUuid == null) return null;
        for (Element element : doc.getElements()) {
            if (selectedElementUuid.equals(element.uuid.toString())) return element;
        }
        return null;
    }

    private static void buildDocumentSwitcher(Element switcher, List<Document> docs, Document selectedDocument) {
        clearChildren(switcher);

        Element prev = toolDocument.createElement("DIV");
        prev.setAttribute("class", "doc-arrow doc-prev");
        prev.innerText = "<";
        prev.addEventListener("mousedown", event -> switchDocumentByOffset(docs, selectedDocument, -1));

        Element name = toolDocument.createElement("DIV");
        name.setAttribute("class", "doc-name");
        name.innerText = selectedDocument.getPath();

        Element next = toolDocument.createElement("DIV");
        next.setAttribute("class", "doc-arrow doc-next");
        next.innerText = ">";
        next.addEventListener("mousedown", event -> switchDocumentByOffset(docs, selectedDocument, 1));

        switcher.append(prev);
        switcher.append(name);
        switcher.append(next);
    }

    private static void switchDocumentByOffset(List<Document> docs, Document selectedDocument, int offset) {
        if (docs == null || docs.isEmpty() || selectedDocument == null) return;
        int currentIndex = docs.indexOf(selectedDocument);
        if (currentIndex < 0) currentIndex = docs.size() - 1;
        int nextIndex = (currentIndex + offset) % docs.size();
        if (nextIndex < 0) nextIndex += docs.size();
        Document nextDoc = docs.get(nextIndex);
        selectedDocumentUuid = nextDoc.getUuid().toString();
        selectedElementUuid = nextDoc.body == null ? null : nextDoc.body.uuid.toString();
        refresh();
    }

    private static void buildTree(Element container, Element node, int depth) {
        String uuid = node.uuid.toString();
        String tag = tagName(node);
        boolean hasChildren = !node.children.isEmpty();
        boolean selfClosing = isSelfClosingTag(node);
        boolean hasText = node.innerText != null && !node.innerText.isBlank();
        boolean collapsed = collapsedNodeUuids.contains(uuid);
        boolean selected = selectedElementUuid != null && selectedElementUuid.equals(uuid);

        Element openRow = toolDocument.createElement("DIV");
        openRow.setAttribute("class", selectedClass(uuid, "tree-row"));
        openRow.setAttribute("style", "padding-left:" + (depth * 10) + "px;");

        Element toggle = toolDocument.createElement("SPAN");
        toggle.setAttribute("class", "toggle");
        toggle.innerText = hasChildren ? (collapsed ? ">" : "v") : ".";
        if (hasChildren) {
            toggle.addEventListener("mousedown", event -> {
                if (collapsedNodeUuids.contains(uuid)) collapsedNodeUuids.remove(uuid);
                else collapsedNodeUuids.add(uuid);
                refresh();
            });
        }
        openRow.append(toggle);

        // Folded rows are rendered in a lightweight form to avoid creating too many editor controls.
        if (collapsed && hasChildren && !selected) {
            openRow.append(tagSymbol("<" + tag + ">"));
            openRow.append(tagSymbol(" ... </" + tag + ">"));
        } else {
            appendTagOpen(openRow, node, selected);
            if (selfClosing) {
                openRow.append(tagSymbol("/>"));
            } else {
                openRow.append(tagSymbol(">"));
            }

            if (!selfClosing && !hasChildren) {
                if (selected) openRow.append(inlineInnerTextInput(node));
                else openRow.append(textLiteral(node.innerText));
                openRow.append(tagSymbol("</" + tag + ">"));
            } else if (collapsed && hasChildren) {
                openRow.append(tagSymbol(" ... </" + tag + ">"));
            }
        }

        openRow.addEventListener("mouseup", event -> {
            if (uuid.equals(selectedElementUuid)) return;
            selectedElementUuid = uuid;
            refresh();
        });
        container.append(openRow);

        if (collapsed || selfClosing || !hasChildren) return;

        if (hasText) {
            if (selected) container.append(innerTextTreeRow(node, depth + 1));
            else container.append(innerTextReadonlyRow(node, depth + 1));
        }

        for (Element child : new ArrayList<>(node.children)) {
            buildTree(container, child, depth + 1);
        }

        Element closeRow = toolDocument.createElement("DIV");
        closeRow.setAttribute("class", selectedClass(uuid, "tree-row end-row"));
        closeRow.setAttribute("style", "padding-left:" + (depth * 10) + "px;");
        Element closeLabel = toolDocument.createElement("SPAN");
        closeLabel.setAttribute("class", "node-label");
        closeLabel.innerText = "</" + tag + ">";
        closeRow.append(closeLabel);
        closeRow.addEventListener("mouseup", event -> {
            if (uuid.equals(selectedElementUuid)) return;
            selectedElementUuid = uuid;
            refresh();
        });
        container.append(closeRow);
    }

    private static void appendTagOpen(Element row, Element node, boolean editable) {
        row.append(tagSymbol("<" + tagName(node)));
        LinkedHashMap<String, String> attrs = new LinkedHashMap<>(node.getAttributes());
        attrs.forEach((key, value) -> {
            if (editable) {
                row.append(tagSymbol(" " + key + "=\""));
                row.append(attributeInlineInput(node, key, value));
                row.append(tagSymbol("\""));
            } else {
                row.append(tagSymbol(" " + key + "=\"" + safe(value) + "\""));
            }
        });
    }

    private static Element attributeInlineInput(Element node, String key, String value) {
        String nodeUuid = node.uuid.toString();
        Document targetDoc = node.document;

        Element input = toolDocument.createElement("INPUT");
        input.setAttribute("class", "tree-edit-input tree-attr-input");
        input.setAttribute("data-editor-type", "attr");
        input.setAttribute("data-node-uuid", nodeUuid);
        input.setAttribute("data-attr-key", key);
        input.value = value == null ? "" : value;
        input.setAttribute("value", input.value);
        input.addEventListener("keydown", event -> {
            if (!isCommitKey(event)) return;
            commitAttributeEdit(targetDoc, nodeUuid, node, key, readEditorValue(input), true);
        });
        input.addEventListener("blur", event -> commitAttributeEdit(targetDoc, nodeUuid, node, key, readEditorValue(input), true));
        input.addEventListener("mousedown", event -> selectedElementUuid = nodeUuid);
        return input;
    }

    private static Element innerTextTreeRow(Element node, int depth) {
        String nodeUuid = node.uuid.toString();
        Document targetDoc = node.document;
        Element row = toolDocument.createElement("DIV");
        row.setAttribute("class", "tree-edit-row");
        row.setAttribute("style", "padding-left:" + (depth * 10) + "px;");
        row.addEventListener("mousedown", event -> selectedElementUuid = nodeUuid);

        Element input = toolDocument.createElement("INPUT");
        input.setAttribute("class", "tree-edit-input tree-text-input");
        input.setAttribute("data-editor-type", "text");
        input.setAttribute("data-node-uuid", nodeUuid);
        input.value = node.innerText == null ? "" : node.innerText;
        input.setAttribute("value", input.value);
        input.addEventListener("keydown", event -> {
            if (!isCommitKey(event)) return;
            commitInnerTextEdit(targetDoc, nodeUuid, node, readEditorValue(input), true);
        });
        input.addEventListener("blur", event -> commitInnerTextEdit(targetDoc, nodeUuid, node, readEditorValue(input), true));
        row.append(input);
        return row;
    }

    private static Element innerTextReadonlyRow(Element node, int depth) {
        Element row = toolDocument.createElement("DIV");
        row.setAttribute("class", "tree-edit-row");
        row.setAttribute("style", "padding-left:" + (depth * 10) + "px;");
        row.append(textLiteral(node.innerText));
        return row;
    }

    private static Element inlineInnerTextInput(Element node) {
        String nodeUuid = node.uuid.toString();
        Document targetDoc = node.document;
        Element input = toolDocument.createElement("INPUT");
        input.setAttribute("class", "tree-edit-input tree-inline-text-input");
        input.setAttribute("data-editor-type", "text");
        input.setAttribute("data-node-uuid", nodeUuid);
        input.value = node.innerText == null ? "" : node.innerText;
        input.setAttribute("value", input.value);
        input.addEventListener("keydown", event -> {
            if (!isCommitKey(event)) return;
            commitInnerTextEdit(targetDoc, nodeUuid, node, readEditorValue(input), true);
        });
        input.addEventListener("blur", event -> commitInnerTextEdit(targetDoc, nodeUuid, node, readEditorValue(input), true));
        input.addEventListener("mousedown", event -> selectedElementUuid = nodeUuid);
        return input;
    }

    private static void buildStyleInspector(Element inspector, Element selectedElement) {
        inspector.append(sectionTitle("Styles"));
        inspector.append(buildInlineStyleEditor(selectedElement));
        inspector.append(buildMatchedStylesView(selectedElement));
    }

    private static Element buildInlineStyleEditor(Element target) {
        String nodeUuid = target.uuid.toString();
        Document targetDoc = target.document;
        Element section = section("Inline Style");
        LinkedHashMap<String, String> styleMap = parseInlineStyle(target.getAttribute("style"));
        styleMap.forEach((key, value) -> section.append(styleRow(target, key, value)));

        Element add = toolDocument.createElement("DIV");
        add.setAttribute("class", "edit-row");
        Element keyInput = textInput("prop");
        Element valueInput = textInput("value");
        Element btn = action("Add");
        java.util.function.Consumer<Event> commit = event -> commitInlineStyleAdd(targetDoc, nodeUuid, target, keyInput, valueInput);
        btn.addEventListener("mousedown", commit);
        btn.addEventListener("mouseup", commit);
        keyInput.addEventListener("keydown", event -> {
            if (!isEnterKey(event)) return;
            commitInlineStyleAdd(targetDoc, nodeUuid, target, keyInput, valueInput);
        });
        valueInput.addEventListener("keydown", event -> {
            if (!isEnterKey(event)) return;
            commitInlineStyleAdd(targetDoc, nodeUuid, target, keyInput, valueInput);
        });
        add.append(keyInput);
        add.append(valueInput);
        add.append(btn);
        section.append(add);
        return section;
    }

    private static Element styleRow(Element target, String key, String value) {
        String nodeUuid = target.uuid.toString();
        Document targetDoc = target.document;
        Element row = toolDocument.createElement("DIV");
        row.setAttribute("class", "edit-row");

        Element keyText = toolDocument.createElement("SPAN");
        keyText.setAttribute("class", "k");
        keyText.innerText = key;

        Element valInput = textInput("");
        valInput.setAttribute("data-editor-type", "style");
        valInput.setAttribute("data-node-uuid", nodeUuid);
        valInput.setAttribute("data-style-key", key);
        valInput.setAttribute("value", value);
        valInput.value = value;
        valInput.addEventListener("keydown", event -> {
            if (!isCommitKey(event)) return;
            commitInlineStyleUpdate(targetDoc, nodeUuid, target, key, readEditorValue(valInput), true);
        });
        valInput.addEventListener("blur", event -> commitInlineStyleUpdate(targetDoc, nodeUuid, target, key, readEditorValue(valInput), true));

        Element remove = action("x");
        remove.addEventListener("mousedown", event -> commitInlineStyleRemove(targetDoc, nodeUuid, target, key, true));

        row.append(keyText);
        row.append(valInput);
        row.append(remove);
        return row;
    }

    private static Element buildMatchedStylesView(Element target) {
        Element section = section("Matched CSS");
        Map<String, Map<String, String>> styles = Selector.getDebugStyles(target);
        if (styles.isEmpty()) {
            Element empty = span("No matched selector.");
            empty.setAttribute("class", "hint");
            section.append(empty);
            return section;
        }

        styles.forEach((selector, props) -> {
            Element block = toolDocument.createElement("DIV");
            block.setAttribute("class", "style-block");

            Element selectorLine = span(selector);
            selectorLine.setAttribute("class", "selector");
            block.append(selectorLine);

            props.forEach((k, v) -> {
                Element prop = span(k + ": " + v + ";");
                prop.setAttribute("class", "prop");
                block.append(prop);
            });
            section.append(block);
        });
        return section;
    }

    private static LinkedHashMap<String, String> parseInlineStyle(String inlineStyle) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (inlineStyle == null || inlineStyle.isBlank()) return result;
        String[] entries = inlineStyle.split(";");
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            String[] kv = entry.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim();
            String value = kv[1].trim();
            if (key.isBlank()) continue;
            result.put(key, value);
        }
        return result;
    }

    private static String toInlineStyle(LinkedHashMap<String, String> styleMap) {
        StringBuilder sb = new StringBuilder();
        styleMap.forEach((key, value) -> {
            String k = safe(key);
            if (k.isBlank()) return;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(k).append(": ").append(safe(value)).append(';');
        });
        return sb.toString();
    }

    private static String tagName(Element element) {
        return element.tagName.toLowerCase(Locale.ENGLISH);
    }

    private static String selectedClass(String uuid, String base) {
        if (selectedElementUuid != null && selectedElementUuid.equals(uuid)) return base + " selected";
        return base;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Element textInput(String placeholder) {
        Element input = toolDocument.createElement("INPUT");
        input.setAttribute("class", "edit-input");
        if (placeholder != null && !placeholder.isBlank()) input.setAttribute("placeholder", placeholder);
        return input;
    }

    private static Element action(String text) {
        Element action = toolDocument.createElement("DIV");
        action.setAttribute("class", "action");
        action.innerText = text;
        return action;
    }

    private static Element section(String titleText) {
        Element section = toolDocument.createElement("DIV");
        section.setAttribute("class", "section");
        section.append(sectionTitle(titleText));
        return section;
    }

    private static Element sectionTitle(String text) {
        Element title = toolDocument.createElement("DIV");
        title.setAttribute("class", "section-title");
        title.innerText = text;
        return title;
    }

    private static Element span(String text) {
        Element span = toolDocument.createElement("SPAN");
        span.innerText = text;
        return span;
    }

    private static Element textLiteral(String text) {
        Element span = span(safe(text));
        span.setAttribute("class", "node-label");
        return span;
    }

    private static Element tagSymbol(String text) {
        Element span = span(text);
        span.setAttribute("class", "tree-code");
        return span;
    }

    private static void clearChildren(Element parent) {
        ArrayList<Element> snapshot = new ArrayList<>(parent.children);
        snapshot.forEach(Element::remove);
    }

    private static String readEditorValue(Element editor) {
        if (editor == null) return "";
        String fromField = editor.value;
        if (fromField != null) return fromField;
        return safe(editor.getAttribute("value"));
    }

    private static boolean isCommitKey(Event event) {
        if (!(event instanceof com.sighs.apricityui.event.KeyEvent keyEvent)) return false;
        return "Escape".equals(keyEvent.key) || "Enter".equals(keyEvent.key);
    }

    private static boolean isEnterKey(Event event) {
        if (!(event instanceof com.sighs.apricityui.event.KeyEvent keyEvent)) return false;
        return "Enter".equals(keyEvent.key);
    }

    private static boolean isSelfClosingTag(Element element) {
        if (element == null || element.tagName == null) return false;
        String tag = element.tagName.toLowerCase(Locale.ENGLISH);
        return Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
                "param", "source", "track", "wbr").contains(tag);
    }

    private static void ensureDefaultCollapsedState(Document document) {
        if (document == null || document.body == null) return;
        String docUuid = document.getUuid().toString();
        if (collapseInitializedDocUuids.contains(docUuid)) return;

        for (Element element : document.getElements()) {
            if (element == null || element == document.body) continue;
            if (!element.children.isEmpty()) {
                collapsedNodeUuids.add(element.uuid.toString());
            }
        }
        collapseInitializedDocUuids.add(docUuid);
    }

    private static void commitInlineStyleAdd(Document preferredDoc, String nodeUuid, Element fallback, Element keyInput, Element valueInput) {
        String key = safe(readEditorValue(keyInput)).trim();
        if (key.isBlank()) return;
        String value = safe(readEditorValue(valueInput));

        Element target = resolveTargetElement(preferredDoc, nodeUuid, fallback);
        if (target == null) return;
        LinkedHashMap<String, String> map = parseInlineStyle(target.getAttribute("style"));
        map.put(key, value);
        applyInlineStyleMap(target, map);
        markDirty(target.document);

        keyInput.value = "";
        valueInput.value = "";
        refresh();
    }

    private static void commitInlineStyleUpdate(Document preferredDoc, String nodeUuid, Element fallback, String key, String value, boolean refreshAfter) {
        if (key == null || key.isBlank()) return;
        Element target = resolveTargetElement(preferredDoc, nodeUuid, fallback);
        if (target == null) return;
        LinkedHashMap<String, String> map = parseInlineStyle(target.getAttribute("style"));
        if (!map.containsKey(key)) return;
        map.put(key, safe(value));
        applyInlineStyleMap(target, map);
        markDirty(target.document);
        if (refreshAfter) refresh();
    }

    private static void commitInlineStyleRemove(Document preferredDoc, String nodeUuid, Element fallback, String key, boolean refreshAfter) {
        if (key == null || key.isBlank()) return;
        Element target = resolveTargetElement(preferredDoc, nodeUuid, fallback);
        if (target == null) return;
        LinkedHashMap<String, String> map = parseInlineStyle(target.getAttribute("style"));
        map.remove(key);
        applyInlineStyleMap(target, map);
        markDirty(target.document);
        if (refreshAfter) refresh();
    }

    private static void commitAttributeEdit(Document preferredDoc, String nodeUuid, Element fallback, String key, String value, boolean refreshAfter) {
        if (key == null || key.isBlank()) return;
        Element target = resolveTargetElement(preferredDoc, nodeUuid, fallback);
        if (target == null) return;

        String normalizedValue = safe(value);
        if ("style".equalsIgnoreCase(key)) {
            if (normalizedValue.isBlank()) target.removeAttribute("style");
            else target.setAttribute("style", normalizedValue);
        } else {
            target.setAttribute(key, normalizedValue);
        }
        markDirty(target.document);
        if (refreshAfter) refresh();
    }

    private static void commitInnerTextEdit(Document preferredDoc, String nodeUuid, Element fallback, String value, boolean refreshAfter) {
        Element target = resolveTargetElement(preferredDoc, nodeUuid, fallback);
        if (target == null) return;
        target.innerText = safe(value);
        markDirty(target.document);
        if (refreshAfter) refresh();
    }

    private static void applyInlineStyleMap(Element target, LinkedHashMap<String, String> styleMap) {
        if (target == null) return;
        String inlineStyle = toInlineStyle(styleMap).trim();
        if (inlineStyle.isBlank()) target.removeAttribute("style");
        else target.setAttribute("style", inlineStyle);
    }

    private static Element resolveTargetElement(Document preferredDoc, String nodeUuid, Element fallback) {
        Document doc = preferredDoc != null ? preferredDoc : Document.getByUUID(selectedDocumentUuid);
        String effectiveUuid = safe(nodeUuid);
        if (effectiveUuid.isBlank()) effectiveUuid = safe(selectedElementUuid);

        if (doc != null && !effectiveUuid.isBlank()) {
            for (Element candidate : doc.getElements()) {
                if (effectiveUuid.equals(candidate.uuid.toString())) return candidate;
            }
        }
        if (fallback != null) return fallback;
        if (doc != null) return doc.body;
        return null;
    }

    private static void markDirty(Document document) {
        if (document == null || document.body == null) return;
        document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
    }
}
