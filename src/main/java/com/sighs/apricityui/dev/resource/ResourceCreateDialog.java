package com.sighs.apricityui.dev.resource;

import com.sighs.apricityui.dev.ToastManager;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.init.Operation;

import java.util.Locale;

/** Java-owned create/import overlay used by the resource browser. */
public final class ResourceCreateDialog {
    private static final String LOCAL_FILE_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><path d=\"M12 5h16l8 8v30H12z\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><path d=\"M28 5v10h8\" stroke=\"#8b5cf6\" stroke-width=\"2\"/><path d=\"M17 24h14M17 30h14M17 36h9\" stroke=\"#8b5cf6\" stroke-width=\"2\"/></svg>";
    private static final String CLIPBOARD_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><rect x=\"11\" y=\"9\" width=\"26\" height=\"34\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"17\" y=\"4\" width=\"14\" height=\"9\" fill=\"#8b5cf6\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><path d=\"M17 23h14M17 30h14M17 37h9\" stroke=\"#8b5cf6\" stroke-width=\"2\"/></svg>";
    private String importedContent = "";
    private Element overlay;
    private Element pathInput;
    private Element localFileInput;
    private Element localCard;
    private Element clipboardCard;
    private Element submitButton;

    public void open(Document document, String currentPath, Runnable afterCreate) {
        close();
        if (document == null || document.body == null) return;

        overlay = element(document, "DIV", "resource-create-overlay opening");
        overlay.setAttribute("id", "resourceCreateDialog");
        Element dialog = element(document, "DIV", "resource-create-dialog");
        overlay.append(dialog);

        Element heading = element(document, "DIV", "resource-create-heading");
        heading.append(text(document, "DIV", "NEW HTML", "resource-create-title"));
        Element close = text(document, "BUTTON", "x", "resource-create-close");
        close.addEventListener("click", event -> close());
        heading.append(close);
        dialog.append(heading);

        Element pathField = element(document, "DIV", "resource-create-path");
        pathField.append(text(document, "LABEL", "SAVE PATH", "resource-create-label"));
        pathInput = element(document, "INPUT", "resource-create-input");
        pathInput.setAttribute("type", "text");
        pathInput.setAttribute("placeholder", "example/original-file.html");
        pathInput.value = ResourcePath.normalize(currentPath).isBlank() ? "" : ResourcePath.normalize(currentPath) + "/";
        pathInput.addEventListener("input", event -> refreshSubmit(document));
        pathInput.addEventListener("change", event -> refreshSubmit(document));
        pathField.append(pathInput);
        dialog.append(pathField);

        Element importGrid = element(document, "DIV", "resource-import-grid");
        localCard = importCard(document, "LOCAL FILE", "OPEN FILE PICKER", LOCAL_FILE_ICON, "resource-import-local");
        localFileInput = element(document, "INPUT", "resource-create-file-input");
        localFileInput.setAttribute("type", "file");
        localFileInput.setAttribute("accept", ".html,text/html");
        localFileInput.addEventListener("change", event -> importLocal(document, localFileInput.value));
        localCard.addEventListener("click", event -> localFileInput.click());
        clipboardCard = importCard(document, "CLIPBOARD", "IMPORT HTML TEXT", CLIPBOARD_ICON, "resource-import-clipboard");
        clipboardCard.addEventListener("click", event -> importClipboard(document));
        importGrid.append(localCard);
        importGrid.append(clipboardCard);
        dialog.append(importGrid);
        dialog.append(localFileInput);

        Element submitRow = element(document, "DIV", "resource-create-submit-row");
        submitButton = text(document, "BUTTON", "CREATE", "resource-create-submit");
        submitButton.addEventListener("click", event -> submit(afterCreate));
        submitRow.append(submitButton);
        dialog.append(submitRow);

        overlay.addEventListener("click", event -> {
            if (event.target == overlay) close();
        });
        document.body.append(overlay);
        markDirty(document);
    }

    public void close() {
        if (overlay != null) {
            Document document = overlay.getOwnerDocument();
            overlay.remove();
            markDirty(document);
        }
        overlay = null;
        pathInput = null;
        localFileInput = null;
        localCard = null;
        clipboardCard = null;
        submitButton = null;
        importedContent = "";
    }

    private void importLocal(Document document, String selectedPath) {
        ResourceFileWriter.ImportedFile imported;
        try {
            imported = selectedPath == null || selectedPath.isBlank() ? null : ResourceFileWriter.readHtmlFile(java.nio.file.Path.of(selectedPath));
        } catch (Exception ignored) {
            imported = null;
        }
        if (imported == null) {
            ToastManager.show("Select an HTML file to import");
            return;
        }
        importedContent = imported.content();
        if (pathInput != null && (readInput().isBlank() || readInput().endsWith("/"))) {
            pathInput.value = readInput() + imported.name();
        }
        updateCard(localCard, imported.name(), abbreviate(imported.path().toString()));
        refreshSubmit(document);
    }

    private void importClipboard(Document document) {
        String content = Operation.getClipboardText();
        if (content.isBlank()) {
            ToastManager.show("Clipboard has no HTML content");
            return;
        }
        importedContent = content;
        updateCard(clipboardCard, "CLIPBOARD READY", ResourcePath.formatSize(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
        refreshSubmit(document);
    }

    private void submit(Runnable afterCreate) {
        if (submitButton == null || !isReady()) {
            ToastManager.show("Choose content and a .html path");
            return;
        }
        ResourceFileWriter.WriteResult result = ResourceFileWriter.writeHtml(readInput(), importedContent);
        if (!result.success()) {
            ToastManager.show(result.message());
            return;
        }
        String createdPath = ResourcePath.normalize(readInput());
        close();
        ToastManager.show("Created " + createdPath);
        if (afterCreate != null) afterCreate.run();
    }

    private boolean isReady() {
        return !importedContent.isBlank() && !ResourceFileWriter.validateHtmlPath(readInput()).isBlank();
    }

    private String readInput() {
        return pathInput == null || pathInput.value == null ? "" : pathInput.value;
    }

    private void refreshSubmit(Document document) {
        if (submitButton == null) return;
        submitButton.setAttribute("class", isReady() ? "resource-create-submit ready" : "resource-create-submit");
        markDirty(document);
    }

    private static Element importCard(Document document, String name, String meta, String icon, String className) {
        Element card = element(document, "DIV", "file-card resource-import-card " + className);
        Element iconElement = element(document, "DIV", "resource-import-icon");
        iconElement.setInnerHTML(icon);
        card.append(iconElement);
        card.append(text(document, "DIV", name, "file-name"));
        card.append(text(document, "DIV", meta, "file-meta"));
        return card;
    }

    private static void updateCard(Element card, String name, String meta) {
        if (card == null) return;
        Element nameElement = card.querySelector(".file-name");
        Element metaElement = card.querySelector(".file-meta");
        if (nameElement != null) nameElement.setTextContent(name.toUpperCase(Locale.ROOT));
        if (metaElement != null) metaElement.setTextContent(meta);
        card.setAttribute("class", card.getAttribute("class") + " imported");
    }

    private static String abbreviate(String value) {
        String safe = ResourcePath.safe(value);
        return safe.length() <= 42 ? safe : "..." + safe.substring(safe.length() - 39);
    }

    private static Element element(Document document, String tagName, String className) {
        Element element = Element.init(document.createElement(tagName));
        element.setAttribute("class", className);
        return element;
    }

    private static Element text(Document document, String tagName, String value, String className) {
        Element element = element(document, tagName, className);
        element.setTextContent(value);
        return element;
    }

    private static void markDirty(Document document) {
        if (document != null && document.body != null) {
            document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        }
    }
}
