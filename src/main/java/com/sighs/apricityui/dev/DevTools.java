package com.sighs.apricityui.dev;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.*;
import com.sighs.apricityui.instance.element.MinecraftElement;
import net.fabricmc.loader.api.FabricLoader;

import java.util.*;

public class DevTools {
    private static final String PATH = "devtools/index.html";
    private static final String EXAMPLE_PATH = "devtools/example-k.html";

    private static Document toolDocument = null;
    private static Document exampleDocument = null;
    private static String selectedDocumentUuid = null;
    private static String selectedElementUuid = null;
    private static final Set<String> collapsedNodeUuids = new LinkedHashSet<>();
    private static final Set<String> collapseInitializedDocUuids = new LinkedHashSet<>();

    private DevTools() {}

    public static boolean isOpen() {
        return toolDocument != null && !Document.get(PATH).isEmpty();
    }

    public static Document getToolDocument() {
        return toolDocument;
    }

    public static boolean ensureOpen() {
        if (isOpen()) return true;
        toggle();
        return isOpen();
    }

    public static boolean isExampleOpen() {
        return exampleDocument != null && !Document.get(EXAMPLE_PATH).isEmpty();
    }

    public static void toggleExample() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            if (Document.get(EXAMPLE_PATH).isEmpty()) {
                exampleDocument = Document.create(EXAMPLE_PATH);
                return;
            }
            closeExample();
        }
    }

    public static void closeExample() {
        exampleDocument = null;
        Document.remove(EXAMPLE_PATH);
    }

    public static boolean selectDocument(Document document) {
        if (document == null || document.body == null) return false;
        selectedDocumentUuid = document.getUuid().toString();
        selectedElementUuid = document.body.uuid.toString();
        refresh();
        return true;
    }

    public static boolean selectElement(Element element) {
        if (element == null || element.document == null) return false;
        selectedDocumentUuid = element.document.getUuid().toString();
        selectedElementUuid = element.uuid.toString();
        refresh();
        return true;
    }

    public static boolean applyInlineStyle(Element element, String key, String value) {
        if (element == null || element.document == null) return false;
        String normalizedKey = normalizeStyleKey(key);
        if (normalizedKey.isBlank()) return false;
        LinkedHashMap<String, String> map = parseInlineStyle(element.getAttribute("style"));
        map.put(normalizedKey, safe(value));
        applyInlineStyleMap(element, map);
        markDirty(element.document);
        syncRuntimeInlineStyleCache(element);
        syncStyleAttributeEditors(element);
        refresh();
        return true;
    }

    public static boolean devTestApplyInlineStyleViaInspector(Element element, String key, String value) {
        if (element == null || element.document == null) return false;
        if (key == null || key.isBlank()) return false;
        if (!ensureOpen()) return false;
        selectElement(element);
        refresh();

        Element keyInput = null;
        Element valueInput = null;
        Element addBtn = null;
        int actionCount = 0;
        for (Element candidate : toolDocument.getElements()) {
            if (candidate == null) continue;
            if ("INPUT".equalsIgnoreCase(candidate.tagName)) {
                String cls = candidate.getAttribute("class");
                if (cls != null && cls.contains("edit-input")) {
                    String placeholder = candidate.getAttribute("placeholder");
                    if ("prop".equalsIgnoreCase(placeholder)) keyInput = candidate;
                    if ("value".equalsIgnoreCase(placeholder)) valueInput = candidate;
                }
            } else if ("DIV".equalsIgnoreCase(candidate.tagName)) {
                String cls = candidate.getAttribute("class");
                if (cls != null && cls.contains("action")) {
                    actionCount++;
                    if ("Add".equalsIgnoreCase(candidate.innerText)) {
                        addBtn = candidate;
                    } else if (addBtn == null) {
                        addBtn = candidate;
                    }
                }
            }
        }

        ApricityUI.LOGGER.info("[DevTools] DevTest inspector controls keyInput={} valueInput={} addBtn={} actionCount={}",
                keyInput != null, valueInput != null, addBtn != null, actionCount);

        if (addBtn != null && addBtn.parentElement != null) {
            Element row = addBtn.parentElement;
            for (Element sibling : row.children) {
                if (sibling == null || !"INPUT".equalsIgnoreCase(sibling.tagName)) continue;
                if (keyInput == null) {
                    keyInput = sibling;
                } else if (valueInput == null) {
                    valueInput = sibling;
                }
            }
        }

        if (keyInput == null || valueInput == null || addBtn == null) return false;
        ApricityUI.LOGGER.info("[DevTools] DevTest apply via inspector target={} key={} value={}", element.uuid, key, value);
        ApricityUI.LOGGER.info("[DevTools] DevTest input before set keyValue={} valValue={}", keyInput.value, valueInput.value);
        keyInput.value = key;
        keyInput.setAttribute("value", key);
        valueInput.value = safe(value);
        valueInput.setAttribute("value", safe(value));
        ApricityUI.LOGGER.info("[DevTools] DevTest input after set keyValue={} valValue={}", keyInput.value, valueInput.value);

        Event.tiggerEvent(new Event(addBtn, "mousedown", null, true));
        Event.tiggerEvent(new Event(addBtn, "mouseup", null, true));
        ApricityUI.LOGGER.info("[DevTools] DevTest add events fired for target={}", element.uuid);
        return true;
    }

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
        Document fallback = candidates.getLast();
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

        Element prev = createToolElement("DIV");
        prev.setAttribute("class", "doc-arrow doc-prev");
        prev.innerText = "<";
        prev.addEventListener("mousedown", event -> switchDocumentByOffset(docs, selectedDocument, -1));

        Element name = createToolElement("DIV");
        name.setAttribute("class", "doc-name");
        name.innerText = selectedDocument.getPath();

        Element next = createToolElement("DIV");
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

        Element openRow = createToolElement("DIV");
        openRow.setAttribute("class", selectedClass(uuid, "tree-row"));
        openRow.setAttribute("style", "padding-left:" + (depth * 10) + "px;");

        Element toggle = createToolElement("SPAN");
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

        Element closeRow = createToolElement("DIV");
        closeRow.setAttribute("class", selectedClass(uuid, "tree-row end-row"));
        closeRow.setAttribute("style", "padding-left:" + (depth * 10) + "px;");
        Element closeLabel = createToolElement("SPAN");
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

        Element input = createToolElement("INPUT");
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
        Element row = createToolElement("DIV");
        row.setAttribute("class", "tree-edit-row");
        row.setAttribute("style", "padding-left:" + (depth * 10) + "px;");
        row.addEventListener("mousedown", event -> selectedElementUuid = nodeUuid);

        Element input = createToolElement("INPUT");
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
        Element row = createToolElement("DIV");
        row.setAttribute("class", "tree-edit-row");
        row.setAttribute("style", "padding-left:" + (depth * 10) + "px;");
        row.append(textLiteral(node.innerText));
        return row;
    }

    private static Element inlineInnerTextInput(Element node) {
        String nodeUuid = node.uuid.toString();
        Document targetDoc = node.document;
        Element input = createToolElement("INPUT");
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

        Element add = createToolElement("DIV");
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
        Element row = createToolElement("DIV");
        row.setAttribute("class", "edit-row");

        Element keyText = createToolElement("SPAN");
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
            Element block = createToolElement("DIV");
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
            String key = normalizeStyleKey(kv[0]);
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
        Element input = createToolElement("INPUT");
        input.setAttribute("class", "edit-input");
        if (placeholder != null && !placeholder.isBlank()) input.setAttribute("placeholder", placeholder);
        return input;
    }

    private static Element action(String text) {
        Element action = createToolElement("DIV");
        action.setAttribute("class", "action");
        action.innerText = text;
        return action;
    }

    private static Element section(String titleText) {
        Element section = createToolElement("DIV");
        section.setAttribute("class", "section");
        section.append(sectionTitle(titleText));
        return section;
    }

    private static Element sectionTitle(String text) {
        Element title = createToolElement("DIV");
        title.setAttribute("class", "section-title");
        title.innerText = text;
        return title;
    }

    private static Element span(String text) {
        Element span = createToolElement("SPAN");
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
        String rawKey = readEditorValue(keyInput);
        String rawValue = readEditorValue(valueInput);
        String key = normalizeStyleKey(rawKey);
        if (key.isBlank()) {
            ApricityUI.LOGGER.info("[DevTools] InlineStyleAdd ignored: blank key (rawKey={}, rawValue={}, keyInputVal={}, valueInputVal={})",
                    rawKey, rawValue, keyInput == null ? null : keyInput.value, valueInput == null ? null : valueInput.value);
            return;
        }
        String value = safe(rawValue);

        Element target = resolveTargetElement(preferredDoc, nodeUuid, fallback);
        if (target == null) {
            ApricityUI.LOGGER.warn("[DevTools] InlineStyleAdd ignored: target missing (nodeUuid={}, rawKey={}, rawValue={})", nodeUuid, rawKey, rawValue);
            return;
        }
        String before = safe(target.getAttribute("style"));
        ApricityUI.LOGGER.info("[DevTools] InlineStyleAdd target={} key={} value={} beforeStyle=[{}]", target.uuid, key, value, before);
        LinkedHashMap<String, String> map = parseInlineStyle(before);
        map.put(key, value);
        applyInlineStyleMap(target, map);
        markDirty(target.document);
        syncRuntimeInlineStyleCache(target);
        syncStyleAttributeEditors(target);
        ApricityUI.LOGGER.info("[DevTools] InlineStyleAdd applied target={} afterStyle=[{}] mapSize={}",
                target.uuid, safe(target.getAttribute("style")), map.size());

        keyInput.value = "";
        valueInput.value = "";
        refresh();
    }

    private static void commitInlineStyleUpdate(Document preferredDoc, String nodeUuid, Element fallback, String key, String value, boolean refreshAfter) {
        String normalizedKey = normalizeStyleKey(key);
        if (normalizedKey.isBlank()) return;
        Element target = resolveTargetElement(preferredDoc, nodeUuid, fallback);
        if (target == null) {
            ApricityUI.LOGGER.warn("[DevTools] InlineStyleUpdate ignored: target missing (nodeUuid={}, key={}, value={})", nodeUuid, key, value);
            return;
        }
        String before = safe(target.getAttribute("style"));
        LinkedHashMap<String, String> map = parseInlineStyle(before);
        boolean existed = map.containsKey(normalizedKey);
        ApricityUI.LOGGER.info("[DevTools] InlineStyleUpdate target={} key={} normalizedKey={} existed={} value={} beforeStyle=[{}]",
                target.uuid, key, normalizedKey, existed, value, before);
        map.put(normalizedKey, safe(value));
        applyInlineStyleMap(target, map);
        markDirty(target.document);
        syncRuntimeInlineStyleCache(target);
        syncStyleAttributeEditors(target);
        ApricityUI.LOGGER.info("[DevTools] InlineStyleUpdate applied target={} afterStyle=[{}] mapSize={}",
                target.uuid, safe(target.getAttribute("style")), map.size());
        if (refreshAfter) refresh();
    }

    private static void commitInlineStyleRemove(Document preferredDoc, String nodeUuid, Element fallback, String key, boolean refreshAfter) {
        String normalizedKey = normalizeStyleKey(key);
        if (normalizedKey.isBlank()) return;
        Element target = resolveTargetElement(preferredDoc, nodeUuid, fallback);
        if (target == null) {
            ApricityUI.LOGGER.warn("[DevTools] InlineStyleRemove ignored: target missing (nodeUuid={}, key={})", nodeUuid, key);
            return;
        }
        String before = safe(target.getAttribute("style"));
        ApricityUI.LOGGER.info("[DevTools] InlineStyleRemove target={} key={} normalizedKey={} beforeStyle=[{}]",
                target.uuid, key, normalizedKey, before);
        LinkedHashMap<String, String> map = parseInlineStyle(before);
        map.remove(normalizedKey);
        applyInlineStyleMap(target, map);
        markDirty(target.document);
        syncRuntimeInlineStyleCache(target);
        syncStyleAttributeEditors(target);
        ApricityUI.LOGGER.info("[DevTools] InlineStyleRemove applied target={} afterStyle=[{}] mapSize={}",
                target.uuid, safe(target.getAttribute("style")), map.size());
        if (refreshAfter) refresh();
    }

    private static void commitAttributeEdit(Document preferredDoc, String nodeUuid, Element fallback, String key, String value, boolean refreshAfter) {
        if (key == null || key.isBlank()) return;
        Element target = resolveTargetElement(preferredDoc, nodeUuid, fallback);
        if (target == null) return;

        String normalizedValue = safe(value);
        if ("style".equalsIgnoreCase(key)) {
            String before = safe(target.getAttribute("style"));
            if (normalizedValue.isBlank()) target.removeAttribute("style");
            else target.setAttribute("style", normalizedValue);
            syncRuntimeInlineStyleCache(target);
            syncStyleAttributeEditors(target);
            ApricityUI.LOGGER.info("[DevTools] AttributeEdit style target={} beforeStyle=[{}] value=[{}]", target.uuid, before, normalizedValue);
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

    private static String normalizeStyleKey(String key) {
        if (key == null) return "";
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("--")) return trimmed;
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static Element createToolElement(String tagName) {
        if (toolDocument == null) return null;
        Element element = toolDocument.createElement(tagName);
        return Element.init(element);
    }

    private static void syncStyleAttributeEditors(Element target) {
        if (toolDocument == null || target == null) return;
        String targetUuid = target.uuid.toString();
        String inlineStyle = safe(target.getAttribute("style"));
        int updated = 0;
        for (Element element : toolDocument.getElements()) {
            if (element == null) continue;
            if (!"INPUT".equalsIgnoreCase(element.tagName)) continue;
            String type = element.getAttribute("data-editor-type");
            if (!"attr".equals(type)) continue;
            String nodeUuid = element.getAttribute("data-node-uuid");
            String key = element.getAttribute("data-attr-key");
            if (!targetUuid.equals(nodeUuid)) continue;
            if (!"style".equalsIgnoreCase(key)) continue;
            element.value = inlineStyle;
            element.setAttribute("value", inlineStyle);
            updated++;
        }
        if (updated > 0) {
            ApricityUI.LOGGER.info("[DevTools] Synced {} style attr editor(s) for {}", updated, targetUuid);
        }
    }

    private static void syncRuntimeInlineStyleCache(Element target) {
        if (!(target instanceof MinecraftElement minecraftElement)) return;
        String raw = target.getAttribute("style");
        if (minecraftElement.getRuntimeCache("bound-base-inline-style") != null) {
            minecraftElement.putRuntimeCache("bound-base-inline-style", raw == null ? "" : raw);
        }
        if (minecraftElement.getRuntimeCache("bound-last-inline-style") != null) {
            minecraftElement.putRuntimeCache("bound-last-inline-style", raw == null ? "" : raw);
        }
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
