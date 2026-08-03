package com.sighs.apricityui.ui;

import com.sighs.apricityui.dev.resource.ResourceFileWriter;
import com.sighs.apricityui.dev.resource.ResourcePath;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.sighs.apricityui.event.Event;

/**
 * Global, resource-manager-backed file selection UI. The result is empty when the
 * user cancels; callers never need to inspect the picker document or its DOM state.
 */
public final class FilePicker {
    private static final String PATH = "devtools/file-picker.html";
    private static final String DEFAULT_TITLE_KEY = "file_picker.apricityui.title";
    private static FilePicker active;

    public record Options(String title, String titleKey, Set<String> extensions, boolean includeResourcePackFiles) {
        public Options(String title, Set<String> extensions, boolean includeResourcePackFiles) {
            this(title, null, extensions, includeResourcePackFiles);
        }
        public Options {
            boolean missingTitle = title == null || title.isBlank();
            title = missingTitle ? "" : title.trim();
            titleKey = titleKey == null || titleKey.isBlank()
                    ? (missingTitle ? DEFAULT_TITLE_KEY : null) : titleKey.trim();
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (extensions != null) for (String extension : extensions) {
                String value = normalizeExtension(extension);
                if (!value.isBlank()) normalized.add(value);
            }
            extensions = Set.copyOf(normalized);
        }

        public static Options html(String title, boolean includeResourcePackFiles) {
            return new Options(title, Set.of("html"), includeResourcePackFiles);
        }

        public static Options htmlTranslation(String titleKey, boolean includeResourcePackFiles) {
            return new Options(null, titleKey, Set.of("html"), includeResourcePackFiles);
        }

        public static Options any(String title, boolean includeResourcePackFiles) {
            return new Options(title, Set.of(), includeResourcePackFiles);
        }

        boolean accepts(Loader.StaticResourceEntry entry) {
            if (entry == null) return false;
            if (!includeResourcePackFiles && entry.layer() == Loader.ResourceLayer.RESOURCE_PACK) return false;
            return extensions.isEmpty() || extensions.contains(normalizeExtension(entry.extension()));
        }

        boolean allowsHtmlCreation() {
            return extensions.isEmpty() || extensions.contains("html");
        }
    }

    public record Selection(String path, Loader.ResourceLayer layer, Path localPath) {
    }

    private final Document document;
    private final Options options;
    private final CompletableFuture<Optional<Selection>> result = new CompletableFuture<>();
    private List<Loader.StaticResourceEntry> entries;
    private String currentPath = "";
    private Loader.StaticResourceEntry selected;
    private boolean creatingHtml;
    private Element root;
    private final Set<String> expandedPaths = new LinkedHashSet<>();

    private FilePicker(Document document, Options options, List<Loader.StaticResourceEntry> entries) {
        this.document = document;
        this.options = options == null ? Options.any(null, true) : options;
        this.entries = filter(entries);
    }

    public static synchronized CompletableFuture<Optional<Selection>> pick(Options options) {
        Document document = Document.create(PATH);
        if (document == null || document.body == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        document.setReloadPersistent(true);
        return open(document, options, ClientLoader.listFinalStaticResources());
    }

    /** Opens inside a caller-owned document; useful for embedded tools and tests. */
    public static synchronized CompletableFuture<Optional<Selection>> pickIn(Document document, Options options,
                                                                                List<Loader.StaticResourceEntry> entries) {
        return open(document, options, entries);
    }

    public static synchronized boolean isOpen() {
        return active != null && active.root != null && active.root.isConnected();
    }

    public static synchronized void closeActive() {
        if (active != null) active.finish(Optional.empty());
    }

    private static CompletableFuture<Optional<Selection>> open(Document document, Options options,
                                                                  List<Loader.StaticResourceEntry> entries) {
        if (document == null || document.body == null) return CompletableFuture.completedFuture(Optional.empty());
        Tooltip.hide();
        if (active != null) active.finish(Optional.empty());
        active = new FilePicker(document, options, entries);
        active.render();
        return active.result;
    }

    private void render() {
        Element template = document.querySelector("#dialogOverlay");
        if (template == null) {
            renderEmbeddedFallback();
            return;
        }
        root = template;
        root.setAttribute("class", "dialog-overlay show");
        root.setTopLayer(true);
        bindTemplate(root, "click", event -> {
            if (event.target == root) finish(Optional.empty());
        });
        Element panel = document.querySelector("#dialogPanel");
        bindTemplate(panel, "click", event -> event.stopPropagation());

        setTemplateText("#dialogTitle", options.title(), options.titleKey());
        setTemplateText("#dlgCloseButton", "X", null);
        setTemplateText("#dlgQuickTitle", "", "file_picker.apricityui.paths");
        setTemplateText("#dlgFileNameLabel", "", "file_picker.apricityui.select_file");
        setTemplateText("#dlgCancelButton", "", "file_picker.apricityui.action.cancel");
        setTemplateText("#dlgConfirmBtn", "", selected == null && options.allowsHtmlCreation()
                ? "file_picker.apricityui.action.create" : "file_picker.apricityui.action.select");

        bindTemplate(document.querySelector("#dlgCloseButton"), "click", event -> finish(Optional.empty()));
        bindTemplate(document.querySelector("#dlgCancelButton"), "click", event -> finish(Optional.empty()));
        bindTemplate(document.querySelector("#dlgUpBtn"), "click", event -> navigateTo(ResourcePath.parent(currentPath)));
        Element back = document.querySelector("#dlgBackBtn");
        if (back != null) back.setDisabled(true);

        Element input = document.querySelector("#dlgFileName");
        if (input != null) {
            input.setAttribute("placeholder", "new-file.html");
            input.setValue(selected == null ? "" : selected.path());
        }
        populateTemplateFilter();
        renderTemplateAddress();
        renderTemplatePaths();
        renderTemplateFiles();
        Element confirm = document.querySelector("#dlgConfirmBtn");
        if (confirm != null) {
            confirm.setDisabled(selected == null && !options.allowsHtmlCreation());
            bindTemplate(confirm, "click", event -> confirmTemplate(input == null ? "" : input.getValue()));
        }
        dirty();
    }

    private void renderEmbeddedFallback() {
        if (root != null) root.remove();
        root = element("DIV", "aui-file-picker-overlay");
        root.setTopLayer(true);
        root.addEventListener("click", event -> {
            if (event.target == root) finish(Optional.empty());
        });
        Element panel = element("DIV", "aui-file-picker");
        Element heading = element("DIV", "header aui-file-picker-heading");
        heading.appendChild(options.titleKey() == null
                ? text("DIV", options.title(), "logo aui-file-picker-title")
                : translation("DIV", options.titleKey(), "logo aui-file-picker-title"));
        Element cancel = element("BUTTON", "action-btn aui-file-picker-cancel");
        cancel.setAttribute("type", "button");
        cancel.appendChild(translation("SPAN", "file_picker.apricityui.action.cancel", null));
        cancel.addEventListener("click", event -> finish(Optional.empty()));
        heading.appendChild(cancel);
        panel.appendChild(heading);

        Element body = element("DIV", "main aui-file-picker-body");
        body.appendChild(renderPaths());
        body.appendChild(renderDetails());
        panel.appendChild(body);
        root.appendChild(panel);
        document.body.appendChild(root);
        dirty();
    }

    private void renderTemplateAddress() {
        Element address = document.querySelector("#dlgAddress");
        if (address == null) return;
        address.clearChildren();
        address.appendChild(templatePathItem("file_picker.apricityui.root", "", true, currentPath.isBlank()));
        StringBuilder path = new StringBuilder();
        for (String part : currentPath.split("/")) {
            if (part.isBlank()) continue;
            if (!path.isEmpty()) path.append('/');
            path.append(part);
            Element separator = element("SPAN", "dialog-address-sep");
            separator.setTextContent(">");
            address.appendChild(separator);
            address.appendChild(templatePathItem(part, path.toString(), false, path.toString().equals(currentPath)));
        }
    }

    private Element templatePathItem(String value, String path, boolean localized, boolean current) {
        Element item = element("SPAN", current ? "dialog-address-crumb current" : "dialog-address-crumb");
        if (localized) item.appendChild(translation("SPAN", value, null));
        else item.setTextContent(value);
        item.addEventListener("click", event -> navigateTo(path));
        return item;
    }

    private void renderTemplatePaths() {
        Element treeContainer = document.querySelector("#dlgQuickAccess");
        if (treeContainer == null) return;
        treeContainer.clearChildren();
        appendTemplateTreeChildren(treeContainer, buildFolderTree(), 0);
    }

    /** Mirrors ResourceManager.appendTreeChildren; this class only supplies filtered data. */
    private void appendTemplateTreeChildren(Element parent, FolderNode folder, int depth) {
        for (FolderNode child : folder.sortedFolders()) {
            boolean expanded = expandedPaths.contains(child.path);
            boolean hasChildren = !child.folders.isEmpty() || !child.files.isEmpty();
            Element item = element("DIV", currentPath.equals(child.path) ? "tree-item selected" : "tree-item");
            item.setAttribute("style", "padding-left:" + (24 + depth * 16) + "px;");
            item.setAttribute("data-path", child.path);
            item.addEventListener("click", event -> navigateTo(child.path));

            Element toggle = text("DIV", "\u25be", hasChildren ? (expanded ? "tree-toggle" : "tree-toggle collapsed") : "tree-toggle empty");
            toggle.addEventListener("click", event -> {
                event.stopPropagation();
                if (!hasChildren) return;
                if (expandedPaths.contains(child.path)) expandedPaths.remove(child.path);
                else expandedPaths.add(child.path);
                renderTemplatePaths();
                dirty();
            });
            item.appendChild(toggle);
            item.appendChild(templateTreeFolderIcon());
            item.appendChild(text("SPAN", child.name.toUpperCase(Locale.ROOT), null));
            parent.appendChild(item);

            if (hasChildren) {
                Element wrapper = element("DIV", expanded ? "tree-children-wrapper expanded" : "tree-children-wrapper");
                Element inner = element("DIV", "tree-children-inner");
                if (expanded) appendTemplateTreeChildren(inner, child, depth + 1);
                wrapper.appendChild(inner);
                parent.appendChild(wrapper);
            }
        }

        for (Loader.StaticResourceEntry entry : folder.sortedFiles()) {
            boolean active = selected != null && selected.path().equals(entry.path()) && selected.layer() == entry.layer();
            Element item = element("DIV", active ? "tree-item selected" : "tree-item");
            item.setAttribute("style", "padding-left:" + (24 + depth * 16) + "px;");
            item.setAttribute("data-path", entry.path());
            item.addEventListener("click", event -> {
                selected = entry;
                render();
            });
            item.appendChild(text("DIV", "\u25be", "tree-toggle empty"));
            item.appendChild(templateTreeFileIcon());
            item.appendChild(text("SPAN", fileName(entry.path()).toUpperCase(Locale.ROOT), null));
            parent.appendChild(item);
        }
    }

    private Element templateTreeFolderIcon() {
        Element icon = element("DIV", "tree-icon");
        Element svg = Element.init(document.createElement("SVG"));
        svg.setAttribute("viewBox", "0 0 40 40");
        svg.setAttribute("fill", "none");
        appendSvg(svg, "RECT", "x", "4", "y", "12", "width", "32", "height", "22", "fill", "#8b5cf6");
        appendSvg(svg, "RECT", "x", "4", "y", "8", "width", "14", "height", "6", "fill", "#6d28d9");
        appendSvg(svg, "RECT", "x", "4", "y", "14", "width", "32", "height", "2", "fill", "#6d28d9");
        icon.appendChild(svg);
        return icon;
    }

    private Element templateTreeFileIcon() {
        Element icon = element("DIV", "tree-icon");
        Element svg = Element.init(document.createElement("SVG"));
        svg.setAttribute("viewBox", "0 0 40 40");
        svg.setAttribute("fill", "none");
        appendSvg(svg, "RECT", "x", "6", "y", "4", "width", "28", "height", "32", "fill", "none", "stroke", "#1a1a1a", "stroke-width", "2");
        appendSvg(svg, "RECT", "x", "10", "y", "12", "width", "20", "height", "2", "fill", "#8b5cf6");
        appendSvg(svg, "RECT", "x", "10", "y", "18", "width", "16", "height", "2", "fill", "#8b5cf6");
        appendSvg(svg, "RECT", "x", "10", "y", "24", "width", "20", "height", "2", "fill", "#8b5cf6");
        appendSvg(svg, "RECT", "x", "10", "y", "30", "width", "12", "height", "2", "fill", "#8b5cf6");
        icon.appendChild(svg);
        return icon;
    }

    private void renderTemplateFiles() {
        Element grid = document.querySelector("#dlgFileGrid");
        if (grid == null) return;
        grid.clearChildren();
        for (String folder : directFolders()) grid.appendChild(templateCard(fileName(folder), true, null));
        for (Loader.StaticResourceEntry entry : directFiles()) grid.appendChild(templateCard(fileName(entry.path()), false, entry));
        if (grid.children.isEmpty()) grid.appendChild(translation("DIV", "file_picker.apricityui.empty", "dialog-empty"));
    }

    private Element templateCard(String name, boolean folder, Loader.StaticResourceEntry entry) {
        boolean activeSelection = entry != null && selected != null && selected.path().equals(entry.path())
                && selected.layer() == entry.layer();
        Element card = element("DIV", activeSelection ? "dialog-file-card selected" : "dialog-file-card");
        card.appendChild(templateIcon(folder ? "folder" : "data", false));
        card.appendChild(text("DIV", folder ? name + "/" : name, "dialog-file-name"));
        if (folder) {
            card.appendChild(text("DIV", "FOLDER", "dialog-file-meta"));
        } else if (entry != null) {
            String sourceKey = entry.layer() == Loader.ResourceLayer.RESOURCE_PACK
                    ? "file_picker.apricityui.source.pack" : "file_picker.apricityui.source.local";
            card.appendChild(translation("DIV", sourceKey, "dialog-file-meta"));
        }
        card.addEventListener("click", event -> {
            if (folder) navigateTo(resolveFolder(name));
            else {
                selected = entry;
                render();
            }
        });
        if (!folder) card.addEventListener("dblclick", event -> {
            selected = entry;
            confirm();
        });
        return card;
    }

    private Element templateIcon(String type, boolean compact) {
        Element icon = element("DIV", "dialog-file-icon");
        Element svg = Element.init(document.createElement("SVG"));
        svg.setAttribute("viewBox", compact ? "0 0 14 14" : "0 0 40 40");
        svg.setAttribute("fill", "none");
        if (compact) appendQuickIcon(svg, type);
        else if ("folder".equals(type)) {
            appendSvg(svg, "RECT", "x", "4", "y", "12", "width", "32", "height", "22", "fill", "#8b5cf6");
            appendSvg(svg, "RECT", "x", "4", "y", "8", "width", "14", "height", "6", "fill", "#6d28d9");
            appendSvg(svg, "RECT", "x", "4", "y", "14", "width", "32", "height", "2", "fill", "#6d28d9");
        } else {
            appendSvg(svg, "RECT", "x", "6", "y", "4", "width", "28", "height", "32", "fill", "none", "stroke", "#1a1a1a", "stroke-width", "2");
            appendSvg(svg, "RECT", "x", "10", "y", "12", "width", "20", "height", "2", "fill", "#8b5cf6");
            appendSvg(svg, "RECT", "x", "10", "y", "18", "width", "16", "height", "2", "fill", "#8b5cf6");
            appendSvg(svg, "RECT", "x", "10", "y", "24", "width", "20", "height", "2", "fill", "#8b5cf6");
            appendSvg(svg, "RECT", "x", "10", "y", "30", "width", "12", "height", "2", "fill", "#8b5cf6");
        }
        icon.appendChild(svg);
        return icon;
    }

    private void appendQuickIcon(Element svg, String type) {
        if ("root".equals(type)) appendSvg(svg, "PATH", "d", "M7 1L1 6h2v6h4V9h0v3h4V6h2L7 1z", "fill", "currentColor");
        else if ("worlds".equals(type)) {
            appendSvg(svg, "CIRCLE", "cx", "7", "cy", "7", "r", "5", "stroke", "currentColor", "stroke-width", "1.2");
            appendSvg(svg, "PATH", "d", "M2 7h10M7 2c-2 2-2 8 0 10M7 2c2 2 2 8 0 10", "stroke", "currentColor", "stroke-width", "0.8");
        } else if ("mods".equals(type)) {
            appendSvg(svg, "RECT", "x", "2", "y", "2", "width", "10", "height", "10", "fill", "none", "stroke", "currentColor", "stroke-width", "1.2");
            appendSvg(svg, "PATH", "d", "M2 7h10M7 2v10", "stroke", "currentColor", "stroke-width", "0.8");
        } else if ("packs".equals(type)) {
            appendSvg(svg, "PATH", "d", "M7 1L1 4v6l6 3 6-3V4L7 1z", "stroke", "currentColor", "stroke-width", "1.2");
            appendSvg(svg, "PATH", "d", "M1 4l6 3 6-3M7 7v6", "stroke", "currentColor", "stroke-width", "0.8");
        } else {
            appendSvg(svg, "CIRCLE", "cx", "7", "cy", "7", "r", "4", "stroke", "currentColor", "stroke-width", "1.2");
            appendSvg(svg, "RECT", "x", "6", "y", "2", "width", "2", "height", "2", "fill", "currentColor");
            appendSvg(svg, "RECT", "x", "6", "y", "10", "width", "2", "height", "2", "fill", "currentColor");
        }
    }

    private void appendSvg(Element parent, String tag, String... attributes) {
        Element child = Element.init(document.createElement(tag));
        for (int index = 0; index + 1 < attributes.length; index += 2) child.setAttribute(attributes[index], attributes[index + 1]);
        parent.appendChild(child);
    }

    private String resolveFolder(String name) {
        return currentPath.isBlank() ? name : currentPath + "/" + name;
    }

    private void navigateTo(String path) {
        currentPath = path == null ? "" : path;
        expandAncestors(currentPath);
        selected = null;
        creatingHtml = false;
        render();
    }

    private void populateTemplateFilter() {
        Element filter = document.querySelector("#dlgFilter");
        if (filter == null) return;
        filter.clearChildren();
        Element option = element("OPTION", "");
        option.setAttribute("value", "active-filter");
        option.setTextContent(options.extensions().isEmpty() ? "*.*" : String.join(", ", options.extensions()));
        filter.appendChild(option);
        filter.setDisabled(true);
    }

    private void confirmTemplate(String requestedPath) {
        if (selected != null) {
            confirm();
            return;
        }
        if (!options.allowsHtmlCreation()) return;
        createHtml(requestedPath);
        if (selected != null) confirm();
    }

    private void setTemplateText(String selector, String literal, String key) {
        Element target = document.querySelector(selector);
        if (target == null) return;
        target.clearChildren();
        if (key == null || key.isBlank()) target.setTextContent(literal == null ? "" : literal);
        else target.appendChild(translation("SPAN", key, null));
    }

    private void bindTemplate(Element element, String type, java.util.function.Consumer<com.sighs.apricityui.event.Event> listener) {
        if (element == null) return;
        String marker = "data-file-picker-bound-" + type;
        if ("1".equals(element.getAttribute(marker))) return;
        element.setAttribute(marker, "1");
        element.addEventListener(type, listener);
    }

    private Element renderPaths() {
        Element paths = element("DIV", "sidebar aui-file-picker-paths");
        paths.appendChild(translation("DIV", "file_picker.apricityui.paths", "sidebar-title aui-file-picker-section-label"));
        Element rootItem = pathItem("file_picker.apricityui.root", "", 0, true);
        paths.appendChild(rootItem);
        for (String folder : folders()) {
            int depth = folder.split("/").length;
            paths.appendChild(pathItem(fileName(folder), folder, depth, false));
        }
        return paths;
    }

    private Element pathItem(String label, String path, int depth, boolean localizedLabel) {
        boolean activePath = currentPath.equals(path);
        Element item = element("BUTTON", activePath ? "tree-item selected aui-file-picker-path" : "tree-item aui-file-picker-path");
        item.setAttribute("type", "button");
        item.setAttribute("style", "padding-left:" + (12 + depth * 14) + "px;");
        if (localizedLabel) item.appendChild(translation("SPAN", label, null));
        else item.setTextContent(label);
        item.addEventListener("click", event -> {
            currentPath = path;
            selected = null;
            creatingHtml = false;
            render();
        });
        return item;
    }

    private Element renderDetails() {
        Element details = element("DIV", "content aui-file-picker-details");
        Element toolbar = element("DIV", "content-header aui-file-picker-toolbar");
        toolbar.appendChild(currentPath.isBlank()
                ? translation("DIV", "file_picker.apricityui.root", "content-title aui-file-picker-current-path")
                : text("DIV", currentPath, "content-title aui-file-picker-current-path"));
        if (options.allowsHtmlCreation()) {
            Element create = element("BUTTON", "action-btn aui-file-picker-create");
            create.setAttribute("type", "button");
            create.appendChild(translation("SPAN", "file_picker.apricityui.action.new_html", null));
            create.addEventListener("click", event -> {
                creatingHtml = !creatingHtml;
                render();
            });
            toolbar.appendChild(create);
        }
        details.appendChild(toolbar);
        if (creatingHtml) details.appendChild(renderCreateHtml());

        Element list = element("DIV", "file-grid aui-file-picker-list");
        for (String folder : directFolders()) {
            Element item = element("BUTTON", "file-card aui-file-picker-folder");
            item.setAttribute("type", "button");
            item.appendChild(text("SPAN", fileName(folder) + "/", "file-name"));
            item.addEventListener("click", event -> {
                currentPath = folder;
                selected = null;
                render();
            });
            list.appendChild(item);
        }
        for (Loader.StaticResourceEntry entry : directFiles()) list.appendChild(fileItem(entry));
        if (list.children.isEmpty()) list.appendChild(translation("DIV", "file_picker.apricityui.empty", "aui-file-picker-empty"));
        details.appendChild(list);

        Element footer = element("DIV", "aui-file-picker-footer");
        footer.appendChild(selected == null
                ? translation("DIV", "file_picker.apricityui.select_file", "aui-file-picker-selection")
                : text("DIV", selected.path(), "aui-file-picker-selection"));
        Element select = element("BUTTON", "action-btn aui-file-picker-select");
        select.setAttribute("type", "button");
        select.setDisabled(selected == null);
        select.appendChild(translation("SPAN", "file_picker.apricityui.action.select", null));
        select.addEventListener("click", event -> confirm());
        footer.appendChild(select);
        details.appendChild(footer);
        return details;
    }

    private Element renderCreateHtml() {
        Element form = element("DIV", "aui-file-picker-create-form");
        Element input = element("INPUT", "dialog-input aui-file-picker-create-input");
        input.setAttribute("type", "text");
        input.setAttribute("placeholder", "new-file.html");
        input.setValue(currentPath.isBlank() ? "new-file.html" : currentPath + "/new-file.html");
        Element submit = element("BUTTON", "action-btn aui-file-picker-create-submit");
        submit.setAttribute("type", "button");
        submit.appendChild(translation("SPAN", "file_picker.apricityui.action.create", null));
        submit.addEventListener("click", event -> createHtml(input.getValue()));
        form.appendChild(input);
        form.appendChild(submit);
        return form;
    }

    private Element fileItem(Loader.StaticResourceEntry entry) {
        boolean activeSelection = selected != null && selected.path().equals(entry.path()) && selected.layer() == entry.layer();
        Element item = element("BUTTON", activeSelection ? "file-card selected aui-file-picker-file" : "file-card aui-file-picker-file");
        item.setAttribute("type", "button");
        item.appendChild(text("SPAN", fileName(entry.path()), "file-name aui-file-picker-file-name"));
        String sourceKey = entry.layer() == Loader.ResourceLayer.RESOURCE_PACK
                ? "file_picker.apricityui.source.pack" : "file_picker.apricityui.source.local";
        item.appendChild(translation("SPAN", sourceKey, "file-meta aui-file-picker-file-source"));
        item.addEventListener("click", event -> {
            selected = entry;
            render();
        });
        item.addEventListener("dblclick", event -> {
            selected = entry;
            confirm();
        });
        return item;
    }

    private void createHtml(String requestedPath) {
        String path = ResourceFileWriter.validateHtmlPath(requestedPath);
        if (path.isBlank()) return;
        ResourceFileWriter.WriteResult write = ResourceFileWriter.writeHtml(path,
                "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"></head><body></body></html>\n");
        if (!write.success()) return;
        ClientLoader.invalidateStaticResourceCache();
        entries = filter(ClientLoader.listFinalStaticResources());
        currentPath = ResourcePath.parent(path);
        selected = entries.stream().filter(entry -> path.equals(entry.path())).findFirst().orElse(null);
        creatingHtml = false;
        render();
    }

    private void confirm() {
        if (selected == null) return;
        finish(Optional.of(new Selection(selected.path(), selected.layer(), resolveLocalPath(selected))));
    }

    private synchronized void finish(Optional<Selection> value) {
        if (root != null) root.remove();
        root = null;
        if (!result.isDone()) result.complete(value == null ? Optional.empty() : value);
        synchronized (FilePicker.class) {
            if (active == this) active = null;
        }
        if (document != null && document.getPath().equals(PATH) && !document.isDisposed()) document.remove();
    }

    private List<Loader.StaticResourceEntry> filter(List<Loader.StaticResourceEntry> source) {
        if (source == null) return List.of();
        return source.stream().filter(options::accepts).sorted(Comparator.comparing(Loader.StaticResourceEntry::path)).toList();
    }

    private List<String> folders() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Loader.StaticResourceEntry entry : entries) {
            String[] parts = ResourcePath.normalize(entry.path()).split("/");
            StringBuilder path = new StringBuilder();
            for (int index = 0; index < parts.length - 1; index++) {
                if (!path.isEmpty()) path.append('/');
                path.append(parts[index]);
                result.add(path.toString());
            }
        }
        return result.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private FolderNode buildFolderTree() {
        FolderNode treeRoot = new FolderNode("ROOT", "");
        for (Loader.StaticResourceEntry entry : entries) {
            if (entry == null) continue;
            String[] parts = ResourcePath.normalize(entry.path()).split("/");
            FolderNode cursor = treeRoot;
            StringBuilder folderPath = new StringBuilder();
            for (int index = 0; index < parts.length - 1; index++) {
                String name = parts[index];
                if (name.isBlank()) continue;
                if (!folderPath.isEmpty()) folderPath.append('/');
                folderPath.append(name);
                String path = folderPath.toString();
                cursor = cursor.folders.computeIfAbsent(name, ignored -> new FolderNode(name, path));
            }
            cursor.files.add(entry);
        }
        return treeRoot;
    }

    private void expandAncestors(String path) {
        StringBuilder ancestor = new StringBuilder();
        for (String part : ResourcePath.normalize(path).split("/")) {
            if (part.isBlank()) continue;
            if (!ancestor.isEmpty()) ancestor.append('/');
            ancestor.append(part);
            expandedPaths.add(ancestor.toString());
        }
    }

    private List<String> directFolders() {
        String prefix = currentPath.isBlank() ? "" : currentPath + "/";
        return folders().stream().filter(folder -> ResourcePath.parent(folder).equals(currentPath))
                .filter(folder -> folder.startsWith(prefix)).toList();
    }

    private List<Loader.StaticResourceEntry> directFiles() {
        return entries.stream().filter(entry -> ResourcePath.parent(entry.path()).equals(currentPath)).toList();
    }

    private static Path resolveLocalPath(Loader.StaticResourceEntry entry) {
        if (entry == null || entry.layer() == Loader.ResourceLayer.RESOURCE_PACK || entry.sourceRoot() == null || entry.sourceRoot().isBlank()) return null;
        try {
            Path root = Path.of(entry.sourceRoot()).toAbsolutePath().normalize();
            if (!Files.exists(root)) return null;
            Path resolved = root.resolve(ResourcePath.normalize(entry.path())).normalize();
            return resolved.startsWith(root) ? resolved : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Element element(String tag, String className) {
        Element element = Element.init(document.createElement(tag));
        element.setAttribute("class", className);
        return element;
    }

    private Element text(String tag, String value, String className) {
        Element element = element(tag, className);
        element.setTextContent(value == null ? "" : value);
        return element;
    }

    private Element translation(String tag, String key, String className) {
        Element element = element(tag, className);
        element.appendChild(Element.init(document.createElement("TRANSLATION")));
        element.children.get(0).setTextContent(key == null ? "" : key);
        return element;
    }

    private void dirty() {
        if (document != null && document.body != null) document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
    }

    private static String fileName(String value) {
        String normalized = ResourcePath.normalize(value);
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    private static String normalizeExtension(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private static final class FolderNode {
        private final String name;
        private final String path;
        private final Map<String, FolderNode> folders = new LinkedHashMap<>();
        private final List<Loader.StaticResourceEntry> files = new ArrayList<>();

        private FolderNode(String name, String path) {
            this.name = name == null ? "" : name;
            this.path = ResourcePath.normalize(path);
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
}
