package com.sighs.apricityui.behavior.richtext;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;

/**
 * 浏览器标准 Selection 的 AUI 桥：绑定 Document，读写当前富文本选区
 * （{@link RichTextSelection}），向 JS 暴露 anchorNode/anchorOffset/
 * focusNode/focusOffset/rangeCount 与 setBaseAndExtent/removeAllRanges/
 * collapse/getRangeAt/extend（近似浏览器 Selection）。
 */
public class SelectionBridge {
    private final Document document;

    public SelectionBridge(Document document) {
        this.document = document;
    }

    private RichTextSelection selection() {
        return document == null ? null : document.getRichTextSelection();
    }

    public Node getAnchorNode() {
        RichTextSelection s = selection();
        if (s == null || !s.hasAnchor() || s.getAnchorUnit() == null) return null;
        return RichTextRange.fromUnitOffset(s.getAnchorUnit(), s.getAnchorOffset()).container();
    }

    public int getAnchorOffset() {
        RichTextSelection s = selection();
        if (s == null || !s.hasAnchor() || s.getAnchorUnit() == null) return 0;
        // 浏览器语义:anchorOffset 是相对于 anchorNode 的容器内偏移。
        // 页面 readSelection 用它做 r.setEnd(node, offset),传归一化偏移会算错位置。
        RichTextRange.RichTextEndpoint ep =
                RichTextRange.fromUnitOffset(s.getAnchorUnit(), s.getAnchorOffset());
        return ep == null ? 0 : ep.offset();
    }

    public Node getFocusNode() {
        RichTextSelection s = selection();
        if (s == null || !s.hasAnchor() || s.getEndUnit() == null) return null;
        return RichTextRange.fromUnitOffset(s.getEndUnit(), s.getEndOffset()).container();
    }

    public int getFocusOffset() {
        RichTextSelection s = selection();
        if (s == null || !s.hasAnchor() || s.getEndUnit() == null) return 0;
        RichTextRange.RichTextEndpoint ep =
                RichTextRange.fromUnitOffset(s.getEndUnit(), s.getEndOffset());
        return ep == null ? 0 : ep.offset();
    }

    public int getRangeCount() {
        RichTextSelection s = selection();
        // 浏览器语义:折叠选区(光标)也是一个 range,rangeCount=1。
        // 不能用 isActive()(它要求 anchor != end),否则页面 readSelection 拿不到光标位置。
        return s != null && s.hasAnchor() ? 1 : 0;
    }

    public RangeBridge getRangeAt(int index) {
        RichTextSelection s = selection();
        if (s == null || !s.hasAnchor() || index != 0 || s.getAnchorUnit() == null) return null;
        return RangeBridge.fromUnitOffsets(s.getAnchorUnit(),
                Math.min(s.getAnchorOffset(), s.getEndOffset()),
                Math.max(s.getAnchorOffset(), s.getEndOffset()));
    }

    public void setBaseAndExtent(Node anchorNode, int anchorOffset, Node focusNode, int focusOffset) {
        RichTextSelection s = selection();
        if (s == null) return;
        RangeBridge.RangeAnchor anchor = RangeBridge.resolveAnchor(anchorNode, anchorOffset);
        RangeBridge.RangeAnchor focus = RangeBridge.resolveAnchor(focusNode, focusOffset);
        if (anchor == null || focus == null) return;
        if (anchor.unit() == focus.unit()) {
            s.setRange(anchor.unit(), anchor.offset(), focus.unit(), focus.offset());
        } else {
            // 跨块：锚点块定位，焦点换算到锚点单元（近似，编辑器主要单块操作）
            s.setRange(anchor.unit(), anchor.offset(), anchor.unit(),
                    RangeBridge.toUnitOffset(anchor.unit(), focusNode, focusOffset));
        }
    }

    public void removeAllRanges() {
        RichTextSelection s = selection();
        if (s != null) s.clear();
    }

    public void collapse(Node node, int offset) {
        RichTextSelection s = selection();
        if (s == null) return;
        RangeBridge.RangeAnchor anchor = RangeBridge.resolveAnchor(node, offset);
        if (anchor == null) return;
        s.setCollapsed(anchor.unit(), anchor.offset());
    }

    public void extend(Node node, int offset) {
        RichTextSelection s = selection();
        if (s == null) return;
        RangeBridge.RangeAnchor focus = RangeBridge.resolveAnchor(node, offset);
        if (focus == null) return;
        s.extendTo(focus.unit(), focus.offset());
    }
}
