package com.sighs.apricityui.dev;

import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.*;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.style.Position;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class ResourceManager {
    private static final String PATH = "devtools/resource-manager.html";
    private static Document toolDocument = null;
    private static String filterText = "";
    private static ViewMode viewMode = ViewMode.ALL;
    private static final Set<String> collapsedFolderPaths = new LinkedHashSet<>();
    private static boolean contextMenuVisible = false;
    private static int contextMenuX = 0;
    private static int contextMenuY = 0;
    private static Loader.StaticResourceEntry contextMenuEntry = null;
    private static final Map<String, Element> fileRowByKey = new HashMap<>();
    private static String selectedRowKey = "";
    private static Loader.StaticResourceEntry previewEntry = null;
    private static Document previewDocument = null;
    private static String previewDocumentPath = "";

    private enum ViewMode {
        ALL,
        FOLDER
    }

    private ResourceManager() {
    }

    public static boolean isOpen() {
        return toolDocument != null && !Document.get(PATH).isEmpty();
    }

    public static void toggle() {
        if (Document.get(PATH).isEmpty()) {
            toolDocument = Document.create(PATH);
            refresh();
            return;
        }
        toolDocument = null;
        filterText = "";
        collapsedFolderPaths.clear();
        clearContextMenuState();
        Document.remove(PATH);
    }

    public static void refresh() {
        if (toolDocument == null || toolDocument.body == null) return;
        Element title = toolDocument.querySelector(".title");
        Element count = toolDocument.querySelector(".count");
        Element rows = toolDocument.querySelector(".rows");
        Element manager = toolDocument.querySelector(".manager");
        Element filterInput = toolDocument.querySelector(".filter-input");
        Element refreshBtn = toolDocument.querySelector(".refresh-btn");
        Element closeBtn = toolDocument.querySelector(".close-btn");
        Element modeAllBtn = toolDocument.querySelector(".mode-all");
        Element modeFolderBtn = toolDocument.querySelector(".mode-folder");
        Element previewPanel = toolDocument.querySelector(".preview-panel");
        Element previewName = toolDocument.querySelector(".preview-name");
        Element previewImage = toolDocument.querySelector(".preview-image");
        Element previewPath = toolDocument.querySelector(".preview-info-path");
        Element previewExt = toolDocument.querySelector(".preview-info-ext");
        Element previewLayer = toolDocument.querySelector(".preview-info-layer");
        Element previewSize = toolDocument.querySelector(".preview-info-size");
        Element previewSource = toolDocument.querySelector(".preview-info-source");
        Element previewStatus = toolDocument.querySelector(".preview-status");
        Element previewStatusPath = toolDocument.querySelector(".preview-status-path");
        Element closePreviewBtn = toolDocument.querySelector(".preview-close-btn");
        if (title == null || count == null || rows == null || manager == null || filterInput == null || refreshBtn == null || closeBtn == null
                || modeAllBtn == null || modeFolderBtn == null || previewPanel == null || previewName == null
                || previewImage == null || previewPath == null || previewExt == null || previewLayer == null
                || previewSize == null || previewSource == null || previewStatus == null
                || previewStatusPath == null || closePreviewBtn == null) return;

        List<Loader.StaticResourceEntry> allEntries = Loader.listFinalStaticResources();
        List<Loader.StaticResourceEntry> displayEntries = applyFilter(allEntries, filterText);
        title.innerText = "Resource Manager";
        count.innerText = displayEntries.size() + " / " + allEntries.size();

        filterInput.value = filterText;
        filterInput.setAttribute("value", filterText);
        bindFilterInput(filterInput);
        bindRefreshButton(refreshBtn);
        bindCloseButton(closeBtn);
        bindModeButtons(modeAllBtn, modeFolderBtn);
        bindMenuDismiss(toolDocument.body);
        bindClosePreviewButton(closePreviewBtn);
        modeAllBtn.setAttribute("class", viewMode == ViewMode.ALL ? "mode-btn mode-all active" : "mode-btn mode-all");
        modeFolderBtn.setAttribute("class", viewMode == ViewMode.FOLDER ? "mode-btn mode-folder active" : "mode-btn mode-folder");

        clearChildren(rows);
        fileRowByKey.clear();
        selectedRowKey = "";
        if (viewMode == ViewMode.ALL) {
            int index = 1;
            for (Loader.StaticResourceEntry entry : displayEntries) {
                rows.append(buildFileRow(index, 0, entry, false, manager));
                index++;
            }
        } else {
            FolderNode root = buildFolderTree(displayEntries);
            appendFolderRows(rows, root, 0, manager);
        }
        updateRowSelection(contextMenuVisible ? contextMenuEntry : null);
        clearContextMenus(manager);
        appendContextMenu(manager);
        updatePreviewSection(previewPanel, previewName, previewImage, previewPath, previewExt, previewLayer, previewSize, previewSource);
        updatePreviewStatus(previewStatus, previewStatusPath);

        markDirty(toolDocument);
    }

    public static boolean devOpenContextMenuForFirstResource() {
        if (toolDocument == null || toolDocument.body == null) return false;
        List<Loader.StaticResourceEntry> allEntries = Loader.listFinalStaticResources();
        List<Loader.StaticResourceEntry> displayEntries = applyFilter(allEntries, filterText);
        if (displayEntries.isEmpty()) return false;
        viewMode = ViewMode.ALL;
        contextMenuVisible = true;
        contextMenuEntry = displayEntries.getFirst();
        contextMenuX = 8;
        contextMenuY = 40;
        refresh();
        return true;
    }

    private static List<Loader.StaticResourceEntry> applyFilter(List<Loader.StaticResourceEntry> entries, String filter) {
        if (entries == null || entries.isEmpty()) return List.of();
        String normalizedFilter = normalizeFilter(filter);
        if (normalizedFilter.isBlank()) return entries;

        List<Loader.StaticResourceEntry> result = new ArrayList<>();
        for (Loader.StaticResourceEntry entry : entries) {
            if (entry == null) continue;
            String haystack = (entry.path() + "|" + entry.extension() + "|" + entry.layer())
                    .toLowerCase(Locale.ROOT);
            if (haystack.contains(normalizedFilter)) result.add(entry);
        }
        return result;
    }

    private static Element buildFileRow(int index, int depth, Loader.StaticResourceEntry entry, boolean inFolderMode, Element manager) {
        Element row = createToolElement("DIV");
        row.setAttribute("class", "row");
        row.setAttribute("data-base-class", "row");
        row.addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouseEvent)) return;
            if (mouseEvent.button != 1) {
                if (contextMenuVisible && mouseEvent.button == 0) {
                    clearContextMenuState();
                    refreshContextMenuOnly();
                }
                return;
            }
            showContextMenu(entry, manager, mouseEvent);
            event.stopPropagation();
            refreshContextMenuOnly();
        });
        String rowKey = rowKeyOf(entry);
        row.setAttribute("data-resource-key", rowKey);
        fileRowByKey.put(rowKey, row);

        row.append(cell("c-index", index > 0 ? String.valueOf(index) : ""));
        String name = inFolderMode ? fileNameOf(entry.path()) : safe(entry.path());
        Element pathCell = cell("c-path", name);
        applyDepthIndent(pathCell, depth);
        row.append(pathCell);
        row.append(cell("c-ext", safe(entry.extension())));
        row.append(cell("c-layer", switch (entry.layer()) {
            case RESOURCE_PACK -> "PACK";
            case LOCAL_FOLDER -> "LOCAL";
            case DEV_FOLDER -> "DEV";
        }));
        row.append(cell("c-size", formatSize(entry.sizeBytes())));
        return row;
    }

    private static Element buildFolderRow(int depth, String path, String name, boolean collapsed) {
        Element row = createToolElement("DIV");
        row.setAttribute("class", "row folder-row");
        row.setAttribute("data-base-class", "row folder-row");
        row.addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) return;
            if (collapsedFolderPaths.contains(path)) collapsedFolderPaths.remove(path);
            else collapsedFolderPaths.add(path);
            refresh();
        });
        row.append(cell("c-index", ""));
        Element pathCell = cell("c-path folder-name", (collapsed ? "► " : "▼ ") + name + "/");
        applyDepthIndent(pathCell, depth);
        row.append(pathCell);
        row.append(cell("c-ext", ""));
        row.append(cell("c-layer", ""));
        row.append(cell("c-size", ""));
        return row;
    }

    private static FolderNode buildFolderTree(List<Loader.StaticResourceEntry> entries) {
        FolderNode root = new FolderNode("", "");
        for (Loader.StaticResourceEntry entry : entries) {
            if (entry == null || entry.path() == null || entry.path().isBlank()) continue;
            String[] parts = entry.path().split("/");
            FolderNode current = root;
            StringBuilder fullPathBuilder = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                if (part == null || part.isBlank()) continue;
                if (!fullPathBuilder.isEmpty()) fullPathBuilder.append('/');
                fullPathBuilder.append(part);
                String folderPath = fullPathBuilder.toString();
                current = current.children.computeIfAbsent(part, ignored -> new FolderNode(part, folderPath));
            }
            current.files.add(entry);
        }
        return root;
    }

    private static void appendFolderRows(Element rows, FolderNode node, int depth, Element manager) {
        if (node == null || rows == null) return;

        if (!node.path.isBlank()) {
            boolean collapsed = collapsedFolderPaths.contains(node.path);
            rows.append(buildFolderRow(depth, node.path, node.name, collapsed));
            if (collapsed) return;
        }

        for (FolderNode child : node.children.values()) {
            appendFolderRows(rows, child, depth + 1, manager);
        }
        int fileDepth = depth + 1;
        for (Loader.StaticResourceEntry file : node.files) {
            rows.append(buildFileRow(0, fileDepth, file, true, manager));
        }
    }

    private static void bindMenuDismiss(Element root) {
        if (root == null) return;
        if ("1".equals(root.getAttribute("data-menu-dismiss-bound"))) return;
        root.setAttribute("data-menu-dismiss-bound", "1");
        root.addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) return;
            if (!contextMenuVisible) return;
            if (isInsideContextMenu(event.target)) return;
            clearContextMenuState();
            refreshContextMenuOnly();
        });
        root.addEventListener("scroll", _ -> {
            if (!contextMenuVisible) return;
            clearContextMenuState();
            refreshContextMenuOnly();
        });
    }

    private static void appendContextMenu(Element manager) {
        if (manager == null || !contextMenuVisible || contextMenuEntry == null) return;
        Loader.StaticResourceEntry selectedEntry = contextMenuEntry;

        Element menu = createToolElement("DIV");
        menu.setAttribute("class", "context-menu");
        menu.setAttribute("style", "left:" + contextMenuX + "px;top:" + contextMenuY + "px;");
        boolean previewable = isPreviewable(selectedEntry);
        menu.append(menuItem("Preview", !previewable, ignored -> {
            openPreview(selectedEntry);
            clearContextMenuState();
            refresh();
        }));
        menu.append(menuItem("Copy Path", false, ignored -> {
            Operation.setClipboardText(safe(selectedEntry.path()));
            ToastManager.show("Path copied");
            clearContextMenuState();
            refreshContextMenuOnly();
        }));
        menu.append(menuItem("Copy Source", false, ignored -> {
            Operation.setClipboardText(resolveSourceForCopy(selectedEntry));
            ToastManager.show("Source copied");
            clearContextMenuState();
            refreshContextMenuOnly();
        }));
        menu.append(menuItem("Browse Local File", false, ignored -> {
            browseLocalFile(selectedEntry);
            clearContextMenuState();
            refreshContextMenuOnly();
        }));
        menu.addEventListener("mousedown", Event::stopPropagation);
        manager.append(menu);
    }

    private static Element menuItem(String label, boolean disabled, Consumer<Event> action) {
        Element item = createToolElement("DIV");
        item.setAttribute("class", disabled ? "context-item disabled" : "context-item");
        Element text = createToolElement("SPAN");
        text.setAttribute("class", "context-item-label");
        text.innerText = safe(label);
        item.append(text);
        if (!disabled) {
            item.addEventListener("mousedown", event -> {
                if (!(event instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) return;
                event.stopPropagation();
                action.accept(event);
            });
        }
        return item;
    }

    private static void clearContextMenus(Element manager) {
        if (manager == null) return;
        ArrayList<Element> snapshot = new ArrayList<>(manager.children);
        for (Element child : snapshot) {
            if (child == null) continue;
            String cls = safe(child.getAttribute("class"));
            if (hasClass(cls, "context-menu")) {
                child.remove();
            }
        }
    }

    private static void showContextMenu(Loader.StaticResourceEntry entry, Element manager, MouseEvent event) {
        if (entry == null || manager == null || event == null) return;
        Position managerPos = Position.of(manager);
        contextMenuX = Math.max(0, (int) Math.round(event.clientX - managerPos.x));
        contextMenuY = Math.max(0, (int) Math.round(event.clientY - managerPos.y));
        contextMenuEntry = entry;
        contextMenuVisible = true;
    }

    private static void refreshContextMenuOnly() {
        if (toolDocument == null || toolDocument.body == null) return;
        Element manager = toolDocument.querySelector(".manager");
        if (manager == null) return;
        updateRowSelection(contextMenuVisible ? contextMenuEntry : null);
        clearContextMenus(manager);
        appendContextMenu(manager);
        toolDocument.markDirty(toolDocument.body, Drawer.REPAINT | Drawer.REORDER);
    }

    private static void updateRowSelection(Loader.StaticResourceEntry entry) {
        String nextKey = entry == null ? "" : rowKeyOf(entry);
        for (Element row : new ArrayList<>(fileRowByKey.values())) {
            if (row == null) continue;
            String baseClass = safe(row.getAttribute("data-base-class"));
            row.setAttribute("class", baseClass.isBlank() ? "row" : baseClass);
        }
        selectedRowKey = "";
        if (nextKey.isBlank()) return;
        Element current = fileRowByKey.get(nextKey);
        if (current != null) {
            String baseClass = safe(current.getAttribute("data-base-class"));
            if (baseClass.isBlank()) baseClass = "row";
            current.setAttribute("class", baseClass + " row-selected");
            selectedRowKey = nextKey;
        }
    }

    private static String rowKeyOf(Loader.StaticResourceEntry entry) {
        if (entry == null) return "";
        return safe(entry.path()) + "|" + (entry.layer() == null ? "" : entry.layer().name());
    }

    private static void browseLocalFile(Loader.StaticResourceEntry entry) {
        Path localPath = resolveLocalPath(entry);
        if (localPath == null || !Files.exists(localPath)) {
            ToastManager.show("No local file source");
            return;
        }
        Path openTarget = Files.isDirectory(localPath) ? localPath : localPath.getParent();
        if (openTarget == null) openTarget = localPath;
        try {
            Util.getPlatform().openFile(openTarget.toFile());
            ToastManager.show("Opened local folder");
        } catch (Exception ignored) {
            ToastManager.show("Failed to open folder");
        }
    }

    private static Path resolveLocalPath(Loader.StaticResourceEntry entry) {
        if (entry == null) return null;
        if (entry.layer() == Loader.ResourceLayer.RESOURCE_PACK) return null;
        String sourceRoot = safe(entry.sourceRoot());
        if (sourceRoot.isBlank()) return null;
        Path root = Path.of(sourceRoot).toAbsolutePath().normalize();
        if (!Files.exists(root)) return null;
        String relative = safe(entry.path());
        Path resolved = root;
        for (String part : relative.split("/")) {
            if (part.isBlank()) continue;
            resolved = resolved.resolve(part);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(root)) return null;
        return resolved;
    }

    private static String resolveSourceForCopy(Loader.StaticResourceEntry entry) {
        if (entry == null) return "";
        if (!safe(entry.sourceDetail()).isBlank()) return entry.sourceDetail();
        return safe(entry.sourceRoot());
    }

    private static boolean isInsideContextMenu(Element target) {
        Element cursor = target;
        while (cursor != null) {
            String cls = safe(cursor.getAttribute("class"));
            if (hasClass(cls, "context-menu")) return true;
            cursor = cursor.parentElement;
        }
        return false;
    }

    private static boolean hasClass(String classes, String expected) {
        if (classes == null || classes.isBlank() || expected == null || expected.isBlank()) return false;
        String[] tokens = classes.split("\\s+");
        for (String token : tokens) {
            if (expected.equals(token)) return true;
        }
        return false;
    }

    private static void clearContextMenuState() {
        contextMenuVisible = false;
        contextMenuX = 0;
        contextMenuY = 0;
        contextMenuEntry = null;
    }

    private static Element cell(String className, String text) {
        Element span = createToolElement("SPAN");
        span.setAttribute("class", "cell " + className);
        span.innerText = text;
        return span;
    }

    private static void bindFilterInput(Element filterInput) {
        if ("1".equals(filterInput.getAttribute("data-bound"))) return;
        filterInput.setAttribute("data-bound", "1");
        filterInput.addEventListener("keydown", event -> {
            if (!(event instanceof KeyEvent keyEvent)) return;
            if (!"Enter".equals(keyEvent.key)) return;
            filterText = normalizeFilter(filterInput.value);
            refresh();
        });
        filterInput.addEventListener("blur", _ -> {
            filterText = normalizeFilter(filterInput.value);
            refresh();
        });
    }

    private static void bindRefreshButton(Element refreshBtn) {
        if ("1".equals(refreshBtn.getAttribute("data-bound"))) return;
        refreshBtn.setAttribute("data-bound", "1");
        refreshBtn.addEventListener("mousedown", _ -> refresh());
    }

    private static void bindCloseButton(Element closeBtn) {
        if ("1".equals(closeBtn.getAttribute("data-bound"))) return;
        closeBtn.setAttribute("data-bound", "1");
        closeBtn.addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) return;
            toggle();
            event.stopPropagation();
        });
    }

    private static void bindClosePreviewButton(Element closePreviewBtn) {
        if ("1".equals(closePreviewBtn.getAttribute("data-bound"))) return;
        closePreviewBtn.setAttribute("data-bound", "1");
        closePreviewBtn.addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) return;
            clearPreviewState();
            refresh();
            event.stopPropagation();
        });
    }

    private static void bindModeButtons(Element modeAllBtn, Element modeFolderBtn) {
        if (!"1".equals(modeAllBtn.getAttribute("data-bound"))) {
            modeAllBtn.setAttribute("data-bound", "1");
            modeAllBtn.addEventListener("mousedown", _ -> {
                if (viewMode == ViewMode.ALL) return;
                viewMode = ViewMode.ALL;
                refresh();
            });
        }
        if (!"1".equals(modeFolderBtn.getAttribute("data-bound"))) {
            modeFolderBtn.setAttribute("data-bound", "1");
            modeFolderBtn.addEventListener("mousedown", _ -> {
                if (viewMode == ViewMode.FOLDER) return;
                viewMode = ViewMode.FOLDER;
                refresh();
            });
        }
    }

    private static String formatSize(long sizeBytes) {
        if (sizeBytes < 0) return "-";
        if (sizeBytes < 1024) return sizeBytes + " B";
        double kb = sizeBytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }

    private static void clearChildren(Element parent) {
        ArrayList<Element> snapshot = new ArrayList<>(parent.children);
        snapshot.forEach(Element::remove);
    }

    private static String normalizeFilter(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String fileNameOf(String path) {
        String safePath = safe(path);
        int idx = safePath.lastIndexOf('/');
        if (idx < 0 || idx == safePath.length() - 1) return safePath;
        return safePath.substring(idx + 1);
    }

    private static void applyDepthIndent(Element pathCell, int depth) {
        if (pathCell == null) return;
        int safeDepth = Math.max(0, depth);
        if (safeDepth == 0) {
            pathCell.setAttribute("style", "padding-left:0px;");
            return;
        }
        int px = safeDepth * 6;
        pathCell.setAttribute("style", "padding-left:" + px + "px;");
    }

    private static Element createToolElement(String tagName) {
        if (toolDocument == null) {
            throw new IllegalStateException("[ResourceManager] Not initialized, failed to create element: " + tagName);
        }
        return Element.init(toolDocument.createElement(tagName));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void markDirty(Document document) {
        if (document == null || document.body == null) return;
        document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
    }

    private static void updatePreviewSection(
            Element previewPanel,
            Element previewName,
            Element previewImage,
            Element previewPath,
            Element previewExt,
            Element previewLayer,
            Element previewSize,
            Element previewSource
    ) {
        Loader.StaticResourceEntry current = previewEntry;
        if (!isImagePreviewable(current)) {
            previewPanel.setAttribute("class", "preview-panel hidden");
            previewPanel.setAttribute("style", "display:none;");
            previewName.innerText = "";
            previewImage.setAttribute("style", "");
            previewPath.innerText = "Path: -";
            previewExt.innerText = "Ext: -";
            previewLayer.innerText = "Layer: -";
            previewSize.innerText = "Size: -";
            previewSource.innerText = "Source: -";
            return;
        }

        previewPanel.setAttribute("class", "preview-panel");
        previewPanel.setAttribute("style", "");
        previewName.innerText = fileNameOf(current.path());
        previewImage.setAttribute("style", previewImageStyle(current));
        previewPath.innerText = "Path: " + safe(current.path());
        previewExt.innerText = "Ext: " + safe(current.extension());
        previewLayer.innerText = "Layer: " + layerLabel(current.layer());
        previewSize.innerText = "Size: " + formatSize(current.sizeBytes());
        String sourceText = safe(resolveSourceForCopy(current));
        previewSource.innerText = "Source: " + (sourceText.isBlank() ? "-" : sourceText);
    }

    private static void updatePreviewStatus(Element previewStatus, Element previewStatusPath) {
        Loader.StaticResourceEntry current = previewEntry;
        if (current == null || !isPreviewStatusVisible(current)) {
            previewStatus.setAttribute("class", "preview-status hidden");
            previewStatusPath.innerText = "";
            return;
        }
        previewStatus.setAttribute("class", "preview-status");
        previewStatusPath.innerText = safe(current.path());
    }

    private static String previewImageStyle(Loader.StaticResourceEntry entry) {
        if (entry == null) return "";
        String path = safe(entry.path());
        if (path.isBlank()) return "";
        String escapedPath = path.replace("\"", "%22");
        return "background-image:url(\"/" + escapedPath + "\");";
    }

    private static boolean isImagePreviewable(Loader.StaticResourceEntry entry) {
        if (entry == null) return false;
        String ext = safe(entry.extension()).toLowerCase(Locale.ROOT);
        return ext.equals("png")
                || ext.equals("jpg")
                || ext.equals("jpeg")
                || ext.equals("bmp")
                || ext.equals("gif")
                || ext.equals("webp");
    }

    private static boolean isHtmlPreviewable(Loader.StaticResourceEntry entry) {
        if (entry == null) return false;
        String ext = safe(entry.extension()).toLowerCase(Locale.ROOT);
        return ext.equals("html") || ext.equals("htm");
    }

    private static boolean isPreviewable(Loader.StaticResourceEntry entry) {
        return isImagePreviewable(entry) || isHtmlPreviewable(entry);
    }

    private static boolean isPreviewStatusVisible(Loader.StaticResourceEntry entry) {
        if (isImagePreviewable(entry)) return true;
        if (!isHtmlPreviewable(entry)) return false;
        return previewDocument != null && safe(entry.path()).equals(previewDocumentPath);
    }

    private static String layerLabel(Loader.ResourceLayer layer) {
        if (layer == null) return "-";
        return switch (layer) {
            case RESOURCE_PACK -> "PACK";
            case LOCAL_FOLDER -> "LOCAL";
            case DEV_FOLDER -> "DEV";
        };
    }

    private static void clearPreviewState() {
        previewEntry = null;
        closePreviewDocument();
    }

    private static void openPreview(Loader.StaticResourceEntry entry) {
        if (entry == null) return;
        if (isHtmlPreviewable(entry)) {
            openHtmlPreview(entry);
            return;
        }
        if (isImagePreviewable(entry)) {
            closePreviewDocument();
            previewEntry = entry;
        }
    }

    private static void openHtmlPreview(Loader.StaticResourceEntry entry) {
        String path = safe(entry.path());
        if (path.isBlank()) return;
        if (previewDocument != null && path.equals(previewDocumentPath)) {
            previewEntry = entry;
            return;
        }
        closePreviewDocument();
        Document created = Document.create(path);
        if (created == null) {
            ToastManager.show("HTML preview unavailable");
            return;
        }
        previewDocument = created;
        previewDocumentPath = path;
        previewEntry = entry;
    }

    private static void closePreviewDocument() {
        if (previewDocument == null) {
            previewDocumentPath = "";
            return;
        }
        previewDocument.remove();
        previewDocument = null;
        previewDocumentPath = "";
    }

    private static class FolderNode {
        private final String name;
        private final String path;
        private final LinkedHashMap<String, FolderNode> children = new LinkedHashMap<>();
        private final List<Loader.StaticResourceEntry> files = new ArrayList<>();

        private FolderNode(String name, String path) {
            this.name = safe(name);
            this.path = safe(path);
        }
    }
}
