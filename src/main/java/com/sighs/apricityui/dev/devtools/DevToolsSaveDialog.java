package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.ui.dialog.DialogWindow;

import java.util.function.Consumer;

/** Confirmation dialog for overwriting the inspected document's source HTML. */
final class DevToolsSaveDialog {
    private DialogWindow dialog;

    void open(Document document, String path, Consumer<Boolean> onConfirm) {
        close();
        if (document == null || document.body == null) return;
        dialog = DialogWindow.open(document, new DialogWindow.Options(
                "SAVE HTML", 440, 0, false,
                "dialog-overlay show", "dialog",
                "dialog-header", "dialog-title", "dialog-close", "dialog-body", "save-dialog-title-icon"
        ), () -> dialog = null);

        Element content = dialog.content();
        Element message = DevToolsDom.text(document, "DIV", "save-dialog-message",
                "Overwrite the source file with the current document?");
        Element file = DevToolsDom.text(document, "DIV", "save-dialog-path", path);
        content.append(message);
        content.append(file);

        Element reminder = DevToolsDom.element(document, "LABEL", "save-dialog-reminder");
        Element checkbox = DevToolsDom.element(document, "INPUT", "save-dialog-checkbox");
        checkbox.setAttribute("type", "checkbox");
        Element checkmark = DevToolsDom.element(document, "SPAN", "save-dialog-checkmark");
        reminder.append(checkbox);
        reminder.append(checkmark);
        reminder.append(DevToolsDom.text(document, "SPAN", "save-dialog-reminder-text", "本次启动不再提醒"));
        reminder.addEventListener("click", event -> {
            if (event.target == checkbox) return;
            checkbox.setChecked(!checkbox.isChecked());
            event.preventDefault();
            DevToolsDom.markDirty(document);
        });
        content.append(reminder);

        Element footer = DevToolsDom.element(document, "DIV", "dialog-footer");
        Element cancel = button(document, "CANCEL", "dialog-btn dialog-btn-cancel");
        Element save = button(document, "SAVE", "dialog-btn dialog-btn-confirm");
        cancel.addEventListener("click", event -> close());
        save.addEventListener("click", event -> {
            boolean skipNextTime = checkbox.isChecked();
            close();
            if (onConfirm != null) onConfirm.accept(skipNextTime);
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
}
