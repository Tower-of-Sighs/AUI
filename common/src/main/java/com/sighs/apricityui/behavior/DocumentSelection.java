package com.sighs.apricityui.behavior;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Drawer;

import java.util.List;

/**
 * 文档级文字选择：锚点与终点均为（单元, 偏移），可跨单元、跨兄弟节点。
 * <p>
 * 非可编辑文本的选区是文档内唯一的一个；输入控件（AbstractText）的选区由控件
 * 自身维护，不在此列。
 */
public final class DocumentSelection {
    /**
     * 拖拽粒度（浏览器行为）：普通拖拽按字符扩展；双击选词后继续拖拽按词边界吸附；
     * 三击选段后继续拖拽按段落（硬换行之间的片段）吸附。
     */
    public enum Granularity { CHARACTER, WORD, PARAGRAPH }

    private final Document owner;
    private Element anchorUnit = null;
    private int anchorOffset = 0;
    private Element endUnit = null;
    private int endOffset = 0;
    private boolean selecting = false;
    // 词/段落粒度拖拽的锚区间：拖拽翻转方向时锚点吸附到该区间的另一侧边界，
    // 保证初始词/段落在反向拖拽中仍保持完整选中。
    private Granularity granularity = Granularity.CHARACTER;
    private Element granularityUnit = null;
    private int granularityStart = 0;
    private int granularityEnd = 0;
    // 选区内单击的待折叠位置：mousedown 落在选区内时不立即折叠（可能是拖拽起点），
    // mouseup 且未形成拖拽时由 TextSelection 消费并折叠到此处。
    private Element pendingCollapseUnit = null;
    private int pendingCollapseOffset = 0;

    public DocumentSelection(Document owner) {
        this.owner = owner;
    }

    /** 折叠到（unit, offset）：锚点与终点同时落在此处。 */
    public void collapse(Element unit, int offset) {
        if (owner != null) owner.bumpSelectionCache();
        if (owner != null) owner.clearRichTextSelection();
        anchorUnit = unit;
        anchorOffset = offset;
        endUnit = unit;
        endOffset = offset;
        resetGranularity();
        clearPendingCollapse();
        markDirty();
    }

    /** 从现有锚点扩展终点到（unit, offset）；无锚点时退化为折叠。 */
    public void extendTo(Element unit, int offset) {
        if (anchorUnit == null) {
            collapse(unit, offset);
            return;
        }
        if (owner != null) owner.bumpSelectionCache();
        if (owner != null) owner.clearRichTextSelection();
        endUnit = unit;
        endOffset = offset;
        clearPendingCollapse();
        markDirty();
    }

    /** 仅移动锚点（词/段落粒度拖拽翻转方向时吸附锚区间另一侧边界），终点不变。 */
    public void moveAnchorTo(Element unit, int offset) {
        if (anchorUnit == unit && anchorOffset == offset) return;
        if (owner != null) owner.bumpSelectionCache();
        if (owner != null) owner.clearRichTextSelection();
        anchorUnit = unit;
        anchorOffset = offset;
        markDirty();
    }

    /** 清空选择。 */
    public void clear() {
        if (anchorUnit == null && endUnit == null && !selecting) return;
        if (owner != null) owner.bumpSelectionCache();
        anchorUnit = null;
        anchorOffset = 0;
        endUnit = null;
        endOffset = 0;
        selecting = false;
        resetGranularity();
        clearPendingCollapse();
        markDirty();
    }

    /** 是否存在非空选区。 */
    public boolean isActive() {
        if (anchorUnit == null || endUnit == null) return false;
        if (anchorUnit == endUnit) return anchorOffset != endOffset;
        return true;
    }

    public boolean hasAnchor() {
        return anchorUnit != null;
    }

    public boolean isSelecting() {
        return selecting;
    }

    public void setSelecting(boolean selecting) {
        this.selecting = selecting;
    }

    // ------------------------------------------------------------------
    // 拖拽粒度（双击/三击后的词/段落吸附）
    // ------------------------------------------------------------------

    /** 设置拖拽粒度与粒度锚区间；CHARACTER 时锚区间清空。 */
    public void setGranularity(Granularity newGranularity, Element unit, int start, int end) {
        granularity = newGranularity == null ? Granularity.CHARACTER : newGranularity;
        if (granularity == Granularity.CHARACTER) {
            granularityUnit = null;
            granularityStart = 0;
            granularityEnd = 0;
        } else {
            granularityUnit = unit;
            granularityStart = start;
            granularityEnd = end;
        }
    }

    public Granularity getGranularity() {
        return granularity;
    }

    /** 粒度锚区间所在单元（初始词/段落）；CHARACTER 粒度或无锚区间时为 null。 */
    public Element getGranularityUnit() {
        return granularityUnit;
    }

    public int getGranularityStart() {
        return granularityStart;
    }

    public int getGranularityEnd() {
        return granularityEnd;
    }

    private void resetGranularity() {
        setGranularity(Granularity.CHARACTER, null, 0, 0);
    }

    // ------------------------------------------------------------------
    // 选区内单击的待折叠位置（mousedown 登记，mouseup 未拖拽时消费）
    // ------------------------------------------------------------------

    public void markPendingCollapse(Element unit, int offset) {
        pendingCollapseUnit = unit;
        pendingCollapseOffset = offset;
    }

    /** 取出并清除待折叠位置；无待折叠时返回 null。 */
    public SelectionUnits.UnitOffset consumePendingCollapse() {
        if (pendingCollapseUnit == null) return null;
        SelectionUnits.UnitOffset pending = new SelectionUnits.UnitOffset(pendingCollapseUnit, pendingCollapseOffset);
        clearPendingCollapse();
        return pending;
    }

    private void clearPendingCollapse() {
        pendingCollapseUnit = null;
        pendingCollapseOffset = 0;
    }

    public Element getAnchorUnit() {
        return anchorUnit;
    }

    public Element getEndUnit() {
        return endUnit;
    }

    public int getAnchorOffset() {
        return anchorOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    /** 选中文档内全部单元文本。没有可选文本时返回 false。 */
    public boolean selectAll(Document document) {
        if (owner != null) owner.bumpSelectionCache();
        if (owner != null) owner.clearRichTextSelection();
        List<Element> units = SelectionUnits.enumerateUnits(document);
        if (units.isEmpty()) return false;
        Element first = units.get(0);
        Element last = units.get(units.size() - 1);
        anchorUnit = first;
        anchorOffset = 0;
        endUnit = last;
        endOffset = SelectionUnits.flattenedSelectableText(last).length();
        markDirty();
        return true;
    }

    /** 只选中给定单元（及其内联后代）的文本。 */
    public void selectUnit(Element unit) {
        if (unit == null) return;
        if (owner != null) owner.bumpSelectionCache();
        if (owner != null) owner.clearRichTextSelection();
        int length = SelectionUnits.flattenedSelectableText(unit).length();
        collapse(unit, 0);
        extendTo(unit, length);
    }

    /**
     * 拼接选区覆盖的单元文本：DOM 序拼接，不同单元之间插入换行。
     * 起始/结束单元按各自偏移截取。空选区或单元已脱离文档时返回空串。
     */
    public String getSelectedText(Document document) {
        if (anchorUnit == null || endUnit == null) return "";
        List<Element> units = SelectionUnits.enumerateUnits(document);
        int anchorIndex = units.indexOf(anchorUnit);
        int endIndex = units.indexOf(endUnit);
        if (anchorIndex < 0 || endIndex < 0) return "";
        int minIndex = Math.min(anchorIndex, endIndex);
        int maxIndex = Math.max(anchorIndex, endIndex);
        boolean reversed = anchorIndex > endIndex
                || (anchorIndex == endIndex && anchorOffset > endOffset);
        int startOffset = reversed ? endOffset : anchorOffset;
        int finalEndOffset = reversed ? anchorOffset : endOffset;

        if (minIndex == maxIndex) {
            String text = SelectionUnits.flattenedSelectableText(anchorUnit);
            int min = Math.max(0, Math.min(startOffset, finalEndOffset));
            int max = Math.min(text.length(), Math.max(startOffset, finalEndOffset));
            return min >= max ? "" : SelectionUnits.rawRangeForNormalizedRange(anchorUnit, min, max);
        }

        StringBuilder builder = new StringBuilder();
        String firstText = SelectionUnits.flattenedSelectableText(units.get(minIndex));
        if (startOffset < firstText.length()) {
            builder.append(SelectionUnits.rawRangeForNormalizedRange(units.get(minIndex), startOffset, firstText.length()));
        }
        for (int i = minIndex + 1; i <= maxIndex; i++) {
            String text = SelectionUnits.flattenedSelectableText(units.get(i));
            int take = i == maxIndex ? Math.min(finalEndOffset, text.length()) : text.length();
            if (take <= 0) continue;
            String piece = SelectionUnits.rawRangeForNormalizedRange(units.get(i), 0, take);
            if (piece.isEmpty()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(piece);
        }
        return builder.toString();
    }

    /**
     * 单元在文档选区内的本地区间 [start, end)（相对单元扁平文本）。
     * 单元不在选区范围内（或未命中任何单元）时返回 null。
     */
    public int[] localRangeForUnit(Element unit) {
        if (unit == null || anchorUnit == null || endUnit == null) return null;
        List<Element> units = SelectionUnits.enumerateUnits(owner);
        int anchorIndex = units.indexOf(anchorUnit);
        int endIndex = units.indexOf(endUnit);
        if (anchorIndex < 0 || endIndex < 0) return null;
        int unitIndex = units.indexOf(unit);
        if (unitIndex < 0) return null;
        int minIndex = Math.min(anchorIndex, endIndex);
        int maxIndex = Math.max(anchorIndex, endIndex);
        if (unitIndex < minIndex || unitIndex > maxIndex) return null;

        int unitLength = SelectionUnits.flattenedSelectableText(unit).length();
        int start;
        int end;
        if (minIndex == maxIndex) {
            start = Math.min(anchorOffset, endOffset);
            end = Math.max(anchorOffset, endOffset);
        } else if (unitIndex == minIndex) {
            int boundary = anchorIndex < endIndex ? anchorOffset : endOffset;
            start = Math.min(boundary, unitLength);
            end = unitLength;
        } else if (unitIndex == maxIndex) {
            int boundary = anchorIndex > endIndex ? anchorOffset : endOffset;
            start = 0;
            end = Math.max(0, Math.min(boundary, unitLength));
        } else {
            start = 0;
            end = unitLength;
        }
        if (start >= end) return null;
        return new int[]{start, end};
    }

    /** 单元在文档（DOM 序）单元列表中的索引，不在列表中返回 -1。 */
    public int unitIndex(Document document, Element unit) {
        if (document == null || unit == null) return -1;
        return SelectionUnits.enumerateUnits(document).indexOf(unit);
    }

    /** 单元 a 是否严格排在单元 b 之前（DOM 序）。 */
    public boolean unitOrderedBefore(Document document, Element first, Element second) {
        if (first == null || second == null || first == second) return false;
        int a = unitIndex(document, first);
        int b = unitIndex(document, second);
        return a >= 0 && b >= 0 && a < b;
    }

    private void markDirty() {
        if (owner == null) return;
        owner.markDirty(Drawer.REPAINT);
    }
}
