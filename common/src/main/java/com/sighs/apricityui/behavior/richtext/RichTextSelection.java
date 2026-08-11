package com.sighs.apricityui.behavior.richtext;

import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.behavior.TextSelection;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Drawer;

/**
 * 富文本编辑选区：锚点与终点均为（单元, 单元扁平文本归一化偏移），与
 * {@link com.sighs.apricityui.behavior.DocumentSelection} 同构，但用于可编辑富文本
 * 元素（{@code richtext}），支持键盘移动（←/→/↑/↓/Home/End）与 Shift 扩展。
 * <p>
 * Phase 1 约束：选区限定在单个富文本单元内（行内内容、无块级子单元）；激活时与
 * 文档级只读选择互斥。DOM 语义视图经 {@link #toRange()} 提供。
 */
public final class RichTextSelection {
    private final Document owner;
    private Element anchorUnit = null;
    private int anchorOffset = 0;
    private Element endUnit = null;
    private int endOffset = 0;
    private String direction = "none";
    private boolean selecting = false;

    public RichTextSelection(Document owner) {
        this.owner = owner;
    }

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    /** 是否存在非空选区（锚点与终点不同）。 */
    public boolean isActive() {
        if (anchorUnit == null || endUnit == null) return false;
        if (anchorUnit == endUnit) return anchorOffset != endOffset;
        return true;
    }

    public boolean collapsed() {
        return anchorUnit == endUnit && anchorOffset == endOffset;
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

    public Element getAnchorUnit() {
        return anchorUnit;
    }

    public int getAnchorOffset() {
        return anchorOffset;
    }

    public Element getEndUnit() {
        return endUnit;
    }

    public int getEndOffset() {
        return endOffset;
    }

    /** 选区方向：forward/backward/none（按单元内归一化偏移比较）。 */
    public String getDirection() {
        return direction;
    }

    /** 折叠到（unit, offset）。激活富文本选择，与文档级只读选择互斥。 */
    public void setCollapsed(Element unit, int offset) {
        if (unit == null) return;
        if (owner != null) owner.clearDocumentSelection();
        int clamped = clamp(offset, 0, flattenedLength(unit));
        anchorUnit = unit;
        anchorOffset = clamped;
        endUnit = unit;
        endOffset = clamped;
        direction = "none";
        markDirty();
    }

    /** 从锚点扩展到（unit, offset）；无锚点时退化为折叠。 */
    public void extendTo(Element unit, int offset) {
        if (unit == null) return;
        if (!hasAnchor()) {
            setCollapsed(unit, offset);
            return;
        }
        if (owner != null) owner.clearDocumentSelection();
        endUnit = unit;
        endOffset = clamp(offset, 0, flattenedLength(unit));
        updateDirection();
        markDirty();
    }

    /** 设置完整选区（anchor + end）。 */
    public void setRange(Element anchorUnit, int anchorOffset, Element endUnit, int endOffset) {
        if (anchorUnit == null || endUnit == null) return;
        if (owner != null) owner.clearDocumentSelection();
        this.anchorUnit = anchorUnit;
        this.anchorOffset = clamp(anchorOffset, 0, flattenedLength(anchorUnit));
        this.endUnit = endUnit;
        this.endOffset = clamp(endOffset, 0, flattenedLength(endUnit));
        updateDirection();
        markDirty();
    }

    public void clear() {
        if (anchorUnit == null && endUnit == null && !selecting) return;
        anchorUnit = null;
        anchorOffset = 0;
        endUnit = null;
        endOffset = 0;
        direction = "none";
        selecting = false;
        markDirty();
    }

    /** 全选单元文本。 */
    public void selectAll(Element unit) {
        if (unit == null) return;
        if (owner != null) owner.clearDocumentSelection();
        int length = flattenedLength(unit);
        anchorUnit = unit;
        anchorOffset = 0;
        endUnit = unit;
        endOffset = length;
        direction = "forward";
        markDirty();
    }

    // ------------------------------------------------------------------
    // 命中与文本
    // ------------------------------------------------------------------

    /** 由鼠标坐标定位选区：shiftExtend 时从既有锚点扩展，否则折叠。 */
    public void setFromPoint(Element hit, MouseEvent event, boolean shiftExtend) {
        if (hit == null || event == null) {
            clear();
            return;
        }
        SelectionUnits.UnitOffset target = TextSelection.resolveUnitOffset(hit, event.clientX, event.clientY);
        if (target == null) {
            clear();
            return;
        }
        if (shiftExtend && hasAnchor()) {
            extendTo(target.unit(), target.offset());
        } else {
            setCollapsed(target.unit(), target.offset());
        }
        setSelecting(true);
    }

    /** 选区覆盖的文本（跨节点拼接，BR 还原为 \n）。 */
    public String getSelectedText() {
        if (!isActive()) return "";
        if (anchorUnit == endUnit) {
            int min = Math.min(anchorOffset, endOffset);
            int max = Math.max(anchorOffset, endOffset);
            return SelectionUnits.rawRangeForNormalizedRange(anchorUnit, min, max);
        }
        // 跨单元（Phase 1 富文本单单元约束的防御分支）：按 DOM 序拼接
        boolean reversed = compareOffsets(anchorUnit, anchorOffset, endUnit, endOffset) > 0;
        Element firstUnit = reversed ? endUnit : anchorUnit;
        int firstOffset = reversed ? endOffset : anchorOffset;
        Element lastUnit = reversed ? anchorUnit : endUnit;
        int lastOffset = reversed ? anchorOffset : endOffset;
        StringBuilder builder = new StringBuilder();
        String firstText = flattenedLength(firstUnit) > 0 ? SelectionUnits.flattenedSelectableText(firstUnit) : "";
        if (firstOffset < firstText.length()) {
            builder.append(SelectionUnits.rawRangeForNormalizedRange(firstUnit, firstOffset, firstText.length()));
        }
        String lastText = flattenedLength(lastUnit) > 0 ? SelectionUnits.flattenedSelectableText(lastUnit) : "";
        if (!lastText.isEmpty()) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(SelectionUnits.rawRangeForNormalizedRange(lastUnit, 0, Math.min(lastOffset, lastText.length())));
        }
        return builder.toString();
    }

    /** 单元在选区内的归一化区间 [start, end)；单元不在选区范围时返回 null。 */
    public int[] localRangeForUnit(Element unit) {
        if (unit == null || !isActive()) return null;
        if (anchorUnit == endUnit) {
            if (unit != anchorUnit) return null;
            int min = Math.min(anchorOffset, endOffset);
            int max = Math.max(anchorOffset, endOffset);
            return min == max ? null : new int[]{min, max};
        }
        // 跨单元（防御分支）
        if (unit != anchorUnit && unit != endUnit) return null;
        int length = flattenedLength(unit);
        if (unit == anchorUnit) {
            int start = Math.min(anchorOffset, length);
            return start >= length ? null : new int[]{start, length};
        }
        int end = Math.min(endOffset, length);
        return end <= 0 ? null : new int[]{0, end};
    }

    /** 单元是否为选区锚点或终点单元。 */
    public boolean coversUnit(Element unit) {
        if (unit == null) return false;
        return unit == anchorUnit || unit == endUnit;
    }

    // ------------------------------------------------------------------
    // 移动（keepSelection = Shift 扩展）
    // ------------------------------------------------------------------

    public void moveLeft(boolean keepSelection) {
        if (!hasAnchor()) return;
        moveFocus(endUnit, endOffset - 1, keepSelection);
    }

    public void moveRight(boolean keepSelection) {
        if (!hasAnchor()) return;
        moveFocus(endUnit, endOffset + 1, keepSelection);
    }

    public void moveUp(boolean keepSelection) {
        if (!hasAnchor()) return;
        moveFocus(endUnit, RichTextNavigation.lineMoveOffset(endUnit, endOffset, -1), keepSelection);
    }

    public void moveDown(boolean keepSelection) {
        if (!hasAnchor()) return;
        moveFocus(endUnit, RichTextNavigation.lineMoveOffset(endUnit, endOffset, 1), keepSelection);
    }

    public void moveToHome(boolean keepSelection) {
        if (!hasAnchor()) return;
        moveFocus(endUnit, RichTextNavigation.lineStartOffset(endUnit, endOffset), keepSelection);
    }

    public void moveToEnd(boolean keepSelection) {
        if (!hasAnchor()) return;
        moveFocus(endUnit, RichTextNavigation.lineEndOffset(endUnit, endOffset), keepSelection);
    }

    private void moveFocus(Element unit, int newOffset, boolean keepSelection) {
        if (unit == null) return;
        int clamped = clamp(newOffset, 0, flattenedLength(unit));
        if (!keepSelection) {
            setCollapsed(unit, clamped);
            return;
        }
        if (!collapsed()) {
            // 已有选区：锚点不动，仅移动终点
            endUnit = unit;
            endOffset = clamped;
        } else {
            // 无选区：记录锚点 = 当前光标，移动终点
            anchorUnit = endUnit;
            anchorOffset = endOffset;
            endUnit = unit;
            endOffset = clamped;
        }
        updateDirection();
        markDirty();
    }

    // ------------------------------------------------------------------
    // DOM 视图与内部
    // ------------------------------------------------------------------

    /** 当前选区对应的 DOM Range（Phase 1 单单元；跨单元时返回 null）。 */
    public RichTextRange toRange() {
        if (anchorUnit == null || endUnit == null) return null;
        if (anchorUnit != endUnit) return null;
        int min = Math.min(anchorOffset, endOffset);
        int max = Math.max(anchorOffset, endOffset);
        return RichTextRange.fromUnitOffsets(anchorUnit, min, max);
    }

    private void updateDirection() {
        if (anchorUnit == null || endUnit == null) {
            direction = "none";
            return;
        }
        if (anchorUnit == endUnit && anchorOffset == endOffset) {
            direction = "none";
            return;
        }
        direction = compareOffsets(anchorUnit, anchorOffset, endUnit, endOffset) < 0 ? "forward" : "backward";
    }

    private int compareOffsets(Element unitA, int offsetA, Element unitB, int offsetB) {
        if (unitA == unitB) return Integer.compare(offsetA, offsetB);
        int a = SelectionUnits.enumerateUnits(owner).indexOf(unitA);
        int b = SelectionUnits.enumerateUnits(owner).indexOf(unitB);
        if (a < 0 || b < 0) return Integer.compare(a < 0 ? -1 : a, b < 0 ? -1 : b);
        return Integer.compare(a, b);
    }

    private static int flattenedLength(Element unit) {
        String flattened = SelectionUnits.flattenedSelectableText(unit);
        return flattened == null ? 0 : flattened.length();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        return Math.min(value, max);
    }

    private void markDirty() {
        if (owner == null) return;
        owner.markDirty(Drawer.REPAINT);
    }
}
