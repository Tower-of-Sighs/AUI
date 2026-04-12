package com.sighs.apricityui.dev;

import com.sighs.apricityui.init.Document;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.glfw.GLFW;

public final class ExamplePage {
    private static final String BASE_PATH = "tests/";

    private static final Int2ObjectMap<String> KEY_TO_PATH = new Int2ObjectOpenHashMap<>();
    static {
        KEY_TO_PATH.put(GLFW.GLFW_KEY_A, "layout-test.html");
        KEY_TO_PATH.put(GLFW.GLFW_KEY_B, "text-style-test.html");
        KEY_TO_PATH.put(GLFW.GLFW_KEY_C, "text-wrap-test.html");
        KEY_TO_PATH.put(GLFW.GLFW_KEY_D, "container-slot-recipe-test.html");
    }

    private static String currentPath = null;
    private static Document currentDocument = null;

    private ExamplePage() {
    }

    public static boolean isOpen() {
        return currentDocument != null && currentPath != null && !Document.get(currentPath).isEmpty();
    }

    public static String getCurrentPath() {
        return currentPath;
    }

    public static void toggle(int key) {
        String path = KEY_TO_PATH.get(key);
        if (path == null) {
            return;
        }

        String fullPath = BASE_PATH + path;

        if (currentPath != null && currentPath.equals(fullPath) && currentDocument != null && !Document.get(fullPath).isEmpty()) {
            close();
            return;
        }

        if (currentDocument != null) {
            close();
        }

        currentDocument = Document.create(fullPath);
        currentPath = fullPath;
    }

    private static void close() {
        if (currentPath != null) {
            Document.remove(currentPath);
        }
        currentDocument = null;
        currentPath = null;
    }
}