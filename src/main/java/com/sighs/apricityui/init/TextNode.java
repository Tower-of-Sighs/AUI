package com.sighs.apricityui.init;

import java.util.Objects;

public class TextNode extends Node {
    private String data;

    public TextNode(Document document, String data) {
        super(document);
        this.data = data == null ? "" : data;
    }

    public String getData() {
        return data;
    }

    public void setData(String value) {
        setTextContent(value);
    }

    @Override
    public short getNodeType() {
        return TEXT_NODE;
    }

    @Override
    public String getNodeName() {
        return "#text";
    }

    @Override
    public String getNodeValue() {
        return data;
    }

    @Override
    public String getTextContent() {
        return data;
    }

    @Override
    public void setTextContent(String value) {
        String normalized = value == null ? "" : value;
        String oldValue = data;
        data = normalized;
        if (parentNode instanceof Element parentElement) {
            parentElement.getRenderer().text.clear();
            parentElement.getRenderer().wrappedText.clear();
            parentElement.getRenderer().size.clear();
            if (document != null) {
                document.markDirty(parentElement, Drawer.RELAYOUT | Drawer.REPAINT);
            }
        }
        if (document != null && !Objects.equals(oldValue, normalized)) {
            document.queueMutation(Document.MutationRecord.characterData(this, oldValue));
        }
    }

    @Override
    public String toString() {
        return data;
    }
}
