package com.sighs.apricityui.instance.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor;
import com.sighs.apricityui.mixin.accessor.SlotAccessor;
import com.sighs.apricityui.render.item.ItemRenderContext;
import com.sighs.apricityui.render.item.ItemRenderState;
import com.sighs.apricityui.style.Position;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 将 DOM 中的 Slot 元素与菜单槽位绑定，并把菜单绘制快照直接同步给 Slot。
 */
public final class SlotDataBinder {
    private final ApricityContainerMenu menu;
    private final LinkedHashMap<Integer, SlotBinding> bindingsByGlobalIndex = new LinkedHashMap<>();
    private int lastBindSlotCount = -1;
    private long lastBindGeneration = -1L;

    public SlotDataBinder(ApricityContainerMenu menu) {
        this.menu = Objects.requireNonNull(menu);
    }

    /**
     * 从 Document 中扫描 Slot 元素并绑定到菜单槽位。
     */
    public void bindSlotsFromDocument(Document document) {
        clear();
        if (document == null) return;

        if (!menu.getLayout().isUiOnly()) {
            List<Element> elements = document.getElements();
            for (Element element : elements) {
                if (!(element instanceof Slot slotElement)) continue;

                Container container = slotElement.findAncestor(Container.class);
                if (container == null) continue;

                String containerId = container.getAttribute("id");
                if (containerId == null || containerId.isBlank()) {
                    containerId = resolveImplicitContainerId(document, container);
                }

                int localIndex = slotElement.getSlotIndex();
                if (localIndex < 0) continue;

                Integer globalIndex = menu.resolveGlobalSlotIndex(containerId, localIndex);
                if (globalIndex == null || globalIndex < 0 || globalIndex >= menu.slots.size()) continue;

                net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(globalIndex);
                ItemStack initialStack = menuSlot.getItem();
                slotElement.bindToMenuSlot(new ItemRenderState(
                        initialStack,
                        null,
                        false,
                        false,
                        ItemRenderContext.resolveCooldownProgress(initialStack)
                ));
                bindingsByGlobalIndex.put(globalIndex, new SlotBinding(slotElement, globalIndex, localIndex));
            }
        }

        lastBindSlotCount = countSlotElements(document);
        lastBindGeneration = document.getRefreshGeneration();
    }

    /**
     * 同步所有绑定槽位的屏幕坐标。
     */
    public void syncAllSlotPositions(int leftPos, int topPos, boolean force) {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            if (binding.globalIndex < 0 || binding.globalIndex >= menu.slots.size()) continue;
            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(binding.globalIndex);

            Position pos = Position.of(binding.slotElement);
            int elementX = (int) Math.round(pos.x) - leftPos;
            int elementY = (int) Math.round(pos.y) - topPos;

            if (force || menuSlot.x != elementX || menuSlot.y != elementY) {
                ((SlotAccessor) menuSlot).setX(elementX);
                ((SlotAccessor) menuSlot).setY(elementY);
            }
        }
    }

    /**
     * 将原版容器路径中的临时物品状态计算为 AUI 节点可消费的快照。
     */
    public void updateBoundItemRenderStates(AbstractContainerScreenAccessor accessor, ItemStack carried) {
        if (accessor == null) return;
        ItemStack safeCarried = carried == null ? ItemStack.EMPTY : carried;
        net.minecraft.world.inventory.Slot clickedSlot = accessor.apricityui$getClickedSlot();
        ItemStack draggingItem = accessor.apricityui$getDraggingItem();
        boolean splitting = accessor.apricityui$isSplittingStack();
        Set<net.minecraft.world.inventory.Slot> quickCraftSlots = accessor.apricityui$getQuickCraftSlots();
        boolean quickCrafting = accessor.apricityui$isQuickCrafting();
        int quickCraftingType = accessor.apricityui$getQuickCraftingType();

        int quickCraftBasePlaceCount = 0;
        if (quickCrafting && !safeCarried.isEmpty() && quickCraftSlots != null && quickCraftSlots.size() > 1) {
            quickCraftBasePlaceCount = net.minecraft.world.inventory.AbstractContainerMenu.getQuickCraftPlaceCount(
                    quickCraftSlots,
                    quickCraftingType,
                    safeCarried
            );
        }

        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            if (binding.globalIndex < 0 || binding.globalIndex >= menu.slots.size()) continue;
            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(binding.globalIndex);
            ItemStack renderStack = menuSlot.getItem();
            String overlayText = null;
            boolean ghost = false;

            if (menuSlot == clickedSlot && draggingItem != null && !draggingItem.isEmpty() && splitting && !renderStack.isEmpty()) {
                renderStack = renderStack.copyWithCount(renderStack.getCount() / 2);
            } else if (quickCrafting && quickCraftSlots != null && quickCraftSlots.contains(menuSlot) && !safeCarried.isEmpty()) {
                if (quickCraftSlots.size() <= 1) {
                    renderStack = ItemStack.EMPTY;
                } else if (net.minecraft.world.inventory.AbstractContainerMenu.canItemQuickReplace(menuSlot, safeCarried, true)
                        && menu.canDragTo(menuSlot)) {
                    ghost = true;
                    int maxStackSize = Math.min(safeCarried.getMaxStackSize(), menuSlot.getMaxStackSize(safeCarried));
                    int existingCount = menuSlot.getItem().isEmpty() ? 0 : menuSlot.getItem().getCount();
                    int placeCount = quickCraftBasePlaceCount + existingCount;
                    if (placeCount > maxStackSize) {
                        placeCount = maxStackSize;
                        overlayText = net.minecraft.ChatFormatting.YELLOW + String.valueOf(maxStackSize);
                    }
                    renderStack = safeCarried.copyWithCount(placeCount);
                }
            }

            binding.slotElement.updateBoundItemRenderState(new ItemRenderState(
                    renderStack,
                    overlayText,
                    ghost,
                    false,
                    ItemRenderContext.resolveCooldownProgress(renderStack)
            ));
        }
    }

    /**
     * 判断是否需要重新绑定（Document 被 refresh 重建，或 slot 数量发生变化）。
     */
    public boolean shouldRebindSlotsFromDom(Document document) {
        if (document == null) return false;
        if (document.getRefreshGeneration() != lastBindGeneration) return true;
        return countSlotElements(document) != lastBindSlotCount;
    }

    /**
     * 查找当前鼠标位置对应的、允许菜单操作的槽位索引。
     */
    public int findOperableSlotIndexAt(double mouseX, double mouseY) {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            if (binding.slotElement.canOperateBoundMenuSlot() && binding.slotElement.containsSlotPoint(mouseX, mouseY)) {
                return binding.globalIndex;
            }
        }
        return -1;
    }

    /**
     * 判断菜单槽位是否可执行玩家物品操作。
     */
    public boolean canOperateSlot(net.minecraft.world.inventory.Slot slot) {
        if (slot == null) return false;
        int index = menu.slots.indexOf(slot);
        if (index < 0) return false;

        SlotBinding binding = bindingsByGlobalIndex.get(index);
        if (binding == null) return true; // 未绑定 DOM 的菜单槽位保留原版交互
        return binding.slotElement.canOperateBoundMenuSlot();
    }

    /**
     * 当前菜单槽位是否绑定到一个 DOM slot。
     */
    public boolean isSlotBound(net.minecraft.world.inventory.Slot slot) {
        if (slot == null) return false;
        int index = menu.slots.indexOf(slot);
        return index >= 0 && bindingsByGlobalIndex.containsKey(index);
    }

    /**
     * 按 DOM 的实际尺寸命中已绑定菜单槽位，供 AbstractContainerScreenMixin 的交互路径使用。
     */
    public boolean isBoundElementHovered(net.minecraft.world.inventory.Slot slot, double mouseX, double mouseY) {
        if (slot == null) return false;
        int index = menu.slots.indexOf(slot);
        if (index < 0) return false;

        SlotBinding binding = bindingsByGlobalIndex.get(index);
        return binding != null
                && binding.slotElement.canOperateBoundMenuSlot()
                && binding.slotElement.containsSlotPoint(mouseX, mouseY);
    }

    public net.minecraft.world.inventory.Slot getBoundMenuSlot(Slot slotElement) {
        if (slotElement == null) return null;
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            if (binding.slotElement != slotElement) continue;
            if (binding.globalIndex < 0 || binding.globalIndex >= menu.slots.size()) return null;
            return menu.slots.get(binding.globalIndex);
        }
        return null;
    }

    /**
     * 清理所有绑定。
     */
    public void clear() {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            binding.slotElement.clearMenuSlotBinding();
        }
        bindingsByGlobalIndex.clear();
    }

    private static int countSlotElements(Document document) {
        if (document == null) return 0;
        int count = 0;
        for (Element element : document.getElements()) {
            if (element instanceof Slot) count++;
        }
        return count;
    }

    private String resolveImplicitContainerId(Document document, Container container) {
        List<Element> elements = document.getElements();
        int index = 0;
        for (Element element : elements) {
            if (!(element instanceof Container c)) continue;
            if (c == container) return "c" + index;
            index++;
        }
        return "c0";
    }

    private record SlotBinding(Slot slotElement, int globalIndex, int localIndex) {
    }
}
