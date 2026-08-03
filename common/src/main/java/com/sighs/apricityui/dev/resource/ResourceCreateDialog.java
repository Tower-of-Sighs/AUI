package com.sighs.apricityui.dev.resource;

import com.sighs.apricityui.ui.ToastManager;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.ui.DialogWindow;

import java.util.Locale;
import com.sighs.apricityui.parser.HTML;

/** Java-owned create/import overlay used by the resource browser. */
public final class ResourceCreateDialog {
    private static final String LOCAL_FILE_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><path d=\"M12 5h16l8 8v30H12z\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><path d=\"M28 5v10h8\" stroke=\"#8b5cf6\" stroke-width=\"2\"/><path d=\"M17 24h14M17 30h14M17 36h9\" stroke=\"#8b5cf6\" stroke-width=\"2\"/></svg>";
    private static final String CLIPBOARD_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><rect x=\"11\" y=\"9\" width=\"26\" height=\"34\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"17\" y=\"4\" width=\"14\" height=\"9\" fill=\"#8b5cf6\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><path d=\"M17 23h14M17 30h14M17 37h9\" stroke=\"#8b5cf6\" stroke-width=\"2\"/></svg>";
    private static final String BLANK_TEMPLATE_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><path d=\"M12 5h16l8 8v30H12z\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><path d=\"M28 5v10h8\" stroke=\"#8b5cf6\" stroke-width=\"2\"/><path d=\"M17 24h14M17 30h14\" stroke=\"#8b5cf6\" stroke-width=\"2\"/><path d=\"M20 37h8\" stroke=\"#8b5cf6\" stroke-width=\"2\"/></svg>";
    private final ResourceMetaDialog templateMetaDialog = new ResourceMetaDialog();
    private String importedContent = "";
    private Element pathInput;
    private Element localFileInput;
    private Element localCard;
    private Element clipboardCard;
    private Element blankTemplateCard;
    private Element submitButton;
    private DialogWindow dialog;

    public void open(Document document, String currentPath, Runnable afterCreate) {
        close();
        if (document == null || document.body == null) return;
        openFrameworkDialog(document, currentPath, afterCreate);
    }

    public void close() {
        templateMetaDialog.close();
        if (dialog != null) dialog.close();
        dialog = null;
        pathInput = null;
        localFileInput = null;
        localCard = null;
        clipboardCard = null;
        blankTemplateCard = null;
        submitButton = null;
        importedContent = "";
    }

    private void openFrameworkDialog(Document document, String currentPath, Runnable afterCreate) {
        dialog = DialogWindow.open(document, new DialogWindow.Options(
                "NEW HTML", 720, 0, false,
                "dialog-overlay show", "dialog",
                "dialog-header", "dialog-title", "dialog-close", "dialog-body", "dialog-title-icon"
        ), null);
        Element root = dialog.content();
        Element pathField = element(document, "DIV", "dialog-field");
        pathField.append(text(document, "LABEL", "SAVE PATH", "dialog-label"));
        pathInput = element(document, "INPUT", "dialog-input");
        pathInput.setAttribute("type", "text");
        pathInput.setAttribute("placeholder", "example/original-file.html");
        String normalizedCurrentPath = ResourcePath.normalize(currentPath);
        pathInput.value = normalizedCurrentPath.isBlank() ? "/" : normalizedCurrentPath + "/";
        pathInput.addEventListener("input", event -> refreshSubmit(document));
        pathInput.addEventListener("change", event -> refreshSubmit(document));
        pathField.append(pathInput); root.append(pathField);
        Element importGrid = element(document, "DIV", "resource-import-grid");
        localCard = importCard(document, "LOCAL FILE", "OPEN FILE PICKER", LOCAL_FILE_ICON, "resource-import-local");
        localFileInput = element(document, "INPUT", "resource-create-file-input");
        localFileInput.setAttribute("type", "file"); localFileInput.setAttribute("accept", ".html,text/html");
        localFileInput.addEventListener("change", event -> importLocal(document, localFileInput.value));
        localCard.addEventListener("click", event -> localFileInput.click());
        clipboardCard = importCard(document, "CLIPBOARD", "IMPORT HTML TEXT", CLIPBOARD_ICON, "resource-import-clipboard");
        clipboardCard.addEventListener("click", event -> importClipboard(document));
        blankTemplateCard = importCard(document, "BLANK TEMPLATE", "CONFIGURE META", BLANK_TEMPLATE_ICON, "resource-import-template");
        blankTemplateCard.addEventListener("click", event -> openBlankTemplate(document));
        importGrid.append(localCard); importGrid.append(clipboardCard); importGrid.append(blankTemplateCard); root.append(importGrid); root.append(localFileInput);
        Element submitRow = element(document, "DIV", "dialog-footer");
        submitButton = element(document, "BUTTON", "dialog-btn dialog-btn-confirm");
        submitButton.append(text(document, "SPAN", "CREATE", "dialog-btn-label"));
        submitButton.addEventListener("click", event -> submit(afterCreate));
        submitRow.append(submitButton); dialog.window().append(submitRow);
        refreshSubmit(document);
        markDirty(document);
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

    private void openBlankTemplate(Document document) {
        templateMetaDialog.openTemplate(document, metaMarkup -> {
            importedContent = blankHtml(metaMarkup);
            updateCard(blankTemplateCard, "BLANK TEMPLATE READY", "BROWSER META");
            refreshSubmit(document);
        });
    }

    private static String blankHtml(String metaMarkup) {
        String meta = metaMarkup == null ? "" : metaMarkup.trim();
        StringBuilder html = new StringBuilder("<!DOCTYPE html>\n<html>\n<head>");
        if (!meta.isBlank()) {
            for (String line : meta.split("\\R")) html.append("\n    ").append(line);
            html.append('\n');
        }
        return html.append("</head>\n<body></body>\n</html>\n").toString();
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
        if (isReady()) submitButton.removeAttribute("disabled");
        else submitButton.setAttribute("disabled", "disabled");
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
