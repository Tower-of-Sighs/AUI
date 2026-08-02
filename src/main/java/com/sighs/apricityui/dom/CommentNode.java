package com.sighs.apricityui.dom;

import java.util.Objects;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;

public class CommentNode extends Node {
    private String data;

    public CommentNode(Document document, String data) {
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
        return COMMENT_NODE;
    }

    @Override
    public String getNodeName() {
        return "#comment";
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
        if (document != null && !Objects.equals(oldValue, normalized)) {
            document.queueMutation(Document.MutationRecord.characterData(this, oldValue));
        }
    }

    @Override
    public String toString() {
        return "<!--" + data + "-->";
    }
}
