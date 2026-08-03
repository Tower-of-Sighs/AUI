package com.sighs.apricityui.dev;

import com.sighs.apricityui.dev.devtools.DevToolsController;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;

/** Public facade for the built-in inspector. */
public final class DevTools {
    private static final DevToolsController CONTROLLER = new DevToolsController();

    private DevTools() {
    }

    public static boolean isOpen() {
        return CONTROLLER.isOpen();
    }

    public static Document getToolDocument() {
        return CONTROLLER.getToolDocument();
    }

    public static boolean ensureOpen() {
        return CONTROLLER.ensureOpen();
    }

    public static boolean selectDocument(Document document) {
        return CONTROLLER.selectDocument(document);
    }

    public static boolean selectElement(Element element) {
        return CONTROLLER.selectElement(element);
    }

    public static boolean applyInlineStyle(Element element, String key, String value) {
        return CONTROLLER.applyInlineStyle(element, key, value);
    }

    public static boolean devTestApplyInlineStyleViaInspector(Element element, String key, String value) {
        if (!ensureOpen() || !selectElement(element)) return false;
        return CONTROLLER.applyInlineStyle(element, key, value);
    }

    public static void toggle() {
        CONTROLLER.toggle();
    }

    public static void refresh() {
        CONTROLLER.refresh();
    }

    /** Drains mirrored logger events on the client thread. */
    public static void drainLogs() {
        CONTROLLER.drainExternalLogs();
    }

    public static boolean handleInspectMouseMove(Position screenPosition) {
        return CONTROLLER.handleInspectMouseMove(screenPosition);
    }

    public static boolean handleInspectMouseDown(Position screenPosition, int button) {
        return CONTROLLER.handleInspectMouseDown(screenPosition, button);
    }

    public static boolean handleInspectMouseUp(int button) {
        return CONTROLLER.handleInspectMouseUp(button);
    }
}
