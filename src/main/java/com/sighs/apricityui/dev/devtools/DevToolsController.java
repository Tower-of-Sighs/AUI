package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.init.FrameTaskScheduler;
import com.sighs.apricityui.instance.ApricityContainerScreen;
import com.sighs.apricityui.instance.ApricityScreen;
import com.sighs.apricityui.instance.element.MinecraftElement;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.ui.tooltip.Tooltip;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DevToolsController {
    public static final String PATH = "devtools/devtools.html";

    enum InspectorTab {
        ATTRIBUTES("attributes"), STYLES("styles"), BOXMODEL("boxmodel");

        final String id;

        InspectorTab(String id) {
            this.id = id;
        }

        static InspectorTab parse(String value) {
            if (value != null) {
                for (InspectorTab tab : values()) {
                    if (tab.id.equalsIgnoreCase(value.trim())) return tab;
                }
            }
            return ATTRIBUTES;
        }
    }

    private final Set<UUID> collapsedNodes = new LinkedHashSet<>();
    private final Map<UUID, LinkedHashMap<String, String>> disabledStyles = new LinkedHashMap<>();
    private final DevToolsDomTree tree = new DevToolsDomTree(this);
    private final DevToolsInspector inspector = new DevToolsInspector(this);

    private Document toolDocument;
    private Document targetDocument;
    private Document.MutationObserver targetObserver;
    private UUID selectedElementUuid;
    private InspectorTab inspectorTab = InspectorTab.ATTRIBUTES;
    private boolean pickMode = true;
    private boolean draggingPanel;
    private double panelDragOffsetX;
    private boolean resizingInspector;
    private boolean refreshQueued;
    private long toastTicket;

    public synchronized boolean isOpen() {
        return toolDocument != null && toolDocument.isActive() && Document.get(PATH).contains(toolDocument);
    }

    public synchronized Document getToolDocument() {
        return isOpen() ? toolDocument : null;
    }

    public synchronized boolean ensureOpen() {
        if (!isOpen()) open();
        return isOpen();
    }

    public synchronized void toggle() {
        if (isOpen()) close();
        else open();
    }

    public synchronized boolean selectDocument(Document document) {
        if (!isDebuggable(document)) return false;
        if (!ensureOpen()) return false;
        bindTarget(document);
        selectedElementUuid = document.body.uuid;
        collapsedNodes.clear();
        refresh();
        return true;
    }

    public synchronized boolean selectElement(Element element) {
        if (element == null || !isDebuggable(element.document)) return false;
        if (!ensureOpen()) return false;
        if (targetDocument != element.document) bindTarget(element.document);
        selectedElementUuid = element.uuid;
        revealAncestors(element);
        refresh();
        return true;
    }

    public synchronized boolean applyInlineStyle(Element element, String property, String value) {
        if (element == null || !isDebuggable(element.document)) return false;
        String normalized = InlineStyleDeclaration.normalizeProperty(property);
        if (normalized.isBlank()) return false;
        LinkedHashMap<String, String> styles = inlineStyles(element);
        styles.put(normalized, value == null ? "" : value.trim());
        disabledStyleMap(element).remove(normalized);
        applyInlineStyles(element, styles);
        afterTargetEdit(element, "Style \"" + normalized + "\" updated");
        return true;
    }

    public synchronized void refresh() {
        refreshQueued = false;
        if (!isOpen()) return;
        if (!isDebuggable(targetDocument)) bindTarget(resolvePreferredTarget());
        bindShell();

        Element domTree = toolDocument.querySelector("#domTree");
        Element nodeCount = toolDocument.querySelector("#nodeCount");
        if (domTree == null || nodeCount == null) return;

        Element selected = selectedElement();
        if (selected == null && targetDocument != null && targetDocument.body != null) {
            selected = targetDocument.body;
            selectedElementUuid = selected.uuid;
        }

        tree.render(domTree, nodeCount, targetDocument, selected);
        inspector.render(targetDocument, selected, inspectorTab);
        updateShellState();
        DevToolsDom.markDirty(toolDocument);
    }

    Document toolDocument() {
        return toolDocument;
    }

    Document targetDocument() {
        return targetDocument;
    }

    Element selectedElement() {
        return findElement(targetDocument, selectedElementUuid);
    }

    UUID selectedElementUuid() {
        return selectedElementUuid;
    }

    boolean isCollapsed(Element element) {
        return element != null && collapsedNodes.contains(element.uuid);
    }

    boolean isPickMode() {
        return pickMode;
    }

    void toggleCollapsed(Element element) {
        if (element == null || (element.children.isEmpty()
                && (element.innerText == null || element.innerText.isBlank()))) return;
        if (!collapsedNodes.remove(element.uuid)) collapsedNodes.add(element.uuid);
        refresh();
    }

    void selectFromView(Element element) {
        if (element == null || element.document != targetDocument) return;
        selectedElementUuid = element.uuid;
        revealAncestors(element);
        refresh();
    }

    void updateAttribute(Element target, String name, String value) {
        if (target == null || name == null || name.isBlank()) return;
        String normalized = name.trim();
        if (value == null || value.isEmpty()) target.removeAttribute(normalized);
        else target.setAttribute(normalized, value);
        if ("style".equalsIgnoreCase(normalized)) syncRuntimeInlineStyleCache(target);
        afterTargetEdit(target, "Attr \"" + normalized + "\" updated");
    }

    void addAttribute(Element target, String name, String value) {
        if (target == null || name == null || name.isBlank()) return;
        String normalized = name.trim();
        target.setAttribute(normalized, value == null ? "" : value);
        if ("style".equalsIgnoreCase(normalized)) syncRuntimeInlineStyleCache(target);
        afterTargetEdit(target, "Attr \"" + normalized + "\" added");
    }

    void deleteAttribute(Element target, String name) {
        if (target == null || name == null || name.isBlank()) return;
        target.removeAttribute(name);
        if ("style".equalsIgnoreCase(name)) syncRuntimeInlineStyleCache(target);
        afterTargetEdit(target, "Attr \"" + name + "\" removed");
    }

    void updateStyle(Element target, String property, String value) {
        applyInlineStyle(target, property, value);
    }

    void renameStyle(Element target, String oldProperty, String newProperty) {
        if (target == null) return;
        String oldKey = InlineStyleDeclaration.normalizeProperty(oldProperty);
        String newKey = InlineStyleDeclaration.normalizeProperty(newProperty);
        if (oldKey.isBlank() || newKey.isBlank() || oldKey.equals(newKey)) return;
        LinkedHashMap<String, String> styles = inlineStyles(target);
        String value = styles.remove(oldKey);
        LinkedHashMap<String, String> disabled = disabledStyleMap(target);
        if (value == null) value = disabled.remove(oldKey);
        if (value == null) return;
        styles.put(newKey, value);
        applyInlineStyles(target, styles);
        afterTargetEdit(target, "Style renamed to \"" + newKey + "\"");
    }

    void deleteStyle(Element target, String property) {
        if (target == null) return;
        String key = InlineStyleDeclaration.normalizeProperty(property);
        LinkedHashMap<String, String> styles = inlineStyles(target);
        styles.remove(key);
        disabledStyleMap(target).remove(key);
        applyInlineStyles(target, styles);
        afterTargetEdit(target, "Style \"" + key + "\" removed");
    }

    void toggleStyle(Element target, String property) {
        if (target == null) return;
        String key = InlineStyleDeclaration.normalizeProperty(property);
        LinkedHashMap<String, String> styles = inlineStyles(target);
        LinkedHashMap<String, String> disabled = disabledStyleMap(target);
        if (disabled.containsKey(key)) {
            styles.put(key, disabled.remove(key));
        } else if (styles.containsKey(key)) {
            disabled.put(key, styles.remove(key));
        }
        applyInlineStyles(target, styles);
        afterTargetEdit(target, "Style \"" + key + "\" toggled");
    }

    LinkedHashMap<String, String> inlineStyles(Element target) {
        return InlineStyleDeclaration.parse(target == null ? "" : target.getAttribute("style"));
    }

    LinkedHashMap<String, String> disabledStyleEntries(Element target) {
        return new LinkedHashMap<>(disabledStyleMap(target));
    }

    boolean isStyleDisabled(Element target, String property) {
        return target != null && disabledStyleMap(target).containsKey(property);
    }

    boolean isCommitKey(Event event) {
        return event instanceof KeyEvent keyEvent && "Enter".equals(keyEvent.key);
    }

    void clearToolFocus() {
        if (toolDocument != null) toolDocument.clearFocus();
    }

    void showToast(String message) {
        if (toolDocument == null) return;
        Element toast = toolDocument.querySelector("#toast");
        if (toast == null) return;
        long ticket = ++toastTicket;
        toast.setTextContent(message == null ? "" : message);
        toast.setAttribute("class", "toast show");
        DevToolsDom.markDirty(toolDocument);
        FrameTaskScheduler.scheduleAfterFrames(32, deadlineNs -> {
            synchronized (DevToolsController.this) {
                if (ticket == toastTicket && toast.isConnected()) {
                    toast.setAttribute("class", "toast");
                    DevToolsDom.markDirty(toolDocument);
                }
            }
            return true;
        });
    }

    private void open() {
        toolDocument = Document.create(PATH);
        if (toolDocument == null) return;
        toolDocument.setReloadPersistent(true);
        bindTarget(resolvePreferredTarget());
        bindShell();
        refresh();
    }

    private void close() {
        disconnectTargetObserver();
        Document closing = toolDocument;
        Tooltip.hide(closing);
        toolDocument = null;
        targetDocument = null;
        selectedElementUuid = null;
        collapsedNodes.clear();
        disabledStyles.clear();
        draggingPanel = false;
        panelDragOffsetX = 0;
        resizingInspector = false;
        refreshQueued = false;
        if (closing != null) closing.remove();
    }

    private void bindTarget(Document target) {
        disconnectTargetObserver();
        targetDocument = isDebuggable(target) ? target : null;
        if (targetDocument == null) {
            selectedElementUuid = null;
            return;
        }
        if (findElement(targetDocument, selectedElementUuid) == null) selectedElementUuid = targetDocument.body.uuid;
        targetObserver = targetDocument.createMutationObserver(records -> scheduleRefresh());
        targetObserver.observe(targetDocument.documentElement, true, true, true, true, false, false, "");
    }

    private void disconnectTargetObserver() {
        if (targetObserver != null) targetObserver.disconnect();
        targetObserver = null;
    }

    private void scheduleRefresh() {
        synchronized (this) {
            if (refreshQueued || !isOpen()) return;
            refreshQueued = true;
        }
        FrameTaskScheduler.scheduleAfterFrames(1, deadlineNs -> {
            refresh();
            return true;
        });
    }

    private void bindShell() {
        if (toolDocument == null || toolDocument.body == null) return;
        Element pickButton = toolDocument.querySelector("#pickBtn");
        Element consoleButton = toolDocument.querySelector(".console-btn");
        Element dragHandle = toolDocument.querySelector("#panelDragHandle");
        Element documentSelect = toolDocument.querySelector("#documentSelect");
        bindOnce(pickButton, event -> {
            pickMode = !pickMode;
            updateShellState();
            showToast(pickMode ? "Inspect mode \u00b7 ON" : "Inspect mode \u00b7 OFF");
        });
        bindOnce(consoleButton, event -> showToast("Console \u00b7 Coming soon"));
        bindTooltipOnce(pickButton, "tooltip.apricityui.devtools.inspect");
        bindTooltipOnce(consoleButton, "tooltip.apricityui.devtools.console");
        bindPanelDrag(dragHandle);
        bindTooltipOnce(dragHandle, "tooltip.apricityui.devtools.move");
        bindDocumentSelector(documentSelect);
        syncDocumentSelector(documentSelect);
        for (Element tab : toolDocument.querySelectorAll(".inspector-tab")) {
            bindOnce(tab, event -> {
                inspectorTab = InspectorTab.parse(tab.getAttribute("data-tab"));
                inspector.render(targetDocument, selectedElement(), inspectorTab);
                updateShellState();
                DevToolsDom.markDirty(toolDocument);
            });
        }

        Element resizeHandle = toolDocument.querySelector("#resizeHandle");
        if (resizeHandle != null && !"1".equals(resizeHandle.getAttribute("data-resize-bound"))) {
            resizeHandle.setAttribute("data-resize-bound", "1");
            resizeHandle.addEventListener("mousedown", event -> {
                resizingInspector = true;
                resizeHandle.setAttribute("class", "resize-handle dragging");
                event.preventDefault();
            });
            toolDocument.body.addEventListener("mousemove", this::resizeInspector);
            toolDocument.body.addEventListener("mouseup", event -> {
                if (!resizingInspector) return;
                resizingInspector = false;
                resizeHandle.setAttribute("class", "resize-handle");
                DevToolsDom.markDirty(toolDocument);
            });
        }
    }

    private void bindOnce(Element element, java.util.function.Consumer<Event> listener) {
        if (element == null || "1".equals(element.getAttribute("data-java-bound"))) return;
        element.setAttribute("data-java-bound", "1");
        element.addEventListener("click", listener);
    }

    private void bindTooltipOnce(Element element, String translationKey) {
        if (element == null || "1".equals(element.getAttribute("data-tooltip-bound"))) return;
        element.setAttribute("data-tooltip-bound", "1");
        Tooltip.bindTranslation(element, translationKey);
    }

    private void bindDocumentSelector(Element select) {
        if (select == null || "1".equals(select.getAttribute("data-document-bound"))) return;
        select.setAttribute("data-document-bound", "1");
        select.addEventListener("click", event -> syncDocumentSelector(select));
        select.addEventListener("change", event -> selectDocumentByUuid(select.getValue()));
    }

    private void bindPanelDrag(Element handle) {
        if (handle == null || toolDocument == null
                || "1".equals(handle.getAttribute("data-panel-drag-bound"))) return;
        handle.setAttribute("data-panel-drag-bound", "1");
        handle.addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) return;
            Element panel = toolDocument.querySelector(".side-panel");
            if (panel == null) return;
            draggingPanel = true;
            panelDragOffsetX = mouseEvent.clientX - Position.of(panel).x;
            toolDocument.setPressedElement(handle);
            Tooltip.hide(toolDocument);
            handle.setAttribute("class", "top-btn drag-handle dragging");
            event.preventDefault();
            event.stopPropagation();
        });
        handle.addEventListener("mousemove", this::movePanel);
        handle.addEventListener("mouseup", event -> endPanelDrag());
    }

    private void movePanel(Event event) {
        if (!draggingPanel || !(event instanceof MouseEvent mouseEvent) || toolDocument == null) return;
        Element panel = toolDocument.querySelector(".side-panel");
        if (panel == null) return;
        double panelWidth = Size.of(panel).width();
        double viewportWidth = toolDocument.getViewport().layoutWidth();
        double maxLeft = Math.max(0, viewportWidth - panelWidth);
        double left = Math.max(0, Math.min(maxLeft, mouseEvent.clientX - panelDragOffsetX));
        panel.setAttribute("style", "left:" + String.format(Locale.ROOT, "%.2fpx", left) + ";right:auto;");
        event.preventDefault();
        event.stopImmediatePropagation();
    }

    private void endPanelDrag() {
        if (!draggingPanel || toolDocument == null) return;
        draggingPanel = false;
        Element handle = toolDocument.querySelector("#panelDragHandle");
        if (handle != null) handle.setAttribute("class", "top-btn drag-handle");
    }

    private void syncDocumentSelector(Element select) {
        if (select == null || toolDocument == null) return;
        List<Document> documents = debuggableDocuments();
        StringBuilder signature = new StringBuilder();
        for (Document document : documents) {
            signature.append(document.getUuid()).append('\n').append(document.getPath()).append('\n');
        }
        String nextSignature = signature.toString();
        if (!nextSignature.equals(select.getAttribute("data-document-signature"))) {
            select.clearChildren();
            for (Document document : documents) {
                Element option = Element.init(toolDocument.createElement("OPTION"));
                option.setAttribute("value", document.getUuid().toString());
                option.setTextContent(documentLabel(document));
                select.append(option);
            }
            select.setAttribute("data-document-signature", nextSignature);
        }
        select.setValue(targetDocument == null ? "" : targetDocument.getUuid().toString());
    }

    private synchronized void selectDocumentByUuid(String uuid) {
        Document selected = uuid == null || uuid.isBlank() ? null : Document.getByUUID(uuid);
        if (!isDebuggable(selected)) {
            refresh();
            return;
        }
        if (selected == targetDocument) return;
        bindTarget(selected);
        selectedElementUuid = selected.body.uuid;
        collapsedNodes.clear();
        refresh();
    }

    private void resizeInspector(Event event) {
        if (!resizingInspector || !(event instanceof MouseEvent mouseEvent) || toolDocument == null) return;
        Element sidePanel = toolDocument.querySelector(".side-panel");
        Element section = toolDocument.querySelector("#inspectorSection");
        if (sidePanel == null || section == null) return;
        double panelBottom = Position.of(sidePanel).y + Size.of(sidePanel).height();
        double maxHeight = Math.max(120, Size.of(sidePanel).height() - 150);
        double height = Math.max(120, Math.min(maxHeight, panelBottom - mouseEvent.clientY));
        section.setAttribute("style", "height:" + String.format(Locale.ROOT, "%.2fpx", height) + ";");
        DevToolsDom.markDirty(toolDocument);
        event.preventDefault();
    }

    private void updateShellState() {
        if (toolDocument == null) return;
        Element pickButton = toolDocument.querySelector("#pickBtn");
        if (pickButton != null) pickButton.setAttribute("class", pickMode ? "top-btn active" : "top-btn");
        for (Element tab : toolDocument.querySelectorAll(".inspector-tab")) {
            boolean active = inspectorTab.id.equalsIgnoreCase(tab.getAttribute("data-tab"));
            tab.setAttribute("class", active ? "inspector-tab active" : "inspector-tab");
        }
        for (Element pane : toolDocument.querySelectorAll(".inspector-pane")) {
            boolean active = ("pane-" + inspectorTab.id).equals(pane.id);
            pane.setAttribute("class", active ? "inspector-pane active" : "inspector-pane");
        }
    }

    private void afterTargetEdit(Element target, String toast) {
        if (target == null || target.document == null) return;
        syncRuntimeInlineStyleCache(target);
        target.document.markDirty(target, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        showToast(toast);
        refresh();
    }

    private void applyInlineStyles(Element target, LinkedHashMap<String, String> styles) {
        String serialized = InlineStyleDeclaration.serialize(styles);
        if (serialized.isBlank()) target.removeAttribute("style");
        else target.setAttribute("style", serialized);
        syncRuntimeInlineStyleCache(target);
    }

    private LinkedHashMap<String, String> disabledStyleMap(Element target) {
        if (target == null) return new LinkedHashMap<>();
        return disabledStyles.computeIfAbsent(target.uuid, ignored -> new LinkedHashMap<>());
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

    private Document resolvePreferredTarget() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen instanceof ApricityScreen screen
                    && isDebuggable(screen.getLinkedDocument())) return screen.getLinkedDocument();
            if (minecraft != null && minecraft.screen instanceof ApricityContainerScreen screen
                    && isDebuggable(screen.getLinkedDocument())) return screen.getLinkedDocument();
        } catch (RuntimeException | LinkageError ignored) {
        }
        List<Document> documents = new ArrayList<>(Document.getAll());
        for (int index = documents.size() - 1; index >= 0; index--) {
            Document document = documents.get(index);
            if (isDebuggable(document)) return document;
        }
        return null;
    }

    private List<Document> debuggableDocuments() {
        List<Document> documents = new ArrayList<>();
        for (Document document : Document.getAll()) {
            if (isDebuggable(document)) documents.add(document);
        }
        return documents;
    }

    private static String documentLabel(Document document) {
        String uuid = document.getUuid().toString();
        return document.getPath() + " [" + uuid.substring(0, 4) + "]";
    }

    private boolean isDebuggable(Document document) {
        return document != null && document != toolDocument && document.isActive()
                && document.body != null && !PATH.equals(document.getPath())
                && !isInternalCursorOverlay(document);
    }

    private static boolean isInternalCursorOverlay(Document document) {
        if (document == null || document.body == null) return false;
        if (document.body.getClassNames().contains("cursor-overlay-body")) return true;
        return document.querySelector("#baeffect-cursor-layer.cursor-layer") != null;
    }

    private static Element findElement(Document document, UUID uuid) {
        if (document == null || uuid == null) return null;
        if (document.documentElement != null && uuid.equals(document.documentElement.uuid)) return document.documentElement;
        if (document.head != null && uuid.equals(document.head.uuid)) return document.head;
        if (document.body != null && uuid.equals(document.body.uuid)) return document.body;
        for (Element element : document.getElements()) {
            if (element != null && uuid.equals(element.uuid)) return element;
        }
        return null;
    }

    private void revealAncestors(Element element) {
        for (Element current = element; current != null; current = current.parentElement) {
            collapsedNodes.remove(current.uuid);
        }
    }
}
