package com.sighs.apricityui.behavior.richtext;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

import java.util.List;

/**
 * 浏览器 TreeWalker 的最小 AUI 桥：按 DOM 序遍历 root 子树，返回文本节点
 * （whatToShow 支持 SHOW_TEXT=4 与 SHOW_ALL=0xFFFFFFFF；其余位忽略）。
 * 供编辑器遍历 contenteditable 内的文本节点定位 DOM 偏移。
 */
public class TreeWalkerBridge {
    public static final int SHOW_ALL = 0xFFFFFFFF;
    public static final int SHOW_TEXT = 4;

    private final List<TextNode> nodes;
    private int index = -1;

    public TreeWalkerBridge(Element root, int whatToShow) {
        this.nodes = RangeBridge.collectTextNodes(root, whatToShow);
    }

    public Node nextNode() {
        index++;
        return index < nodes.size() ? nodes.get(index) : null;
    }

    public Node getCurrentNode() {
        return index >= 0 && index < nodes.size() ? nodes.get(index) : null;
    }

    public String getNodeValue() {
        Node current = getCurrentNode();
        return current == null ? null : current.getNodeValue();
    }
}
