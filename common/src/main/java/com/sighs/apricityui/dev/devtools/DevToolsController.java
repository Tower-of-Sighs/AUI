package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.task.FrameTaskScheduler;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import com.sighs.apricityui.world.WorldWindow;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;

import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.ui.Tooltip;
import com.sighs.apricityui.ui.ContextMenu;
import com.sighs.apricityui.ui.DialogWindow;
import com.sighs.apricityui.ui.FilePicker;
import com.sighs.apricityui.editor.ore.OreEditor;
import com.sighs.apricityui.dev.resource.ResourceMetaDialog;
import com.sighs.apricityui.loader.ClientLoader;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.sighs.apricityui.style.Style;

public final class DevToolsController {
    public static final String PATH = "devtools/devtools.html";
    private static final String[] BOX_MODEL_REGION_IDS = {
            "inspectMarginTop", "inspectMarginRight", "inspectMarginBottom", "inspectMarginLeft",
            "inspectBorderTop", "inspectBorderRight", "inspectBorderBottom", "inspectBorderLeft",
            "inspectPaddingTop", "inspectPaddingRight", "inspectPaddingBottom", "inspectPaddingLeft",
            "inspectContent"
    };

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

    record RuleStyle(String value, boolean important, boolean overridden, boolean disabled) {
        String displayValue() {
            return value + (important ? " !important" : "");
        }
    }

    private record RuleDeclarationKey(int ruleOrder, String property) {
    }

    private record StylesheetSnapshot(List<CSS.DebugRule> rules,
                                      Map<RuleDeclarationKey, CSS.Declaration> disabled) {
    }

    private final Set<UUID> expandedNodes = new LinkedHashSet<>();
    private final Map<UUID, LinkedHashMap<String, String>> disabledStyles = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashMap<RuleDeclarationKey, CSS.Declaration>> disabledRuleStyles = new LinkedHashMap<>();
    private final DevToolsDomTree tree = new DevToolsDomTree(this);
    private final DevToolsInspector inspector = new DevToolsInspector(this);
    private final DevToolsConsole console = new DevToolsConsole(this);
    private final DevToolsEditHistory editHistory = new DevToolsEditHistory();
    private final DevToolsSaveDialog saveDialog = new DevToolsSaveDialog();
    private final DevToolsConfigDialog configDialog = new DevToolsConfigDialog();
    private final ResourceMetaDialog metaDialog = new ResourceMetaDialog();

    private Document toolDocument;
    private Document targetDocument;
    private Document inspectShellCacheDocument;
    private long inspectShellCacheGeneration = -1L;
    private Element inspectPanelElement;
    private Element inspectHighlightElement;
    private Element inspectHighlightLabelElement;
    private Map<String, Element> inspectBoxRegionElements = Map.of();
    private Document.MutationObserver targetObserver;
    private UUID selectedElementUuid;
    private InspectorTab inspectorTab = InspectorTab.ATTRIBUTES;
    private boolean pickMode;
    private UUID treeHoverElementUuid;
    private boolean consumeInspectMouseUp;
    private boolean draggingPanel;
    private double panelDragOffsetX;
    private boolean resizingInspector;
    private boolean consoleMode;
    private boolean refreshQueued;
    private boolean skipSaveConfirmation;
    private long toastTicket;
    private DialogWindow createElementDialog;
    private Tooltip.Binding consoleTooltipBinding;
    private Element consoleTooltipTarget;
    private String consoleTooltipKey;

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
        boolean wasOpen = isOpen();
        if (!ensureOpen()) return false;
        boolean openedWithRequestedTarget = !wasOpen && targetDocument == document;
        if (targetDocument != document) {
            bindTarget(document);
        } else if (wasOpen) {
            resetTreeExpansion();
        }
        selectedElementUuid = document.body.uuid;
        hideInspectHighlight();
        if (openedWithRequestedTarget) return true;
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
        DevToolsEditHistory.Snapshot before = editSnapshot(element);
        LinkedHashMap<String, String> styles = inlineStyles(element);
        styles.put(normalized, value == null ? "" : value.trim());
        disabledStyleMap(element).remove(normalized);
        applyInlineStyles(element, styles);
        afterTargetEdit(element, before, "Style \"" + normalized + "\" updated");
        return true;
    }

    public synchronized boolean handleInspectMouseMove(Position screenPosition) {
        if (!isOpen()) {
            hideInspectHighlight();
            return false;
        }
        Element treeHover = findElement(targetDocument, treeHoverElementUuid);
        if (treeHover != null) {
            showInspectHighlight(treeHover);
            return false;
        }
        if (!pickMode) {
            hideInspectHighlight();
            return false;
        }
        if (!isDebuggable(targetDocument)) {
            pickMode = false;
            hideInspectHighlight();
            updateShellState();
            return false;
        }
        if (isOverToolPanel(screenPosition)) {
            hideInspectHighlight();
            return false;
        }
        Cursor.applyCssCursor("crosshair");
        Element hit = inspectHit(screenPosition);
        if (hit == null) {
            hideInspectHighlight();
            return false;
        }
        showInspectHighlight(hit);
        return true;
    }

    public synchronized boolean handleInspectMouseDown(Position screenPosition, int button) {
        if (button != 0 || !isOpen() || !pickMode || !isDebuggable(targetDocument)
                || isOverToolPanel(screenPosition)) return false;
        Element hit = inspectHit(screenPosition);
        if (hit != null) {
            selectedElementUuid = hit.uuid;
            revealAncestors(hit);
        }
        consumeInspectMouseUp = true;
        pickMode = false;
        Cursor.resetToDefault();
        if (hit != null) {
            refresh();
            scheduleTreeReveal(hit.uuid);
        } else {
            updateShellState();
        }
        hideInspectHighlight();
        return true;
    }

    public synchronized boolean handleInspectMouseUp(int button) {
        if (button != 0 || !consumeInspectMouseUp) return false;
        consumeInspectMouseUp = false;
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
        return element != null && !expandedNodes.contains(element.uuid);
    }

    boolean isPickMode() {
        return pickMode;
    }

    boolean isConsoleMode() {
        return consoleMode;
    }

    public synchronized void drainExternalLogs() {
        if (!isOpen() || !consoleMode) return;
        console.drainExternalLogs();
    }

    void toggleConsoleMode() {
        consoleMode = !consoleMode;
        if (consoleMode && pickMode) {
            pickMode = false;
            Cursor.resetToDefault();
            hideInspectHighlight();
        }
        if (!consoleMode && toolDocument != null && toolDocument.getFocusedElement() != null) {
            clearToolFocus();
        }
        updateShellState();
        if (consoleMode) {
            console.bind();
            Element input = toolDocument == null ? null : toolDocument.querySelector("#consoleInput");
            if (input != null) input.focus();
        }
        DevToolsDom.markDirty(toolDocument);
    }

    void togglePickModeFromConsole() {
        if (!isDebuggable(targetDocument)) {
            pickMode = false;
            hideInspectHighlight();
            showToast(DevToolsTranslations.translate("devtools.apricityui.select_document_first"));
            updateShellState();
            return;
        }
        pickMode = !pickMode;
        if (!pickMode) {
            hideInspectHighlight();
            Cursor.resetToDefault();
        }
        updateShellState();
    }

    void toggleCollapsed(Element element) {
        if (!DevToolsDomTree.hasInspectableChildren(element)) return;
        if (!expandedNodes.remove(element.uuid)) expandedNodes.add(element.uuid);
        refreshTree();
    }

    void selectFromView(Element element) {
        if (element == null || element.document != targetDocument) return;
        treeHoverElementUuid = null;
        selectedElementUuid = element.uuid;
        revealAncestors(element);
        refresh();
        hideInspectHighlight();
    }

    void showElementContextMenu(Element element, MouseEvent event) {
        if (element == null || event == null || element.document != targetDocument || !element.isConnected()) return;
        selectFromView(element);
        boolean canChangeStructure = canChangeStructure(element);
        List<ContextMenu.Item> items = new ArrayList<>();
        items.add(ContextMenu.Item.header(DevToolsTranslations.translate("devtools.apricityui.element_menu", element.tagName.toLowerCase(Locale.ROOT))));
        items.add(ContextMenu.Item.action(DevToolsTranslations.translate("devtools.apricityui.copy_outer_html"), ContextMenu.Icons.COPY, "Ctrl+C",
                () -> copyElementOuterHtml(element)));
        items.add(ContextMenu.Item.action(DevToolsTranslations.translate("devtools.apricityui.copy_selector"), ContextMenu.Icons.REFERENCE,
                () -> copyElementSelector(element)));
        items.add(ContextMenu.Item.separator());
        items.add(structureItem(DevToolsTranslations.translate("devtools.apricityui.add_child_element"), ContextMenu.Icons.NEW_FILE, canChangeStructure,
                () -> openCreateElementDialog(element)));
        items.add(structureItem(DevToolsTranslations.translate("devtools.apricityui.hide_element"), ContextMenu.Icons.PROPERTIES, canChangeStructure,
                () -> hideElement(element)));
        items.add(structureItem(DevToolsTranslations.translate("devtools.apricityui.duplicate_element"), ContextMenu.Icons.NEW_FILE, canChangeStructure,
                () -> duplicateElement(element)));
        items.add(ContextMenu.Item.separator());
        items.add(structureItem(DevToolsTranslations.translate("devtools.apricityui.delete_element"), ContextMenu.Icons.DELETE, canChangeStructure,
                () -> deleteElement(element)).dangerous());
        ContextMenu.show(toolDocument, new Position(event.clientX, event.clientY), items);
    }

    void hoverFromView(Element element) {
        if (element == null || element.document != targetDocument || !element.isConnected()) return;
        treeHoverElementUuid = element.uuid;
        showInspectHighlight(element);
    }

    private ContextMenu.Item structureItem(String label, String icon, boolean enabled, Runnable action) {
        ContextMenu.Item item = ContextMenu.Item.action(label, icon, action);
        return enabled ? item : item.disabled();
    }

    private void copyElementOuterHtml(Element element) {
        if (!isCurrentTarget(element)) return;
        Operation.setClipboardText(DevToolsHtmlSerializer.serializeElement(element));
        showToast(DevToolsTranslations.translate("devtools.apricityui.outer_html_copied"));
    }

    private void copyElementSelector(Element element) {
        if (!isCurrentTarget(element)) return;
        Operation.setClipboardText(cssSelector(element));
        showToast(DevToolsTranslations.translate("devtools.apricityui.selector_copied"));
    }

    private void hideElement(Element element) {
        if (!canChangeStructure(element)) return;
        applyInlineStyle(element, "display", "none");
    }

    private void openCreateElementDialog(Element parent) {
        if (!canChangeStructure(parent) || toolDocument == null) return;
        closeCreateElementDialog();
        DialogWindow dialog = DialogWindow.open(toolDocument, new DialogWindow.Options(
                DevToolsTranslations.translate("devtools.apricityui.add_child_element"), 360, 0, false,
                "dialog-overlay show", "dialog create-element-dialog", "dialog-header", "dialog-title",
                "dialog-close", "dialog-body", ""
        ), () -> createElementDialog = null);
        createElementDialog = dialog;

        Element content = dialog.content();
        Element label = DevToolsDom.text(toolDocument, "LABEL", "create-element-label",
                DevToolsTranslations.translate("devtools.apricityui.tag_name"));
        label.setAttribute("for", "createElementTag");
        content.append(label);
        Element input = DevToolsDom.input(toolDocument, "create-element-input", "div", "div");
        input.setAttribute("id", "createElementTag");
        input.addEventListener("keydown", event -> {
            if (isCommitKey(event)) createElement(parent, DevToolsDom.value(input), dialog);
        });
        content.append(input);
        content.append(DevToolsDom.text(toolDocument, "DIV", "create-element-hint",
                DevToolsTranslations.translate("devtools.apricityui.add_child_hint")));

        Element footer = DevToolsDom.element(toolDocument, "DIV", "dialog-footer");
        Element cancel = DevToolsDom.text(toolDocument, "BUTTON", "dialog-btn dialog-btn-cancel",
                DevToolsTranslations.translate("devtools.apricityui.cancel"));
        cancel.addEventListener("click", event -> dialog.close());
        footer.append(cancel);
        Element create = DevToolsDom.text(toolDocument, "BUTTON", "dialog-btn dialog-btn-confirm",
                DevToolsTranslations.translate("devtools.apricityui.create"));
        create.addEventListener("click", event -> createElement(parent, DevToolsDom.value(input), dialog));
        footer.append(create);
        dialog.window().append(footer);
        DevToolsDom.markDirty(toolDocument);
    }

    private void createElement(Element parent, String tagName, DialogWindow dialog) {
        if (!canChangeStructure(parent)) return;
        String tag = tagName == null ? "" : tagName.trim().toLowerCase(Locale.ROOT);
        if (!tag.matches("[a-z][a-z0-9-]*")) {
            showToast(DevToolsTranslations.translate("devtools.apricityui.invalid_tag_name"));
            return;
        }
        Element child = Element.init(targetDocument.createElement(tag));
        parent.append(child);
        expandedNodes.add(parent.uuid);
        selectedElementUuid = child.uuid;
        editHistory.record(targetDocument,
                () -> removeElementFromHistory(child),
                () -> appendElementFromHistory(parent, child),
                DevToolsTranslations.translate("devtools.apricityui.element_created", tag));
        if (dialog != null) dialog.close();
        targetDocument.markDirty(targetDocument.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        showToast(DevToolsTranslations.translate("devtools.apricityui.element_created", tag));
        refresh();
    }

    private void duplicateElement(Element element) {
        if (!canChangeStructure(element)) return;
        Element duplicate = element.cloneNode(true);
        if (duplicate == null) return;
        element.after(duplicate);
        selectedElementUuid = duplicate.uuid;
        editHistory.record(targetDocument,
                () -> removeElementFromHistory(duplicate),
                () -> insertAfterFromHistory(element, duplicate),
                DevToolsTranslations.translate("devtools.apricityui.element_duplicated"));
        targetDocument.markDirty(targetDocument.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        showToast(DevToolsTranslations.translate("devtools.apricityui.element_duplicated"));
        refresh();
    }

    private void deleteElement(Element element) {
        if (!canChangeStructure(element)) return;
        Element parent = element.parentElement;
        Node nextSibling = element.getNextSibling();
        if (parent == null) return;
        element.remove();
        selectedElementUuid = parent.uuid;
        editHistory.record(targetDocument,
                () -> insertBeforeFromHistory(parent, element, nextSibling),
                () -> removeElementFromHistory(element),
                DevToolsTranslations.translate("devtools.apricityui.element_deleted"));
        targetDocument.markDirty(targetDocument.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        showToast(DevToolsTranslations.translate("devtools.apricityui.element_deleted"));
        refresh();
    }

    private boolean removeElementFromHistory(Element element) {
        if (element == null || !element.isConnected()) return false;
        element.remove();
        refresh();
        return true;
    }

    private boolean insertAfterFromHistory(Element reference, Element element) {
        if (!isCurrentTarget(reference) || element == null || element.isConnected()) return false;
        reference.after(element);
        refresh();
        return true;
    }

    private boolean appendElementFromHistory(Element parent, Element element) {
        if (!isCurrentTarget(parent) || element == null || element.isConnected()) return false;
        parent.append(element);
        expandedNodes.add(parent.uuid);
        refresh();
        return true;
    }

    private boolean insertBeforeFromHistory(Element parent, Element element, Node nextSibling) {
        if (!isCurrentTarget(parent) || element == null || element.isConnected()) return false;
        if (nextSibling != null && nextSibling.isConnected() && nextSibling.parentNode == parent) {
            parent.insertBefore(element, nextSibling);
        } else {
            parent.append(element);
        }
        refresh();
        return true;
    }

    private boolean canChangeStructure(Element element) {
        return isCurrentTarget(element) && element != targetDocument.documentElement && element.parentElement != null;
    }

    private boolean isCurrentTarget(Element element) {
        return element != null && element.document == targetDocument && element.isConnected();
    }

    private static String cssSelector(Element element) {
        if (element.id != null && !element.id.isBlank()) return "#" + escapeSelectorToken(element.id);
        ArrayList<String> parts = new ArrayList<>();
        for (Element current = element; current != null && current.document != null; current = current.parentElement) {
            String tag = current.tagName.toLowerCase(Locale.ROOT);
            if (current.id != null && !current.id.isBlank()) {
                parts.add("#" + escapeSelectorToken(current.id));
                break;
            }
            StringBuilder part = new StringBuilder(tag);
            for (String className : current.getClassNames()) part.append('.').append(escapeSelectorToken(className));
            if (current.parentElement != null) {
                int index = 1;
                for (Element sibling : current.parentElement.children) {
                    if (sibling == current) break;
                    if (tag.equalsIgnoreCase(sibling.tagName)) index++;
                }
                part.append(":nth-of-type(").append(index).append(')');
            }
            parts.add(part.toString());
            if (current == current.document.documentElement) break;
        }
        java.util.Collections.reverse(parts);
        return String.join(" > ", parts);
    }

    private static String escapeSelectorToken(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "\\\\$0");
    }

    void clearHoverFromView(Element element) {
        if (element == null || !element.uuid.equals(treeHoverElementUuid)) return;
        clearTreeHover();
    }

    void updateAttribute(Element target, String name, String value) {
        if (target == null || name == null || name.isBlank()) return;
        DevToolsEditHistory.Snapshot before = editSnapshot(target);
        String normalized = name.trim();
        if (value == null || value.isEmpty()) target.removeAttribute(normalized);
        else target.setAttribute(normalized, value);
        if ("style".equalsIgnoreCase(normalized)) syncRuntimeInlineStyleCache(target);
        afterTargetEdit(target, before, "Attr \"" + normalized + "\" updated");
    }

    void addAttribute(Element target, String name, String value) {
        if (target == null || name == null || name.isBlank()) return;
        DevToolsEditHistory.Snapshot before = editSnapshot(target);
        String normalized = name.trim();
        target.setAttribute(normalized, value == null ? "" : value);
        if ("style".equalsIgnoreCase(normalized)) syncRuntimeInlineStyleCache(target);
        afterTargetEdit(target, before, "Attr \"" + normalized + "\" added");
    }

    void deleteAttribute(Element target, String name) {
        if (target == null || name == null || name.isBlank()) return;
        DevToolsEditHistory.Snapshot before = editSnapshot(target);
        target.removeAttribute(name);
        if ("style".equalsIgnoreCase(name)) syncRuntimeInlineStyleCache(target);
        afterTargetEdit(target, before, "Attr \"" + name + "\" removed");
    }

    void updateStyle(Element target, String property, String value) {
        applyInlineStyle(target, property, value);
    }

    void renameStyle(Element target, String oldProperty, String newProperty) {
        if (target == null) return;
        String oldKey = InlineStyleDeclaration.normalizeProperty(oldProperty);
        String newKey = InlineStyleDeclaration.normalizeProperty(newProperty);
        if (oldKey.isBlank() || newKey.isBlank() || oldKey.equals(newKey)) return;
        DevToolsEditHistory.Snapshot before = editSnapshot(target);
        LinkedHashMap<String, String> styles = inlineStyles(target);
        String value = styles.remove(oldKey);
        LinkedHashMap<String, String> disabled = disabledStyleMap(target);
        if (value == null) value = disabled.remove(oldKey);
        if (value == null) return;
        styles.put(newKey, value);
        applyInlineStyles(target, styles);
        afterTargetEdit(target, before, "Style renamed to \"" + newKey + "\"");
    }

    void deleteStyle(Element target, String property) {
        if (target == null) return;
        DevToolsEditHistory.Snapshot before = editSnapshot(target);
        String key = InlineStyleDeclaration.normalizeProperty(property);
        LinkedHashMap<String, String> styles = inlineStyles(target);
        styles.remove(key);
        disabledStyleMap(target).remove(key);
        applyInlineStyles(target, styles);
        afterTargetEdit(target, before, "Style \"" + key + "\" removed");
    }

    void toggleStyle(Element target, String property) {
        if (target == null) return;
        DevToolsEditHistory.Snapshot before = editSnapshot(target);
        String key = InlineStyleDeclaration.normalizeProperty(property);
        LinkedHashMap<String, String> styles = inlineStyles(target);
        LinkedHashMap<String, String> disabled = disabledStyleMap(target);
        if (disabled.containsKey(key)) {
            styles.put(key, disabled.remove(key));
        } else if (styles.containsKey(key)) {
            disabled.put(key, styles.remove(key));
        }
        applyInlineStyles(target, styles);
        afterTargetEdit(target, before, "Style \"" + key + "\" toggled");
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

    LinkedHashMap<String, RuleStyle> stylesheetStyles(Selector.DebugStyleBlock block) {
        LinkedHashMap<String, RuleStyle> result = new LinkedHashMap<>();
        if (block == null) return result;
        block.declarations().forEach((property, declaration) -> result.put(property,
                new RuleStyle(declaration.value(), declaration.important(), declaration.overridden(), false)));
        if (targetDocument == null) return result;
        disabledRuleStyleMap(targetDocument).forEach((key, declaration) -> {
            if (key.ruleOrder() == block.ruleOrder()) {
                result.putIfAbsent(key.property(), new RuleStyle(
                        declaration.value(), declaration.important(), false, true));
            }
        });
        return result;
    }

    void updateStylesheetStyle(Element target, int ruleOrder, String property, String value) {
        if (!canEditRule(target, ruleOrder)) return;
        String key = InlineStyleDeclaration.normalizeProperty(property);
        if (key.isBlank()) return;
        StylesheetSnapshot before = stylesheetSnapshot(target.document);
        RuleDeclarationKey declarationKey = new RuleDeclarationKey(ruleOrder, key);
        LinkedHashMap<RuleDeclarationKey, CSS.Declaration> disabled = disabledRuleStyleMap(target.document);
        CSS.Declaration declaration = parseRuleDeclaration(value);
        if (disabled.containsKey(declarationKey)) disabled.put(declarationKey, declaration);
        else findDebugRule(target.document, ruleOrder).properties().put(key, declaration);
        afterStylesheetEdit(target.document, before, "Rule \"" + key + "\" updated");
    }

    void renameStylesheetStyle(Element target, int ruleOrder, String oldProperty, String newProperty) {
        if (!canEditRule(target, ruleOrder)) return;
        String oldKey = InlineStyleDeclaration.normalizeProperty(oldProperty);
        String newKey = InlineStyleDeclaration.normalizeProperty(newProperty);
        if (oldKey.isBlank() || newKey.isBlank() || oldKey.equals(newKey)) return;
        StylesheetSnapshot before = stylesheetSnapshot(target.document);
        CSS.DebugRule rule = findDebugRule(target.document, ruleOrder);
        RuleDeclarationKey oldDeclarationKey = new RuleDeclarationKey(ruleOrder, oldKey);
        RuleDeclarationKey newDeclarationKey = new RuleDeclarationKey(ruleOrder, newKey);
        LinkedHashMap<RuleDeclarationKey, CSS.Declaration> disabled = disabledRuleStyleMap(target.document);
        CSS.Declaration declaration = disabled.remove(oldDeclarationKey);
        if (declaration != null) disabled.put(newDeclarationKey, declaration);
        else {
            declaration = rule.properties().remove(oldKey);
            if (declaration == null) return;
            rule.properties().put(newKey, declaration);
        }
        afterStylesheetEdit(target.document, before, "Rule renamed to \"" + newKey + "\"");
    }

    void deleteStylesheetStyle(Element target, int ruleOrder, String property) {
        if (!canEditRule(target, ruleOrder)) return;
        String key = InlineStyleDeclaration.normalizeProperty(property);
        if (key.isBlank()) return;
        StylesheetSnapshot before = stylesheetSnapshot(target.document);
        CSS.DebugRule rule = findDebugRule(target.document, ruleOrder);
        rule.properties().remove(key);
        disabledRuleStyleMap(target.document).remove(new RuleDeclarationKey(ruleOrder, key));
        afterStylesheetEdit(target.document, before, "Rule \"" + key + "\" removed");
    }

    void toggleStylesheetStyle(Element target, int ruleOrder, String property) {
        if (!canEditRule(target, ruleOrder)) return;
        String key = InlineStyleDeclaration.normalizeProperty(property);
        if (key.isBlank()) return;
        StylesheetSnapshot before = stylesheetSnapshot(target.document);
        CSS.DebugRule rule = findDebugRule(target.document, ruleOrder);
        RuleDeclarationKey declarationKey = new RuleDeclarationKey(ruleOrder, key);
        LinkedHashMap<RuleDeclarationKey, CSS.Declaration> disabled = disabledRuleStyleMap(target.document);
        CSS.Declaration declaration = disabled.remove(declarationKey);
        if (declaration != null) rule.properties().put(key, declaration);
        else {
            declaration = rule.properties().remove(key);
            if (declaration == null) return;
            disabled.put(declarationKey, declaration);
        }
        afterStylesheetEdit(target.document, before, "Rule \"" + key + "\" toggled");
    }

    void addStylesheetStyle(Element target, int ruleOrder, String property, String value) {
        updateStylesheetStyle(target, ruleOrder, property, value);
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
        cacheInspectShellElements();
        toolDocument.setReloadPersistent(true);
        bindTarget(resolvePreferredTarget());
        refresh();
    }

    private void close() {
        disconnectTargetObserver();
        saveDialog.close();
        configDialog.close();
        metaDialog.close();
        closeCreateElementDialog();
        Document closing = toolDocument;
        Tooltip.hide(closing);
        if (consoleTooltipBinding != null) consoleTooltipBinding.close();
        consoleTooltipBinding = null;
        consoleTooltipTarget = null;
        consoleTooltipKey = null;
        clearInspectShellElementCache();
        toolDocument = null;
        targetDocument = null;
        selectedElementUuid = null;
        expandedNodes.clear();
        disabledStyles.clear();
        disabledRuleStyles.clear();
        editHistory.clear();
        pickMode = false;
        consoleMode = false;
        treeHoverElementUuid = null;
        consumeInspectMouseUp = false;
        draggingPanel = false;
        panelDragOffsetX = 0;
        resizingInspector = false;
        refreshQueued = false;
        if (closing != null) closing.remove();
    }

    private void cacheInspectShellElements() {
        if (toolDocument == null) {
            clearInspectShellElementCache();
            return;
        }
        long generation = toolDocument.getRefreshGeneration();
        if (inspectShellCacheDocument == toolDocument && inspectShellCacheGeneration == generation) return;

        inspectShellCacheDocument = toolDocument;
        inspectShellCacheGeneration = generation;
        inspectPanelElement = toolDocument.querySelector(".side-panel");
        inspectHighlightElement = toolDocument.getElementById("inspectHighlight");
        inspectHighlightLabelElement = toolDocument.getElementById("inspectHighlightLabel");
        Map<String, Element> regions = new LinkedHashMap<>();
        for (String id : BOX_MODEL_REGION_IDS) {
            regions.put(id, toolDocument.getElementById(id));
        }
        inspectBoxRegionElements = regions;
    }

    private void clearInspectShellElementCache() {
        inspectShellCacheDocument = null;
        inspectShellCacheGeneration = -1L;
        inspectPanelElement = null;
        inspectHighlightElement = null;
        inspectHighlightLabelElement = null;
        inspectBoxRegionElements = Map.of();
    }

    private void closeCreateElementDialog() {
        DialogWindow dialog = createElementDialog;
        createElementDialog = null;
        if (dialog != null) dialog.close();
    }

    private void bindTarget(Document target) {
        metaDialog.close();
        disconnectTargetObserver();
        pickMode = false;
        treeHoverElementUuid = null;
        hideInspectHighlight();
        targetDocument = isDebuggable(target) ? target : null;
        resetTreeExpansion();
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
        Element saveButton = toolDocument.querySelector("#saveBtn");
        Element reloadDocumentButton = toolDocument.querySelector("#reloadDocumentBtn");
        Element metaButton = toolDocument.querySelector("#metaButton");
        Element consoleButton = toolDocument.querySelector(".console-btn");
        Element oreEditorButton = toolDocument.querySelector("#oreEditorButton");
        Element settingsButton = toolDocument.querySelector("#settingsButton");
        Element dragHandle = toolDocument.querySelector("#panelDragHandle");
        Element closeDevToolsButton = toolDocument.querySelector("#closeDevToolsBtn");
        Element closeDocumentButton = toolDocument.querySelector("#closeDocumentBtn");
        Element documentSelect = toolDocument.querySelector("#documentSelect");
        localizeAccessibility();
        bindOnce(pickButton, event -> {
            if (!isDebuggable(targetDocument)) {
                pickMode = false;
                hideInspectHighlight();
                updateShellState();
                showToast(DevToolsTranslations.translate("devtools.apricityui.select_document_first"));
                return;
            }
            pickMode = !pickMode;
            if (!pickMode) {
                hideInspectHighlight();
                Cursor.resetToDefault();
            }
            updateShellState();
            showToast(DevToolsTranslations.translate(pickMode
                    ? "devtools.apricityui.inspect_mode_on" : "devtools.apricityui.inspect_mode_off"));
        });
        bindOnce(saveButton, event -> requestSave());
        bindOnce(reloadDocumentButton, event -> reloadTargetDocument());
        bindOnce(metaButton, event -> openMetaEditor());
        bindOnce(consoleButton, event -> toggleConsoleMode());
        bindOnce(oreEditorButton, event -> openOreEditorFilePicker());
        bindOnce(settingsButton, event -> configDialog.open(toolDocument));
        bindOnce(closeDevToolsButton, event -> close());
        bindOnce(closeDocumentButton, event -> closeTargetDocument());
        bindTooltipOnce(pickButton, "tooltip.apricityui.devtools.inspect");
        bindTooltipOnce(saveButton, "tooltip.apricityui.devtools.save");
        bindTooltipOnce(reloadDocumentButton, "tooltip.apricityui.devtools.reload_document");
        bindTooltipOnce(metaButton, "tooltip.apricityui.devtools.meta");
        bindConsoleTooltip(consoleButton);
        bindTooltipOnce(oreEditorButton, "tooltip.apricityui.ore_editor.open");
        bindTooltipOnce(settingsButton, "tooltip.apricityui.devtools.settings");
        bindTooltipOnce(closeDevToolsButton, "tooltip.apricityui.devtools.close");
        bindTooltipOnce(closeDocumentButton, "tooltip.apricityui.devtools.close_document");
        bindPanelDrag(dragHandle);
        bindTooltipOnce(dragHandle, "tooltip.apricityui.devtools.move");
        bindDocumentSelector(documentSelect);
        bindHistoryShortcuts();
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
        console.bind();
    }

    private void openOreEditorFilePicker() {
        if (OreEditor.isOpen() && OreEditor.getSession().dirty()) {
            showToast(DevToolsTranslations.translate("devtools.apricityui.ore_editor.unsaved"));
            return;
        }
        FilePicker.pick(FilePicker.Options.htmlTranslation(
                "devtools.apricityui.ore_editor.select_html", false
        )).thenAccept(selection -> selection.ifPresent(file -> {
            if (!OreEditor.openHtml(file.localPath())) {
                showToast(DevToolsTranslations.translate("devtools.apricityui.ore_editor.open_failed"));
            }
        }));
    }

    private void bindOnce(Element element, java.util.function.Consumer<Event> listener) {
        if (element == null || "1".equals(element.getAttribute("data-java-bound"))) return;
        element.setAttribute("data-java-bound", "1");
        element.addEventListener("click", listener);
    }

    private void localizeAccessibility() {
        Element logo = toolDocument.querySelector(".logo");
        if (logo != null) logo.setTextContent(DevToolsTranslations.translate("devtools.apricityui.title"));
        setAttribute("#panelDragHandle", "aria-label", "devtools.apricityui.move");
        setAttribute("#saveBtn", "aria-label", "devtools.apricityui.save_current_html");
        setAttribute("#reloadDocumentBtn", "aria-label", "devtools.apricityui.reload_document");
        setAttribute("#metaButton", "aria-label", "devtools.apricityui.edit_meta");
        setAttribute("#pickBtn", "aria-label", "devtools.apricityui.inspect_elements");
        setAttribute(".console-btn", "aria-label",
                consoleMode ? "devtools.apricityui.inspect_elements" : "devtools.apricityui.console");
        setAttribute("#oreEditorButton", "aria-label", "tooltip.apricityui.ore_editor.open");
        setAttribute("#settingsButton", "aria-label", "tooltip.apricityui.devtools.settings");
        setAttribute("#closeDevToolsBtn", "aria-label", "tooltip.apricityui.devtools.close");
        setAttribute("#closeDocumentBtn", "aria-label", "tooltip.apricityui.devtools.close_document");
    }

    private void setAttribute(String selector, String attribute, String key) {
        Element element = toolDocument.querySelector(selector);
        if (element != null) element.setAttribute(attribute, DevToolsTranslations.translate(key));
    }

    private void bindTooltipOnce(Element element, String translationKey) {
        if (element == null || "1".equals(element.getAttribute("data-tooltip-bound"))) return;
        element.setAttribute("data-tooltip-bound", "1");
        Tooltip.bindTranslation(element, translationKey);
    }

    private void bindConsoleTooltip(Element element) {
        if (element == null) return;
        String key = consoleMode
                ? "tooltip.apricityui.devtools.inspect"
                : "tooltip.apricityui.devtools.console";
        if (element == consoleTooltipTarget && key.equals(consoleTooltipKey)) return;
        if (consoleTooltipBinding != null) consoleTooltipBinding.close();
        element.setAttribute("data-tooltip-key", key);
        element.setAttribute("data-tooltip-bound", "1");
        consoleTooltipBinding = Tooltip.bindTranslation(element, key);
        consoleTooltipTarget = element;
        consoleTooltipKey = key;
    }

    private void bindDocumentSelector(Element select) {
        if (select == null || "1".equals(select.getAttribute("data-document-bound"))) return;
        select.setAttribute("data-document-bound", "1");
        select.addEventListener("click", event -> syncDocumentSelector(select));
        select.addEventListener("change", event -> selectDocumentByUuid(select.getValue()));
    }

    private synchronized void closeTargetDocument() {
        Document closing = targetDocument;
        if (!isDebuggable(closing)) {
            refresh();
            return;
        }
        disconnectTargetObserver();
        saveDialog.close();
        metaDialog.close();
        closeCreateElementDialog();
        Tooltip.hide(toolDocument);
        Cursor.resetToDefault();
        consumeInspectMouseUp = false;
        closing.remove();
        disabledStyles.clear();
        disabledRuleStyles.clear();
        editHistory.clear();
        bindTarget(resolvePreferredTarget());
        selectedElementUuid = targetDocument == null ? null : targetDocument.body.uuid;
        refresh();
    }

    private synchronized void reloadTargetDocument() {
        Document document = targetDocument;
        if (!isDebuggable(document)) {
            showToast(DevToolsTranslations.translate("devtools.apricityui.select_document_first"));
            return;
        }

        disconnectTargetObserver();
        saveDialog.close();
        metaDialog.close();
        closeCreateElementDialog();
        Tooltip.hide(toolDocument);
        Cursor.resetToDefault();
        pickMode = false;
        treeHoverElementUuid = null;
        consumeInspectMouseUp = false;
        hideInspectHighlight();
        expandedNodes.clear();
        disabledStyles.clear();
        disabledRuleStyles.clear();
        editHistory.clear();
        selectedElementUuid = null;

        // Refresh the source template first so a saved or externally edited HTML file is used.
        HTML.reload(document.getPath());
        document.refresh();

        if (!isOpen()) return;
        bindTarget(document);
        selectedElementUuid = targetDocument == null || targetDocument.body == null
                ? null : targetDocument.body.uuid;
        refresh();
        showToast(DevToolsTranslations.translate(
                "devtools.apricityui.document_reloaded", document.getPath()));
    }

    private void bindHistoryShortcuts() {
        if (toolDocument == null || toolDocument.body == null
                || "1".equals(toolDocument.body.getAttribute("data-history-bound"))) return;
        toolDocument.body.setAttribute("data-history-bound", "1");
        toolDocument.body.addEventListener("keydown", event -> {
            if (!(event instanceof KeyEvent keyEvent) || (!keyEvent.controlKey && !keyEvent.metaKey)) return;
            if (toolDocument != null && toolDocument.getFocusedElement() instanceof AbstractText) return;
            boolean handled = false;
            if ("KeyZ".equals(keyEvent.code)) {
                handled = keyEvent.shiftKey ? redoEdit() : undoEdit();
            } else if ("KeyY".equals(keyEvent.code)) {
                handled = redoEdit();
            }
            if (handled) {
                event.preventDefault();
                event.stopPropagation();
            }
        });
    }

    private void requestSave() {
        Document document = targetDocument;
        Tooltip.hide(toolDocument);
        DevToolsDocumentStore.Resolution resolution = DevToolsDocumentStore.resolve(document);
        if (!resolution.writable()) {
            showToast(resolution.message());
            return;
        }
        if (skipSaveConfirmation) {
            saveDocument(document, resolution.target(), false);
            return;
        }
        saveDialog.open(toolDocument, resolution.target().relativePath(), options -> {
            if (options.skipConfirmation()) skipSaveConfirmation = true;
            saveDocument(document, resolution.target(), options.saveDomTree());
        });
    }

    private void openMetaEditor() {
        Document document = targetDocument;
        if (!isDebuggable(document)) {
            showToast(DevToolsTranslations.translate("devtools.apricityui.select_document_first"));
            return;
        }
        DevToolsDocumentStore.Resolution resolution = DevToolsDocumentStore.resolve(document);
        if (!resolution.writable()) {
            showToast(resolution.message());
            return;
        }
        Tooltip.hide(toolDocument);
        metaDialog.open(toolDocument, document.getPath(), resolution.target().file(), ClientLoader::reload,
                document.getViewport().zoom(), zoom -> FrameTaskScheduler.scheduleAfterFrames(3, deadlineNs -> {
                    if (document.isActive()) document.setViewportZoom(zoom);
                    return true;
                }));
    }

    private void saveDocument(Document document, DevToolsDocumentStore.SaveTarget target,
                              boolean saveDomTree) {
        if (document == null || target == null || !document.isActive()) {
            showToast(DevToolsTranslations.translate("devtools.apricityui.document_unavailable"));
            return;
        }
        String original = DevToolsDocumentStore.read(target);
        if (original == null) {
            showToast(DevToolsTranslations.translate("devtools.apricityui.source_read_failed"));
            return;
        }
        DevToolsCssSerializer.Result prepared = DevToolsCssSerializer.prepare(
                document, original, target, ClientLoader.listFinalStaticResources(),
                AuiServices.client().isProduction(), saveDomTree);
        if (!prepared.success()) {
            showToast(prepared.message());
            return;
        }
        if (prepared.edits().isEmpty()) {
            showToast(DevToolsTranslations.translate("devtools.apricityui.no_css_changes"));
            return;
        }
        for (DevToolsCssSerializer.Edit edit : prepared.edits()) {
            DevToolsDocumentStore.SaveResult result = DevToolsDocumentStore.save(
                    edit.target(), edit.content());
            if (!result.success()) {
                showToast(DevToolsTranslations.translate(
                        "devtools.apricityui.source_save_failed", edit.target().relativePath()));
                return;
            }
        }
        showToast(DevToolsTranslations.translate("devtools.apricityui.saved", target.relativePath()));
    }

    boolean undoEdit() {
        return finishHistoryAction(editHistory.undo(targetDocument), DevToolsTranslations.translate("devtools.apricityui.undo"));
    }

    boolean redoEdit() {
        return finishHistoryAction(editHistory.redo(targetDocument), DevToolsTranslations.translate("devtools.apricityui.redo"));
    }

    private boolean finishHistoryAction(DevToolsEditHistory.Applied applied, String action) {
        if (applied == null || targetDocument == null) return false;
        showToast(action + " \u00b7 " + applied.description());
        refresh();
        return true;
    }

    private boolean restoreElementSnapshot(Document document, UUID elementUuid,
                                           DevToolsEditHistory.Snapshot snapshot) {
        if (document == null || snapshot == null || !document.isActive()) return false;
        Element target = findElement(document, elementUuid);
        if (target == null) return false;
        for (String name : new ArrayList<>(target.getAttributes().keySet())) target.removeAttribute(name);
        for (Map.Entry<String, String> attribute : snapshot.attributes().entrySet()) {
            target.setAttribute(attribute.getKey(), attribute.getValue());
        }
        LinkedHashMap<String, String> disabled = disabledStyleMap(target);
        disabled.clear();
        disabled.putAll(snapshot.disabledStyles());
        syncRuntimeInlineStyleCache(target);
        target.document.markDirty(target, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        selectedElementUuid = target.uuid;
        return true;
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
        Element consoleButton = toolDocument.querySelector(".console-btn");
        if (consoleButton != null) {
            consoleButton.setAttribute("class", consoleMode
                    ? "top-btn console-btn mode-console" : "top-btn console-btn");
            consoleButton.setAttribute("aria-pressed", Boolean.toString(consoleMode));
            consoleButton.setAttribute("aria-label", DevToolsTranslations.translate(consoleMode
                    ? "devtools.apricityui.inspect_elements" : "devtools.apricityui.console"));
            bindConsoleTooltip(consoleButton);
        }
        setPanelVisibility(".document-selector-bar", !consoleMode);
        setPanelVisibility("#domSection", !consoleMode);
        setPanelVisibility("#inspectorSection", !consoleMode);
        setPanelVisibility("#consoleContent", consoleMode);
        Element pickButton = toolDocument.querySelector("#pickBtn");
        if (pickButton != null) pickButton.setAttribute("class", pickMode ? "top-btn active" : "top-btn");
        Element saveButton = toolDocument.querySelector("#saveBtn");
        if (saveButton != null) {
            DevToolsDocumentStore.Resolution resolution = DevToolsDocumentStore.resolve(targetDocument);
            if (resolution.writable()) {
                saveButton.removeAttribute("disabled");
                saveButton.setAttribute("aria-disabled", "false");
            } else {
                saveButton.setAttribute("disabled", "disabled");
                saveButton.setAttribute("aria-disabled", "true");
            }
        }
        Element metaButton = toolDocument.querySelector("#metaButton");
        if (metaButton != null) {
            DevToolsDocumentStore.Resolution resolution = DevToolsDocumentStore.resolve(targetDocument);
            if (resolution.writable()) {
                metaButton.removeAttribute("disabled");
                metaButton.setAttribute("aria-disabled", "false");
            } else {
                metaButton.setAttribute("disabled", "disabled");
                metaButton.setAttribute("aria-disabled", "true");
            }
        }
        Element reloadDocumentButton = toolDocument.querySelector("#reloadDocumentBtn");
        if (reloadDocumentButton != null) {
            if (isDebuggable(targetDocument)) {
                reloadDocumentButton.removeAttribute("disabled");
                reloadDocumentButton.setAttribute("aria-disabled", "false");
            } else {
                reloadDocumentButton.setAttribute("disabled", "disabled");
                reloadDocumentButton.setAttribute("aria-disabled", "true");
            }
        }
        Element closeDocumentButton = toolDocument.querySelector("#closeDocumentBtn");
        if (closeDocumentButton != null) {
            if (isDebuggable(targetDocument)) {
                closeDocumentButton.removeAttribute("disabled");
                closeDocumentButton.setAttribute("aria-disabled", "false");
            } else {
                closeDocumentButton.setAttribute("disabled", "disabled");
                closeDocumentButton.setAttribute("aria-disabled", "true");
            }
        }
        for (Element tab : toolDocument.querySelectorAll(".inspector-tab")) {
            boolean active = inspectorTab.id.equalsIgnoreCase(tab.getAttribute("data-tab"));
            tab.setAttribute("class", active ? "inspector-tab active" : "inspector-tab");
        }
        for (Element pane : toolDocument.querySelectorAll(".inspector-pane")) {
            boolean active = ("pane-" + inspectorTab.id).equals(pane.id);
            pane.setAttribute("class", active ? "inspector-pane active" : "inspector-pane");
        }
    }

    private void setPanelVisibility(String selector, boolean visible) {
        Element element = toolDocument.querySelector(selector);
        if (element == null) return;
        String current = element.getAttribute("class");
        String[] tokens = current == null ? new String[0] : current.trim().split("\\s+");
        LinkedHashSet<String> next = new LinkedHashSet<>();
        for (String token : tokens) {
            if (!token.isBlank() && !"hidden".equals(token)) next.add(token);
        }
        if (!visible) next.add("hidden");
        element.setAttribute("class", String.join(" ", next));
    }

    private Element inspectHit(Position screenPosition) {
        if (screenPosition == null || !isDebuggable(targetDocument)) return null;
        if (targetDocument.inWorld) {
            WorldWindow worldWindow = WorldWindow.findByDocument(targetDocument);
            Position documentPosition = worldWindow == null
                    ? null : worldWindow.getDocumentPositionAtScreen(screenPosition);
            return documentPosition == null ? null : targetDocument.hitTest(documentPosition);
        }
        return targetDocument.hitTest(targetDocument.screenToDocumentPosition(screenPosition));
    }

    private boolean isOverToolPanel(Position screenPosition) {
        if (screenPosition == null || toolDocument == null) return false;
        cacheInspectShellElements();
        Element panel = inspectPanelElement;
        if (panel == null) return false;
        Position local = toolDocument.screenToDocumentPosition(screenPosition);
        Element.DOMRect rect = panel.getBoundingClientRect();
        return local.x >= rect.left && local.x <= rect.right
                && local.y >= rect.top && local.y <= rect.bottom;
    }

    private void showInspectHighlight(Element element) {
        if (element == null || toolDocument == null || targetDocument == null) {
            hideInspectHighlight();
            return;
        }
        cacheInspectShellElements();
        Element highlight = inspectHighlightElement;
        Element label = inspectHighlightLabelElement;
        if (highlight == null || label == null) return;

        Element.DOMRect rect = element.getBoundingClientRect();
        Box box = Box.of(element);
        double marginLeft = Math.max(0, box.getMarginLeft());
        double marginTop = Math.max(0, box.getMarginTop());
        double marginRight = Math.max(0, box.getMarginRight());
        double marginBottom = Math.max(0, box.getMarginBottom());
        double borderLeft = Math.max(0, box.getBorderLeft());
        double borderTop = Math.max(0, box.getBorderTop());
        double borderRight = Math.max(0, box.getBorderRight());
        double borderBottom = Math.max(0, box.getBorderBottom());
        double paddingLeft = Math.max(0, box.getPaddingLeft());
        double paddingTop = Math.max(0, box.getPaddingTop());
        double paddingRight = Math.max(0, box.getPaddingRight());
        double paddingBottom = Math.max(0, box.getPaddingBottom());

        setBoxModelBands("inspectMargin", rect.x - marginLeft, rect.y - marginTop,
                rect.width + marginLeft + marginRight, rect.height + marginTop + marginBottom,
                marginTop, marginRight, marginBottom, marginLeft);
        setBoxModelBands("inspectBorder", rect.x, rect.y, rect.width, rect.height,
                borderTop, borderRight, borderBottom, borderLeft);
        double paddingBoxX = rect.x + borderLeft;
        double paddingBoxY = rect.y + borderTop;
        double paddingBoxWidth = Math.max(0, rect.width - borderLeft - borderRight);
        double paddingBoxHeight = Math.max(0, rect.height - borderTop - borderBottom);
        setBoxModelBands("inspectPadding", paddingBoxX, paddingBoxY, paddingBoxWidth, paddingBoxHeight,
                paddingTop, paddingRight, paddingBottom, paddingLeft);
        setBoxModelRegion("inspectContent", paddingBoxX + paddingLeft, paddingBoxY + paddingTop,
                Math.max(0, paddingBoxWidth - paddingLeft - paddingRight),
                Math.max(0, paddingBoxHeight - paddingTop - paddingBottom));

        Position outerScreen = projectTargetPosition(
                new Position(rect.x - marginLeft, rect.y - marginTop));
        if (outerScreen == null) {
            hideInspectHighlight();
            return;
        }
        Position outerLocal = toolDocument.screenToDocumentPosition(outerScreen);
        double labelTop = outerLocal.y < 20 ? outerLocal.y : outerLocal.y - 20;
        String labelStyle = String.format(Locale.ROOT, "left:%.2fpx;top:%.2fpx;", outerLocal.x, labelTop);
        String labelText = inspectLabel(element, rect);
        if (!"inspect-highlight show".equals(highlight.getAttribute("class"))) {
            highlight.setAttribute("class", "inspect-highlight show");
        }
        if (!labelStyle.equals(label.getAttribute("style"))) label.setAttribute("style", labelStyle);
        if (!labelText.equals(label.getTextContent())) label.setTextContent(labelText);
    }

    private void setBoxModelBands(String prefix, double x, double y, double width, double height,
                                  double top, double right, double bottom, double left) {
        double safeWidth = Math.max(0, width);
        double safeHeight = Math.max(0, height);
        double safeTop = Math.min(Math.max(0, top), safeHeight);
        double safeBottom = Math.min(Math.max(0, bottom), Math.max(0, safeHeight - safeTop));
        double middleHeight = Math.max(0, safeHeight - safeTop - safeBottom);
        double safeLeft = Math.min(Math.max(0, left), safeWidth);
        double safeRight = Math.min(Math.max(0, right), Math.max(0, safeWidth - safeLeft));
        setBoxModelRegion(prefix + "Top", x, y, safeWidth, safeTop);
        setBoxModelRegion(prefix + "Right", x + safeWidth - safeRight, y + safeTop,
                safeRight, middleHeight);
        setBoxModelRegion(prefix + "Bottom", x, y + safeHeight - safeBottom, safeWidth, safeBottom);
        setBoxModelRegion(prefix + "Left", x, y + safeTop, safeLeft, middleHeight);
    }

    private void setBoxModelRegion(String id, double x, double y, double width, double height) {
        cacheInspectShellElements();
        Element region = inspectBoxRegionElements.get(id);
        if (region == null) return;
        String style;
        if (width <= 0 || height <= 0) {
            style = "left:0px;top:0px;width:0px;height:0px;";
        } else {
            WorldWindow worldWindow = targetDocument != null && targetDocument.inWorld
                    ? WorldWindow.findByDocument(targetDocument) : null;
            WorldWindow.ScreenRect projected = worldWindow == null
                    ? null : worldWindow.projectDocumentRect(x, y, width, height);
            if (worldWindow != null && projected == null) {
                style = "left:0px;top:0px;width:0px;height:0px;";
                if (!style.equals(region.getAttribute("style"))) region.setAttribute("style", style);
                return;
            }
            Position screen = projected == null
                    ? projectTargetPosition(new Position(x, y)) : new Position(projected.x(), projected.y());
            if (screen == null) {
                style = "left:0px;top:0px;width:0px;height:0px;";
                if (!style.equals(region.getAttribute("style"))) region.setAttribute("style", style);
                return;
            }
            Position local = toolDocument.screenToDocumentPosition(screen);
            double screenWidth = projected == null ? width * targetDocument.getViewportScaleX() : projected.width();
            double screenHeight = projected == null ? height * targetDocument.getViewportScaleY() : projected.height();
            style = String.format(Locale.ROOT, "left:%.2fpx;top:%.2fpx;width:%.2fpx;height:%.2fpx;",
                    local.x, local.y, screenWidth / toolDocument.getViewportScaleX(),
                    screenHeight / toolDocument.getViewportScaleY());
        }
        if (!style.equals(region.getAttribute("style"))) region.setAttribute("style", style);
    }

    private Position projectTargetPosition(Position documentPosition) {
        if (targetDocument == null || documentPosition == null) return null;
        if (targetDocument.inWorld) {
            WorldWindow worldWindow = WorldWindow.findByDocument(targetDocument);
            return worldWindow == null ? null : worldWindow.projectDocumentPosition(documentPosition);
        }
        return targetDocument.documentToScreenPosition(documentPosition);
    }

    private void hideInspectHighlight() {
        if (toolDocument == null) return;
        cacheInspectShellElements();
        Element highlight = inspectHighlightElement;
        if (highlight != null && !"inspect-highlight".equals(highlight.getAttribute("class"))) {
            highlight.setAttribute("class", "inspect-highlight");
        }
        for (String id : BOX_MODEL_REGION_IDS) {
            Element region = inspectBoxRegionElements.get(id);
            if (region != null) {
                String hiddenStyle = "left:0px;top:0px;width:0px;height:0px;";
                if (!hiddenStyle.equals(region.getAttribute("style"))) region.setAttribute("style", hiddenStyle);
            }
        }
        Element label = inspectHighlightLabelElement;
        if (label != null) {
            String hiddenStyle = "left:-10000px;top:-10000px;";
            if (!hiddenStyle.equals(label.getAttribute("style"))) label.setAttribute("style", hiddenStyle);
        }
    }

    private void clearTreeHover() {
        treeHoverElementUuid = null;
        hideInspectHighlight();
    }

    private static String inspectLabel(Element element, Element.DOMRect rect) {
        StringBuilder label = new StringBuilder(element.tagName.toLowerCase(Locale.ROOT));
        if (element.id != null && !element.id.isBlank()) label.append('#').append(element.id);
        for (String className : element.getClassNames()) label.append('.').append(className);
        label.append(' ').append(Math.round(rect.width)).append(" x ").append(Math.round(rect.height));
        return label.toString();
    }

    private DevToolsEditHistory.Snapshot editSnapshot(Element target) {
        return editHistory.snapshot(target, target == null ? Map.of() : disabledStyleMap(target));
    }

    private void afterTargetEdit(Element target, DevToolsEditHistory.Snapshot before, String toast) {
        if (target == null || target.document == null) return;
        syncRuntimeInlineStyleCache(target);
        DevToolsEditHistory.Snapshot after = editSnapshot(target);
        if (!before.equals(after)) {
            Document document = target.document;
            UUID elementUuid = target.uuid;
            editHistory.record(document,
                    () -> restoreElementSnapshot(document, elementUuid, before),
                    () -> restoreElementSnapshot(document, elementUuid, after), toast);
        }
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

    private boolean canEditRule(Element target, int ruleOrder) {
        return target != null && target.document != null && target.document == targetDocument
                && findDebugRule(target.document, ruleOrder) != null;
    }

    private static CSS.DebugRule findDebugRule(Document document, int ruleOrder) {
        if (document == null) return null;
        for (CSS.DebugRule rule : document.CSSDebugRules) {
            if (rule != null && rule.order() == ruleOrder) return rule;
        }
        return null;
    }

    private LinkedHashMap<RuleDeclarationKey, CSS.Declaration> disabledRuleStyleMap(Document document) {
        if (document == null) return new LinkedHashMap<>();
        return disabledRuleStyles.computeIfAbsent(document.getUuid(), ignored -> new LinkedHashMap<>());
    }

    private StylesheetSnapshot stylesheetSnapshot(Document document) {
        ArrayList<CSS.DebugRule> rules = new ArrayList<>();
        if (document != null) {
            for (CSS.DebugRule rule : document.CSSDebugRules) rules.add(copyDebugRule(rule));
        }
        return new StylesheetSnapshot(List.copyOf(rules),
                Map.copyOf(document == null ? Map.of() : disabledRuleStyleMap(document)));
    }

    private static CSS.DebugRule copyDebugRule(CSS.DebugRule rule) {
        return new CSS.DebugRule(rule.selector(), rule.properties(), rule.sourcePath(), rule.order());
    }

    private void afterStylesheetEdit(Document document, StylesheetSnapshot before, String toast) {
        if (document == null || before == null) return;
        rebuildStylesheet(document);
        StylesheetSnapshot after = stylesheetSnapshot(document);
        if (!before.equals(after)) {
            editHistory.record(document,
                    () -> restoreStylesheetSnapshot(document, before),
                    () -> restoreStylesheetSnapshot(document, after), toast);
        }
        showToast(toast);
        refresh();
    }

    private boolean restoreStylesheetSnapshot(Document document, StylesheetSnapshot snapshot) {
        if (document == null || snapshot == null || !document.isActive()) return false;
        document.CSSDebugRules.clear();
        for (CSS.DebugRule rule : snapshot.rules()) document.CSSDebugRules.add(copyDebugRule(rule));
        LinkedHashMap<RuleDeclarationKey, CSS.Declaration> disabled = disabledRuleStyleMap(document);
        disabled.clear();
        disabled.putAll(snapshot.disabled());
        rebuildStylesheet(document);
        return true;
    }

    private static void rebuildStylesheet(Document document) {
        CSS.rebuildCacheFromDebugRules(document.CSSDebugRules, document.CSSCache);
        document.rebuildSelectorIndex();
        document.reapplyStylesFromCache();
    }

    private static CSS.Declaration parseRuleDeclaration(String raw) {
        String value = raw == null ? "" : raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        boolean important = lower.endsWith("!important");
        if (important) value = value.substring(0, value.length() - "!important".length()).trim();
        return new CSS.Declaration(value, important);
    }

    private static void syncRuntimeInlineStyleCache(Element target) {
        if (target == null) return;
        String raw = target.getAttribute("style");
        if (target.getRuntimeCache("bound-base-inline-style") != null) {
            target.putRuntimeCache("bound-base-inline-style", raw == null ? "" : raw);
        }
        if (target.getRuntimeCache("bound-last-inline-style") != null) {
            target.putRuntimeCache("bound-last-inline-style", raw == null ? "" : raw);
        }
    }

    private Document resolvePreferredTarget() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen instanceof AuiLinkedScreen screen
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
        for (Element current = element == null ? null : element.parentElement;
             current != null;
             current = current.parentElement) {
            expandedNodes.add(current.uuid);
        }
    }

    private void resetTreeExpansion() {
        expandedNodes.clear();
        if (targetDocument == null) return;
        if (targetDocument.documentElement != null) expandedNodes.add(targetDocument.documentElement.uuid);
        if (targetDocument.body != null) expandedNodes.add(targetDocument.body.uuid);
    }

    private void scheduleTreeReveal(UUID elementUuid) {
        if (elementUuid == null) return;
        FrameTaskScheduler.scheduleAfterFrames(1, deadlineNs -> {
            synchronized (DevToolsController.this) {
                if (!isOpen() || !elementUuid.equals(selectedElementUuid)) return true;
                revealTreeRow(elementUuid);
            }
            return true;
        });
    }

    private void revealTreeRow(UUID elementUuid) {
        Element domTree = toolDocument == null ? null : toolDocument.querySelector("#domTree");
        Element row = domTree == null ? null : domTree.querySelector(".dom-node[data-node-id=\""
                + elementUuid + "\"]");
        if (domTree == null || row == null) return;
        Element.DOMRect treeRect = domTree.getBoundingClientRect();
        Element.DOMRect rowRect = row.getBoundingClientRect();
        double rowCenter = rowRect.top + rowRect.height / 2;
        double viewportCenter = treeRect.top + treeRect.height / 2;
        domTree.setScrollTop(domTree.getScrollTop() + rowCenter - viewportCenter);
    }

    private void refreshTree() {
        if (!isOpen()) return;
        Element domTree = toolDocument.querySelector("#domTree");
        Element nodeCount = toolDocument.querySelector("#nodeCount");
        if (domTree == null || nodeCount == null) return;
        tree.render(domTree, nodeCount, targetDocument, selectedElement());
        toolDocument.markDirty(domTree, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
    }
}
