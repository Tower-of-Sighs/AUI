package com.sighs.apricityui.dev;

import com.sighs.apricityui.dev.resource.ResourceCreateDialog;
import com.sighs.apricityui.dev.resource.ResourceMetaDialog;
import com.sighs.apricityui.dev.resource.ResourceFontAsset;
import com.sighs.apricityui.dev.resource.ResourcePath;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.dev.resource.ResourceReferenceDialog;
import com.sighs.apricityui.ui.ToastManager;
import com.sighs.apricityui.ui.ContextMenu;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.world.WorldWindow;
import com.sighs.apricityui.layout.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

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
import com.sighs.apricityui.parser.HTML;

public final class ResourceManager {
    private static final String PATH = "devtools/resource.html";
    private static final String INTERNAL_IMAGE_PREVIEW_PATH = "devtools/resource-preview-image.html";
    private static final String ROOT_PATH = "";
    private static final int TREE_ANIMATION_LIMIT = 32;

    private static final String FOLDER_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"4\" y=\"12\" width=\"32\" height=\"22\" fill=\"#8b5cf6\"/><rect x=\"4\" y=\"8\" width=\"14\" height=\"6\" fill=\"#6d28d9\"/><rect x=\"4\" y=\"14\" width=\"32\" height=\"2\" fill=\"#6d28d9\"/></svg>";
    private static final String FILE_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"6\" y=\"4\" width=\"28\" height=\"32\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"10\" y=\"12\" width=\"20\" height=\"2\" fill=\"#8b5cf6\"/><rect x=\"10\" y=\"18\" width=\"16\" height=\"2\" fill=\"#8b5cf6\"/><rect x=\"10\" y=\"24\" width=\"20\" height=\"2\" fill=\"#8b5cf6\"/><rect x=\"10\" y=\"30\" width=\"12\" height=\"2\" fill=\"#8b5cf6\"/></svg>";
    private static final String IMAGE_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"6\" y=\"4\" width=\"28\" height=\"32\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"10\" y=\"12\" width=\"20\" height=\"14\" fill=\"#8b5cf6\" opacity=\"0.2\"/><circle cx=\"16\" cy=\"18\" r=\"3\" fill=\"#8b5cf6\"/><path d=\"M10 24l6-6 4 4 6-8 4 6v4H10z\" fill=\"#8b5cf6\"/></svg>";
    private static final String LOCK_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"10\" y=\"18\" width=\"20\" height=\"16\" fill=\"#8b5cf6\"/><path d=\"M14 18v-5a6 6 0 0 1 12 0v5\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><circle cx=\"20\" cy=\"26\" r=\"2\" fill=\"#fff\"/></svg>";
    private static final String ARCHIVE_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"6\" y=\"4\" width=\"28\" height=\"32\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"18\" y=\"6\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/><rect x=\"18\" y=\"14\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/><rect x=\"18\" y=\"22\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/></svg>";
    private static final String CONFIG_ICON = "<svg viewBox=\"0 0 40 40\" fill=\"none\"><rect x=\"6\" y=\"4\" width=\"28\" height=\"32\" fill=\"none\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><circle cx=\"20\" cy=\"20\" r=\"6\" fill=\"none\" stroke=\"#8b5cf6\" stroke-width=\"2\"/><rect x=\"18\" y=\"10\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/><rect x=\"18\" y=\"26\" width=\"4\" height=\"4\" fill=\"#8b5cf6\"/></svg>";

    private static Document toolDocument;
    private static FolderNode root = new FolderNode("ROOT", ROOT_PATH);
    private static String currentPath = ROOT_PATH;
    private static SelectedItem selectedItem;
    private static final List<String> history = new ArrayList<>(List.of(ROOT_PATH));
    private static int historyIndex;
    private static final Set<String> expandedPaths = new LinkedHashSet<>();
    /** Direct references avoid selector misses falling back to a full tree rebuild. */
    private static final Map<String, TreeBranch> treeBranches = new LinkedHashMap<>();
    private static final ResourceCreateDialog createDialog = new ResourceCreateDialog();
    private static final ResourcePreviewDialog previewDialog = new ResourcePreviewDialog();
    private static final ResourceMetaDialog metaDialog = new ResourceMetaDialog();
    private static final ResourceReferenceDialog referenceDialog = new ResourceReferenceDialog();
    private static WorldWindow worldWindow;

    private ResourceManager() {
    }

    public static boolean isOpen() {
        return toolDocument != null && !toolDocument.isDisposed();
    }

    /** Switches an already-open resource manager when its display-mode config changes. */
    public static void reconcileConfiguredMode() {
        if (!isOpen()) return;

        boolean wantsWorldWindow = AuiServices.config().resourceManagerWorldWindow();
        boolean isWorldWindow = worldWindow != null && toolDocument == worldWindow.document;
        if (wantsWorldWindow == isWorldWindow) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (wantsWorldWindow
                && (minecraft == null || minecraft.level == null
                || minecraft.screen != null || minecraft.player == null)) {
            return;
        }

        close();
        open();
    }

    public static void toggle() {
        if (isOpen()) {
            close();
        } else {
            open();
        }
    }

    public static void open() {
        if (shouldOpenWorldWindow()) {
            openWorldWindow();
            return;
        }
        if (worldWindow != null) closeWorldWindow();
        if (!isOpen()) {
            List<Document> existing = Document.get(PATH);
            toolDocument = existing.isEmpty() ? Document.create(PATH) : existing.get(existing.size() - 1);
        }
        if (toolDocument == null) return;
        toolDocument.setReloadPersistent(true);
        refresh();
    }

    private static boolean shouldOpenWorldWindow() {
        Minecraft minecraft = Minecraft.getInstance();
        return AuiServices.config().resourceManagerWorldWindow()
                && minecraft != null
                && minecraft.level != null
                && minecraft.screen == null
                && minecraft.player != null;
    }

    private static void openWorldWindow() {
        if (toolDocument != null && !toolDocument.isDisposed() && !toolDocument.inWorld) {
            toolDocument.remove();
            toolDocument = null;
        }
        if (worldWindow == null || worldWindow.document == null || worldWindow.document.isDisposed()) {
            // Place the panel along the actual render camera direction in third person.
            Vec3 cameraPosition = com.sighs.apricityui.spi.AuiServices.client().getCameraPosition();
            var lookVector = com.sighs.apricityui.spi.AuiServices.client().getCameraLookVector();
            Vec3 look = new Vec3(lookVector.x, lookVector.y, lookVector.z).normalize();
            Vec3 position = cameraPosition.add(look.scale(3.0d));
            Vec3 toCamera = cameraPosition.subtract(position);
            double horizontal = Math.sqrt(toCamera.x * toCamera.x + toCamera.z * toCamera.z);
            float yaw = (float) (Math.toDegrees(Math.atan2(toCamera.z, toCamera.x)) + 90.0d);
            float pitch = (float) -Math.toDegrees(Math.atan2(toCamera.y, horizontal));
            worldWindow = new WorldWindow(PATH, position, 16, yaw, pitch);
            WorldWindow.addWindow(worldWindow);
        }
        toolDocument = worldWindow.document;
        if (toolDocument != null) {
            toolDocument.setReloadPersistent(true);
            refresh();
        }
    }

    private static void closeWorldWindow() {
        if (worldWindow != null) {
            WorldWindow.removeWindow(worldWindow);
            worldWindow = null;
        }
        if (toolDocument != null && toolDocument.isDisposed()) toolDocument = null;
    }

    public static void close() {
        ContextMenu.closeActive();
        createDialog.close();
        previewDialog.close();
        metaDialog.close();
        referenceDialog.close();
        if (worldWindow != null) closeWorldWindow();
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
        bindOnce(".content", "contextmenu", ResourceManager::showEmptyContextMenu);
    }

    private static void bindOnce(String selector, java.util.function.Consumer<Event> listener) {
        bindOnce(selector, "click", listener);
    }

    private static void bindOnce(String selector, String eventType, java.util.function.Consumer<Event> listener) {
        Element element = toolDocument.querySelector(selector);
        String normalizedEvent = safe(eventType).isBlank() ? "click" : eventType.trim().toLowerCase(Locale.ROOT);
        String marker = "click".equals(normalizedEvent) ? "data-java-bound" : "data-java-bound-" + normalizedEvent;
        if (element == null || "1".equals(element.getAttribute(marker))) return;
        element.setAttribute(marker, "1");
        element.addEventListener(normalizedEvent, listener);
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
        expandAncestorsLocally(normalized);
        renderNavigation();
        refreshSelectionClasses();
        renderFiles(true);
        renderDetail();
        markNavigationDirty();
    }

    private static void select(SelectedItem item) {
        selectedItem = item;
        refreshSelectionClasses();
        renderDetail();
        markSelectionDirty();
    }

    /** Selecting an existing tile must not replace it before its double-click can be dispatched. */
    private static void refreshSelectionClasses() {
        if (toolDocument == null) return;
        for (Element element : toolDocument.querySelectorAll(".file-card")) {
            updateSelectedClass(element, selectedItem != null && selectedItem.path.equals(normalizePath(element.getAttribute("data-path"))));
        }
        for (Element element : toolDocument.querySelectorAll(".tree-item")) {
            String path = normalizePath(element.getAttribute("data-path"));
            boolean selected = selectedItem != null
                    ? selectedItem.path.equals(path)
                    : currentPath.equals(path);
            updateSelectedClass(element, selected);
        }
    }

    private static void updateSelectedClass(Element element, boolean selected) {
        if (element == null) return;
        String classes = safe(element.getAttribute("class"));
        List<String> tokens = new ArrayList<>();
        for (String token : classes.split("\\s+")) {
            if (!token.isBlank() && !"selected".equals(token)) tokens.add(token);
        }
        if (selected) tokens.add("selected");
        String updated = String.join(" ", tokens);
        if (!updated.equals(classes)) element.setAttribute("class", updated);
    }

    private static void markSelectionDirty() {
        if (toolDocument == null) return;
        int repaint = Drawer.REPAINT | Drawer.REORDER;
        for (Element element : toolDocument.querySelectorAll(".file-card")) toolDocument.markDirty(element, repaint);
        for (Element element : toolDocument.querySelectorAll(".tree-item")) toolDocument.markDirty(element, repaint);
        Element detail = toolDocument.querySelector("#detailContent");
        if (detail != null) toolDocument.markDirty(detail, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
    }

    /** Folder navigation changes the content panes, never the document root. */
    private static void markNavigationDirty() {
        if (toolDocument == null) return;
        int mask = Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER;
        for (String selector : List.of("#navPath", "#fileGrid", "#contentTitle", "#contentCount", "#detailContent")) {
            Element element = toolDocument.querySelector(selector);
            if (element != null) toolDocument.markDirty(element, mask);
        }
        markSelectionDirty();
    }

    private static void selectFromTree(Loader.StaticResourceEntry entry) {
        if (entry == null) return;
        String parent = parentPath(entry.path());
        if (!parent.equals(currentPath)) {
            while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
            history.add(parent);
            historyIndex = history.size() - 1;
            currentPath = parent;
            expandAncestorsLocally(parent);
        }
        selectedItem = SelectedItem.file(entry);
        renderNavigation();
        refreshSelectionClasses();
        renderFiles(false);
        renderDetail();
        markNavigationDirty();
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
        treeBranches.clear();
        container.clearChildren();
        Node fragment = toolDocument.createDocumentFragment();
        appendTreeChildren(fragment, root, 0, true);
        container.appendChild(fragment);
    }

    private static void appendTreeChildren(Node parent, FolderNode folder, int depth, boolean animate) {
        int index = 0;
        for (FolderNode child : folder.sortedFolders()) {
            boolean expanded = expandedPaths.contains(child.path);
            boolean hasChildren = !child.folders.isEmpty() || !child.files.isEmpty();
            Element item = createElement("DIV", treeItemClass(child.path.equals(currentPath), animate));
            item.setAttribute("style", "padding-left:" + (24 + depth * 16) + "px;animation-delay:" + (index * 0.04d) + "s;");
            item.setAttribute("data-path", child.path);
            item.setAttribute("data-tree-folder", child.path);
            item.addEventListener("click", event -> navigate(child.path, true));
            item.addEventListener("contextmenu", event -> showContextMenu(event, SelectedItem.folder(child)));

            Element toggle = textElement("DIV", "▾");
            toggle.setAttribute("class", hasChildren ? (expanded ? "tree-toggle" : "tree-toggle collapsed") : "tree-toggle empty");
            toggle.addEventListener("click", event -> {
                event.stopPropagation();
                toggleTreeFolder(child, depth, hasChildren);
            });
            item.append(toggle);
            item.append(iconElement("tree-icon", FOLDER_ICON));
            item.append(textElement("SPAN", child.name.toUpperCase(Locale.ROOT)));
            parent.appendChild(item);

            if (hasChildren) {
                Element wrapper = createElement("DIV", expanded ? "tree-children-wrapper expanded" : "tree-children-wrapper");
                wrapper.setAttribute("data-tree-children", child.path);
                Element inner = createElement("DIV", "tree-children-inner");
                inner.setAttribute("data-tree-children-inner", child.path);
                if (expanded) appendTreeChildren(inner, child, depth + 1, animate);
                wrapper.append(inner);
                treeBranches.put(child.path, new TreeBranch(item, toggle, wrapper, inner));
                parent.appendChild(wrapper);
            }
            index++;
        }

        for (Loader.StaticResourceEntry entry : folder.sortedFiles()) {
            Element item = createElement("DIV", treeItemClass(selectedItem != null && selectedItem.matches(entry), animate));
            item.setAttribute("style", "padding-left:" + (24 + depth * 16) + "px;animation-delay:" + (index * 0.04d) + "s;");
            item.setAttribute("data-path", safe(entry.path()));
            item.setAttribute("data-resource-key", resourceKey(entry));
            item.addEventListener("click", event -> selectFromTree(entry));
            item.addEventListener("contextmenu", event -> showContextMenu(event, SelectedItem.file(entry)));
            item.append(textElement("DIV", "▾", "tree-toggle empty"));
            item.append(iconElement("tree-icon", iconFor(entry)));
            item.append(textElement("SPAN", fileName(entry.path()).toUpperCase(Locale.ROOT)));
            parent.appendChild(item);
            index++;
        }
    }

    private static String treeItemClass(boolean selected, boolean animate) {
        return "tree-item" + (selected ? " selected" : "") + (animate ? " anim-in" : "");
    }

    private static void toggleTreeFolder(FolderNode folder, int depth, boolean hasChildren) {
        if (folder == null || !hasChildren) return;
        if (expandedPaths.contains(folder.path)) expandedPaths.remove(folder.path);
        else expandedPaths.add(folder.path);
        updateTreeExpansion(folder, depth, true);
    }

    /**
     * Expanding a folder is deliberately local: replacing #treeContainer destroys every
     * tree node, restarts their entrance animations, and makes the whole left pane look
     * like a page reload.  Keep the existing branch and only populate or clear its
     * immediate child container instead.
     */
    private static void updateTreeExpansion(FolderNode folder, int depth, boolean hasChildren) {
        if (toolDocument == null || folder == null || !hasChildren) return;

        boolean expanded = expandedPaths.contains(folder.path);
        TreeBranch branch = treeBranches.get(folder.path);
        if (branch == null || !branch.isConnected()) return;
        Element item = branch.item();
        Element wrapper = branch.wrapper();
        Element inner = branch.inner();

        Element toggle = branch.toggle();
        if (toggle != null) toggle.setAttribute("class", expanded ? "tree-toggle" : "tree-toggle collapsed");
        wrapper.setAttribute("class", expanded ? "tree-children-wrapper expanded" : "tree-children-wrapper");
        removeDescendantTreeBranches(folder.path);
        inner.clearChildren();
        if (expanded) {
            Node fragment = toolDocument.createDocumentFragment();
            appendTreeChildren(fragment, folder, depth + 1, countTreeEntries(folder) <= TREE_ANIMATION_LIMIT);
            inner.appendChild(fragment);
        }

        toolDocument.markDirty(item, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        toolDocument.markDirty(wrapper, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
    }

    private static int countTreeEntries(FolderNode folder) {
        if (folder == null) return 0;
        int total = folder.files.size();
        for (FolderNode child : folder.folders.values()) {
            total += 1 + countTreeEntries(child);
            if (total > TREE_ANIMATION_LIMIT) return total;
        }
        return total;
    }

    private static void removeDescendantTreeBranches(String path) {
        String prefix = safe(path) + "/";
        treeBranches.keySet().removeIf(candidate -> candidate.startsWith(prefix));
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
            Element card = fileCard(fileName(entry.path()), formatSize(entry.sizeBytes()), iconFor(entry), entry, fileItem, animate, index++);
            card.setAttribute("data-path", safe(entry.path()));
            card.setAttribute("data-resource-key", resourceKey(entry));
            if (isPreviewable(entry) && !PATH.equals(safe(entry.path()))) card.addEventListener("dblclick", event -> openPreview(entry));
            grid.append(card);
        }
    }

    private static Element fileCard(String name, String meta, String icon, SelectedItem item, boolean animate, int index) {
        return fileCard(name, meta, icon, null, item, animate, index);
    }

    private static Element fileCard(String name, String meta, String icon, Loader.StaticResourceEntry entry,
                                    SelectedItem item, boolean animate, int index) {
        boolean selected = selectedItem != null && selectedItem.key.equals(item.key);
        String classes = "file-card" + (selected ? " selected" : "") + (animate ? " entering" : "");
        Element card = createElement("DIV", classes);
        if (animate) card.setAttribute("style", "animation-delay:" + (index * 0.05d) + "s;");
        card.addEventListener("click", event -> select(item));
        card.addEventListener("contextmenu", event -> showContextMenu(event, item));
        card.append(fileIcon(entry, icon));
        card.append(textElement("DIV", name.toUpperCase(Locale.ROOT), "file-name"));
        card.append(textElement("DIV", meta, "file-meta"));
        return card;
    }

    private static Element fileIcon(Loader.StaticResourceEntry entry, String fallbackIcon) {
        Element icon = createElement("DIV", "file-icon");
        if (isImagePreviewable(entry)) {
            Element thumbnail = createElement("IMG", "file-thumbnail");
            thumbnail.setAttribute("src", "/" + safe(entry.path()));
            thumbnail.setAttribute("alt", fileName(entry.path()));
            thumbnail.setAttribute("style", "width:48px;height:48px;object-fit:contain;");
            icon.append(thumbnail);
        } else if (ResourceFontAsset.isFont(entry)) {
            ResourceFontAsset.ensureLoaded(entry);
            Element glyph = textElement("DIV", "Aa", "file-font-glyph");
            glyph.setAttribute("style", "font-family:'" + ResourceFontAsset.familyName(entry) + "',sans-serif;");
            icon.append(glyph);
        } else {
            icon.setInnerHTML(fallbackIcon);
        }
        return icon;
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
        icon.setInnerHTML(selectedItem.folder != null ? FOLDER_ICON : iconFor(entry));
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
        content.append(detail);
    }

    private static Element detailRow(String label, String value) {
        Element row = createElement("DIV", "detail-row");
        row.append(textElement("SPAN", label, "detail-label"));
        row.append(textElement("SPAN", value, "detail-value"));
        return row;
    }

    private static void showContextMenu(Event event, SelectedItem item) {
        if (item == null || toolDocument == null) return;
        event.preventDefault();
        event.stopPropagation();
        select(item);
        List<ContextMenu.Item> items = new ArrayList<>();
        items.add(ContextMenu.Item.header(item.name));
        if (item.folder != null) {
            items.add(ContextMenu.Item.action("OPEN", ContextMenu.Icons.OPEN, () -> navigate(item.path, true)));
            items.add(ContextMenu.Item.separator());
            items.add(ContextMenu.Item.action("NEW FILE HERE", ContextMenu.Icons.NEW_FILE,
                    () -> createDialog.open(toolDocument, item.path, ClientLoader::reload)));
        } else if (item.entry != null) {
            boolean previewable = isPreviewable(item.entry) && !PATH.equals(safe(item.entry.path()));
            ContextMenu.Item preview = ContextMenu.Item.action(
                    "PREVIEW", ContextMenu.Icons.OPEN, "DBL-CLK", () -> openPreview(item.entry));
            items.add(previewable ? preview : preview.disabled());
            if (ResourceReferenceDialog.supports(item.entry)) {
                items.add(ContextMenu.Item.action("REFERENCE", ContextMenu.Icons.REFERENCE,
                        () -> referenceDialog.open(toolDocument, item.entry)));
            }
            if ("html".equalsIgnoreCase(safe(item.entry.extension()))) {
                Path localPath = resolveLocalPath(item.entry);
                ContextMenu.Item editMeta = ContextMenu.Item.action(
                        "EDIT META", ContextMenu.Icons.EDIT, () -> openMetaEditor(item.entry));
                items.add(localPath != null && Files.isRegularFile(localPath) ? editMeta : editMeta.disabled());
            }
        }
        items.add(ContextMenu.Item.separator());
        items.add(ContextMenu.Item.action("COPY PATH", ContextMenu.Icons.COPY, "CTRL+C", () -> {
            Operation.setClipboardText(item.path);
            ToastManager.show("Path copied");
        }));
        if (item.entry != null) {
            Loader.StaticResourceEntry entry = item.entry;
            String source = resolveSourceForCopy(entry);
            if (!source.isBlank() || resolveLocalPath(entry) != null) items.add(ContextMenu.Item.separator());
            if (!source.isBlank()) items.add(ContextMenu.Item.action("COPY SOURCE", ContextMenu.Icons.COPY, () -> {
                Operation.setClipboardText(source);
                ToastManager.show("Source copied");
            }));
            if (resolveLocalPath(entry) != null) {
                items.add(ContextMenu.Item.action("OPEN FOLDER", ContextMenu.Icons.OPEN, () -> browseLocalFile(entry)));
            }
        }
        items.add(ContextMenu.Item.separator());
        items.add(ContextMenu.Item.action("PROPERTIES", ContextMenu.Icons.PROPERTIES, "ALT+ENTER", () -> select(item)));
        ContextMenu.show(toolDocument, mousePosition(event), items);
    }

    private static void showEmptyContextMenu(Event event) {
        if (toolDocument == null) return;
        event.preventDefault();
        event.stopPropagation();
        FolderNode folder = findFolder(currentPath);
        String title = folder == null ? "DIRECTORY" : folder.name;
        ContextMenu.Item goUpItem = ContextMenu.Item.action("GO UP", ContextMenu.Icons.UP, ResourceManager::goUp);
        List<ContextMenu.Item> items = List.of(
                ContextMenu.Item.header(title),
                ContextMenu.Item.action("NEW FILE", ContextMenu.Icons.NEW_FILE,
                        () -> createDialog.open(toolDocument, currentPath, ClientLoader::reload)),
                ContextMenu.Item.separator(),
                currentPath.isBlank() ? goUpItem.disabled() : goUpItem,
                ContextMenu.Item.action("REFRESH", ContextMenu.Icons.REFRESH, "F5", ResourceManager::refresh)
        );
        ContextMenu.show(toolDocument, mousePosition(event), items);
    }

    private static Position mousePosition(Event event) {
        return event instanceof com.sighs.apricityui.event.MouseEvent mouse
                ? new Position(mouse.clientX, mouse.clientY)
                : Operation.getMousePositionDirectly();
    }

    private static void openPreview(Loader.StaticResourceEntry entry) {
        if (entry == null || toolDocument == null) return;
        previewDialog.open(toolDocument, entry);
    }

    private static void openMetaEditor(Loader.StaticResourceEntry entry) {
        if (entry == null || toolDocument == null) return;
        Path localPath = resolveLocalPath(entry);
        if (localPath == null || !Files.isRegularFile(localPath)) {
            ToastManager.show("HTML source is read-only");
            return;
        }
        metaDialog.open(toolDocument, entry.path(), localPath, ClientLoader::reload);
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
            com.sighs.apricityui.spi.AuiServices.client().openFile(openTarget.toFile());
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
            if (path.isBlank() || INTERNAL_IMAGE_PREVIEW_PATH.equals(path)) continue;
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

    /** Opens only ancestors that are not already present in the rendered tree. */
    private static void expandAncestorsLocally(String path) {
        Set<String> previous = new LinkedHashSet<>(expandedPaths);
        expandAncestors(path);
        for (String expandedPath : expandedPaths) {
            if (previous.contains(expandedPath)) continue;
            FolderNode folder = findFolder(expandedPath);
            if (folder == null) continue;
            boolean hasChildren = !folder.folders.isEmpty() || !folder.files.isEmpty();
            updateTreeExpansion(folder, treeDepth(expandedPath), hasChildren);
        }
    }

    private static int treeDepth(String path) {
        String normalized = normalizePath(path);
        return normalized.isBlank() ? 0 : Math.max(0, normalized.split("/").length - 1);
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
        return isHtmlPreviewable(entry) || isImagePreviewable(entry) || ResourceFontAsset.isFont(entry);
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

    private record TreeBranch(Element item, Element toggle, Element wrapper, Element inner) {
        private boolean isConnected() {
            return item != null && wrapper != null && inner != null
                    && item.isConnected() && wrapper.isConnected() && inner.isConnected();
        }
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
