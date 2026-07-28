package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

public final class OreEditorDom {
    private OreEditorDom() {
    }

    public static Element translation(Document document, String key, String className) {
        Element element = Element.init(document.createElement("TRANSLATION"));
        if (className != null && !className.isBlank()) element.setAttribute("class", className);
        element.setTextContent(key == null ? "" : key);
        return element;
    }
}
