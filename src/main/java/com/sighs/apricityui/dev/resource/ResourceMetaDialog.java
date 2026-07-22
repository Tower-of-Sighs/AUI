package com.sighs.apricityui.dev.resource;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.ui.dialog.DialogWindow;
import com.sighs.apricityui.ui.toast.ToastManager;
import com.sighs.apricityui.ui.tooltip.Tooltip;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resource-browser dialog for editing the meta elements in an HTML head. */
public final class ResourceMetaDialog {
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
            new Choice("SCREEN", "mode=screen", "tooltip.apricityui.meta.viewport.screen"),
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
    private Element saveButton;
    private Document document;
    private Path target;
    private Runnable afterSave;
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
        this.document = document;
        this.target = target;
        this.afterSave = afterSave;
        this.dialog = DialogWindow.open(document, new DialogWindow.Options(
                "EDIT META / " + ResourcePath.fileName(resourcePath).toUpperCase(java.util.Locale.ROOT),
                720, 520, true,
                "dialog-overlay show", "dialog",
                "dialog-header", "dialog-title", "dialog-close", "dialog-body", "dialog-title-icon"
        ), this::clearReferences);

        Element root = dialog.content();
        root.setAttribute("style", "position:relative;flex:1;min-height:0;display:flex;flex-direction:column;");
        Element fields = element("DIV", "resource-meta-fields");
        fields.setAttribute("style", "margin-top:16px;flex:1;min-height:0;overflow:auto;");
        HtmlMetaEditor.MetaSettings settings = HtmlMetaEditor.parseSettings(loaded.metaMarkup());
        charset = settings.charset();
        preservedMeta = settings.preservedMeta();
        viewportSelect = appendSelectField(fields, "VIEWPORT", "tooltip.apricityui.meta.viewport", VIEWPORT_CHOICES, settings.viewport());
        fontModeSelect = appendSelectField(fields, "FONT MODE", "tooltip.apricityui.meta.font_mode", FONT_MODE_CHOICES, settings.fontMode());
        mouseEventsSelect = appendSelectField(fields, "MOUSE EVENTS", "tooltip.apricityui.meta.mouse_events", MOUSE_EVENT_CHOICES, settings.mouseEvents());
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
        String markup = HtmlMetaEditor.toMetaMarkup(new HtmlMetaEditor.MetaSettings(
                charset, valueOf(fontModeSelect), valueOf(viewportSelect),
                valueOf(mouseEventsSelect), preservedMeta));
        HtmlMetaEditor.EditResult result = HtmlMetaEditor.save(target, markup);
        if (!result.success()) {
            ToastManager.show(result.message());
            return;
        }
        Runnable callback = afterSave;
        String name = target == null || target.getFileName() == null ? "HTML" : target.getFileName().toString();
        close();
        ToastManager.show("Updated META in " + name);
        if (callback != null) callback.run();
    }

    private void refreshSaveState() {
        if (saveButton == null) return;
        saveButton.removeAttribute("disabled");
        markDirty();
    }

    private Element appendSelectField(Element parent, String label, String tooltipKey,
                                      List<Choice> choices, String currentValue) {
        Element field = element("DIV", "dialog-field");
        field.setAttribute("style", "margin:0 0 14px;");
        field.append(text("LABEL", label, "dialog-label"));
        Element selectWrap = element("DIV", "dialog-select-wrap");
        Element select = element("SELECT", "dialog-select resource-meta-select");
        select.setAttribute("data-tooltip-key", tooltipKey);
        Tooltip.bindTranslation(select, tooltipKey);
        List<Choice> available = new ArrayList<>(choices);
        boolean known = available.stream().anyMatch(choice -> choice.value().equals(currentValue));
        if (!known && currentValue != null && !currentValue.isBlank()) {
            available.add(new Choice("CURRENT / " + abbreviate(currentValue).toUpperCase(Locale.ROOT), currentValue,
                    "tooltip.apricityui.meta.current"));
        }
        for (Choice choice : available) {
            Element option = text("OPTION", choice.label(), "resource-meta-option");
            option.setAttribute("value", choice.value());
            option.setAttribute("data-tooltip-key", choice.tooltipKey());
            select.append(option);
        }
        select.setValue(currentValue == null ? "" : currentValue);
        select.addEventListener("input", event -> refreshSaveState());
        select.addEventListener("change", event -> refreshSaveState());
        selectWrap.append(select);
        selectWrap.append(text("DIV", "\u25be", "dialog-select-arrow"));
        field.append(selectWrap);
        parent.append(field);
        return select;
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
        saveButton = null;
        document = null;
        target = null;
        afterSave = null;
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
