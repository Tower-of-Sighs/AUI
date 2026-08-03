package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.ui.Tooltip;
import com.sighs.apricityui.ui.ToastManager;
import com.sighs.apricityui.ui.UiTranslations;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.editor.ore.canvas.OreCanvasHitTester;
import com.sighs.apricityui.editor.ore.canvas.OreCanvasRenderer;
import com.sighs.apricityui.editor.ore.canvas.OreFlexInsertionResolver;
import com.sighs.apricityui.editor.ore.drag.OreDragController;
import com.sighs.apricityui.editor.ore.model.OreCanvasNode;
import com.sighs.apricityui.editor.ore.model.OreAbsoluteConstraints;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.editor.ore.palette.OreComponentDefinition;
import com.sighs.apricityui.editor.ore.palette.OreComponentRegistry;
import com.sighs.apricityui.editor.ore.persistence.OreEditorDocumentStore;
import com.sighs.apricityui.editor.ore.persistence.OreEditorHtmlExporter;
import com.sighs.apricityui.editor.ore.persistence.OreEditorHtmlImporter;
import com.sighs.apricityui.editor.ore.persistence.OreEditorProjectCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.lwjgl.glfw.GLFW;
import com.sighs.apricityui.event.Event;

/** Owns only the editor document and shell interactions. Canvas behavior is added separately. */
final class OreEditorController {
    static final OreEditorController INSTANCE = new OreEditorController();
    static final String PATH = "editor/ore/ore-editor.html";
    private static final double MIN_WIDTH = 360;
    private static final double MAX_WIDTH = 560;
    private static final List<ThemeToken> THEME_TOKENS = List.of(
            new ThemeToken("--ore-ink", "#f4f5f7", "ink"),
            new ThemeToken("--ore-ink-muted", "#b6bac1", "ink_muted"),
            new ThemeToken("--ore-ink-dark", "#191a1c", "ink_dark"),
            new ThemeToken("--ore-canvas", "#202124", "canvas"),
            new ThemeToken("--ore-surface", "#48494a", "surface"),
            new ThemeToken("--ore-surface-deep", "#313233", "surface_deep"),
            new ThemeToken("--ore-surface-soft", "#d0d1d4", "surface_soft"),
            new ThemeToken("--ore-edge", "#1e1e1f", "edge"),
            new ThemeToken("--ore-edge-light", "#77797c", "edge_light"),
            new ThemeToken("--ore-green", "#3c8527", "green"),
            new ThemeToken("--ore-green-hover", "#2a641c", "green_hover"),
            new ThemeToken("--ore-green-shadow", "#1d4d13", "green_shadow"),
            new ThemeToken("--ore-purple", "#7345e5", "purple"),
            new ThemeToken("--ore-purple-hover", "#5d2cc6", "purple_hover"),
            new ThemeToken("--ore-purple-shadow", "#4a1cac", "purple_shadow"),
            new ThemeToken("--ore-gold", "#f0b92d", "gold"),
            new ThemeToken("--ore-gold-shadow", "#936715", "gold_shadow"),
            new ThemeToken("--ore-red", "#b33b31", "red"),
            new ThemeToken("--ore-red-hover", "#8b2923", "red_hover"),
            new ThemeToken("--ore-red-shadow", "#662019", "red_shadow"),
            new ThemeToken("--ore-blue", "#2d78a8", "blue"),
            new ThemeToken("--ore-success", "#69ad45", "success"),
            new ThemeToken("--ore-warning", "#f0b92d", "warning"),
            new ThemeToken("--ore-danger", "#d45b50", "danger"),
            new ThemeToken("--ore-info", "#58a6d2", "info"),
            new ThemeToken("--ore-focus", "#ffffff", "focus"),
            new ThemeToken("--ore-space-1", "4px", "space_1"),
            new ThemeToken("--ore-space-2", "8px", "space_2"),
            new ThemeToken("--ore-space-3", "16px", "space_3"),
            new ThemeToken("--ore-space-4", "24px", "space_4"),
            new ThemeToken("--ore-space-5", "32px", "space_5"),
            new ThemeToken("--ore-font-sm", "13px", "font_sm"),
            new ThemeToken("--ore-font-md", "16px", "font_md"),
            new ThemeToken("--ore-font-lg", "20px", "font_lg"),
            new ThemeToken("--ore-font-xl", "28px", "font_xl")
    );
    private static final List<ThemeGroup> THEME_GROUPS = List.of(
            new ThemeGroup("typography", List.of("--ore-ink", "--ore-ink-muted", "--ore-ink-dark",
                    "--ore-font-sm", "--ore-font-md", "--ore-font-lg", "--ore-font-xl")),
            new ThemeGroup("surfaces", List.of("--ore-canvas", "--ore-surface", "--ore-surface-deep",
                    "--ore-surface-soft", "--ore-edge", "--ore-edge-light", "--ore-focus")),
            new ThemeGroup("actions", List.of("--ore-green", "--ore-green-hover", "--ore-green-shadow",
                    "--ore-purple", "--ore-purple-hover", "--ore-purple-shadow", "--ore-gold",
                    "--ore-gold-shadow", "--ore-red", "--ore-red-hover", "--ore-red-shadow", "--ore-blue")),
            new ThemeGroup("feedback", List.of("--ore-success", "--ore-warning", "--ore-danger", "--ore-info")),
            new ThemeGroup("spacing", List.of("--ore-space-1", "--ore-space-2", "--ore-space-3",
                    "--ore-space-4", "--ore-space-5"))
    );

    private Document document;
    private final OreEditorSession session = new OreEditorSession();
    private OreEditorProject project = new OreEditorProject();
    private final OreEditorHistory history = new OreEditorHistory();
    private final OreEditorProjectCodec projectCodec = new OreEditorProjectCodec();
    private final OreEditorHtmlExporter htmlExporter = new OreEditorHtmlExporter();
    private final OreEditorHtmlImporter htmlImporter = new OreEditorHtmlImporter();
    private final OreEditorDocumentStore documentStore = new OreEditorDocumentStore();
    private final OreDragController drag = new OreDragController();
    private final OreCanvasHitTester hitTester = new OreCanvasHitTester();
    private final OreFlexInsertionResolver insertionResolver = new OreFlexInsertionResolver();
    private OreCanvasRenderer canvasRenderer;
    private Element dragGhost;
    private Element unsavedChangesDialog;
    private Path openedHtmlPath;
    private UUID hoveredNode;
    private OreContainerNode dropTarget;
    private OreFlexInsertionResolver.Insertion dropInsertion;
    private UUID movingNode;
    private UUID absoluteDragNode;
    private double absoluteDragStartX;
    private double absoluteDragStartY;
    private double absoluteDragLeft;
    private double absoluteDragTop;
    private double absoluteDragRight;
    private double absoluteDragBottom;
    private UUID absoluteResizeNode;
    private double absoluteResizeStartX;
    private double absoluteResizeStartY;
    private double absoluteResizeWidth;
    private double absoluteResizeHeight;
    private double absoluteResizeRight;
    private double absoluteResizeBottom;
    private ComponentState absoluteDragBefore;
    private ComponentState absoluteResizeBefore;
    private com.sighs.apricityui.editor.ore.model.OreComponentNode.VisualState editingVisualState =
            com.sighs.apricityui.editor.ore.model.OreComponentNode.VisualState.DEFAULT;
    private boolean showingPaletteContainers = true;
    private boolean resizing;

    synchronized boolean isOpen() {
        return document != null && document.isActive() && Document.get(PATH).contains(document);
    }

    synchronized Document getDocument() { return isOpen() ? document : null; }
    synchronized OreEditorSession getSession() { return session; }

    synchronized boolean loadSavedProject() {
        OreEditorDocumentStore.ReadResult result = documentStore.readProject();
        if (!result.success()) return false;
        try {
            project = projectCodec.read(result.content());
            openedHtmlPath = null;
            session.reset();
            session.select(project.root().id());
            history.reset();
            if (isOpen()) {
                updateDocumentState();
                renderCanvas();
                renderMode();
                renderBreadcrumb();
                updateHistoryControls();
            }
            ToastManager.showTranslation("ore_editor.apricityui.notice.loaded");
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    synchronized boolean open() {
        if (!isOpen()) document = Document.create(PATH);
        if (document == null) return false;
        document.setReloadPersistent(true);
        Element canvas = document.querySelector("#editorCanvas");
        if (canvas != null) canvasRenderer = new OreCanvasRenderer(document, canvas, this::selectNode,
                this::beginNodeDrag, this::beginAbsoluteResize);
        if (session.selectedNode() == null || project.find(session.selectedNode()) == null) {
            session.select(project.root().id());
        }
        bindShell();
        updateDocumentState();
        renderMode();
        renderCanvas();
        renderBreadcrumb();
        history.reset();
        updateHistoryControls();
        return true;
    }

    synchronized boolean openHtml(Path path, String source) {
        if (session.dirty() && isOpen()) {
            showDiscardConfirmation("ore_editor.apricityui.dialog.open_html.title",
                    "ore_editor.apricityui.dialog.open_html.message", () -> openHtmlNow(path, source));
            return false;
        }
        return openHtmlNow(path, source);
    }

    private boolean openHtmlNow(Path path, String source) {
        try {
            if (path == null || !Files.isRegularFile(path)) return false;
            project = htmlImporter.read(source);
            openedHtmlPath = path.toAbsolutePath().normalize();
            session.reset();
            session.select(project.root().id());
            history.reset();
            return open();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    synchronized void close() {
        if (isOpen() && session.dirty()) {
            showDiscardConfirmation("ore_editor.apricityui.dialog.unsaved.title",
                    "ore_editor.apricityui.dialog.unsaved.message", this::closeNow);
            return;
        }
        closeNow();
    }

    private void closeNow() {
        Document closing = document;
        document = null;
        unsavedChangesDialog = null;
        canvasRenderer = null;
        hoveredNode = null;
        dropTarget = null;
        dropInsertion = null;
        movingNode = null;
        absoluteDragNode = null;
        absoluteResizeNode = null;
        openedHtmlPath = null;
        removeDragGhost();
        drag.cancel();
        resizing = false;
        showingPaletteContainers = true;
        session.reset();
        Tooltip.hide(closing);
        if (closing != null && !closing.isDisposed()) closing.remove();
    }

    synchronized void toggle() {
        if (isOpen()) close(); else open();
    }

    private void bindShell() {
        if (document == null || document.body == null) return;
        bindAccessibilityLabels();
        bindClick("#closeButton", this::close);
        bindClick("#undoButton", this::undo);
        bindClick("#redoButton", this::redo);
        bindClick("#loadButton", this::requestLoadSavedProject);
        bindClick("#saveButton", this::saveProject);
        bindClick("#exportButton", this::exportHtml);
        bindTooltip("#undoButton");
        bindTooltip("#redoButton");
        bindTooltip("#loadButton");
        bindTooltip("#saveButton");
        bindTooltip("#exportButton");
        bindTooltip("#closeButton");
        for (Element tab : document.querySelectorAll(".editor-tab")) {
            if ("1".equals(tab.getAttribute("data-java-bound"))) continue;
            tab.setAttribute("data-java-bound", "1");
            tab.addEventListener("click", event -> {
                session.setMode(parseMode(tab.getAttribute("data-editor-mode")));
                renderMode();
            });
        }
        Element handle = document.querySelector("#editorResizeHandle");
        if (handle != null && !"1".equals(handle.getAttribute("data-java-bound"))) {
            handle.setAttribute("data-java-bound", "1");
            handle.addEventListener("mousedown", event -> {
                resizing = true;
                handle.setAttribute("class", "editor-resize-handle dragging");
                event.preventDefault();
            });
        }
        if (!"1".equals(document.body.getAttribute("data-editor-pointer-bound"))) {
            document.body.setAttribute("data-editor-pointer-bound", "1");
            document.body.addEventListener("mousemove", event -> {
                if (!(event instanceof MouseEvent mouseEvent)) return;
                resizeSidebar(mouseEvent.clientX);
                movePaletteDrag(mouseEvent.clientX, mouseEvent.clientY);
                moveNodeDrag(mouseEvent.clientX, mouseEvent.clientY);
                moveAbsoluteNode(mouseEvent.clientX, mouseEvent.clientY);
                moveAbsoluteResize(mouseEvent.clientX, mouseEvent.clientY);
                updateCanvasHover(mouseEvent.clientX, mouseEvent.clientY);
            });
            document.body.addEventListener("mouseup", event -> {
                if (!(event instanceof MouseEvent mouseEvent)) return;
                stopResize(handle);
                finishPaletteDrag(mouseEvent.clientX, mouseEvent.clientY);
                finishNodeDrag(mouseEvent.clientX, mouseEvent.clientY);
                finishAbsoluteNode();
                finishAbsoluteResize();
            });
        }
        if (!"1".equals(document.body.getAttribute("data-editor-key-bound"))) {
            document.body.setAttribute("data-editor-key-bound", "1");
            document.body.addEventListener("keydown", this::handleEditorShortcut);
        }
    }

    private void bindAccessibilityLabels() {
        for (Element element : document.querySelectorAll("[data-aria-label-key]")) {
            String key = element.getAttribute("data-aria-label-key");
            if (key != null && !key.isBlank()) element.setAttribute("aria-label", UiTranslations.translate(key));
        }
    }

    private void bindClick(String selector, Runnable action) {
        Element element = document.querySelector(selector);
        if (element == null || "1".equals(element.getAttribute("data-java-bound"))) return;
        element.setAttribute("data-java-bound", "1");
        element.addEventListener("click", event -> action.run());
    }

    private void handleEditorShortcut(com.sighs.apricityui.event.Event event) {
        if (!(event instanceof KeyEvent keyEvent) || (!keyEvent.controlKey && !keyEvent.metaKey)
                || keyEvent.altKey || isTextEntry(keyEvent.target)) return;
        boolean redo = keyEvent.keyCode == GLFW.GLFW_KEY_Y
                || (keyEvent.keyCode == GLFW.GLFW_KEY_Z && keyEvent.shiftKey);
        boolean undo = keyEvent.keyCode == GLFW.GLFW_KEY_Z && !keyEvent.shiftKey;
        if (!undo && !redo) return;
        if (undo) undo(); else redo();
        event.preventDefault();
        event.stopPropagation();
    }

    private static boolean isTextEntry(Object target) {
        if (!(target instanceof Element element)) return false;
        return "INPUT".equalsIgnoreCase(element.tagName) || "TEXTAREA".equalsIgnoreCase(element.tagName)
                || "SELECT".equalsIgnoreCase(element.tagName);
    }

    private void requestLoadSavedProject() {
        if (session.dirty()) {
            showDiscardConfirmation("ore_editor.apricityui.dialog.load.title",
                    "ore_editor.apricityui.dialog.load.message", this::loadSavedProject);
            return;
        }
        loadSavedProject();
    }

    private void showDiscardConfirmation(String titleKey, String messageKey, Runnable discardAction) {
        if (document == null || document.body == null || unsavedChangesDialog != null) return;
        Element overlay = Element.init(document.createElement("DIV"));
        overlay.setAttribute("class", "editor-confirm-overlay");
        overlay.setAttribute("data-ore-editor-ui", "unsaved-changes-dialog");
        overlay.setTopLayer(true);
        overlay.addEventListener("click", event -> closeUnsavedChangesDialog());

        Element dialog = Element.init(document.createElement("DIV"));
        dialog.setAttribute("class", "panel editor-confirm-dialog");
        dialog.addEventListener("click", event -> event.stopPropagation());
        Element header = Element.init(document.createElement("DIV"));
        header.setAttribute("class", "panel-header");
        header.appendChild(OreEditorDom.translation(document, titleKey, null));
        Element body = Element.init(document.createElement("DIV"));
        body.setAttribute("class", "panel-body");
        body.appendChild(OreEditorDom.translation(document, messageKey, null));
        Element actions = Element.init(document.createElement("DIV"));
        actions.setAttribute("class", "editor-inspector-actions");
        Element cancel = Element.init(document.createElement("BUTTON"));
        cancel.setAttribute("class", "button button-secondary button-small");
        cancel.setAttribute("type", "button");
        cancel.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.cancel", null));
        cancel.addEventListener("click", event -> closeUnsavedChangesDialog());
        Element discard = Element.init(document.createElement("BUTTON"));
        discard.setAttribute("class", "button button-danger button-small");
        discard.setAttribute("type", "button");
        discard.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.discard_changes", null));
        discard.addEventListener("click", event -> {
            closeUnsavedChangesDialog();
            discardAction.run();
        });
        actions.appendChild(cancel);
        actions.appendChild(discard);
        body.appendChild(actions);
        dialog.appendChild(header);
        dialog.appendChild(body);
        overlay.appendChild(dialog);
        document.body.appendChild(overlay);
        unsavedChangesDialog = overlay;
        document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
    }

    private void closeUnsavedChangesDialog() {
        if (unsavedChangesDialog != null) unsavedChangesDialog.remove();
        unsavedChangesDialog = null;
        if (document != null && document.body != null) {
            document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        }
    }

    private void bindTooltip(String selector) {
        Element element = document.querySelector(selector);
        if (element == null || "1".equals(element.getAttribute("data-tooltip-bound"))) return;
        String key = element.getAttribute("data-tooltip-key");
        if (key == null || key.isBlank()) return;
        element.setAttribute("data-tooltip-bound", "1");
        Tooltip.bindTranslation(element, key);
    }

    private OreEditorSession.Mode parseMode(String raw) {
        if (raw == null) return OreEditorSession.Mode.ADD;
        try {
            return OreEditorSession.Mode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return OreEditorSession.Mode.ADD;
        }
    }

    private void renderMode() {
        if (document == null) return;
        for (Element tab : document.querySelectorAll(".editor-tab")) {
            boolean active = parseMode(tab.getAttribute("data-editor-mode")) == session.mode();
            tab.setAttribute("class", active ? "button button-small editor-tab active" : "button button-small editor-tab");
        }
        Element content = document.querySelector("#editorSidebarContent");
        if (content == null) return;
        for (Node child : new ArrayList<>(content.children)) child.remove();
        Element panel = Element.init(document.createElement("DIV"));
        panel.setAttribute("class", "panel");
        Element header = Element.init(document.createElement("DIV"));
        header.setAttribute("class", "panel-header");
        Element body = Element.init(document.createElement("DIV"));
        body.setAttribute("class", "panel-body");
        String title;
        String message;
        switch (session.mode()) {
            case INSPECT -> { title = "ore_editor.apricityui.inspector.title"; message = "ore_editor.apricityui.empty.inspect"; }
            case THEME -> { title = "ore_editor.apricityui.theme.title"; message = "ore_editor.apricityui.empty.theme"; }
            default -> {
                title = showingPaletteContainers ? "ore_editor.apricityui.palette.containers" : "ore_editor.apricityui.palette.components";
                message = "ore_editor.apricityui.empty.add";
            }
        }
        header.appendChild(OreEditorDom.translation(document, title, null));
        if (session.mode() == OreEditorSession.Mode.ADD) renderPalette(body);
        else if (session.mode() == OreEditorSession.Mode.INSPECT) renderInspector(body);
        else renderTheme(body);
        panel.appendChild(header);
        panel.appendChild(body);
        content.appendChild(panel);
        document.markDirty(content, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
    }

    private void selectNode(java.util.UUID id) {
        session.select(id);
        renderCanvas();
        renderBreadcrumb();
        if (session.mode() == OreEditorSession.Mode.INSPECT) renderMode();
    }

    private void renderCanvas() {
        if (canvasRenderer != null) canvasRenderer.render(project, session.selectedNode(), hoveredNode);
    }

    private void updateCanvasHover(double x, double y) {
        if (drag.active() || canvasRenderer == null || document == null) return;
        Element canvas = document.querySelector("#editorCanvas");
        if (canvas == null) return;
        Element.DOMRect rect = canvas.getBoundingClientRect();
        UUID next = x < rect.left || x > rect.right || y < rect.top || y > rect.bottom
                ? null : hitTester.hit(canvasRenderer.elements(), x, y);
        if (java.util.Objects.equals(hoveredNode, next)) return;
        hoveredNode = next;
        renderCanvas();
    }

    private void renderBreadcrumb() {
        if (document == null) return;
        Element breadcrumb = document.querySelector("#editorBreadcrumb");
        if (breadcrumb == null) return;
        for (Node child : new ArrayList<>(breadcrumb.children)) child.remove();
        OreCanvasNode node = session.selectedNode() == null ? project.root() : project.find(session.selectedNode());
        ArrayList<OreCanvasNode> path = new ArrayList<>();
        while (node != null) {
            path.add(0, node);
            node = node.parent();
        }
        for (int index = 0; index < path.size(); index++) {
            if (index > 0) breadcrumb.appendChild(document.createTextNode(" / "));
            OreCanvasNode entry = path.get(index);
            String key = entry instanceof OreContainerNode container && container.isRoot()
                    ? "ore_editor.apricityui.breadcrumb.canvas"
                    : entry instanceof OreContainerNode ? "ore_editor.apricityui.breadcrumb.container"
                    : "ore_editor.apricityui.breadcrumb.component";
            breadcrumb.appendChild(OreEditorDom.translation(document, key, null));
        }
        document.markDirty(breadcrumb, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
    }

    private void updateDocumentState() {
        if (document == null) return;
        Element state = document.querySelector(".editor-document-state");
        if (state == null) return;
        for (Node child : new ArrayList<>(state.children)) child.remove();
        if (openedHtmlPath == null) state.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.document.untitled", null));
        else state.appendChild(document.createTextNode(openedHtmlPath.getFileName().toString()));
        if (session.dirty()) {
            Element dirty = Element.init(document.createElement("SPAN"));
            dirty.setAttribute("class", "editor-document-dirty");
            dirty.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.document.modified", null));
            state.appendChild(dirty);
        }
        document.markDirty(state, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
    }

    private void renderPalette(Element body) {
        Element switcher = Element.init(document.createElement("DIV"));
        switcher.setAttribute("class", "editor-segmented editor-palette-switcher");
        switcher.setAttribute("role", "group");
        appendPaletteModeButton(switcher, true, "ore_editor.apricityui.palette.containers");
        appendPaletteModeButton(switcher, false, "ore_editor.apricityui.palette.components");
        body.appendChild(switcher);
        Element items = Element.init(document.createElement("DIV"));
        items.setAttribute("class", "editor-palette-items");
        for (OreComponentDefinition definition : OreComponentRegistry.definitions()) {
            if (definition.container() != showingPaletteContainers) continue;
            Element item = Element.init(document.createElement("BUTTON"));
            item.setAttribute("class", "button button-secondary button-small editor-palette-item");
            item.setAttribute("type", "button");
            item.appendChild(OreEditorDom.translation(document, definition.nameKey(), null));
            Tooltip.bindTranslation(item, definition.descriptionKey());
            item.addEventListener("mousedown", event -> {
                if (!(event instanceof MouseEvent mouseEvent)) return;
                beginPaletteDrag(definition, mouseEvent.clientX, mouseEvent.clientY);
                event.preventDefault();
                event.stopPropagation();
            });
            items.appendChild(item);
        }
        body.appendChild(items);
    }

    private void appendPaletteModeButton(Element parent, boolean containers, String labelKey) {
        Element button = Element.init(document.createElement("BUTTON"));
        boolean active = showingPaletteContainers == containers;
        button.setAttribute("class", active ? "button button-primary button-small" : "button button-secondary button-small");
        button.setAttribute("type", "button");
        button.setAttribute("aria-pressed", Boolean.toString(active));
        button.appendChild(OreEditorDom.translation(document, labelKey, null));
        button.addEventListener("click", event -> {
            if (showingPaletteContainers == containers) return;
            showingPaletteContainers = containers;
            renderMode();
        });
        parent.appendChild(button);
    }

    private void renderInspector(Element body) {
        OreCanvasNode node = project.find(session.selectedNode());
        if (node == null) {
            body.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.empty.inspect", null));
            return;
        }
        if (node.locked() && node != project.root()) {
            appendNodeActions(body, node);
            return;
        }
        if (node instanceof OreContainerNode container) renderContainerInspector(body, container);
        else if (node instanceof com.sighs.apricityui.editor.ore.model.OreComponentNode component) {
            renderComponentInspector(body, component);
        }
    }

    private void renderTheme(Element body) {
        for (ThemeGroup group : THEME_GROUPS) {
            Element section = Element.init(document.createElement("FIELDSET"));
            section.setAttribute("class", "editor-theme-group");
            Element header = Element.init(document.createElement("DIV"));
            header.setAttribute("class", "editor-theme-group-header");
            header.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.theme_group." + group.translationSuffix(), null));
            Element resetGroup = Element.init(document.createElement("BUTTON"));
            resetGroup.setAttribute("class", "button button-secondary button-small");
            resetGroup.setAttribute("type", "button");
            resetGroup.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.reset_group", null));
            resetGroup.addEventListener("click", event -> {
                java.util.Map<String, String> before = project.theme().overrides();
                for (String token : group.tokens()) project.theme().set(token, null);
                java.util.Map<String, String> after = project.theme().overrides();
                if (before.equals(after)) return;
                updateProject();
                commitHistory(OreEditorHistory.action("ResetThemeGroup", session.selectedNode(), session.selectedNode(),
                        () -> applyTheme(before), () -> applyTheme(after)));
                renderMode();
            });
            header.appendChild(resetGroup);
            section.appendChild(header);
            for (String name : group.tokens()) {
                ThemeToken token = themeToken(name);
                if (token == null) continue;
                String current = project.theme().get(token.name());
                appendThemeTokenInput(section, token, current == null ? token.defaultValue() : current);
            }
            body.appendChild(section);
        }
        Element reset = Element.init(document.createElement("BUTTON"));
        reset.setAttribute("class", "button button-secondary button-small");
        reset.setAttribute("type", "button");
        reset.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.reset_theme", null));
        reset.addEventListener("click", event -> {
            java.util.Map<String, String> before = project.theme().overrides();
            project.theme().reset();
            if (before.isEmpty()) return;
            updateProject();
            commitHistory(OreEditorHistory.action("ResetThemeGroup", session.selectedNode(), session.selectedNode(),
                    () -> applyTheme(before), () -> project.theme().reset()));
            renderMode();
        });
        body.appendChild(reset);
    }

    private void renderContainerInspector(Element body, OreContainerNode container) {
        appendSegmented(body, "ore_editor.apricityui.property.flex_direction", container.flex().direction(),
                value -> updateContainer(container, () -> container.flex().setDirection(value)),
                "row", "row-reverse", "column", "column-reverse");
        appendSegmented(body, "ore_editor.apricityui.property.flex_wrap", container.flex().wrap(),
                value -> updateContainer(container, () -> container.flex().setWrap(value)), "nowrap", "wrap", "wrap-reverse");
        appendAlignmentSelect(body, "ore_editor.apricityui.property.justify_content", "justify", container.flex().justifyContent(),
                value -> updateContainer(container, () -> container.flex().setJustifyContent(value)),
                "flex-start", "center", "flex-end", "space-between", "space-around", "space-evenly");
        appendAlignmentSelect(body, "ore_editor.apricityui.property.align_items", "items", container.flex().alignItems(),
                value -> updateContainer(container, () -> container.flex().setAlignItems(value)),
                "stretch", "flex-start", "center", "flex-end", "baseline");
        appendAlignmentSelect(body, "ore_editor.apricityui.property.align_content", "content", container.flex().alignContent(),
                value -> updateContainer(container, () -> container.flex().setAlignContent(value)),
                "stretch", "flex-start", "center", "flex-end", "space-between", "space-around", "space-evenly");
        appendLengthStyleInput(body, container, "ore_editor.apricityui.property.gap", "gap", container.flex().gap(),
                value -> updateContainer(container, () -> container.flex().setGap(value)));
        appendLengthStyleInput(body, container, "ore_editor.apricityui.property.row_gap", "row-gap", container.flex().rowGap(),
                value -> updateContainer(container, () -> container.flex().setRowGap(value)));
        appendLengthStyleInput(body, container, "ore_editor.apricityui.property.column_gap", "column-gap", container.flex().columnGap(),
                value -> updateContainer(container, () -> container.flex().setColumnGap(value)));
        appendLengthStyleInput(body, container, "ore_editor.apricityui.property.width", "width", container.style().get("width"), null);
        appendLengthStyleInput(body, container, "ore_editor.apricityui.property.height", "height", container.style().get("height"), null);
        appendLengthStyleInput(body, container, "ore_editor.apricityui.property.min_width", "min-width", container.style().get("min-width"), null);
        appendLengthStyleInput(body, container, "ore_editor.apricityui.property.min_height", "min-height", container.style().get("min-height"), null);
        appendLengthStyleInput(body, container, "ore_editor.apricityui.property.max_width", "max-width", container.style().get("max-width"), null);
        appendLengthStyleInput(body, container, "ore_editor.apricityui.property.max_height", "max-height", container.style().get("max-height"), null);
        appendBoxModelField(body, container, "padding", "ore_editor.apricityui.property.padding");
        appendStyleInput(body, container, "ore_editor.apricityui.property.background", "background", container.style().get("background"), null);
        appendSelect(body, "ore_editor.apricityui.property.overflow", container.style().get("overflow"),
                value -> updateNodeStyle(container, "overflow", value), "visible", "hidden", "auto", "scroll");
        appendSelect(body, "ore_editor.apricityui.property.overflow_x", container.style().get("overflow-x"),
                value -> updateNodeStyle(container, "overflow-x", value), "visible", "hidden", "auto", "scroll");
        appendSelect(body, "ore_editor.apricityui.property.overflow_y", container.style().get("overflow-y"),
                value -> updateNodeStyle(container, "overflow-y", value), "visible", "hidden", "auto", "scroll");
        appendNodeActions(body, container);
    }

    private void renderComponentInspector(Element body, com.sighs.apricityui.editor.ore.model.OreComponentNode component) {
        appendInput(body, "ore_editor.apricityui.property.content", component.content(), value -> {
            updateContent(component, value);
        });
        appendSelect(body, "ore_editor.apricityui.property.visual_state", editingVisualState.name().toLowerCase(), value -> {
            editingVisualState = com.sighs.apricityui.editor.ore.model.OreComponentNode.VisualState.valueOf(value.toUpperCase());
            renderMode();
        }, "default", "hover", "active", "focus", "disabled");
        Element stateStatus = Element.init(document.createElement("DIV"));
        boolean overridden = hasStateOverride(component, editingVisualState);
        stateStatus.setAttribute("class", overridden ? "badge badge-purple editor-state-status" : "badge editor-state-status");
        stateStatus.appendChild(OreEditorDom.translation(document, editingVisualState == OreComponentNode.VisualState.DEFAULT
                ? "ore_editor.apricityui.state.base" : overridden
                ? "ore_editor.apricityui.state.overridden" : "ore_editor.apricityui.state.no_override", null));
        body.appendChild(stateStatus);
        appendNumberStyleInput(body, component, "ore_editor.apricityui.property.order", "order", component.style().get("order"), null, null, 1);
        appendNumberStyleInput(body, component, "ore_editor.apricityui.property.flex_grow", "flex-grow", component.style().get("flex-grow"), 0D, null, 0.1D);
        appendNumberStyleInput(body, component, "ore_editor.apricityui.property.flex_shrink", "flex-shrink", component.style().get("flex-shrink"), 0D, null, 0.1D);
        appendLengthStyleInput(body, component, "ore_editor.apricityui.property.flex_basis", "flex-basis", component.style().get("flex-basis"), null);
        appendSelect(body, "ore_editor.apricityui.property.align_self", component.style().get("align-self"),
                value -> updateNodeStyle(component, "align-self", value),
                "auto", "stretch", "flex-start", "center", "flex-end", "baseline");
        appendSelect(body, "ore_editor.apricityui.property.position", component.absolute() ? "absolute" : "static",
                value -> toggleAbsolute(component, "absolute".equals(value)),
                "static", "absolute");
        Element left = appendLengthStyleInput(body, component, "ore_editor.apricityui.property.left", "left", component.style().get("left"),
                value -> updateAbsoluteOffset(component, "left", value));
        Element right = appendLengthStyleInput(body, component, "ore_editor.apricityui.property.right", "right", component.style().get("right"),
                value -> updateAbsoluteOffset(component, "right", value));
        Element top = appendLengthStyleInput(body, component, "ore_editor.apricityui.property.top", "top", component.style().get("top"),
                value -> updateAbsoluteOffset(component, "top", value));
        Element bottom = appendLengthStyleInput(body, component, "ore_editor.apricityui.property.bottom", "bottom", component.style().get("bottom"),
                value -> updateAbsoluteOffset(component, "bottom", value));
        if (!component.absolute()) {
            disableField(left, "ore_editor.apricityui.disabled.absolute_offsets");
            disableField(right, "ore_editor.apricityui.disabled.absolute_offsets");
            disableField(top, "ore_editor.apricityui.disabled.absolute_offsets");
            disableField(bottom, "ore_editor.apricityui.disabled.absolute_offsets");
        }
        appendLengthStyleInput(body, component, "ore_editor.apricityui.property.width", "width", component.style().get("width"), null);
        appendLengthStyleInput(body, component, "ore_editor.apricityui.property.height", "height", component.style().get("height"), null);
        appendLengthStyleInput(body, component, "ore_editor.apricityui.property.min_width", "min-width", component.style().get("min-width"), null);
        appendLengthStyleInput(body, component, "ore_editor.apricityui.property.min_height", "min-height", component.style().get("min-height"), null);
        appendLengthStyleInput(body, component, "ore_editor.apricityui.property.max_width", "max-width", component.style().get("max-width"), null);
        appendLengthStyleInput(body, component, "ore_editor.apricityui.property.max_height", "max-height", component.style().get("max-height"), null);
        appendNumberStyleInput(body, component, "ore_editor.apricityui.property.z_index", "z-index", component.style().get("z-index"), null, null, 1);
        appendBoxModelField(body, component, "margin", "ore_editor.apricityui.property.margin");
        appendBoxModelField(body, component, "padding", "ore_editor.apricityui.property.padding");
        appendComponentColorField(body, component, "ore_editor.apricityui.property.color", "color");
        appendComponentColorField(body, component, "ore_editor.apricityui.property.background", "background");
        appendComponentStateStyleInput(body, component, "ore_editor.apricityui.property.border", "border");
        appendComponentStateNumberField(body, component, "ore_editor.apricityui.property.opacity", "opacity", 0D, 1D, 0.05D);
        appendComponentStateStyleInput(body, component, "ore_editor.apricityui.property.font_family", "font-family");
        appendComponentStateLengthField(body, component, "ore_editor.apricityui.property.font_size", "font-size");
        appendComponentStateLengthField(body, component, "ore_editor.apricityui.property.line_height", "line-height");
        appendComponentStateNumberField(body, component, "ore_editor.apricityui.property.font_weight", "font-weight", 100D, 900D, 100D);
        appendComponentStateStyleSelect(body, component, "ore_editor.apricityui.property.text_align", "text-align",
                "left", "center", "right", "justify");
        appendShadowField(body, component);
        appendNodeActions(body, component);
    }

    private void appendNodeActions(Element body, OreCanvasNode node) {
        if (node == project.root() || node.parent() == null) return;
        Element actions = Element.init(document.createElement("DIV"));
        actions.setAttribute("class", "editor-inspector-actions");
        Element lock = Element.init(document.createElement("BUTTON"));
        lock.setAttribute("class", "button button-secondary button-small");
        lock.setAttribute("type", "button");
        lock.appendChild(OreEditorDom.translation(document, node.locked()
                ? "ore_editor.apricityui.action.unlock" : "ore_editor.apricityui.action.lock", null));
        lock.addEventListener("click", event -> {
            boolean before = node.locked();
            node.setLocked(!before);
            updateProject();
            commitHistory(OreEditorHistory.booleanValue("LockNode", node.id(), node.id(), before, !before, node::setLocked));
            renderMode();
        });
        actions.appendChild(lock);
        if (node.locked()) {
            body.appendChild(actions);
            return;
        }
        int index = node.parent().children().indexOf(node);
        Element moveUp = Element.init(document.createElement("BUTTON"));
        moveUp.setAttribute("class", "button button-secondary button-small");
        moveUp.setAttribute("type", "button");
        moveUp.setDisabled(index <= 0);
        moveUp.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.move_up", null));
        moveUp.addEventListener("click", event -> moveNodeSibling(node, -1));
        Element moveDown = Element.init(document.createElement("BUTTON"));
        moveDown.setAttribute("class", "button button-secondary button-small");
        moveDown.setAttribute("type", "button");
        moveDown.setDisabled(index < 0 || index + 1 >= node.parent().children().size());
        moveDown.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.move_down", null));
        moveDown.addEventListener("click", event -> moveNodeSibling(node, 1));
        Element moveOut = Element.init(document.createElement("BUTTON"));
        moveOut.setAttribute("class", "button button-secondary button-small");
        moveOut.setAttribute("type", "button");
        moveOut.setDisabled(node.parent().parent() == null);
        moveOut.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.move_out", null));
        moveOut.addEventListener("click", event -> moveNodeOut(node));
        Element duplicate = Element.init(document.createElement("BUTTON"));
        duplicate.setAttribute("class", "button button-secondary button-small");
        duplicate.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.duplicate", null));
        duplicate.addEventListener("click", event -> duplicateNode(node));
        Element delete = Element.init(document.createElement("BUTTON"));
        delete.setAttribute("class", "button button-danger button-small");
        delete.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.delete", null));
        delete.addEventListener("click", event -> deleteNode(node));
        actions.appendChild(moveUp);
        actions.appendChild(moveDown);
        actions.appendChild(moveOut);
        actions.appendChild(duplicate);
        actions.appendChild(delete);
        body.appendChild(actions);
    }

    static boolean hasStateOverride(OreComponentNode component, OreComponentNode.VisualState state) {
        return component != null && state != null && state != OreComponentNode.VisualState.DEFAULT
                && component.stateStyles().containsKey(state)
                && !component.stateStyles().get(state).properties().isEmpty();
    }

    private void duplicateNode(OreCanvasNode node) {
        OreContainerNode parent = node.parent();
        if (node.locked() || parent == null || structureLocked(parent)) return;
        OreCanvasNode copy = copyNode(node);
        int index = parent.children().indexOf(node) + 1;
        parent.insert(index, copy);
        session.select(copy.id());
        updateProject();
        commitHistory(OreEditorHistory.action("DuplicateNode", node.id(), copy.id(),
                () -> parent.remove(copy), () -> parent.insert(index, copy)));
        renderMode();
        renderBreadcrumb();
    }

    private void deleteNode(OreCanvasNode node) {
        OreContainerNode parent = node.parent();
        if (node.locked() || parent == null || structureLocked(parent)) return;
        int index = parent.children().indexOf(node);
        parent.remove(node);
        session.select(parent.id());
        updateProject();
        commitHistory(OreEditorHistory.action("RemoveNode", node.id(), parent.id(),
                () -> parent.insert(index, node), () -> parent.remove(node)));
        renderMode();
        renderBreadcrumb();
    }

    private void moveNodeSibling(OreCanvasNode node, int offset) {
        OreContainerNode parent = node == null ? null : node.parent();
        if (node == null || node.locked() || parent == null || structureLocked(parent)) return;
        int beforeIndex = parent.children().indexOf(node);
        int afterIndex = beforeIndex + offset;
        if (beforeIndex < 0 || afterIndex < 0 || afterIndex >= parent.children().size()) return;
        parent.remove(node);
        parent.insert(afterIndex, node);
        session.select(node.id());
        updateProject();
        commitHistory(OreEditorHistory.action("MoveNode", node.id(), node.id(),
                () -> { parent.remove(node); parent.insert(beforeIndex, node); },
                () -> { parent.remove(node); parent.insert(afterIndex, node); }));
        renderMode();
        renderBreadcrumb();
    }

    private void moveNodeOut(OreCanvasNode node) {
        OreContainerNode source = node == null ? null : node.parent();
        OreContainerNode target = source == null ? null : source.parent();
        if (node == null || node.locked() || source == null || structureLocked(source)
                || target == null || structureLocked(target)) return;
        int sourceIndex = source.children().indexOf(node);
        int targetIndex = target.children().indexOf(source) + 1;
        if (sourceIndex < 0 || targetIndex <= 0) return;
        source.remove(node);
        target.insert(targetIndex, node);
        session.select(node.id());
        updateProject();
        commitHistory(OreEditorHistory.action("ReparentNode", node.id(), node.id(),
                () -> { target.remove(node); source.insert(sourceIndex, node); },
                () -> { source.remove(node); target.insert(targetIndex, node); }));
        renderMode();
        renderBreadcrumb();
    }

    private OreCanvasNode copyNode(OreCanvasNode node) {
        OreCanvasNode copy;
        if (node instanceof OreContainerNode source) {
            OreContainerNode container = new OreContainerNode(false);
            container.setTag(source.tag());
            container.flex().setDirection(source.flex().direction());
            container.flex().setWrap(source.flex().wrap());
            container.flex().setJustifyContent(source.flex().justifyContent());
            container.flex().setAlignItems(source.flex().alignItems());
            container.flex().setAlignContent(source.flex().alignContent());
            container.flex().setGap(source.flex().gap());
            container.flex().setRowGap(source.flex().rowGap());
            container.flex().setColumnGap(source.flex().columnGap());
            for (OreCanvasNode child : source.children()) container.add(copyNode(child));
            copy = container;
        } else if (node instanceof com.sighs.apricityui.editor.ore.model.OreComponentNode source) {
            com.sighs.apricityui.editor.ore.model.OreComponentNode component =
                    new com.sighs.apricityui.editor.ore.model.OreComponentNode(source.type(), source.content());
            if (source.absolute()) component.enterAbsolute(source.flowIndex());
            if (source.hasFlowStyleSnapshot()) component.setFlowStyleSnapshot(source.flowStyleSnapshot());
            source.stateStyles().forEach((state, style) -> style.properties().forEach((key, value) -> component.stateStyle(state).set(key, value)));
            copy = component;
        } else throw new IllegalArgumentException("Unknown canvas node");
        node.style().properties().forEach(copy.style()::set);
        node.attributes().forEach(copy::setAttribute);
        copy.setLocked(node.locked());
        return copy;
    }

    private void appendStyleInput(Element body, OreCanvasNode node, String label, String property, String value,
                                  Consumer<String> override) {
        appendInput(body, label, value, next -> {
            if (override != null) override.accept(next);
            else updateNodeStyle(node, property, next);
        }, this::validCssValue);
    }

    private Element appendLengthStyleInput(Element body, OreCanvasNode node, String label, String property, String value,
                                           Consumer<String> override) {
        return appendLengthField(body, label, value, allowsAuto(property), next -> {
            if (override != null) override.accept(next);
            else updateNodeStyle(node, property, next);
        });
    }

    private void appendNumberStyleInput(Element body, OreCanvasNode node, String label, String property, String value,
                                        Double min, Double max, double step) {
        appendNumberField(body, label, value, min, max, step, next -> updateNodeStyle(node, property, next));
    }

    private void appendComponentStateStyleInput(Element body, com.sighs.apricityui.editor.ore.model.OreComponentNode component,
                                                String label, String property) {
        appendInput(body, label, component.stateStyle(editingVisualState).get(property), next -> {
            OreComponentNode.VisualState state = editingVisualState;
            String before = component.stateStyle(state).get(property);
            if (same(before, next)) return;
            component.stateStyle(state).set(property, next);
            updateProject();
            commitHistory(OreEditorHistory.stringValue("UpdateComponentProperty", history.activeMergeKey(), component.id(), component.id(),
                    before, next, value -> component.stateStyle(state).set(property, value)));
        }, this::validCssValue);
    }

    private void appendComponentStateLengthField(Element body, OreComponentNode component, String label, String property) {
        OreComponentNode.VisualState state = editingVisualState;
        appendLengthField(body, label, component.stateStyle(state).get(property), allowsAuto(property),
                next -> updateComponentStateStyle(component, state, property, next));
    }

    private void appendComponentStateNumberField(Element body, OreComponentNode component, String label, String property,
                                                 Double min, Double max, double step) {
        OreComponentNode.VisualState state = editingVisualState;
        appendNumberField(body, label, component.stateStyle(state).get(property), min, max, step,
                next -> updateComponentStateStyle(component, state, property, next));
    }

    private void appendComponentColorField(Element body, OreComponentNode component, String labelKey, String property) {
        Element group = Element.init(document.createElement("DIV"));
        group.setAttribute("class", "form-group editor-form-group");
        Element label = Element.init(document.createElement("LABEL"));
        label.setAttribute("class", "form-label");
        label.appendChild(OreEditorDom.translation(document, labelKey, null));
        Element controls = Element.init(document.createElement("DIV"));
        controls.setAttribute("class", "editor-color-field");
        OreComponentNode.VisualState state = editingVisualState;
        String current = component.stateStyle(state).get(property);
        ColorValue initial = ColorValue.parse(current);
        Element input = Element.init(document.createElement("INPUT"));
        input.setAttribute("class", "form-input");
        input.setAttribute("type", "text");
        input.setValue(current == null ? "" : current);
        Element color = Element.init(document.createElement("INPUT"));
        color.setAttribute("class", "editor-component-color");
        color.setAttribute("type", "color");
        color.setValue(initial.hex());
        Element alpha = Element.init(document.createElement("INPUT"));
        alpha.setAttribute("class", "editor-component-alpha");
        alpha.setAttribute("type", "range");
        alpha.setAttribute("min", "0");
        alpha.setAttribute("max", "1");
        alpha.setAttribute("step", "0.01");
        alpha.setValue(formatNumber(initial.alpha()));
        alpha.setAttribute("data-tooltip-key", "ore_editor.apricityui.property.alpha");
        input.addEventListener("focus", event -> history.beginMerge("component-color:" + System.identityHashCode(input)));
        input.addEventListener("blur", event -> history.endMerge());
        input.addEventListener("change", event -> {
            String next = input.getValue();
            if (!validCssColor(next)) {
                input.setAttribute("class", "form-input is-invalid");
                Tooltip.bindTranslation(input, "ore_editor.apricityui.validation.color");
                return;
            }
            input.setAttribute("class", "form-input");
            ColorValue parsed = ColorValue.parse(next);
            color.setValue(parsed.hex());
            alpha.setValue(formatNumber(parsed.alpha()));
            updateComponentStateStyle(component, state, property, next);
        });
        Runnable commitPickerColor = () -> {
            double opacity = validNumber(alpha.getValue()) ? Double.parseDouble(alpha.getValue()) : 1D;
            String next = ColorValue.toCss(color.getValue(), opacity);
            input.setValue(next);
            updateComponentStateStyle(component, state, property, next);
        };
        color.addEventListener("change", event -> commitPickerColor.run());
        alpha.addEventListener("focus", event -> history.beginMerge("component-color:" + System.identityHashCode(input)));
        alpha.addEventListener("blur", event -> history.endMerge());
        alpha.addEventListener("change", event -> commitPickerColor.run());
        controls.appendChild(input);
        controls.appendChild(color);
        controls.appendChild(alpha);
        group.appendChild(label);
        group.appendChild(controls);
        body.appendChild(group);
    }

    private void appendShadowField(Element body, OreComponentNode component) {
        OreComponentNode.VisualState state = editingVisualState;
        ShadowValue initial = parseShadow(component.stateStyle(state).get("box-shadow"));
        Element group = Element.init(document.createElement("FIELDSET"));
        group.setAttribute("class", "editor-shadow-field");
        Element legend = Element.init(document.createElement("LEGEND"));
        legend.setAttribute("class", "form-label");
        legend.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.property.shadow", null));
        group.appendChild(legend);
        Element insetChoice = Element.init(document.createElement("LABEL"));
        insetChoice.setAttribute("class", "choice");
        Element inset = Element.init(document.createElement("INPUT"));
        inset.setAttribute("type", "checkbox");
        inset.setChecked(initial.inset());
        insetChoice.appendChild(inset);
        insetChoice.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.property.shadow_inset", null));
        group.appendChild(insetChoice);
        Element fields = Element.init(document.createElement("DIV"));
        fields.setAttribute("class", "editor-shadow-fields");
        List<Element> values = new ArrayList<>();
        values.add(appendShadowInput(fields, "ore_editor.apricityui.property.shadow_offset_x", initial.offsetX()));
        values.add(appendShadowInput(fields, "ore_editor.apricityui.property.shadow_offset_y", initial.offsetY()));
        values.add(appendShadowInput(fields, "ore_editor.apricityui.property.shadow_blur", initial.blur()));
        values.add(appendShadowInput(fields, "ore_editor.apricityui.property.shadow_spread", initial.spread()));
        values.add(appendShadowInput(fields, "ore_editor.apricityui.property.shadow_color", initial.color()));
        Runnable commit = () -> {
            String color = values.get(4).getValue();
            if (color == null || color.isBlank()) {
                updateComponentStateStyle(component, state, "box-shadow", "");
                return;
            }
            if (!validCssColor(color) || !validCssValue(values.get(0).getValue()) || !validCssValue(values.get(1).getValue())
                    || !validCssValue(values.get(2).getValue()) || !validCssValue(values.get(3).getValue())) return;
            ShadowValue next = new ShadowValue(inset.isChecked(), values.get(0).getValue(), values.get(1).getValue(),
                    values.get(2).getValue(), values.get(3).getValue(), color);
            updateComponentStateStyle(component, state, "box-shadow", next.toCss());
        };
        inset.addEventListener("change", event -> commit.run());
        for (Element input : values) input.addEventListener("change", event -> commit.run());
        group.appendChild(fields);
        body.appendChild(group);
    }

    private Element appendShadowInput(Element parent, String labelKey, String value) {
        Element field = Element.init(document.createElement("DIV"));
        field.setAttribute("class", "editor-shadow-input");
        Element label = Element.init(document.createElement("LABEL"));
        label.setAttribute("class", "form-label");
        label.appendChild(OreEditorDom.translation(document, labelKey, null));
        Element input = Element.init(document.createElement("INPUT"));
        input.setAttribute("class", "form-input");
        input.setAttribute("type", "text");
        input.setValue(value);
        field.appendChild(label);
        field.appendChild(input);
        parent.appendChild(field);
        return input;
    }

    private void updateComponentStateStyle(OreComponentNode component, OreComponentNode.VisualState state,
                                           String property, String value) {
        String before = component.stateStyle(state).get(property);
        if (same(before, value)) return;
        component.stateStyle(state).set(property, value);
        updateProject();
        commitHistory(OreEditorHistory.stringValue("UpdateComponentProperty", history.activeMergeKey(), component.id(), component.id(),
                before, value, next -> component.stateStyle(state).set(property, next)));
    }

    private void appendComponentStateStyleSelect(Element body, OreComponentNode component, String label, String property,
                                                 String... values) {
        OreComponentNode.VisualState state = editingVisualState;
        appendSelect(body, label, component.stateStyle(state).get(property), value -> {
            String before = component.stateStyle(state).get(property);
            if (same(before, value)) return;
            component.stateStyle(state).set(property, value);
            updateProject();
            commitHistory(OreEditorHistory.stringValue("UpdateComponentProperty", history.activeMergeKey(), component.id(), component.id(),
                    before, value, next -> component.stateStyle(state).set(property, next)));
        }, values);
    }

    private void appendBoxModelField(Element body, OreCanvasNode node, String property, String labelKey) {
        BoxValue initial = BoxValue.of(node, property);
        Element group = Element.init(document.createElement("FIELDSET"));
        group.setAttribute("class", "editor-box-field");
        Element legend = Element.init(document.createElement("LEGEND"));
        legend.setAttribute("class", "form-label");
        legend.appendChild(OreEditorDom.translation(document, labelKey, null));
        group.appendChild(legend);
        Element linkedChoice = Element.init(document.createElement("LABEL"));
        linkedChoice.setAttribute("class", "choice editor-box-link");
        Element linked = Element.init(document.createElement("INPUT"));
        linked.setAttribute("type", "checkbox");
        linked.setChecked(false);
        linkedChoice.appendChild(linked);
        linkedChoice.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.property.box_link", null));
        group.appendChild(linkedChoice);
        Element fields = Element.init(document.createElement("DIV"));
        fields.setAttribute("class", "editor-box-fields");
        String[] sides = {"top", "right", "bottom", "left"};
        String[] values = {initial.top(), initial.right(), initial.bottom(), initial.left()};
        for (int index = 0; index < sides.length; index++) {
            String side = sides[index];
            Element field = Element.init(document.createElement("DIV"));
            field.setAttribute("class", "editor-box-input");
            field.setAttribute("data-editor-box-side", side);
            Element label = Element.init(document.createElement("LABEL"));
            label.setAttribute("class", "form-label");
            label.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.property.box_" + side, null));
            field.appendChild(label);
            appendLengthControls(field, values[index], "margin".equals(property), next -> {
                if (linked.isChecked()) updateBoxModelSides(node, property, next);
                else updateNodeStyle(node, property + '-' + side, next);
            }, "box:" + node.id() + ':' + side);
            fields.appendChild(field);
        }
        group.appendChild(fields);
        body.appendChild(group);
    }

    private void updateBoxModelSides(OreCanvasNode node, String property, String value) {
        java.util.Map<String, String> before = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
        for (String side : List.of("top", "right", "bottom", "left")) {
            String key = property + '-' + side;
            before.put(key, node.style().get(key));
            after.put(key, value);
        }
        if (before.equals(after)) return;
        applyNodeStyles(node, after);
        updateProject();
        commitHistory(OreEditorHistory.action("UpdateComponentProperty", history.activeMergeKey(), node.id(), node.id(),
                () -> applyNodeStyles(node, before), () -> applyNodeStyles(node, after)));
    }

    private static void applyNodeStyles(OreCanvasNode node, java.util.Map<String, String> styles) {
        styles.forEach(node.style()::set);
    }

    private void appendInput(Element body, String labelKey, String value, Consumer<String> changed) {
        appendInput(body, labelKey, value, changed, ignored -> true);
    }

    private Element appendLengthField(Element body, String labelKey, String value, boolean autoAllowed, Consumer<String> changed) {
        Element group = Element.init(document.createElement("DIV"));
        group.setAttribute("class", "form-group editor-form-group");
        Element label = Element.init(document.createElement("LABEL"));
        label.setAttribute("class", "form-label");
        label.appendChild(OreEditorDom.translation(document, labelKey, null));
        group.appendChild(label);
        appendLengthControls(group, value, autoAllowed, changed, "length:" + System.identityHashCode(group));
        body.appendChild(group);
        return group;
    }

    private static void disableField(Element group, String tooltipKey) {
        if (group == null) return;
        for (Element control : group.querySelectorAll("input, select")) {
            control.setDisabled(true);
            Tooltip.bindTranslation(control, tooltipKey);
        }
    }

    private Element appendLengthControls(Element parent, String value, boolean autoAllowed, Consumer<String> changed, String mergeKey) {
        LengthValue initial = LengthValue.parse(value);
        Element controls = Element.init(document.createElement("DIV"));
        controls.setAttribute("class", autoAllowed ? "editor-length-field" : "editor-length-field editor-length-no-auto");
        Element input = Element.init(document.createElement("INPUT"));
        input.setAttribute("class", "form-input");
        input.setAttribute("type", "number");
        input.setAttribute("step", "any");
        input.setValue(initial.number());
        Element unit = Element.init(document.createElement("SELECT"));
        unit.setAttribute("class", "form-select editor-length-unit");
        for (String candidate : LengthValue.UNITS) {
            Element option = Element.init(document.createElement("OPTION"));
            option.setAttribute("value", candidate);
            option.setTextContent(candidate);
            unit.appendChild(option);
        }
        unit.setValue(initial.unit());
        Element mode = null;
        if (autoAllowed) {
            mode = Element.init(document.createElement("SELECT"));
            mode.setAttribute("class", "form-select editor-length-mode");
            appendLengthModeOption(mode, "value", "ore_editor.apricityui.value.value");
            appendLengthModeOption(mode, "auto", "ore_editor.apricityui.value.auto");
            mode.setValue(initial.auto() ? "auto" : "value");
        }
        Element modeControl = mode;
        Runnable commit = () -> {
            boolean auto = modeControl != null && "auto".equals(modeControl.getValue());
            input.setDisabled(auto);
            unit.setDisabled(auto);
            if (auto) {
                input.setAttribute("class", "form-input");
                changed.accept("auto");
                return;
            }
            String number = input.getValue();
            if (number == null || number.isBlank()) {
                input.setAttribute("class", "form-input");
                changed.accept("");
                return;
            }
            if (!validNumber(number)) {
                input.setAttribute("class", "form-input is-invalid");
                Tooltip.bindTranslation(input, "ore_editor.apricityui.validation.css");
                return;
            }
            input.setAttribute("class", "form-input");
            changed.accept(number.trim() + unit.getValue());
        };
        input.setDisabled(initial.auto());
        unit.setDisabled(initial.auto());
        input.addEventListener("focus", event -> history.beginMerge(mergeKey));
        input.addEventListener("blur", event -> history.endMerge());
        input.addEventListener("keydown", event -> {
            if (event instanceof KeyEvent keyEvent && keyEvent.keyCode == GLFW.GLFW_KEY_ENTER) history.endMerge();
        });
        input.addEventListener("change", event -> commit.run());
        unit.addEventListener("change", event -> commit.run());
        if (mode != null) mode.addEventListener("change", event -> commit.run());
        controls.appendChild(input);
        controls.appendChild(unit);
        if (mode != null) controls.appendChild(mode);
        parent.appendChild(controls);
        return input;
    }

    private void appendLengthModeOption(Element mode, String value, String key) {
        Element option = Element.init(document.createElement("OPTION"));
        option.setAttribute("value", value);
        option.appendChild(OreEditorDom.translation(document, key, null));
        mode.appendChild(option);
    }

    private void appendNumberField(Element body, String labelKey, String value, Double min, Double max, double step,
                                   Consumer<String> changed) {
        Element group = Element.init(document.createElement("DIV"));
        group.setAttribute("class", "form-group editor-form-group");
        Element label = Element.init(document.createElement("LABEL"));
        label.setAttribute("class", "form-label");
        label.appendChild(OreEditorDom.translation(document, labelKey, null));
        Element input = Element.init(document.createElement("INPUT"));
        input.setAttribute("class", "form-input");
        input.setAttribute("type", "number");
        input.setAttribute("step", formatNumber(step));
        if (min != null) input.setAttribute("min", formatNumber(min));
        if (max != null) input.setAttribute("max", formatNumber(max));
        input.setValue(value == null ? "" : value);
        String mergeKey = "number:" + System.identityHashCode(input);
        input.addEventListener("focus", event -> history.beginMerge(mergeKey));
        input.addEventListener("blur", event -> history.endMerge());
        input.addEventListener("keydown", event -> {
            if (event instanceof KeyEvent keyEvent && keyEvent.keyCode == GLFW.GLFW_KEY_ENTER) history.endMerge();
        });
        input.addEventListener("change", event -> {
            String next = input.getValue();
            if (next == null || next.isBlank()) {
                input.setAttribute("class", "form-input");
                changed.accept("");
                return;
            }
            if (!validNumber(next) || (min != null && Double.parseDouble(next) < min)
                    || (max != null && Double.parseDouble(next) > max)) {
                input.setAttribute("class", "form-input is-invalid");
                Tooltip.bindTranslation(input, "ore_editor.apricityui.validation.css");
                return;
            }
            input.setAttribute("class", "form-input");
            changed.accept(next.trim());
        });
        group.appendChild(label);
        group.appendChild(input);
        body.appendChild(group);
    }

    private static boolean validNumber(String value) {
        try {
            return value != null && !value.isBlank() && Double.isFinite(Double.parseDouble(value.trim()));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static boolean allowsAuto(String property) {
        return "width".equals(property) || "height".equals(property) || "flex-basis".equals(property)
                || "left".equals(property) || "right".equals(property) || "top".equals(property) || "bottom".equals(property);
    }

    private void appendInput(Element body, String labelKey, String value, Consumer<String> changed, Predicate<String> valid) {
        Element group = Element.init(document.createElement("DIV"));
        group.setAttribute("class", "form-group editor-form-group");
        Element label = Element.init(document.createElement("LABEL"));
        label.setAttribute("class", "form-label");
        label.appendChild(OreEditorDom.translation(document, labelKey, null));
        Element input = Element.init(document.createElement("INPUT"));
        input.setAttribute("class", "form-input");
        input.setAttribute("type", "text");
        input.setValue(value == null ? "" : value);
        String mergeKey = "input:" + System.identityHashCode(input);
        input.addEventListener("focus", event -> history.beginMerge(mergeKey));
        input.addEventListener("blur", event -> history.endMerge());
        input.addEventListener("keydown", event -> {
            if (event instanceof KeyEvent keyEvent && keyEvent.keyCode == GLFW.GLFW_KEY_ENTER) history.endMerge();
        });
        input.addEventListener("change", event -> {
            String next = input.getValue();
            if (!valid.test(next)) {
                input.setAttribute("class", "form-input is-invalid");
                Tooltip.bindTranslation(input, "ore_editor.apricityui.validation.css");
                return;
            }
            input.setAttribute("class", "form-input");
            changed.accept(next);
        });
        group.appendChild(label);
        group.appendChild(input);
        body.appendChild(group);
    }

    private void appendSelect(Element body, String labelKey, String selected, Consumer<String> changed, String... values) {
        Element group = Element.init(document.createElement("DIV"));
        group.setAttribute("class", "form-group editor-form-group");
        Element label = Element.init(document.createElement("LABEL"));
        label.setAttribute("class", "form-label");
        label.appendChild(OreEditorDom.translation(document, labelKey, null));
        Element select = Element.init(document.createElement("SELECT"));
        select.setAttribute("class", "form-select");
        for (String value : values) {
            Element option = Element.init(document.createElement("OPTION"));
            option.setAttribute("value", value);
            option.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.value." + value.replace('-', '_'), null));
            select.appendChild(option);
        }
        select.setValue(selected == null || selected.isBlank() ? values[0] : selected);
        select.addEventListener("change", event -> changed.accept(select.getValue()));
        group.appendChild(label);
        group.appendChild(select);
        body.appendChild(group);
    }

    private void appendAlignmentSelect(Element body, String labelKey, String axis, String selected,
                                       Consumer<String> changed, String... values) {
        Element group = Element.init(document.createElement("DIV"));
        group.setAttribute("class", "form-group editor-form-group editor-alignment-field");
        Element label = Element.init(document.createElement("LABEL"));
        label.setAttribute("class", "form-label");
        label.appendChild(OreEditorDom.translation(document, labelKey, null));
        Element select = Element.init(document.createElement("SELECT"));
        select.setAttribute("class", "form-select");
        for (String value : values) {
            Element option = Element.init(document.createElement("OPTION"));
            option.setAttribute("value", value);
            option.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.value." + value.replace('-', '_'), null));
            select.appendChild(option);
        }
        String current = selected == null || selected.isBlank() ? values[0] : selected;
        select.setValue(current);
        Element preview = alignmentPreview(axis, current);
        select.addEventListener("change", event -> {
            String next = select.getValue();
            preview.setAttribute("class", alignmentPreviewClass(axis, next));
            changed.accept(next);
        });
        group.appendChild(label);
        group.appendChild(select);
        group.appendChild(preview);
        body.appendChild(group);
    }

    private Element alignmentPreview(String axis, String value) {
        Element preview = Element.init(document.createElement("DIV"));
        preview.setAttribute("class", alignmentPreviewClass(axis, value));
        preview.setAttribute("aria-hidden", "true");
        int markers = "content".equals(axis) ? 4 : 3;
        for (int index = 0; index < markers; index++) {
            Element marker = Element.init(document.createElement("SPAN"));
            marker.setAttribute("class", "editor-alignment-marker");
            preview.appendChild(marker);
        }
        return preview;
    }

    private static String alignmentPreviewClass(String axis, String value) {
        String normalized = value == null || value.isBlank() ? "flex-start" : value;
        return "editor-alignment-preview editor-alignment-" + axis + " editor-alignment-value-" + normalized;
    }

    private void appendSegmented(Element body, String labelKey, String selected, Consumer<String> changed, String... values) {
        Element group = Element.init(document.createElement("DIV"));
        group.setAttribute("class", "form-group editor-form-group");
        Element label = Element.init(document.createElement("LABEL"));
        label.setAttribute("class", "form-label");
        label.appendChild(OreEditorDom.translation(document, labelKey, null));
        Element options = Element.init(document.createElement("DIV"));
        options.setAttribute("class", "editor-segmented");
        options.setAttribute("role", "group");
        List<Element> buttons = new ArrayList<>();
        String current = selected == null || selected.isBlank() ? values[0] : selected;
        for (String value : values) {
            Element button = Element.init(document.createElement("BUTTON"));
            button.setAttribute("type", "button");
            button.setAttribute("data-editor-segmented-value", value);
            button.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.value." + value.replace('-', '_'), null));
            buttons.add(button);
            button.addEventListener("click", event -> {
                for (Element candidate : buttons) {
                    boolean active = value.equals(candidate.getAttribute("data-editor-segmented-value"));
                    candidate.setAttribute("class", active ? "button button-primary button-small" : "button button-secondary button-small");
                    candidate.setAttribute("aria-pressed", Boolean.toString(active));
                }
                changed.accept(value);
            });
            options.appendChild(button);
        }
        for (Element button : buttons) {
            boolean active = current.equals(button.getAttribute("data-editor-segmented-value"));
            button.setAttribute("class", active ? "button button-primary button-small" : "button button-secondary button-small");
            button.setAttribute("aria-pressed", Boolean.toString(active));
        }
        group.appendChild(label);
        group.appendChild(options);
        body.appendChild(group);
    }

    private void updateContainer(OreContainerNode container, Runnable change) {
        FlexState before = FlexState.of(container);
        change.run();
        FlexState after = FlexState.of(container);
        if (before.equals(after)) return;
        updateProject();
        commitHistory(OreEditorHistory.action("UpdateContainerFlex", history.activeMergeKey(), container.id(), container.id(),
                () -> before.apply(container), () -> after.apply(container)));
    }

    private void updateContent(OreComponentNode component, String value) {
        String before = component.content();
        if (same(before, value)) return;
        component.setContent(value);
        updateProject();
        commitHistory(OreEditorHistory.stringValue("UpdateContent", history.activeMergeKey(), component.id(), component.id(),
                before, value, component::setContent));
    }

    private void updateNodeStyle(OreCanvasNode node, String property, String value) {
        String before = node.style().get(property);
        if (same(before, value)) return;
        node.style().set(property, value);
        updateProject();
        commitHistory(OreEditorHistory.stringValue("UpdateComponentProperty", history.activeMergeKey(), node.id(), node.id(),
                before, value, next -> node.style().set(property, next)));
    }

    private static boolean same(String left, String right) {
        String normalizedLeft = left == null || left.isBlank() ? null : left.trim();
        String normalizedRight = right == null || right.isBlank() ? null : right.trim();
        return java.util.Objects.equals(normalizedLeft, normalizedRight);
    }

    private record FlexState(String direction, String wrap, String justifyContent, String alignItems,
                             String alignContent, String gap, String rowGap, String columnGap) {
        static FlexState of(OreContainerNode container) {
            return new FlexState(container.flex().direction(), container.flex().wrap(), container.flex().justifyContent(),
                    container.flex().alignItems(), container.flex().alignContent(), container.flex().gap(),
                    container.flex().rowGap(), container.flex().columnGap());
        }
        void apply(OreContainerNode container) {
            container.flex().setDirection(direction);
            container.flex().setWrap(wrap);
            container.flex().setJustifyContent(justifyContent);
            container.flex().setAlignItems(alignItems);
            container.flex().setAlignContent(alignContent);
            container.flex().setGap(gap);
            container.flex().setRowGap(rowGap);
            container.flex().setColumnGap(columnGap);
        }
    }

    private record ComponentState(boolean absolute, int flowIndex, java.util.Map<String, String> styles,
                                  java.util.Map<String, String> flowSnapshot) {
        static ComponentState of(OreComponentNode component) {
            return new ComponentState(component.absolute(), component.flowIndex(), component.style().properties(),
                    component.hasFlowStyleSnapshot() ? component.flowStyleSnapshot() : java.util.Map.of());
        }
        void apply(OreComponentNode component) {
            for (String property : new ArrayList<>(component.style().properties().keySet())) component.style().set(property, null);
            styles.forEach(component.style()::set);
            if (absolute) component.enterAbsolute(flowIndex);
            else component.leaveAbsolute();
            component.setFlowStyleSnapshot(flowSnapshot);
        }
    }

    private boolean validCssValue(String value) {
        if (value == null || value.isBlank()) return true;
        if (value.length() > 256) return false;
        return value.indexOf(';') < 0 && value.indexOf('{') < 0 && value.indexOf('}') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

    private void toggleAbsolute(com.sighs.apricityui.editor.ore.model.OreComponentNode component, boolean absolute) {
        if (component.locked() || component.absolute() == absolute) return;
        ComponentState before = ComponentState.of(component);
        OreContainerNode parent = component.parent();
        Element target = canvasRenderer == null ? null : canvasRenderer.elementFor(component.id());
        Element parentElement = parent == null || canvasRenderer == null ? null : canvasRenderer.elementFor(parent.id());
        if (absolute && parent != null && target != null && parentElement != null) {
            Element.DOMRect childRect = target.getBoundingClientRect();
            Element.DOMRect parentRect = parentElement.getBoundingClientRect();
            Box parentBox = Box.of(parentElement);
            component.captureFlowStyleSnapshot();
            component.enterAbsolute(parent.children().indexOf(component));
            component.style().set("position", "absolute");
            component.style().set("left", px(childRect.left - parentRect.left - parentBox.getBorderLeft()));
            component.style().set("top", px(childRect.top - parentRect.top - parentBox.getBorderTop()));
            component.style().set("width", px(childRect.width));
            component.style().set("height", px(childRect.height));
        } else if (!absolute && parent != null) {
            component.leaveAbsolute();
            component.restoreFlowStyleSnapshot();
            parent.insert(component.flowIndex(), component);
        }
        updateProject();
        ComponentState after = ComponentState.of(component);
        commitHistory(OreEditorHistory.action("ToggleAbsolute", component.id(), component.id(),
                () -> before.apply(component), () -> after.apply(component)));
        renderMode();
    }

    private void updateAbsoluteOffset(com.sighs.apricityui.editor.ore.model.OreComponentNode component,
                                      String property, String value) {
        if (component.locked()) return;
        ComponentState before = ComponentState.of(component);
        OreAbsoluteConstraints.setOffset(component, property, value);
        ComponentState after = ComponentState.of(component);
        if (before.equals(after)) return;
        updateProject();
        commitHistory(OreEditorHistory.action("UpdateAbsolutePosition", component.id(), component.id(),
                () -> before.apply(component), () -> after.apply(component)));
    }

    private void beginAbsoluteMove(com.sighs.apricityui.editor.ore.model.OreComponentNode component, MouseEvent event) {
        if (component.locked()) return;
        absoluteDragBefore = ComponentState.of(component);
        absoluteDragNode = component.id();
        absoluteDragStartX = event.clientX;
        absoluteDragStartY = event.clientY;
        absoluteDragLeft = number(component.style().get("left"));
        absoluteDragTop = number(component.style().get("top"));
        absoluteDragRight = number(component.style().get("right"));
        absoluteDragBottom = number(component.style().get("bottom"));
        event.preventDefault();
        event.stopPropagation();
    }

    private void moveAbsoluteNode(double x, double y) {
        if (absoluteDragNode == null) return;
        OreCanvasNode node = project.find(absoluteDragNode);
        if (!(node instanceof com.sighs.apricityui.editor.ore.model.OreComponentNode component) || component.locked()) return;
        double dx = x - absoluteDragStartX;
        double dy = y - absoluteDragStartY;
        if (hasPixelOffset(component, "right")) component.style().set("right", px(absoluteDragRight - dx));
        else component.style().set("left", px(absoluteDragLeft + dx));
        if (hasPixelOffset(component, "bottom")) component.style().set("bottom", px(absoluteDragBottom - dy));
        else component.style().set("top", px(absoluteDragTop + dy));
        renderCanvas();
    }

    private void finishAbsoluteNode() {
        if (absoluteDragNode == null) return;
        OreCanvasNode node = project.find(absoluteDragNode);
        absoluteDragNode = null;
        if (node instanceof OreComponentNode component && absoluteDragBefore != null) {
            ComponentState after = ComponentState.of(component);
            if (!absoluteDragBefore.equals(after)) {
                ComponentState before = absoluteDragBefore;
                commitHistory(OreEditorHistory.action("UpdateAbsolutePosition", component.id(), component.id(),
                        () -> before.apply(component), () -> after.apply(component)));
            }
        }
        absoluteDragBefore = null;
        setDirty(true);
    }

    private void beginAbsoluteResize(UUID id, MouseEvent event) {
        OreCanvasNode node = project.find(id);
        Element element = canvasRenderer == null ? null : canvasRenderer.elementFor(id);
        if (!(node instanceof com.sighs.apricityui.editor.ore.model.OreComponentNode component)
                || component.locked() || !component.absolute() || event == null || element == null) return;
        Element.DOMRect bounds = element.getBoundingClientRect();
        absoluteResizeNode = id;
        absoluteResizeStartX = event.clientX;
        absoluteResizeStartY = event.clientY;
        absoluteResizeBefore = ComponentState.of(component);
        absoluteResizeWidth = bounds.width;
        absoluteResizeHeight = bounds.height;
        absoluteResizeRight = number(component.style().get("right"));
        absoluteResizeBottom = number(component.style().get("bottom"));
        event.preventDefault();
        event.stopPropagation();
    }

    private void moveAbsoluteResize(double x, double y) {
        if (absoluteResizeNode == null) return;
        OreCanvasNode node = project.find(absoluteResizeNode);
        if (!(node instanceof com.sighs.apricityui.editor.ore.model.OreComponentNode component) || component.locked()) return;
        double dx = x - absoluteResizeStartX;
        double dy = y - absoluteResizeStartY;
        component.style().set("width", px(Math.max(16, absoluteResizeWidth + dx)));
        component.style().set("height", px(Math.max(16, absoluteResizeHeight + dy)));
        if (hasPixelOffset(component, "right")) {
            component.style().set("right", px(absoluteResizeRight - dx));
        }
        if (hasPixelOffset(component, "bottom")) {
            component.style().set("bottom", px(absoluteResizeBottom - dy));
        }
        renderCanvas();
    }

    private void finishAbsoluteResize() {
        if (absoluteResizeNode == null) return;
        OreCanvasNode node = project.find(absoluteResizeNode);
        absoluteResizeNode = null;
        if (node instanceof OreComponentNode component && absoluteResizeBefore != null) {
            ComponentState after = ComponentState.of(component);
            if (!absoluteResizeBefore.equals(after)) {
                ComponentState before = absoluteResizeBefore;
                commitHistory(OreEditorHistory.action("UpdateAbsolutePosition", component.id(), component.id(),
                        () -> before.apply(component), () -> after.apply(component)));
            }
        }
        absoluteResizeBefore = null;
        setDirty(true);
    }

    private static double number(String value) {
        if (value == null) return 0;
        try { return Double.parseDouble(value.replace("px", "").trim()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static boolean hasPixelOffset(com.sighs.apricityui.editor.ore.model.OreComponentNode component,
                                          String property) {
        String value = component.style().get(property);
        return value != null && value.trim().endsWith("px");
    }

    private static String px(double value) { return Math.round(value * 100.0) / 100.0 + "px"; }

    private void updateProject() {
        setDirty(true);
        renderCanvas();
    }

    private void undo() { applyHistory(history.undo()); }
    private void redo() { applyHistory(history.redo()); }
    private void applyHistory(OreEditorHistory.Result result) {
        if (!result.changed()) return;
        setDirty(!history.isAtSavedRevision());
        session.select(result.selection() == null ? project.root().id() : result.selection());
        renderCanvas();
        renderMode();
        renderBreadcrumb();
        updateHistoryControls();
    }

    private void commitHistory(OreEditorHistory.Command command) {
        history.recordExecuted(command);
        updateHistoryControls();
    }

    private void updateHistoryControls() {
        if (document == null) return;
        Element undo = document.querySelector("#undoButton");
        Element redo = document.querySelector("#redoButton");
        if (undo != null) undo.setDisabled(!history.canUndo());
        if (redo != null) redo.setDisabled(!history.canRedo());
    }

    private void saveProject() {
        boolean saved = openedHtmlPath == null
                ? documentStore.saveProject(projectCodec.write(project)).success()
                : saveOpenedHtml();
        if (saved) {
            history.markSaved();
            setDirty(false);
            ToastManager.showTranslation("ore_editor.apricityui.notice.saved");
        } else ToastManager.showTranslation("ore_editor.apricityui.notice.save_failed");
    }

    private boolean saveOpenedHtml() {
        if (openedHtmlPath == null || !Files.isRegularFile(openedHtmlPath)) return false;
        Path target = openedHtmlPath;
        try {
            Path parent = target.getParent();
            if (parent == null) return false;
            Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            Files.writeString(temporary, htmlExporter.export(project), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            ClientLoader.invalidateStaticResourceCache();
            return true;
        } catch (java.io.IOException | RuntimeException ignored) {
            return false;
        }
    }

    private void setDirty(boolean dirty) {
        session.setDirty(dirty);
        updateDocumentState();
    }

    private void exportHtml() {
        OreEditorDocumentStore.Result result = documentStore.exportHtml(htmlExporter.export(project));
        ToastManager.showTranslation(result.success()
                ? "ore_editor.apricityui.notice.exported" : "ore_editor.apricityui.notice.export_failed");
    }

    private record ThemeToken(String name, String defaultValue, String translationSuffix) { }
    private record ThemeGroup(String translationSuffix, List<String> tokens) { }

    private static ThemeToken themeToken(String name) {
        for (ThemeToken token : THEME_TOKENS) if (token.name().equals(name)) return token;
        return null;
    }

    private void appendThemeTokenInput(Element body, ThemeToken token, String value) {
        Element group = Element.init(document.createElement("DIV"));
        group.setAttribute("class", "form-group editor-form-group editor-theme-token");
        Element label = Element.init(document.createElement("LABEL"));
        label.setAttribute("class", "form-label");
        label.appendChild(OreEditorDom.translation(document,
                "ore_editor.apricityui.theme." + token.translationSuffix(), null));
        Element controls = Element.init(document.createElement("DIV"));
        controls.setAttribute("class", "editor-theme-token-controls");
        Element input = Element.init(document.createElement("INPUT"));
        input.setAttribute("class", "form-input");
        input.setAttribute("type", "text");
        input.setValue(value == null ? "" : value);
        input.addEventListener("focus", event -> history.beginMerge("theme-token:" + token.name()));
        input.addEventListener("blur", event -> history.endMerge());
        input.addEventListener("change", event -> {
            String next = input.getValue();
            if (!validCssValue(next)) {
                input.setAttribute("class", "form-input is-invalid");
                Tooltip.bindTranslation(input, "ore_editor.apricityui.validation.css");
                return;
            }
            input.setAttribute("class", "form-input");
            ColorValue parsed = ColorValue.parse(next);
            Element colorInput = controls.querySelector(".editor-theme-color");
            Element alphaInput = controls.querySelector(".editor-theme-alpha");
            if (colorInput != null) colorInput.setValue(parsed.hex());
            if (alphaInput != null) alphaInput.setValue(formatNumber(parsed.alpha()));
            updateThemeToken(token, next);
            Element resetButton = controls.querySelector(".editor-theme-token-reset");
            if (resetButton != null) resetButton.setDisabled(false);
        });
        if (isHexColor(token.defaultValue())) {
            ColorValue initial = ColorValue.parse(value);
            Element color = Element.init(document.createElement("INPUT"));
            color.setAttribute("class", "editor-theme-color");
            color.setAttribute("type", "color");
            color.setValue(initial.hex());
            Element alpha = Element.init(document.createElement("INPUT"));
            alpha.setAttribute("class", "editor-theme-alpha");
            alpha.setAttribute("type", "range");
            alpha.setAttribute("min", "0");
            alpha.setAttribute("max", "1");
            alpha.setAttribute("step", "0.01");
            alpha.setAttribute("data-tooltip-key", "ore_editor.apricityui.property.alpha");
            alpha.setValue(formatNumber(initial.alpha()));
            Runnable commitPickerColor = () -> {
                double opacity = validNumber(alpha.getValue()) ? Double.parseDouble(alpha.getValue()) : 1D;
                String next = ColorValue.toCss(color.getValue(), opacity);
                input.setValue(next);
                updateThemeToken(token, next);
                Element resetButton = controls.querySelector(".editor-theme-token-reset");
                if (resetButton != null) resetButton.setDisabled(false);
            };
            color.addEventListener("change", event -> commitPickerColor.run());
            alpha.addEventListener("focus", event -> history.beginMerge("theme-token:" + token.name()));
            alpha.addEventListener("blur", event -> history.endMerge());
            alpha.addEventListener("change", event -> commitPickerColor.run());
            controls.appendChild(color);
            controls.appendChild(alpha);
        }
        controls.appendChild(input);
        Element reset = Element.init(document.createElement("BUTTON"));
        reset.setAttribute("class", "button button-secondary button-small editor-theme-token-reset");
        reset.setAttribute("type", "button");
        reset.setDisabled(project.theme().get(token.name()) == null);
        reset.appendChild(OreEditorDom.translation(document, "ore_editor.apricityui.action.reset_token", null));
        reset.addEventListener("click", event -> {
            if (project.theme().get(token.name()) == null) return;
            updateThemeToken(token, null);
            renderMode();
        });
        controls.appendChild(reset);
        group.appendChild(label);
        group.appendChild(controls);
        body.appendChild(group);
    }

    private void updateThemeToken(ThemeToken token, String value) {
        String before = project.theme().get(token.name());
        if (same(before, value)) return;
        project.theme().set(token.name(), value);
        updateProject();
        commitHistory(OreEditorHistory.stringValue("UpdateThemeVariable", history.activeMergeKey(), session.selectedNode(),
                session.selectedNode(), before, value, next -> project.theme().set(token.name(), next)));
    }

    private void applyTheme(java.util.Map<String, String> values) {
        project.theme().reset();
        if (values != null) values.forEach(project.theme()::set);
    }

    private static boolean isHexColor(String value) {
        return value != null && value.matches("#[0-9a-fA-F]{6}");
    }

    record ColorValue(String hex, double alpha) {
        static ColorValue parse(String value) {
            if (value == null || value.isBlank()) return new ColorValue("#000000", 1D);
            String source = value.trim();
            if (source.matches("#[0-9a-fA-F]{3,4}")) {
                String red = source.substring(1, 2);
                String green = source.substring(2, 3);
                String blue = source.substring(3, 4);
                double alpha = source.length() == 5 ? Integer.parseInt(source.substring(4, 5) + source.substring(4, 5), 16) / 255D : 1D;
                return new ColorValue("#" + red + red + green + green + blue + blue, alpha);
            }
            if (source.matches("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?")) {
                double alpha = source.length() == 9 ? Integer.parseInt(source.substring(7, 9), 16) / 255D : 1D;
                return new ColorValue(source.substring(0, 7), alpha);
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "(?i)^rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})(?:\\s*,\\s*(0(?:\\.\\d+)?|1(?:\\.0+)?))?\\s*\\)$")
                    .matcher(source);
            if (!matcher.matches()) return new ColorValue("#000000", 1D);
            int red = Integer.parseInt(matcher.group(1));
            int green = Integer.parseInt(matcher.group(2));
            int blue = Integer.parseInt(matcher.group(3));
            if (red > 255 || green > 255 || blue > 255) return new ColorValue("#000000", 1D);
            double alpha = matcher.group(4) == null ? 1D : Double.parseDouble(matcher.group(4));
            return new ColorValue(String.format(java.util.Locale.ROOT, "#%02x%02x%02x", red, green, blue), alpha);
        }

        static String toCss(String hex, double alpha) {
            String normalized = isHexColor(hex) ? hex.toLowerCase(java.util.Locale.ROOT) : "#000000";
            double clamped = Math.max(0D, Math.min(1D, alpha));
            if (clamped >= 1D) return normalized;
            int red = Integer.parseInt(normalized.substring(1, 3), 16);
            int green = Integer.parseInt(normalized.substring(3, 5), 16);
            int blue = Integer.parseInt(normalized.substring(5, 7), 16);
            return "rgba(" + red + ", " + green + ", " + blue + ", " + formatNumber(clamped) + ')';
        }
    }

    static boolean validCssColor(String value) {
        if (value == null || value.isBlank()) return true;
        String color = value.trim();
        return color.matches("#[0-9a-fA-F]{3,8}") || color.matches("(?i)(transparent|currentcolor|[a-z]+)")
                || color.matches("(?i)(rgb|rgba|hsl|hsla|lab|lch|oklab|oklch|color|var)\\([^;{}\\r\\n]+\\)");
    }

    static ShadowValue parseShadow(String value) {
        if (value == null || value.isBlank()) return new ShadowValue(false, "0px", "0px", "0px", "0px", "#000000");
        String source = value.trim();
        boolean inset = source.startsWith("inset ");
        if (inset) source = source.substring("inset ".length()).trim();
        String[] tokens = source.split("\\s+", 3);
        if (tokens.length < 3 || !isLength(tokens[0]) || !isLength(tokens[1])) {
            return new ShadowValue(inset, "0px", "0px", "0px", "0px", "#000000");
        }
        String remainder = tokens[2];
        List<String> lengths = new ArrayList<>(List.of(tokens[0], tokens[1]));
        while (lengths.size() < 4) {
            int split = remainder.indexOf(' ');
            String candidate = split < 0 ? remainder : remainder.substring(0, split);
            if (!isLength(candidate)) break;
            lengths.add(candidate);
            remainder = split < 0 ? "" : remainder.substring(split + 1).trim();
        }
        if (!validCssColor(remainder)) return new ShadowValue(inset, "0px", "0px", "0px", "0px", "#000000");
        while (lengths.size() < 4) lengths.add("0px");
        return new ShadowValue(inset, lengths.get(0), lengths.get(1), lengths.get(2), lengths.get(3), remainder);
    }

    private static boolean isLength(String value) {
        return value != null && value.matches("(?i)-?(?:\\d+|\\d*\\.\\d+)(?:px|em|rem|%|vh|vw|vmin|vmax|pt|cm|mm|in|pc|ch|ex)?");
    }

    record ShadowValue(boolean inset, String offsetX, String offsetY, String blur, String spread, String color) {
        String toCss() {
            String prefix = inset ? "inset " : "";
            return prefix + offsetX + ' ' + offsetY + ' ' + blur + ' ' + spread + ' ' + color;
        }
    }

    record LengthValue(String number, String unit, boolean auto) {
        private static final List<String> UNITS = List.of("px", "rem", "em", "%", "vw", "vh");

        static LengthValue parse(String value) {
            if (value != null && "auto".equalsIgnoreCase(value.trim())) return new LengthValue("", "px", true);
            if (value == null || value.isBlank()) return new LengthValue("", "px", false);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))(px|rem|em|%|vw|vh)?$", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(value.trim());
            if (!matcher.matches()) return new LengthValue("", "px", false);
            String unit = matcher.group(2);
            return new LengthValue(matcher.group(1), unit == null ? "px" : unit.toLowerCase(java.util.Locale.ROOT), false);
        }
    }

    record BoxValue(String top, String right, String bottom, String left) {
        static BoxValue of(OreCanvasNode node, String property) {
            String shorthand = node == null ? null : node.style().get(property);
            BoxValue parsed = parse(shorthand);
            if (node == null) return parsed;
            return new BoxValue(valueOr(node.style().get(property + "-top"), parsed.top),
                    valueOr(node.style().get(property + "-right"), parsed.right),
                    valueOr(node.style().get(property + "-bottom"), parsed.bottom),
                    valueOr(node.style().get(property + "-left"), parsed.left));
        }
        static BoxValue parse(String shorthand) {
            if (shorthand == null || shorthand.isBlank()) return new BoxValue("", "", "", "");
            String[] values = shorthand.trim().split("\\s+");
            if (values.length < 1 || values.length > 4) return new BoxValue("", "", "", "");
            return switch (values.length) {
                case 1 -> new BoxValue(values[0], values[0], values[0], values[0]);
                case 2 -> new BoxValue(values[0], values[1], values[0], values[1]);
                case 3 -> new BoxValue(values[0], values[1], values[2], values[1]);
                default -> new BoxValue(values[0], values[1], values[2], values[3]);
            };
        }
        private static String valueOr(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    private void beginPaletteDrag(OreComponentDefinition definition, double x, double y) {
        drag.begin(definition, x, y);
        if (!drag.active() || document == null) return;
        removeDragGhost();
        dragGhost = Element.init(document.createElement("DIV"));
        dragGhost.setAttribute("class", "panel editor-drag-ghost");
        dragGhost.appendChild(OreEditorDom.translation(document, definition.nameKey(), null));
        document.body.appendChild(dragGhost);
        positionDragGhost(x, y);
    }

    private void movePaletteDrag(double x, double y) {
        if (!drag.active()) return;
        drag.move(x, y);
        positionDragGhost(x, y);
        updateDropFeedback(x, y);
    }

    private void beginNodeDrag(UUID id, MouseEvent event) {
        OreCanvasNode node = project.find(id);
        if (node == null || node.locked() || node == project.root() || event == null) return;
        if (node instanceof com.sighs.apricityui.editor.ore.model.OreComponentNode component && component.absolute()) {
            beginAbsoluteMove(component, event);
            return;
        }
        movingNode = id;
        updateDropFeedback(event.clientX, event.clientY);
        event.preventDefault();
        event.stopPropagation();
    }

    private void moveNodeDrag(double x, double y) {
        if (movingNode != null) updateDropFeedback(x, y);
    }

    private void finishNodeDrag(double x, double y) {
        if (movingNode == null) return;
        updateDropFeedback(x, y);
        OreCanvasNode node = project.find(movingNode);
        OreContainerNode target = dropTarget;
        OreFlexInsertionResolver.Insertion insertion = dropInsertion;
        movingNode = null;
        clearDropFeedback();
        if (node == null || node.locked() || node.parent() == null || target == null
                || structureLocked(node.parent()) || structureLocked(target)) return;
        OreContainerNode source = node.parent();
        int index = insertionIndex(target, insertion);
        int sourceIndex = source.children().indexOf(node);
        if (source == target && sourceIndex >= 0 && sourceIndex < index) index--;
        target.insert(index, node);
        int targetIndex = target.children().indexOf(node);
        session.select(node.id());
        setDirty(true);
        renderCanvas();
        renderBreadcrumb();
        commitHistory(OreEditorHistory.action(source == target ? "MoveNode" : "ReparentNode", node.id(), node.id(),
                () -> {
                    target.remove(node);
                    source.insert(sourceIndex, node);
                }, () -> {
                    source.remove(node);
                    target.insert(targetIndex, node);
                }));
    }

    private void positionDragGhost(double x, double y) {
        if (dragGhost == null) return;
        dragGhost.setAttribute("style", "left:" + (x + 12) + "px;top:" + (y + 12) + "px;");
        document.markDirty(dragGhost, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.HITTEST);
    }

    private void finishPaletteDrag(double x, double y) {
        if (!drag.active()) return;
        updateDropFeedback(x, y);
        OreContainerNode target = dropTarget;
        OreFlexInsertionResolver.Insertion insertion = dropInsertion;
        drag.end((definition, point) -> addPaletteNode(definition, target, insertion));
        removeDragGhost();
        clearDropFeedback();
    }

    private void addPaletteNode(OreComponentDefinition definition, OreContainerNode target,
                                OreFlexInsertionResolver.Insertion insertion) {
        if (target == null || structureLocked(target)) return;
        OreCanvasNode node = definition.createNode();
        int index = insertionIndex(target, insertion);
        target.insert(index, node);
        session.select(node.id());
        setDirty(true);
        renderCanvas();
        renderBreadcrumb();
        commitHistory(OreEditorHistory.action("AddNode", target.id(), node.id(),
                () -> target.remove(node), () -> target.insert(index, node)));
    }

    private void updateDropFeedback(double x, double y) {
        dropTarget = dropContainerAt(x, y);
        if (movingNode != null && !canMoveTo(movingNode, dropTarget)) dropTarget = null;
        dropInsertion = resolveInsertion(dropTarget, x, y);
        if (canvasRenderer != null) canvasRenderer.renderInsertion(dropInsertion);
    }

    private void clearDropFeedback() {
        dropTarget = null;
        dropInsertion = null;
        if (canvasRenderer != null) canvasRenderer.renderInsertion(null);
    }

    private OreFlexInsertionResolver.Insertion resolveInsertion(OreContainerNode target, double x, double y) {
        if (target == null || canvasRenderer == null) return null;
        List<OreFlexInsertionResolver.Item> items = new ArrayList<>();
        for (OreCanvasNode child : target.children()) {
            Element element = canvasRenderer.elementFor(child.id());
            if (element == null) continue;
            Element.DOMRect bounds = element.getBoundingClientRect();
            items.add(new OreFlexInsertionResolver.Item(child.id(),
                    new OreFlexInsertionResolver.Bounds(bounds.left, bounds.top, bounds.width, bounds.height),
                    "absolute".equals(element.getComputedStyle().position)));
        }
        return insertionResolver.resolve(target.flex().direction(), target.flex().wrap(), items, x, y);
    }

    private int insertionIndex(OreContainerNode target, OreFlexInsertionResolver.Insertion insertion) {
        if (insertion == null || insertion.beforeId() == null) return target.children().size();
        List<OreCanvasNode> children = target.children();
        for (int index = 0; index < children.size(); index++) {
            if (insertion.beforeId().equals(children.get(index).id())) return index;
        }
        return children.size();
    }

    private OreContainerNode dropContainerAt(double x, double y) {
        if (canvasRenderer == null || document == null) return null;
        Element canvas = document.querySelector("#editorCanvas");
        if (canvas == null) return null;
        Element.DOMRect rect = canvas.getBoundingClientRect();
        if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) return null;
        UUID hit = hitTester.hit(canvasRenderer.elements(), x, y);
        OreCanvasNode node = hit == null ? project.root() : project.find(hit);
        while (node != null && (!(node instanceof OreContainerNode container) || structureLocked(container))) node = node.parent();
        return node instanceof OreContainerNode container ? container : project.root();
    }

    private boolean canMoveTo(UUID nodeId, OreContainerNode target) {
        OreCanvasNode node = project.find(nodeId);
        if (node == null || node.locked() || node == project.root() || node.parent() == null
                || structureLocked(node.parent()) || target == null || structureLocked(target)) return false;
        for (OreContainerNode current = target; current != null; current = current.parent()) {
            if (current == node) return false;
        }
        return true;
    }

    /** The root is locked against structural deletion, but remains the editable canvas drop target. */
    private static boolean structureLocked(OreContainerNode container) {
        return container != null && !container.acceptsStructuralChildren();
    }

    private void removeDragGhost() {
        if (dragGhost != null) dragGhost.remove();
        dragGhost = null;
    }

    private void resizeSidebar(double pointerX) {
        if (!resizing || document == null) return;
        Element sidebar = document.querySelector("#editorSidebar");
        if (sidebar == null) return;
        double width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, document.getViewport().layoutWidth() - pointerX));
        sidebar.setAttribute("style", "flex-basis:" + width + "px;width:" + width + "px;");
        document.markDirty(sidebar, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
    }

    private void stopResize(Element handle) {
        if (!resizing) return;
        resizing = false;
        handle.setAttribute("class", "editor-resize-handle");
        document.markDirty(handle, Drawer.REPAINT | Drawer.HITTEST);
    }
}
