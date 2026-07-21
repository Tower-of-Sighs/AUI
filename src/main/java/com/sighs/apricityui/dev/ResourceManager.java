package com.sighs.apricityui.dev;

import com.sighs.apricityui.dev.resource.ResourceCreateDialog;
import com.sighs.apricityui.dev.resource.ResourcePath;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.init.Operation;
import com.sighs.apricityui.instance.ClientLoader;
import com.sighs.apricityui.instance.Loader;
import net.minecraft.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ResourceManager {
    private static final String PATH = "devtools/resource.html";
    private static final String ROOT_PATH = "";

    private static final String FOLDER_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"4\" y=\"12\" width=\"32\" height=\"22\" fill=\"#8b5cf6\"/><rect x=\"4\" y=\"8\" width=\"14\" height=\"6\" fill=\"#6d28d9\"/><rect x=\"4\" y=\"14\" width=\"32\" height=\"2\" fill=\"#6d28d9\"/></svg>";
    private static final String FILE_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"6\" y=\"4\" width=\"28\" height=\"32\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"10\" y=\"12\" width=\"20\" height=\"2\" fill=\"#8b5cf6\"/><rect x=\"10\" y=\"18\" width=\"16\" height=\"2\" fill=\"#8b5cf6\"/><rect x=\"10\" y=\"24\" width=\"20\" height=\"2\" fill=\"#8b5cf6\"/><rect x=\"10\" y=\"30\" width=\"12\" height=\"2\" fill=\"#8b5cf6\"/></svg>";
    private static final String IMAGE_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"6\" y=\"4\" width=\"28\" height=\"32\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"10\" y=\"12\" width=\"20\" height=\"14\" fill=\"#8b5cf6\" opacity=\"0.2\"/><circle cx=\"16\" cy=\"18\" r=\"3\" fill=\"#8b5cf6\"/><path d=\"M10 24l6-6 4 4 6-8 4 6v4H10z\" fill=\"#8b5cf6\"/></svg>";
    private static final String LOCK_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"10\" y=\"18\" width=\"20\" height=\"16\" fill=\"#8b5cf6\"/><path d=\"M14 18v-5a6 6 0 0 1 12 0v5\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><circle cx=\"20\" cy=\"26\" r=\"2\" fill=\"#fff\"/></svg>";
    private static final String ARCHIVE_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"6\" y=\"4\" width=\"28\" height=\"32\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"18\" y=\"6\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/><rect x=\"18\" y=\"14\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/><rect x=\"18\" y=\"22\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/></svg>";
    private static final String CONFIG_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"6\" y=\"4\" width=\"28\" height=\"32\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><circle cx=\"20\" cy=\"20\" r=\"6\" fill=\"none\" stroke=\"#8b5cf6\" stroke-width=\"2\"/><rect x=\"18\" y=\"10\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/><rect x=\"18\" y=\"26\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/></svg>";

    private static Document toolDocument;
    private static Document previewDocument;
    private static String previewDocumentPath = "";
    private static FolderNode root = new FolderNode("ROOT", ROOT_PATH);
    private static String currentPath = ROOT_PATH;
    private static SelectedItem selectedItem;
    private static final List<String> history = new ArrayList<>(List.of(ROOT_PATH));
    private static int historyIndex;
    private static final Set<String> expandedPaths = new LinkedHashSet<>();
    private static final ResourceCreateDialog createDialog = new ResourceCreateDialog();

    private ResourceManager() {
    }

    public static boolean isOpen() {
        return toolDocument != null && !toolDocument.isDisposed();
    }

    public static void toggle() {
        if (isOpen()) {
            close();
        } else {
            open();
        }
    }

    public static void open() {
        if (!isOpen()) {
            List<Document> existing = Document.get(PATH);
            toolDocument = existing.isEmpty() ? Document.create(PATH) : existing.get(existing.size() - 1);
        }
        if (toolDocument == null) return;
        toolDocument.setReloadPersistent(true);
        refresh();
    }

    public static void close() {
        createDialog.close();
        closePreviewDocument();
        if (toolDocument != null && !toolDocument.isDisposed()) {
            toolDocument.remove();
        }
        toolDocument = null;
        resetNavigation();
    }

    public static void refresh() {
        if (!isOpen()) {
            List<Document> existing = Document.get(PATH);
            if (existing.isEmpty()) return;
            toolDocument = existing.get(existing.size() - 1);
            toolDocument.setReloadPersistent(true);
        }
        render(ClientLoader.listFinalStaticResources());
    }

    private static void render(List<Loader.StaticResourceEntry> entries) {
        if (toolDocument == null || toolDocument.body == null) return;
        root = buildTree(entries);
        if (findFolder(currentPath) == null) {
            currentPath = ROOT_PATH;
            selectedItem = null;
            resetHistory(ROOT_PATH);
        } else if (selectedItem != null && !selectedItem.existsIn(root)) {
            selectedItem = null;
        }

        bindShellActions();
        renderNavigation();
        renderTree();
        renderFiles(true);
        renderDetail();
        markDirty();
    }

    private static void bindShellActions() {
        bindOnce("#backButton", event -> goBack());
        bindOnce("#upButton", event -> goUp());
        bindOnce("#newButton", event -> createDialog.open(toolDocument, currentPath, ClientLoader::reload));
    }

    private static void bindOnce(String selector, java.util.function.Consumer<Event> listener) {
        Element element = toolDocument.querySelector(selector);
        if (element == null || "1".equals(element.getAttribute("data-java-bound"))) return;
        element.setAttribute("data-java-bound", "1");
        element.addEventListener("click", listener);
    }

    private static void goBack() {
        if (historyIndex <= 0) return;
        historyIndex--;
        navigate(history.get(historyIndex), false);
    }

    private static void goUp() {
        if (currentPath.isBlank()) return;
        navigate(parentPath(currentPath), true);
    }

    private static void navigate(String path, boolean recordHistory) {
        String normalized = normalizePath(path);
        if (findFolder(normalized) == null) return;
        if (recordHistory && !normalized.equals(currentPath)) {
            while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
            history.add(normalized);
            historyIndex = history.size() - 1;
        }
        currentPath = normalized;
        selectedItem = null;
        expandAncestors(normalized);
        renderNavigation();
        renderTree();
        renderFiles(true);
        renderDetail();
        markDirty();
    }

    private static void select(SelectedItem item) {
        selectedItem = item;
        renderTree();
        renderFiles(false);
        renderDetail();
        markDirty();
    }

    private static void selectFromTree(Loader.StaticResourceEntry entry) {
        if (entry == null) return;
        String parent = parentPath(entry.path());
        if (!parent.equals(currentPath)) {
            while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
            history.add(parent);
            historyIndex = history.size() - 1;
            currentPath = parent;
            expandAncestors(parent);
        }
        selectedItem = SelectedItem.file(entry);
        renderNavigation();
        renderTree();
        renderFiles(false);
        renderDetail();
        markDirty();
    }

    private static void renderNavigation() {
        Element nav = toolDocument.querySelector("#navPath");
        if (nav == null) return;
        nav.clearChildren();

        Element rootLink = textElement("SPAN", "ROOT");
        rootLink.addEventListener("click", event -> navigate(ROOT_PATH, true));
        nav.append(rootLink);

        if (currentPath.isBlank()) return;
        StringBuilder path = new StringBuilder();
        String[] parts = currentPath.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isBlank()) continue;
            if (!path.isEmpty()) path.append('/');
            path.append(part);
            String targetPath = path.toString();

            Element separator = textElement("SPAN", "▸");
            separator.setAttribute("class", "nav-sep");
            nav.append(separator);

            Element link = textElement("SPAN", part.toUpperCase(Locale.ROOT));
            if (i == parts.length - 1) link.setAttribute("class", "current");
            link.addEventListener("click", event -> navigate(targetPath, true));
            nav.append(link);
        }
    }

    private static void renderTree() {
        Element container = toolDocument.querySelector("#treeContainer");
        if (container == null) return;
        container.clearChildren();
        appendTreeChildren(container, root, 0);
    }

    private static void appendTreeChildren(Element parent, FolderNode folder, int depth) {
        int index = 0;
        for (FolderNode child : folder.sortedFolders()) {
            boolean expanded = expandedPaths.contains(child.path);
            boolean hasChildren = !child.folders.isEmpty() || !child.files.isEmpty();
            Element item = createElement("DIV", "tree-item anim-in");
            if (child.path.equals(currentPath)) item.setAttribute("class", "tree-item selected anim-in");
            item.setAttribute("style", "padding-left:" + (24 + depth * 16) + "px;animation-delay:" + (index * 0.04d) + "s;");
            item.setAttribute("data-path", child.path);
            item.addEventListener("click", event -> navigate(child.path, true));

            Element toggle = textElement("DIV", "▾");
            toggle.setAttribute("class", hasChildren ? (expanded ? "tree-toggle" : "tree-toggle collapsed") : "tree-toggle empty");
            toggle.addEventListener("click", event -> {
                event.stopPropagation();
                if (expandedPaths.contains(child.path)) expandedPaths.remove(child.path);
                else expandedPaths.add(child.path);
                renderTree();
                markDirty();
            });
            item.append(toggle);
            item.append(iconElement("tree-icon", FOLDER_ICON));
            item.append(textElement("SPAN", child.name.toUpperCase(Locale.ROOT)));
            parent.append(item);

            if (hasChildren) {
                Element wrapper = createElement("DIV", expanded ? "tree-children-wrapper expanded" : "tree-children-wrapper");
                Element inner = createElement("DIV", "tree-children-inner");
                if (expanded) appendTreeChildren(inner, child, depth + 1);
                wrapper.append(inner);
                parent.append(wrapper);
            }
            index++;
        }

        for (Loader.StaticResourceEntry entry : folder.sortedFiles()) {
            Element item = createElement("DIV", "tree-item anim-in");
            if (selectedItem != null && selectedItem.matches(entry)) {
                item.setAttribute("class", "tree-item selected anim-in");
            }
            item.setAttribute("style", "padding-left:" + (24 + depth * 16) + "px;animation-delay:" + (index * 0.04d) + "s;");
            item.setAttribute("data-path", safe(entry.path()));
            item.setAttribute("data-resource-key", resourceKey(entry));
            item.addEventListener("click", event -> selectFromTree(entry));
            item.append(textElement("DIV", "▾", "tree-toggle empty"));
            item.append(iconElement("tree-icon", iconFor(entry)));
            item.append(textElement("SPAN", fileName(entry.path()).toUpperCase(Locale.ROOT)));
            parent.append(item);
            index++;
        }
    }

    private static void renderFiles(boolean animate) {
        Element grid = toolDocument.querySelector("#fileGrid");
        Element title = toolDocument.querySelector("#contentTitle");
        Element count = toolDocument.querySelector("#contentCount");
        if (grid == null || title == null || count == null) return;

        FolderNode folder = findFolder(currentPath);
        grid.clearChildren();
        if (folder == null) return;

        List<FolderNode> folders = folder.sortedFolders();
        List<Loader.StaticResourceEntry> files = folder.sortedFiles();
        int total = folders.size() + files.size();
        title.setTextContent(folder.name.toUpperCase(Locale.ROOT));
        count.setTextContent(total + (total == 1 ? " ITEM" : " ITEMS"));

        if (total == 0) {
            Element empty = textElement("DIV", "EMPTY");
            empty.setAttribute("style", "color:var(--gray);text-align:center;padding:40px;");
            grid.append(empty);
            return;
        }

        int index = 0;
        for (FolderNode child : folders) {
            SelectedItem folderItem = SelectedItem.folder(child);
            Element card = fileCard(child.name, "--", FOLDER_ICON, folderItem, animate, index++);
            card.setAttribute("data-path", child.path);
            card.addEventListener("dblclick", event -> navigate(child.path, true));
            grid.append(card);
        }
        for (Loader.StaticResourceEntry entry : files) {
            SelectedItem fileItem = SelectedItem.file(entry);
            Element card = fileCard(fileName(entry.path()), formatSize(entry.sizeBytes()), iconFor(entry), fileItem, animate, index++);
            card.setAttribute("data-path", safe(entry.path()));
            card.setAttribute("data-resource-key", resourceKey(entry));
            card.addEventListener("dblclick", event -> openPreview(entry));
            grid.append(card);
        }
    }

    private static Element fileCard(String name, String meta, String icon, SelectedItem item, boolean animate, int index) {
        boolean selected = selectedItem != null && selectedItem.key.equals(item.key);
        String classes = "file-card" + (selected ? " selected" : "") + (animate ? " entering" : "");
        Element card = createElement("DIV", classes);
        if (animate) card.setAttribute("style", "animation-delay:" + (index * 0.05d) + "s;");
        card.addEventListener("click", event -> select(item));
        card.append(iconElement("file-icon", icon));
        card.append(textElement("DIV", name.toUpperCase(Locale.ROOT), "file-name"));
        card.append(textElement("DIV", meta, "file-meta"));
        return card;
    }

    private static void renderDetail() {
        Element panel = toolDocument.querySelector("#detailPanel");
        Element content = toolDocument.querySelector("#detailContent");
        if (panel == null || content == null) return;
        content.clearChildren();

        if (selectedItem == null) {
            panel.setAttribute("class", "detail-panel");
            content.append(textElement("DIV", "SELECT FILE TO VIEW DETAILS", "detail-empty"));
            return;
        }

        panel.setAttribute("class", "detail-panel active");
        Element detail = createElement("DIV", "detail-content");
        Element icon = createElement("DIV", "detail-icon");
        Loader.StaticResourceEntry entry = selectedItem.entry;
        if (entry != null && isImagePreviewable(entry)) {
            Element image = createElement("IMG", "detail-preview-image");
            image.setAttribute("src", "/" + safe(entry.path()));
            image.setAttribute("alt", fileName(entry.path()));
            image.setAttribute("style", "width:56px;height:56px;object-fit:contain;");
            icon.append(image);
        } else {
            icon.setInnerHTML(selectedItem.folder != null ? FOLDER_ICON : iconFor(entry));
        }
        detail.append(icon);
        detail.append(textElement("DIV", selectedItem.name.toUpperCase(Locale.ROOT), "detail-name"));

        if (selectedItem.folder != null) {
            detail.append(detailRow("TYPE", "FOLDER"));
            detail.append(detailRow("SIZE", "--"));
            detail.append(detailRow("LAYER", "--"));
            detail.append(detailRow("PATH", displayPath(selectedItem.path)));
        } else {
            String type = safe(entry.extension()).isBlank() ? "FILE" : entry.extension().toUpperCase(Locale.ROOT);
            detail.append(detailRow("TYPE", type));
            detail.append(detailRow("SIZE", formatSize(entry.sizeBytes())));
            detail.append(detailRow("LAYER", layerLabel(entry.layer())));
            detail.append(detailRow("PATH", safe(entry.path())));
        }
        appendActions(detail, selectedItem);
        content.append(detail);
    }

    private static Element detailRow(String label, String value) {
        Element row = createElement("DIV", "detail-row");
        row.append(textElement("SPAN", label, "detail-label"));
        row.append(textElement("SPAN", value, "detail-value"));
        return row;
    }

    private static void appendActions(Element detail, SelectedItem item) {
        Element actions = createElement("DIV", "detail-tags");
        actions.append(textElement("DIV", "ACTIONS", "detail-tags-title"));
        actions.append(action("COPY PATH", event -> {
            Operation.setClipboardText(item.path);
            ToastManager.show("Path copied");
        }));

        if (item.entry != null) {
            Loader.StaticResourceEntry entry = item.entry;
            if (isPreviewable(entry)) {
                actions.append(action("PREVIEW", event -> openPreview(entry)));
            }
            String source = resolveSourceForCopy(entry);
            if (!source.isBlank()) {
                actions.append(action("COPY SOURCE", event -> {
                    Operation.setClipboardText(source);
                    ToastManager.show("Source copied");
                }));
            }
            if (resolveLocalPath(entry) != null) {
                actions.append(action("OPEN FOLDER", event -> browseLocalFile(entry)));
            }
        }
        detail.append(actions);
    }

    private static Element action(String label, java.util.function.Consumer<Event> listener) {
        Element action = textElement("SPAN", label, "tag");
        action.addEventListener("click", listener);
        return action;
    }

    private static void openPreview(Loader.StaticResourceEntry entry) {
        if (entry == null) return;
        if (isImagePreviewable(entry)) {
            selectedItem = SelectedItem.file(entry);
            renderFiles(false);
            renderDetail();
            markDirty();
            return;
        }
        if (isHtmlPreviewable(entry)) openHtmlPreview(entry);
    }

    private static void openHtmlPreview(Loader.StaticResourceEntry entry) {
        String path = entry == null ? "" : safe(entry.path());
        if (path.isBlank() || PATH.equals(path)) return;
        if (previewDocument != null && path.equals(previewDocumentPath) && !previewDocument.isDisposed()) return;
        closePreviewDocument();
        Document created = Document.create(path);
        if (created == null) {
            ToastManager.show("HTML preview unavailable");
            return;
        }
        created.setReloadPersistent(true);
        previewDocument = created;
        previewDocumentPath = path;
    }

    private static void closePreviewDocument() {
        if (previewDocument != null && !previewDocument.isDisposed()) previewDocument.remove();
        previewDocument = null;
        previewDocumentPath = "";
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
        if (entry == null || entry.layer() == Loader.ResourceLayer.RESOURCE_PACK) return null;
        String sourceRoot = safe(entry.sourceRoot());
        if (sourceRoot.isBlank()) return null;
        Path rootPath = Path.of(sourceRoot).toAbsolutePath().normalize();
        if (!Files.exists(rootPath)) return null;
        Path resolved = rootPath;
        for (String part : normalizePath(entry.path()).split("/")) {
            if (!part.isBlank()) resolved = resolved.resolve(part);
        }
        resolved = resolved.normalize();
        return resolved.startsWith(rootPath) ? resolved : null;
    }

    private static FolderNode buildTree(List<Loader.StaticResourceEntry> entries) {
        FolderNode treeRoot = new FolderNode("ROOT", ROOT_PATH);
        if (entries == null) return treeRoot;
        for (Loader.StaticResourceEntry entry : entries) {
            if (entry == null) continue;
            String path = normalizePath(entry.path());
            if (path.isBlank()) continue;
            String[] parts = path.split("/");
            FolderNode cursor = treeRoot;
            StringBuilder folderPath = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                String name = parts[i];
                if (name.isBlank()) continue;
                if (!folderPath.isEmpty()) folderPath.append('/');
                folderPath.append(name);
                String nextPath = folderPath.toString();
                cursor = cursor.folders.computeIfAbsent(name, ignored -> new FolderNode(name, nextPath));
            }
            cursor.files.add(entry);
        }
        return treeRoot;
    }

    private static FolderNode findFolder(String path) {
        String normalized = normalizePath(path);
        if (normalized.isBlank()) return root;
        FolderNode cursor = root;
        for (String part : normalized.split("/")) {
            cursor = cursor.folders.get(part);
            if (cursor == null) return null;
        }
        return cursor;
    }

    private static void expandAncestors(String path) {
        StringBuilder cursor = new StringBuilder();
        for (String part : normalizePath(path).split("/")) {
            if (part.isBlank()) continue;
            if (!cursor.isEmpty()) cursor.append('/');
            cursor.append(part);
            expandedPaths.add(cursor.toString());
        }
    }

    private static String iconFor(Loader.StaticResourceEntry entry) {
        if (entry == null) return FILE_ICON;
        String extension = safe(entry.extension()).toLowerCase(Locale.ROOT);
        if (isImagePreviewable(entry)) return IMAGE_ICON;
        if (extension.equals("lock")) return LOCK_ICON;
        if (extension.equals("zip") || extension.equals("jar") || extension.equals("rar") || extension.equals("7z")) return ARCHIVE_ICON;
        if (extension.equals("json") || extension.equals("toml") || extension.equals("properties") || extension.equals("cfg") || extension.equals("conf")) return CONFIG_ICON;
        return FILE_ICON;
    }

    private static boolean isImagePreviewable(Loader.StaticResourceEntry entry) {
        String extension = entry == null ? "" : safe(entry.extension()).toLowerCase(Locale.ROOT);
        return extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg")
                || extension.equals("bmp") || extension.equals("gif") || extension.equals("webp");
    }

    private static boolean isHtmlPreviewable(Loader.StaticResourceEntry entry) {
        String extension = entry == null ? "" : safe(entry.extension()).toLowerCase(Locale.ROOT);
        return extension.equals("html") || extension.equals("htm");
    }

    private static boolean isPreviewable(Loader.StaticResourceEntry entry) {
        return isImagePreviewable(entry) || (isHtmlPreviewable(entry) && !PATH.equals(safe(entry.path())));
    }

    private static String resolveSourceForCopy(Loader.StaticResourceEntry entry) {
        if (entry == null) return "";
        return safe(entry.sourceDetail()).isBlank() ? safe(entry.sourceRoot()) : safe(entry.sourceDetail());
    }

    private static String layerLabel(Loader.ResourceLayer layer) {
        if (layer == null) return "--";
        return switch (layer) {
            case RESOURCE_PACK -> "PACK";
            case LOCAL_FOLDER -> "LOCAL";
            case DEV_FOLDER -> "DEV";
        };
    }

    private static String formatSize(long bytes) {
        return ResourcePath.formatSize(bytes);
    }

    private static Element createElement(String tagName, String className) {
        Element element = Element.init(toolDocument.createElement(tagName));
        if (className != null && !className.isBlank()) element.setAttribute("class", className);
        return element;
    }

    private static Element textElement(String tagName, String text) {
        return textElement(tagName, text, "");
    }

    private static Element textElement(String tagName, String text, String className) {
        Element element = createElement(tagName, className);
        element.innerText = safe(text);
        return element;
    }

    private static Element iconElement(String className, String icon) {
        Element element = createElement("DIV", className);
        element.setInnerHTML(icon);
        return element;
    }

    private static String resourceKey(Loader.StaticResourceEntry entry) {
        if (entry == null) return "";
        return safe(entry.path()) + "|" + (entry.layer() == null ? "" : entry.layer().name());
    }

    private static String normalizePath(String path) {
        return ResourcePath.normalize(path);
    }

    private static String parentPath(String path) {
        return ResourcePath.parent(path);
    }

    private static String fileName(String path) {
        return ResourcePath.fileName(path);
    }

    private static String displayPath(String path) {
        String normalized = normalizePath(path);
        return normalized.isBlank() ? "/" : normalized;
    }

    private static String safe(String value) {
        return ResourcePath.safe(value);
    }

    private static void markDirty() {
        if (toolDocument == null || toolDocument.body == null) return;
        toolDocument.markDirty(toolDocument.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
    }

    private static void resetNavigation() {
        root = new FolderNode("ROOT", ROOT_PATH);
        currentPath = ROOT_PATH;
        selectedItem = null;
        expandedPaths.clear();
        resetHistory(ROOT_PATH);
    }

    private static void resetHistory(String path) {
        history.clear();
        history.add(path);
        historyIndex = 0;
    }

    private static final class FolderNode {
        private final String name;
        private final String path;
        private final Map<String, FolderNode> folders = new LinkedHashMap<>();
        private final List<Loader.StaticResourceEntry> files = new ArrayList<>();

        private FolderNode(String name, String path) {
            this.name = safe(name);
            this.path = normalizePath(path);
        }

        private List<FolderNode> sortedFolders() {
            return folders.values().stream()
                    .sorted(Comparator.comparing(folder -> folder.name.toLowerCase(Locale.ROOT)))
                    .toList();
        }

        private List<Loader.StaticResourceEntry> sortedFiles() {
            return files.stream()
                    .sorted(Comparator.comparing(entry -> fileName(entry.path()).toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    private static final class SelectedItem {
        private final String key;
        private final String name;
        private final String path;
        private final FolderNode folder;
        private final Loader.StaticResourceEntry entry;

        private SelectedItem(String key, String name, String path, FolderNode folder, Loader.StaticResourceEntry entry) {
            this.key = key;
            this.name = name;
            this.path = normalizePath(path);
            this.folder = folder;
            this.entry = entry;
        }

        private static SelectedItem folder(FolderNode folder) {
            return new SelectedItem("folder|" + folder.path, folder.name, folder.path, folder, null);
        }

        private static SelectedItem file(Loader.StaticResourceEntry entry) {
            return new SelectedItem("file|" + resourceKey(entry), fileName(entry.path()), entry.path(), null, entry);
        }

        private boolean matches(Loader.StaticResourceEntry other) {
            return entry != null && resourceKey(entry).equals(resourceKey(other));
        }

        private boolean existsIn(FolderNode tree) {
            if (folder != null) return findFolder(path) != null;
            if (entry == null) return false;
            return findEntry(tree, resourceKey(entry));
        }

        private static boolean findEntry(FolderNode folder, String key) {
            for (Loader.StaticResourceEntry entry : folder.files) {
                if (resourceKey(entry).equals(key)) return true;
            }
            for (FolderNode child : folder.folders.values()) {
                if (findEntry(child, key)) return true;
            }
            return false;
        }
    }
}
