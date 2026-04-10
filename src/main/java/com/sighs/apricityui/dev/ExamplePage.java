package com.sighs.apricityui.dev;

import com.sighs.apricityui.init.Document;

public final class ExamplePage {
    private static final String PATH = "devtools/example.html";

    private static Document document = null;

    private ExamplePage() {
    }

    public static boolean isOpen() {
        return document != null && !Document.get(PATH).isEmpty();
    }

    public static void toggle() {
        if (Document.get(PATH).isEmpty()) {
            document = Document.create(PATH);
            return;
        }
        document = null;
        Document.remove(PATH);
    }
}

