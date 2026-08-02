package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.init.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.sighs.apricityui.parser.HTML;

/** Public, DevTools-independent facade for the Ore visual editor. */
public final class OreEditor {
    private OreEditor() {
    }

    public static boolean isOpen() { return OreEditorController.INSTANCE.isOpen(); }
    public static Document getDocument() { return OreEditorController.INSTANCE.getDocument(); }
    public static OreEditorSession getSession() { return OreEditorController.INSTANCE.getSession(); }
    public static boolean loadSavedProject() { return OreEditorController.INSTANCE.loadSavedProject(); }
    public static boolean open() { return OreEditorController.INSTANCE.open(); }
    /** Opens a local HTML file as a new editable Ore project. */
    public static boolean openHtml(Path path) {
        if (path == null || !Files.isRegularFile(path)) return false;
        try {
            return OreEditorController.INSTANCE.openHtml(path, Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }
    public static void close() { OreEditorController.INSTANCE.close(); }
    public static void toggle() { OreEditorController.INSTANCE.toggle(); }
}
