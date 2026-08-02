package com.sighs.apricityui.dev.resource;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.ui.DialogWindow;
import com.sighs.apricityui.ui.ToastManager;
import com.sighs.apricityui.ui.Tooltip;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import com.sighs.apricityui.parser.HTML;

/** Resource-browser dialog for editing the meta elements in an HTML head. */
public final class ResourceMetaDialog {
    private static final double MIN_ZOOM = 0.01d;
    private static final double MAX_ZOOM = 10.0d;
    private static final List<Choice> FONT_MODE_CHOICES = List.of(
            new Choice("NOT SET", "", "tooltip.apricityui.meta.font_mode.not_set"),
            new Choice("MC", "mc", "tooltip.apricityui.meta.font_mode.mc"),
            new Choice("WEB", "web", "tooltip.apricityui.meta.font_mode.web"),
            new Choice("WEB SCALED", "web-scaled", "tooltip.apricityui.meta.font_mode.web_scaled")
    );
    private static final List<Choice> VIEWPORT_CHOICES = List.of(
            new Choice("NOT SET", "", "tooltip.apricityui.meta.viewport.not_set"),
            new Choice("GUI", "mode=gui", "tooltip.apricityui.meta.viewport.gui"),
            new Choice("BROWSER", "mode=browser", "tooltip.apricityui.meta.viewport.browser"),
            new Choice("WINDOW", "mode=window", "tooltip.apricityui.meta.viewport.window"),
            new Choice("FIXED / 427 x 249", "mode=fixed,width=427,height=249", "tooltip.apricityui.meta.viewport.fixed_gui"),
            new Choice("FIXED / 1920 x 1080 / FIT", "mode=fixed,width=1920,height=1080,scale=fit", "tooltip.apricityui.meta.viewport.fixed_fit")
    );
    private static final List<Choice> MOUSE_EVENT_CHOICES = List.of(
            new Choice("NOT SET / PASS THROUGH", "", "tooltip.apricityui.meta.mouse_events.pass_through"),
            new Choice("INTERCEPT", "intercept", "tooltip.apricityui.meta.mouse_events.intercept")
    );
    private DialogWindow dialog;
    private Element fontModeSelect;
    private Element viewportSelect;
    private Element mouseEventsSelect;
    private Element zoomInput;
    private Element saveButton;
    private Document document;
    private Path target;
    private Runnable afterSave;
    private Consumer<String> templateSave;
    private Consumer<Double> zoomSave;
    private String charset = "";
    private List<String> preservedMeta = List.of();

    public void open(Document document, String resourcePath, Path target, Runnable afterSave) {
        close();
        if (document == null || document.body == null) return;
        HtmlMetaEditor.LoadResult loaded = HtmlMetaEditor.load(target);
        if (!loaded.success()) {
            ToastManager.show(loaded.message());
            return;
        }
        openEditor(document, "EDIT META / " + ResourcePath.fileName(resourcePath).toUpperCase(java.util.Locale.ROOT),
                target, afterSave, HtmlMetaEditor.parseSettings(loaded.metaMarkup()), null,
                Double.NaN, null);
    }

    /** Opens the editor with a live document zoom field, used by DevTools. */
    public void open(Document document, String resourcePath, Path target, Runnable afterSave,
                     double currentZoom, Consumer<Double> onZoomSave) {
        close();
        if (document == null || document.body == null) return;
        HtmlMetaEditor.LoadResult loaded = HtmlMetaEditor.load(target);
        if (!loaded.success()) {
            ToastManager.show(loaded.message());
            return;
        }
        openEditor(document, "EDIT META / " + ResourcePath.fileName(resourcePath).toUpperCase(java.util.Locale.ROOT),
                target, afterSave, HtmlMetaEditor.parseSettings(loaded.metaMarkup()), null,
                currentZoom, onZoomSave);
    }

    /** Opens the same editor for a not-yet-created document and returns its meta markup. */
    public void openTemplate(Document document, Consumer<String> onSave) {
        close();
        if (document == null || document.body == null) return;
        HtmlMetaEditor.MetaSettings browserDefaults = new HtmlMetaEditor.MetaSettings(
                "UTF-8", "web", "mode=browser", "intercept", List.of());
        openEditor(document, "NEW HTML META", null, null, browserDefaults, onSave,
                Double.NaN, null);
    }

    private void openEditor(Document document, String title, Path target, Runnable afterSave,
                            HtmlMetaEditor.MetaSettings settings, Consumer<String> templateSave,
                            double currentZoom, Consumer<Double> zoomSave) {
        this.document = document;
        this.target = target;
        this.afterSave = afterSave;
        this.templateSave = templateSave;
        this.zoomSave = zoomSave;
        this.dialog = DialogWindow.open(document, new DialogWindow.Options(
                title, 720, 520, true,
                "dialog-overlay show", "dialog",
                "dialog-header", "dialog-title", "dialog-close", "dialog-body", "dialog-title-icon"
        ), this::clearReferences);

        Element root = dialog.content();
        root.setAttribute("style", "position:relative;flex:1;min-height:0;display:flex;flex-direction:column;");
        Element fields = element("DIV", "resource-meta-fields");
        fields.setAttribute("style", "margin-top:16px;flex:1;min-height:0;overflow:auto;");
        charset = settings.charset();
        preservedMeta = settings.preservedMeta();
        viewportSelect = appendSelectField(fields, "VIEWPORT", "tooltip.apricityui.meta.viewport", VIEWPORT_CHOICES, settings.viewport());
        fontModeSelect = appendSelectField(fields, "FONT MODE", "tooltip.apricityui.meta.font_mode", FONT_MODE_CHOICES, settings.fontMode());
        mouseEventsSelect = appendSelectField(fields, "MOUSE EVENTS", "tooltip.apricityui.meta.mouse_events", MOUSE_EVENT_CHOICES, settings.mouseEvents());
        if (zoomSave != null) zoomInput = appendZoomField(fields, currentZoom);
        root.append(fields);

        Element submitRow = element("DIV", "dialog-footer");
        saveButton = element("BUTTON", "dialog-btn dialog-btn-confirm");
        saveButton.append(text("SPAN", "SAVE", "dialog-btn-label"));
        saveButton.addEventListener("click", event -> save());
        submitRow.append(saveButton);
        dialog.window().append(submitRow);
        refreshSaveState();
        markDirty();
    }

    public void close() {
        DialogWindow openDialog = dialog;
        dialog = null;
        if (openDialog != null) openDialog.close();
        clearReferences();
    }

    private void save() {
        Double zoom = zoomValue();
        if (zoomInput != null && zoom == null) {
            ToastManager.show("ZOOM must be a number between 0.01 and 10");
            return;
        }
        String markup = HtmlMetaEditor.toMetaMarkup(new HtmlMetaEditor.MetaSettings(
                charset, valueOf(fontModeSelect), valueOf(viewportSelect),
                valueOf(mouseEventsSelect), preservedMeta));
        Consumer<String> templateCallback = templateSave;
        if (templateCallback != null) {
            close();
            templateCallback.accept(markup);
            return;
        }
        HtmlMetaEditor.EditResult result = HtmlMetaEditor.save(target, markup);
        if (!result.success()) {
            ToastManager.show(result.message());
            return;
        }
        Runnable callback = afterSave;
        Consumer<Double> zoomCallback = zoomSave;
        String name = target == null || target.getFileName() == null ? "HTML" : target.getFileName().toString();
        close();
        ToastManager.show("Updated META in " + name);
        if (zoomCallback != null && zoom != null) zoomCallback.accept(zoom);
        if (callback != null) callback.run();
    }

    private void refreshSaveState() {
        if (saveButton == null) return;
        if (zoomInput != null && zoomValue() == null) saveButton.setAttribute("disabled", "disabled");
        else saveButton.removeAttribute("disabled");
        markDirty();
    }

    private Element appendZoomField(Element parent, double currentZoom) {
        Element field = element("DIV", "dialog-field");
        field.setAttribute("style", "margin:0 0 14px;");
        field.append(text("LABEL", "ZOOM", "dialog-label"));
        Element input = element("INPUT", "dialog-input");
        input.setAttribute("type", "number");
        input.setAttribute("inputmode", "decimal");
        input.setAttribute("min", Double.toString(MIN_ZOOM));
        input.setAttribute("max", Double.toString(MAX_ZOOM));
        input.setAttribute("step", "0.01");
        input.setValue(formatZoom(currentZoom));
        input.addEventListener("input", event -> refreshSaveState());
        input.addEventListener("change", event -> refreshSaveState());
        field.append(input);
        parent.append(field);
        return input;
    }

    private Double zoomValue() {
        if (zoomInput == null) return null;
        String raw = zoomInput.getValue();
        if (raw == null || raw.isBlank()) return null;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) && value >= MIN_ZOOM && value <= MAX_ZOOM ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String formatZoom(double value) {
        return Double.isFinite(value) && value > 0 ? Double.toString(value) : "1.0";
    }

    private Element appendSelectField(Element parent, String label, String tooltipKey,
                                      List<Choice> choices, String currentValue) {
        Element field = element("DIV", "dialog-field");
        field.setAttribute("style", "margin:0 0 14px;");
        field.append(text("LABEL", label, "dialog-label"));
        Element selectWrap = element("DIV", "dialog-select-wrap");
        Element select = element("SELECT", "dialog-select resource-meta-select");
        select.setAttribute("data-native-arrow", "false");
        select.setAttribute("data-tooltip-key", tooltipKey);
        Tooltip.bindTranslation(select, tooltipKey);
        String selectedValue = canonicalChoiceValue(choices, currentValue);
        List<Choice> available = new ArrayList<>(choices);
        boolean known = available.stream().anyMatch(choice -> choice.value().equals(selectedValue));
        if (!known && selectedValue != null && !selectedValue.isBlank()) {
            available.add(new Choice("CURRENT / " + abbreviate(selectedValue).toUpperCase(Locale.ROOT), selectedValue,
                    "tooltip.apricityui.meta.current"));
        }
        for (Choice choice : available) {
            Element option = text("OPTION", choice.label(), "resource-meta-option");
            option.setAttribute("value", choice.value());
            option.setAttribute("data-tooltip-key", choice.tooltipKey());
            select.append(option);
        }
        select.setValue(selectedValue == null ? "" : selectedValue);
        select.addEventListener("input", event -> refreshSaveState());
        select.addEventListener("change", event -> refreshSaveState());
        selectWrap.append(select);
        selectWrap.append(text("DIV", "\u25be", "dialog-select-arrow"));
        field.append(selectWrap);
        parent.append(field);
        return select;
    }

    private static String canonicalChoiceValue(List<Choice> choices, String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.equalsIgnoreCase("mode=screen")
                && choices.stream().anyMatch(choice -> "mode=window".equals(choice.value()))) {
            return "mode=window";
        }
        return safe;
    }

    private static String valueOf(Element select) {
        return select == null || select.getValue() == null ? "" : select.getValue();
    }

    private static String abbreviate(String value) {
        String safe = value == null ? "" : value;
        return safe.length() <= 54 ? safe : safe.substring(0, 51) + "...";
    }

    private void clearReferences() {
        dialog = null;
        fontModeSelect = null;
        viewportSelect = null;
        mouseEventsSelect = null;
        zoomInput = null;
        saveButton = null;
        document = null;
        target = null;
        afterSave = null;
        templateSave = null;
        zoomSave = null;
        charset = "";
        preservedMeta = List.of();
    }

    private Element element(String tag, String className) {
        Element element = Element.init(document.createElement(tag));
        element.setAttribute("class", className);
        return element;
    }

    private Element text(String tag, String value, String className) {
        Element element = element(tag, className);
        element.setTextContent(value);
        return element;
    }

    private void markDirty() {
        if (document != null && document.body != null) {
            document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        }
    }

    private record Choice(String label, String value, String tooltipKey) {
    }
}
