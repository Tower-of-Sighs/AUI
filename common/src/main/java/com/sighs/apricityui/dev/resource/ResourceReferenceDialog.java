package com.sighs.apricityui.dev.resource;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.ui.DialogWindow;
import com.sighs.apricityui.ui.ToastManager;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.HTML;

/** Builds copyable HTML/CSS references for image and font resources. */
public final class ResourceReferenceDialog {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp", "gif", "webp");
    private static final Set<String> HTML_EXTENSIONS = Set.of("html", "htm");
    private static final String BACKGROUND_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><rect x=\"5\" y=\"7\" width=\"38\" height=\"34\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><circle cx=\"17\" cy=\"19\" r=\"4\" fill=\"#8b5cf6\"/><path d=\"M7 37l10-10 7 6 8-12 9 16\" fill=\"none\" stroke=\"#8b5cf6\" stroke-width=\"3\"/></svg>";
    private static final String IMAGE_TAG_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><path d=\"M13 10L4 24l9 14M35 10l9 14-9 14\" stroke=\"#1a1a1a\" stroke-width=\"3\"/><rect x=\"17\" y=\"14\" width=\"14\" height=\"20\" stroke=\"#8b5cf6\" stroke-width=\"2\"/><path d=\"M19 31l4-5 3 3 3-5\" stroke=\"#8b5cf6\" stroke-width=\"2\"/></svg>";
    private static final String FONT_FACE_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><path d=\"M9 38L21 9h6l12 29M14 28h20\" stroke=\"#8b5cf6\" stroke-width=\"3\"/><rect x=\"4\" y=\"4\" width=\"40\" height=\"40\" stroke=\"#1a1a1a\" stroke-width=\"2\"/></svg>";
    private static final String FONT_FAMILY_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><path d=\"M7 37L18 10h5l11 27M11 28h19\" stroke=\"#8b5cf6\" stroke-width=\"3\"/><path d=\"M30 34c0-5 3-9 7-9 3 0 5 2 5 5v8\" stroke=\"#1a1a1a\" stroke-width=\"2\"/></svg>";
    private static final String SCREEN_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><rect x=\"5\" y=\"7\" width=\"38\" height=\"29\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><path d=\"M18 42h12M24 36v6\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"10\" y=\"12\" width=\"28\" height=\"19\" fill=\"#8b5cf6\" opacity=\".22\"/><path d=\"M15 18h18M15 23h12\" stroke=\"#8b5cf6\" stroke-width=\"2\"/></svg>";
    private static final String CONTAINER_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><rect x=\"5\" y=\"5\" width=\"38\" height=\"38\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><path d=\"M8 16h32\" stroke=\"#8b5cf6\" stroke-width=\"3\"/><rect x=\"11\" y=\"21\" width=\"7\" height=\"7\" fill=\"#8b5cf6\"/><rect x=\"21\" y=\"21\" width=\"7\" height=\"7\" fill=\"#8b5cf6\"/><rect x=\"31\" y=\"21\" width=\"7\" height=\"7\" fill=\"#8b5cf6\"/><rect x=\"11\" y=\"31\" width=\"7\" height=\"7\" fill=\"#1a1a1a\"/><rect x=\"21\" y=\"31\" width=\"7\" height=\"7\" fill=\"#1a1a1a\"/></svg>";
    private static final String OVERLAY_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><rect x=\"5\" y=\"10\" width=\"30\" height=\"28\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"13\" y=\"5\" width=\"30\" height=\"28\" fill=\"white\" stroke=\"#8b5cf6\" stroke-width=\"3\"/><path d=\"M18 12h20M18 18h14M18 24h18\" stroke=\"#8b5cf6\" stroke-width=\"2\"/></svg>";
    private static final String WORLD_ICON = "<svg viewBox=\"0 0 48 48\" fill=\"none\"><path d=\"M6 34l18 9 18-9-18-9-18 9z\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><path d=\"M24 25v18M6 34l18 9 18-9\" stroke=\"#1a1a1a\" stroke-width=\"2\"/><rect x=\"12\" y=\"5\" width=\"24\" height=\"20\" fill=\"white\" stroke=\"#8b5cf6\" stroke-width=\"3\"/><path d=\"M17 11h14M17 16h10\" stroke=\"#8b5cf6\" stroke-width=\"2\"/></svg>";

    private DialogWindow dialog;
    private Document document;
    private Loader.StaticResourceEntry entry;
    private Element optionGrid;
    private Element codeArea;
    private Element familyInput;
    private Element languageSelect;
    private Element copyButton;
    private List<ReferenceOption> options = List.of();
    private int selectedIndex;

    public static boolean supports(Loader.StaticResourceEntry entry) {
        return isImage(entry) || ResourceFontAsset.isFont(entry) || isHtml(entry);
    }

    public void open(Document document, Loader.StaticResourceEntry entry) {
        close();
        if (document == null || document.body == null || !supports(entry)) return;
        this.document = document;
        this.entry = entry;
        this.selectedIndex = 0;
        boolean html = isHtml(entry);
        this.dialog = DialogWindow.open(document, new DialogWindow.Options(
                "REFERENCE / " + ResourcePath.fileName(entry.path()).toUpperCase(Locale.ROOT),
                720, html ? 620 : 520, false,
                "dialog-overlay show resource-reference-overlay",
                "dialog resource-reference-dialog" + (html ? " resource-reference-html-dialog" : ""),
                "dialog-header", "dialog-title", "dialog-close",
                "dialog-body resource-reference-body", "resource-reference-title-icon"
        ), this::clearReferences);

        Element root = dialog.content();
        root.setAttribute("style", "position:relative;flex:1;min-height:0;display:flex;flex-direction:column;");
        if (ResourceFontAsset.isFont(entry)) root.append(createFamilyField());
        if (html) root.append(createLanguageField());

        optionGrid = element("DIV", "resource-reference-grid" + (html ? " resource-reference-html-grid" : ""));
        root.append(optionGrid);

        Element codeField = element("DIV", "dialog-field resource-reference-code-field");
        codeField.append(text("LABEL", "REFERENCE CODE", "dialog-label"));
        // This dialog is created from Java before a document is necessarily
        // parsed through the element registry. Construct the specialized
        // control directly so its value is painted, not just stored on a
        // generic Element fallback.
        codeArea = new TextArea(document);
        codeArea.setAttribute("class", "resource-reference-code");
        codeArea.setAttribute("readonly", "readonly");
        codeArea.setAttribute("spellcheck", "false");
        codeField.append(codeArea);
        root.append(codeField);

        Element footer = element("DIV", "dialog-footer");
        copyButton = element("BUTTON", "dialog-btn dialog-btn-confirm resource-reference-copy");
        copyButton.append(text("SPAN", "COPY", "dialog-btn-label"));
        copyButton.addEventListener("click", event -> copyReference());
        footer.append(copyButton);
        dialog.window().append(footer);

        rebuildOptions();
        markDirty();
    }

    public void close() {
        DialogWindow openDialog = dialog;
        dialog = null;
        if (openDialog != null) openDialog.close();
        clearReferences();
    }

    private Element createFamilyField() {
        Element field = element("DIV", "dialog-field resource-reference-family-field");
        field.append(text("LABEL", "FONT FAMILY", "dialog-label"));
        familyInput = element("INPUT", "dialog-input resource-reference-family-input");
        familyInput.setAttribute("type", "text");
        familyInput.setAttribute("placeholder", "custom-font");
        familyInput.value = defaultFamily(entry);
        familyInput.setAttribute("value", familyInput.value);
        familyInput.addEventListener("input", event -> rebuildOptions());
        familyInput.addEventListener("change", event -> rebuildOptions());
        field.append(familyInput);
        return field;
    }

    private Element createLanguageField() {
        Element field = element("DIV", "dialog-field resource-reference-language-field");
        field.append(text("LABEL", "LANGUAGE", "dialog-label"));
        Element selectWrap = element("DIV", "dialog-select-wrap");
        languageSelect = element("SELECT", "dialog-select resource-reference-language-select");
        languageSelect.setAttribute("data-native-arrow", "false");
        languageSelect.append(option("JAVA", "java"));
        languageSelect.append(option("KUBEJS / KJS", "kjs"));
        languageSelect.setValue("java");
        languageSelect.addEventListener("input", event -> rebuildOptions());
        languageSelect.addEventListener("change", event -> rebuildOptions());
        selectWrap.append(languageSelect);
        selectWrap.append(text("DIV", "\u25be", "dialog-select-arrow"));
        field.append(selectWrap);
        return field;
    }

    private Element option(String label, String value) {
        Element option = text("OPTION", label, "resource-reference-language-option");
        option.setAttribute("value", value);
        return option;
    }

    private void rebuildOptions() {
        if (optionGrid == null || entry == null) return;
        options = optionsFor(entry,
                familyInput == null ? "" : familyInput.value,
                languageSelect == null ? "java" : languageSelect.getValue());
        selectedIndex = Math.max(0, Math.min(selectedIndex, Math.max(0, options.size() - 1)));
        optionGrid.clearChildren();
        for (int index = 0; index < options.size(); index++) {
            ReferenceOption option = options.get(index);
            Element card = element("DIV", optionClass(index));
            card.setAttribute("data-reference-index", Integer.toString(index));
            Element icon = element("DIV", "resource-reference-option-icon");
            icon.setInnerHTML(option.icon());
            card.append(icon);
            Element copy = element("DIV", "resource-reference-option-copy");
            copy.append(text("DIV", option.label(), "resource-reference-option-name"));
            copy.append(text("DIV", option.description(), "resource-reference-option-description"));
            card.append(copy);
            int selected = index;
            card.addEventListener("click", event -> selectOption(selected));
            optionGrid.append(card);
        }
        updateCode();
        markDirty();
    }

    private void selectOption(int index) {
        if (index < 0 || index >= options.size()) return;
        selectedIndex = index;
        for (int i = 0; i < optionGrid.children.size(); i++) {
            optionGrid.children.get(i).setAttribute("class", optionClass(i));
        }
        updateCode();
        markDirty();
    }

    private String optionClass(int index) {
        return "resource-reference-option" + (index == selectedIndex ? " selected" : "");
    }

    private void updateCode() {
        String snippet = selectedSnippet();
        if (codeArea != null) {
            codeArea.setValue(snippet);
            codeArea.setAttribute("value", snippet);
        }
        if (copyButton != null) {
            if (snippet.isBlank()) copyButton.setAttribute("disabled", "disabled");
            else copyButton.removeAttribute("disabled");
        }
    }

    private void copyReference() {
        String snippet = selectedSnippet();
        if (snippet.isBlank()) return;
        Operation.setClipboardText(snippet);
        ToastManager.show("Reference copied");
    }

    private String selectedSnippet() {
        return options.isEmpty() || selectedIndex < 0 || selectedIndex >= options.size()
                ? "" : options.get(selectedIndex).snippet();
    }

    private void clearReferences() {
        document = null;
        entry = null;
        optionGrid = null;
        codeArea = null;
        familyInput = null;
        languageSelect = null;
        copyButton = null;
        options = List.of();
        selectedIndex = 0;
    }

    static List<ReferenceOption> optionsFor(Loader.StaticResourceEntry entry, String requestedFamily) {
        return optionsFor(entry, requestedFamily, "java");
    }

    static List<ReferenceOption> optionsFor(Loader.StaticResourceEntry entry, String requestedFamily,
                                            String requestedLanguage) {
        if (isImage(entry)) {
            String path = rootPath(entry.path());
            String alt = htmlAttribute(fileStem(entry.path()));
            return List.of(
                    new ReferenceOption("BACKGROUND IMAGE", "CSS BACKGROUND PROPERTY", BACKGROUND_ICON,
                            "background-image: url(\"" + cssString(path) + "\");"),
                    new ReferenceOption("IMG TAG", "HTML IMAGE ELEMENT", IMAGE_TAG_ICON,
                            "<img src=\"" + htmlAttribute(path) + "\" alt=\"" + alt + "\">")
            );
        }
        if (ResourceFontAsset.isFont(entry)) {
            String path = rootPath(entry.path());
            String family = normalizeFamily(requestedFamily, entry);
            String escapedFamily = cssString(family);
            String format = "otf".equalsIgnoreCase(ResourcePath.safe(entry.extension())) ? "opentype" : "truetype";
            String face = "@font-face {\n"
                    + "    font-family: \"" + escapedFamily + "\";\n"
                    + "    src: url(\"" + cssString(path) + "\") format(\"" + format + "\");\n"
                    + "}";
            return List.of(
                    new ReferenceOption("FONT FACE", "REGISTER FONT RESOURCE", FONT_FACE_ICON, face),
                    new ReferenceOption("FONT FAMILY", "APPLY REGISTERED FONT", FONT_FAMILY_ICON,
                            "font-family: \"" + escapedFamily + "\", sans-serif;")
            );
        }
        if (isHtml(entry)) {
            String path = codeString(ResourcePath.normalize(entry.path()));
            boolean kjs = "kjs".equalsIgnoreCase(ResourcePath.safe(requestedLanguage));
            if (kjs) {
                return List.of(
                        new ReferenceOption("SCREEN", "OPEN A STANDARD UI SCREEN", SCREEN_ICON,
                                "ApricityUI.screen(\"" + path + "\")"),
                        new ReferenceOption("CONTAINER", "OPEN A SERVER-BOUND CONTAINER", CONTAINER_ICON,
                                "ApricityUI.menu(player, \"" + path
                                        + "\").bind(bindings => bindings.player())"),
                        new ReferenceOption("OVERLAY", "CREATE A SCREEN OVERLAY DOCUMENT", OVERLAY_ICON,
                                "let overlay = ApricityUI.createDocument(\"" + path + "\")"),
                        new ReferenceOption("IN-WORLD", "RENDER HTML ON A WORLD PLANE", WORLD_ICON,
                                "let worldWindow = ApricityUI.createWorldWindow(\n"
                                        + "    \"" + path + "\",\n"
                                        + "    0, 64, 0,\n"
                                        + "    16\n"
                                        + ")")
                );
            }
            return List.of(
                    new ReferenceOption("SCREEN", "OPEN A STANDARD UI SCREEN", SCREEN_ICON,
                            "ApricityUI.screen(\"" + path + "\");"),
                    new ReferenceOption("CONTAINER", "OPEN A SERVER-BOUND CONTAINER", CONTAINER_ICON,
                            "ApricityUI.menu(player, \"" + path
                                    + "\").bind(bindings -> bindings.player());"),
                    new ReferenceOption("OVERLAY", "CREATE A SCREEN OVERLAY DOCUMENT", OVERLAY_ICON,
                            "var overlay = ApricityUI.createDocument(\"" + path + "\");"),
                    new ReferenceOption("IN-WORLD", "RENDER HTML ON A WORLD PLANE", WORLD_ICON,
                            "var worldWindow = ApricityUI.createWorldWindow(\n"
                                    + "    \"" + path + "\",\n"
                                    + "    new Vec3(0.0, 64.0, 0.0),\n"
                                    + "    16\n"
                                    + ");")
            );
        }
        return List.of();
    }

    private static boolean isImage(Loader.StaticResourceEntry entry) {
        String extension = entry == null ? "" : ResourcePath.safe(entry.extension()).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(extension);
    }

    private static boolean isHtml(Loader.StaticResourceEntry entry) {
        String extension = entry == null ? "" : ResourcePath.safe(entry.extension()).toLowerCase(Locale.ROOT);
        return HTML_EXTENSIONS.contains(extension);
    }

    private static String normalizeFamily(String requestedFamily, Loader.StaticResourceEntry entry) {
        String family = ResourcePath.safe(requestedFamily).trim();
        return family.isBlank() ? defaultFamily(entry) : family;
    }

    private static String defaultFamily(Loader.StaticResourceEntry entry) {
        String stem = fileStem(entry == null ? "" : entry.path()).trim();
        return stem.isBlank() ? "custom-font" : stem;
    }

    private static String fileStem(String path) {
        String name = ResourcePath.fileName(path);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String rootPath(String path) {
        String normalized = ResourcePath.normalize(path);
        return normalized.isBlank() ? "/" : "/" + normalized;
    }

    private static String cssString(String value) {
        return ResourcePath.safe(value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "").replace("\n", "\\a ");
    }

    private static String htmlAttribute(String value) {
        return ResourcePath.safe(value).replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String codeString(String value) {
        return ResourcePath.safe(value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "").replace("\n", "");
    }

    private Element element(String tagName, String className) {
        Element element = Element.init(document.createElement(tagName));
        if (className != null && !className.isBlank()) element.setAttribute("class", className);
        return element;
    }

    private Element text(String tagName, String value, String className) {
        Element element = element(tagName, className);
        element.setTextContent(ResourcePath.safe(value));
        return element;
    }

    private void markDirty() {
        if (document != null && document.body != null) {
            document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        }
    }

    record ReferenceOption(String label, String description, String icon, String snippet) {
    }
}
