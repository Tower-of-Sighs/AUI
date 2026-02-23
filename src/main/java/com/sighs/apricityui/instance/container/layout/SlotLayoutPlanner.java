package com.sighs.apricityui.instance.container.layout;

import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

import java.util.*;

public final class SlotLayoutPlanner {
    private static boolean isManualLayout(SlotLayoutEntry entry) {
        if (entry == null || entry.slotElement() == null) return false;
        Style style = entry.slotElement().getComputedStyle();
        if (style == null) return false;

        // 只要不是 static，就认为它由 CSS/布局系统决定位置，属于“手动布局”。
        if (!"static".equals(style.position)) return true;

        // 兼容“static 但声明了 top/left/right/bottom”这种写法（虽然严格 CSS 里无效，但这里允许）。
        return !"unset".equals(style.top)
                || !"unset".equals(style.bottom)
                || !"unset".equals(style.left)
                || !"unset".equals(style.right);
    }

    private static void applyManual(List<SlotLayoutEntry> entries, SlotLayoutContext context, SlotLayoutResult result) {
        if (entries == null || entries.isEmpty()) return;

        for (SlotLayoutEntry entry : entries) {
            if (entry.disabled()) {
                result.put(entry.globalSlotIndex(), SlotLayoutPlacement.hidden(SlotLayoutMode.MANUAL));
                continue;
            }

            Rect rect = Rect.of(entry.slotElement());
            Position bodyPosition = rect.getBodyRectPosition();
            int screenX = (int) Math.round(bodyPosition.x);
            int screenY = (int) Math.round(bodyPosition.y);
            int menuX = screenX - context.guiLeft();
            int menuY = screenY - context.guiTop();

            int slotSize = resolveManualSlotSize(entry, context);
            result.put(entry.globalSlotIndex(), new SlotLayoutPlacement(
                    screenX,
                    screenY,
                    menuX,
                    menuY,
                    slotSize,
                    false,
                    SlotLayoutMode.MANUAL
            ));
        }
    }

    private static int resolveManualSlotSize(SlotLayoutEntry entry, SlotLayoutContext context) {
        // 1) 优先使用 style 的 width/height。
        Style style = entry.slotElement().getComputedStyle();
        if (style != null) {
            int byStyle = Math.max(Size.parse(style.width), Size.parse(style.height));
            if (byStyle > 0) return byStyle;
        }

        // 2) 再退化到元素实际 box 的 body size。
        Rect rect = Rect.of(entry.slotElement());
        Size bodySize = rect.getBodyRectSize();
        int byRect = Math.max((int) Math.round(bodySize.width()), (int) Math.round(bodySize.height()));
        if (byRect > 0) return byRect;

        // 3) 最后使用容器的统一 slotPixelSize。
        return context.slotPixelSize();
    }

    private static void applyGrid(List<SlotLayoutEntry> entries, SlotLayoutContext context, SlotLayoutResult result) {
        if (entries == null || entries.isEmpty()) return;

        // 计算“网格需要分配多少个可视格子”。
        // - compactDisabledSlot = true：disabled 不占格子
        // - compactDisabledSlot = false：disabled 仍占格子（只是隐藏）
        int cellCount = 0;
        for (SlotLayoutEntry entry : entries) {
            if (!entry.disabled() || !context.compactDisabledSlot()) cellCount++;
        }

        // 如果你想严格保持旧行为，把 cellCount 改成 entries.size()
        // int cellCount = entries.size();

        SlotLayoutMath.GridShape gridShape;
        if (context.slotLayoutSpec() == null) {
            gridShape = SlotLayoutMath.resolveAutoGrid(
                    context.contentWidth(),
                    context.contentHeight(),
                    cellCount,
                    context.slotPixelSize(),
                    context.gap()
            );
        } else {
            gridShape = SlotLayoutMath.resolveConfiguredGrid(
                    context.contentWidth(),
                    cellCount,
                    context.slotLayoutSpec(),
                    context.slotPixelSize(),
                    context.gap()
            );
        }

        int columns = Math.max(1, gridShape.columns());
        int slotSize = Math.max(1, context.slotPixelSize());
        int gap = Math.max(0, context.gap());
        int stride = slotSize + gap;

        int visualIndex = 0;
        for (SlotLayoutEntry entry : entries) {
            if (entry.disabled()) {
                result.put(entry.globalSlotIndex(), SlotLayoutPlacement.hidden(SlotLayoutMode.GRID));
                if (!context.compactDisabledSlot()) visualIndex++;
                continue;
            }

            int col = visualIndex % columns;
            int row = visualIndex / columns;
            int screenX = (int) Math.round(context.contentX() + col * stride);
            int screenY = (int) Math.round(context.contentY() + row * stride);
            int menuX = screenX - context.guiLeft();
            int menuY = screenY - context.guiTop();

            result.put(entry.globalSlotIndex(), new SlotLayoutPlacement(
                    screenX,
                    screenY,
                    menuX,
                    menuY,
                    slotSize,
                    false,
                    SlotLayoutMode.GRID
            ));
            visualIndex++;
        }
    }

    public SlotLayoutResult plan(List<SlotLayoutEntry> entries, SlotLayoutContext context) {
        SlotLayoutResult result = new SlotLayoutResult();
        if (entries == null || entries.isEmpty()) return result;

        // 1) 稳定顺序：同一个容器内所有布局都基于 globalSlotIndex 排序。
        ArrayList<SlotLayoutEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(SlotLayoutEntry::globalSlotIndex));

        // 2) 先把“手动布局”的槽位拿出来（它们不占用网格格子），剩余的走网格布局。
        ArrayList<SlotLayoutEntry> manual = new ArrayList<>();
        ArrayList<SlotLayoutEntry> grid = new ArrayList<>();
        for (SlotLayoutEntry entry : sorted) {
            if (isManualLayout(entry)) manual.add(entry);
            else grid.add(entry);
        }

        // 3) 手动布局（基于 slot 元素自身的 computed layout 位置）。
        applyManual(manual, context, result);

        // 4) 网格布局（对剩余槽位按可视索引排列）。
        applyGrid(grid, context, result);

        // 5) 兜底：理论上上面已经覆盖全部 entry；如果未来新增分支漏了，这里保证结果完整。
        for (SlotLayoutEntry entry : sorted) {
            if (!result.contains(entry.globalSlotIndex())) {
                result.put(entry.globalSlotIndex(), SlotLayoutPlacement.hidden(SlotLayoutMode.GRID));
            }
        }

        return result;
    }

    public static final class SlotLayoutResult {
        private final LinkedHashMap<Integer, SlotLayoutPlacement> placements = new LinkedHashMap<>();

        public void put(int globalSlotIndex, SlotLayoutPlacement placement) {
            if (placement == null) return;
            placements.put(globalSlotIndex, placement);
        }

        public SlotLayoutPlacement get(int globalSlotIndex) {
            return placements.get(globalSlotIndex);
        }

        public boolean contains(int globalSlotIndex) {
            return placements.containsKey(globalSlotIndex);
        }

        public Map<Integer, SlotLayoutPlacement> asMap() {
            return placements;
        }
    }

    public record SlotLayoutContext(
            int guiLeft,
            int guiTop,
            double contentX,
            double contentY,
            double contentWidth,
            double contentHeight,
            int slotPixelSize,
            int gap,
            Container.SlotLayoutSpec slotLayoutSpec,
            boolean compactDisabledSlot
    ) {
    }

    public record SlotLayoutEntry(int globalSlotIndex, Slot slotElement, boolean disabled) {
    }
}
