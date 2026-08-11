package com.sighs.apricityui.dom;

import java.util.Objects;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

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
        // 文本内容影响单元判定与扁平文本，选择缓存随之失效
        if (document != null) {
            document.bumpSelectionCache();
        }
        if (document != null && !Objects.equals(oldValue, normalized)) {
            document.queueMutation(Document.MutationRecord.characterData(this, oldValue));
        }
    }

    /**
     * 替换本文本节点 [offset, offset+count) 区间的内容为 replacement
     * （浏览器 Text.replaceData 语义）。复用 setTextContent 的缓存失效与选择缓存失效。
     */
    public void replaceData(int offset, int count, String replacement) {
        int len = data.length();
        int start = Math.max(0, Math.min(offset, len));
        int end = Math.max(start, Math.min(start + Math.max(0, count), len));
        String next = data.substring(0, start) + (replacement == null ? "" : replacement) + data.substring(end);
        setTextContent(next);
    }

    /**
     * 在 offset 处拆分本文本节点：原节点保留前半，返回承载后半的新 TextNode 并插入到
     * 原节点之后（浏览器 Text.splitText 语义）。
     */
    public TextNode splitText(int offset) {
        int len = data.length();
        int split = Math.max(0, Math.min(offset, len));
        String head = data.substring(0, split);
        String tail = data.substring(split);
        setTextContent(head);
        TextNode tailNode = new TextNode(document, tail);
        if (parentNode != null) {
            parentNode.insertBefore(tailNode, getNextSibling());
        }
        return tailNode;
    }

    @Override
    public String toString() {
        return data;
    }
}
