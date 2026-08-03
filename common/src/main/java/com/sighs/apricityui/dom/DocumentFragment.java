package com.sighs.apricityui.dom;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;

public class DocumentFragment extends Node {
    public static final short DOCUMENT_FRAGMENT_NODE = 11;

    public DocumentFragment(Document document) {
        super(document);
    }

    @Override
    public short getNodeType() {
        return DOCUMENT_FRAGMENT_NODE;
    }

    @Override
    public String getNodeName() {
        return "#document-fragment";
    }

    @Override
    public String getTextContent() {
        StringBuilder builder = new StringBuilder();
        for (Node child : childNodes) {
            if (child == null) continue;
            String text = child.getTextContent();
            if (text != null) builder.append(text);
        }
        return builder.toString();
    }

    @Override
    public void setTextContent(String value) {
        for (Node child : new java.util.ArrayList<>(childNodes)) {
            removeChild(child);
        }
        if (value == null || value.isEmpty()) return;
        appendChild(new TextNode(document, value));
    }
}
