package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.ui.DialogWindow;

import java.util.function.Consumer;
import com.sighs.apricityui.parser.CSS;

/** Confirmation dialog for saving the inspected document's CSS and optional DOM tree. */
final class DevToolsSaveDialog {
    private DialogWindow dialog;

    void open(Document document, String path, Consumer<SaveOptions> onConfirm) {
        close();
        if (document == null || document.body == null) return;
        dialog = DialogWindow.open(document, new DialogWindow.Options(
                DevToolsTranslations.translate("devtools.apricityui.save_html"), 440, 0, false,
                "dialog-overlay show", "dialog",
                "dialog-header", "dialog-title", "dialog-close", "dialog-body", "save-dialog-title-icon"
        ), () -> dialog = null);

        Element content = dialog.content();
        Element message = DevToolsDom.text(document, "DIV", "save-dialog-message",
                DevToolsTranslations.translate("devtools.apricityui.save_confirm"));
        Element file = DevToolsDom.text(document, "DIV", "save-dialog-path", path);
        content.append(message);
        content.append(file);

        Element scope = DevToolsDom.text(document, "DIV", "save-dialog-scope",
                DevToolsTranslations.translate("devtools.apricityui.save_scope_description"));
        content.append(scope);

        Element domOption = DevToolsDom.element(document, "LABEL", "save-dialog-option");
        Element domCheckbox = DevToolsDom.element(document, "INPUT",
                "save-dialog-checkbox save-dialog-dom-checkbox");
        domCheckbox.setAttribute("type", "checkbox");
        Element domCheckmark = DevToolsDom.element(document, "SPAN", "save-dialog-checkmark");
        Element domCopy = DevToolsDom.element(document, "SPAN", "save-dialog-option-copy");
        domCopy.append(DevToolsDom.text(document, "SPAN", "save-dialog-option-title",
                DevToolsTranslations.translate("devtools.apricityui.save_dom_tree")));
        domCopy.append(DevToolsDom.text(document, "SPAN", "save-dialog-option-description",
                DevToolsTranslations.translate("devtools.apricityui.save_dom_tree.description")));
        domOption.append(domCheckbox);
        domOption.append(domCheckmark);
        domOption.append(domCopy);
        domOption.addEventListener("click", event -> {
            if (event.target == domCheckbox) return;
            domCheckbox.setChecked(!domCheckbox.isChecked());
            event.preventDefault();
            DevToolsDom.markDirty(document);
        });
        content.append(domOption);

        Element reminder = DevToolsDom.element(document, "LABEL", "save-dialog-reminder");
        Element checkbox = DevToolsDom.element(document, "INPUT", "save-dialog-checkbox");
        checkbox.setAttribute("type", "checkbox");
        Element checkmark = DevToolsDom.element(document, "SPAN", "save-dialog-checkmark");
        reminder.append(checkbox);
        reminder.append(checkmark);
        reminder.append(DevToolsDom.text(document, "SPAN", "save-dialog-reminder-text",
                DevToolsTranslations.translate("devtools.apricityui.do_not_ask_again")));
        reminder.addEventListener("click", event -> {
            if (event.target == checkbox) return;
            checkbox.setChecked(!checkbox.isChecked());
            event.preventDefault();
            DevToolsDom.markDirty(document);
        });
        content.append(reminder);

        Element footer = DevToolsDom.element(document, "DIV", "dialog-footer");
        Element cancel = button(document, DevToolsTranslations.translate("devtools.apricityui.cancel"), "dialog-btn dialog-btn-cancel");
        Element save = button(document, DevToolsTranslations.translate("devtools.apricityui.save"), "dialog-btn dialog-btn-confirm");
        cancel.addEventListener("click", event -> close());
        save.addEventListener("click", event -> {
            boolean skipNextTime = checkbox.isChecked();
            boolean saveDomTree = domCheckbox.isChecked();
            close();
            if (onConfirm != null) onConfirm.accept(new SaveOptions(skipNextTime, saveDomTree));
        });
        footer.append(cancel);
        footer.append(save);
        dialog.window().append(footer);
        DevToolsDom.markDirty(document);
    }

    void close() {
        DialogWindow current = dialog;
        dialog = null;
        if (current != null && current.isOpen()) current.close();
    }

    private static Element button(Document document, String label, String className) {
        Element button = DevToolsDom.element(document, "BUTTON", className);
        button.append(DevToolsDom.text(document, "SPAN", "dialog-btn-label", label));
        return button;
    }

    record SaveOptions(boolean skipConfirmation, boolean saveDomTree) {
    }
}
