package com.sighs.apricityui.ui.file;

import com.sighs.apricityui.dev.resource.ResourceFileWriter;
import com.sighs.apricityui.dev.resource.ResourcePath;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.ClientLoader;
import com.sighs.apricityui.instance.Loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Global, resource-manager-backed file selection UI. The result is empty when the
 * user cancels; callers never need to inspect the picker document or its DOM state.
 */
public final class FilePicker {
    private static final String PATH = "devtools/file-picker.html";
    private static FilePicker active;

    public record Options(String title, Set<String> extensions, boolean includeResourcePackFiles) {
        public Options {
            title = title == null || title.isBlank() ? "SELECT FILE" : title.trim();
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

    private FilePicker(Document document, Options options, List<Loader.StaticResourceEntry> entries) {
        this.document = document;
        this.options = options == null ? Options.any("SELECT FILE", true) : options;
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
        if (active != null) active.finish(Optional.empty());
        active = new FilePicker(document, options, entries);
        active.render();
        return active.result;
    }

    private void render() {
        if (root != null) root.remove();
        root = element("DIV", "aui-file-picker-overlay");
        root.setTopLayer(true);
        root.addEventListener("click", event -> {
            if (event.target == root) finish(Optional.empty());
        });
        Element panel = element("DIV", "aui-file-picker");
        Element heading = element("DIV", "aui-file-picker-heading");
        heading.appendChild(text("DIV", options.title(), "aui-file-picker-title"));
        Element cancel = element("BUTTON", "aui-file-picker-cancel");
        cancel.setAttribute("type", "button");
        cancel.setTextContent("CANCEL");
        cancel.addEventListener("click", event -> finish(Optional.empty()));
        heading.appendChild(cancel);
        panel.appendChild(heading);

        Element body = element("DIV", "aui-file-picker-body");
        body.appendChild(renderPaths());
        body.appendChild(renderDetails());
        panel.appendChild(body);
        root.appendChild(panel);
        document.body.appendChild(root);
        dirty();
    }

    private Element renderPaths() {
        Element paths = element("DIV", "aui-file-picker-paths");
        paths.appendChild(text("DIV", "PATHS", "aui-file-picker-section-label"));
        Element rootItem = pathItem("ROOT", "", 0);
        paths.appendChild(rootItem);
        for (String folder : folders()) {
            int depth = folder.split("/").length;
            paths.appendChild(pathItem(fileName(folder), folder, depth));
        }
        return paths;
    }

    private Element pathItem(String label, String path, int depth) {
        boolean activePath = currentPath.equals(path);
        Element item = element("BUTTON", activePath ? "aui-file-picker-path active" : "aui-file-picker-path");
        item.setAttribute("type", "button");
        item.setAttribute("style", "padding-left:" + (12 + depth * 14) + "px;");
        item.setTextContent(label);
        item.addEventListener("click", event -> {
            currentPath = path;
            selected = null;
            creatingHtml = false;
            render();
        });
        return item;
    }

    private Element renderDetails() {
        Element details = element("DIV", "aui-file-picker-details");
        Element toolbar = element("DIV", "aui-file-picker-toolbar");
        toolbar.appendChild(text("DIV", currentPath.isBlank() ? "ROOT" : currentPath, "aui-file-picker-current-path"));
        if (options.allowsHtmlCreation()) {
            Element create = element("BUTTON", "aui-file-picker-create");
            create.setAttribute("type", "button");
            create.setTextContent("NEW HTML");
            create.addEventListener("click", event -> {
                creatingHtml = !creatingHtml;
                render();
            });
            toolbar.appendChild(create);
        }
        details.appendChild(toolbar);
        if (creatingHtml) details.appendChild(renderCreateHtml());

        Element list = element("DIV", "aui-file-picker-list");
        for (String folder : directFolders()) {
            Element item = element("BUTTON", "aui-file-picker-folder");
            item.setAttribute("type", "button");
            item.setTextContent(fileName(folder) + "/");
            item.addEventListener("click", event -> {
                currentPath = folder;
                selected = null;
                render();
            });
            list.appendChild(item);
        }
        for (Loader.StaticResourceEntry entry : directFiles()) list.appendChild(fileItem(entry));
        if (list.children.isEmpty()) list.appendChild(text("DIV", "NO MATCHING FILES", "aui-file-picker-empty"));
        details.appendChild(list);

        Element footer = element("DIV", "aui-file-picker-footer");
        footer.appendChild(text("DIV", selected == null ? "SELECT A FILE" : selected.path(), "aui-file-picker-selection"));
        Element select = element("BUTTON", "aui-file-picker-select");
        select.setAttribute("type", "button");
        select.setDisabled(selected == null);
        select.setTextContent("SELECT");
        select.addEventListener("click", event -> confirm());
        footer.appendChild(select);
        details.appendChild(footer);
        return details;
    }

    private Element renderCreateHtml() {
        Element form = element("DIV", "aui-file-picker-create-form");
        Element input = element("INPUT", "aui-file-picker-create-input");
        input.setAttribute("type", "text");
        input.setAttribute("placeholder", "new-file.html");
        input.setValue(currentPath.isBlank() ? "new-file.html" : currentPath + "/new-file.html");
        Element submit = element("BUTTON", "aui-file-picker-create-submit");
        submit.setAttribute("type", "button");
        submit.setTextContent("CREATE");
        submit.addEventListener("click", event -> createHtml(input.getValue()));
        form.appendChild(input);
        form.appendChild(submit);
        return form;
    }

    private Element fileItem(Loader.StaticResourceEntry entry) {
        boolean activeSelection = selected != null && selected.path().equals(entry.path()) && selected.layer() == entry.layer();
        Element item = element("BUTTON", activeSelection ? "aui-file-picker-file active" : "aui-file-picker-file");
        item.setAttribute("type", "button");
        item.appendChild(text("SPAN", fileName(entry.path()), "aui-file-picker-file-name"));
        String source = entry.layer() == Loader.ResourceLayer.RESOURCE_PACK ? "PACK" : "LOCAL";
        item.appendChild(text("SPAN", source, "aui-file-picker-file-source"));
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
}
