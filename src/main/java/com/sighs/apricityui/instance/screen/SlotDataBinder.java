package com.sighs.apricityui.instance.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.mixin.accessor.SlotAccessor;
import com.sighs.apricityui.style.Position;

import java.util.*;

/**
 * 将 DOM 中的 Slot 元素与菜单槽位进行绑定和同步。
 */
public final class SlotDataBinder {
    private final ApricityContainerMenu menu;
    private final LinkedHashMap<Integer, SlotBinding> bindingsByGlobalIndex = new LinkedHashMap<>();
    private final ArrayList<Slot> displaySlots = new ArrayList<>();
    private int lastBindSlotCount = -1;

    public SlotDataBinder(ApricityContainerMenu menu) {
        this.menu = Objects.requireNonNull(menu);
    }

    /**
     * 从 Document 中扫描 Slot 元素并绑定到菜单槽位。
     */
    public void bindSlotsFromDocument(Document document) {
        clear();
        if (document == null || menu.getLayoutSpec().isUiOnly()) return;

        List<Element> elements = document.getElements();
        for (Element element : elements) {
            if (!(element instanceof Slot slotElement)) continue;

            Container container = slotElement.findAncestor(Container.class);
            if (container == null) {
                displaySlots.add(slotElement);
                continue;
            }

            String containerId = container.getAttribute("id");
            if (containerId == null || containerId.isBlank()) {
                containerId = resolveImplicitContainerId(document, container);
            }

            int localIndex = slotElement.getSlotIndex();
            if (localIndex < 0) {
                displaySlots.add(slotElement);
                continue;
            }

            Integer globalIndex = menu.resolveGlobalSlotIndex(containerId, localIndex);
            if (globalIndex == null || globalIndex < 0 || globalIndex >= menu.slots.size()) {
                displaySlots.add(slotElement);
                continue;
            }

            SlotBinding binding = new SlotBinding(slotElement, globalIndex, localIndex);
            bindingsByGlobalIndex.put(globalIndex, binding);

            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(globalIndex);
            slotElement.bindMcSlot(menuSlot);
        }

        lastBindSlotCount = countSlotElements(document);
    }

    /**
     * 同步所有绑定槽位的屏幕坐标。
     */
    public void syncAllSlotPositions(Document document, int leftPos, int topPos, boolean force) {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            if (binding.globalIndex < 0 || binding.globalIndex >= menu.slots.size()) continue;
            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(binding.globalIndex);

            Slot slotElement = binding.slotElement;
            Position pos = Position.getOffset(slotElement);
            int elementX = (int) Math.round(pos.x) - leftPos;
            int elementY = (int) Math.round(pos.y) - topPos;

            if (force || menuSlot.x != elementX || menuSlot.y != elementY) {
                ((SlotAccessor) menuSlot).setX(elementX);
                ((SlotAccessor) menuSlot).setY(elementY);
            }

            // 同步 UiSlot 状态
            if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                uiSlot.setUiDisabled(slotElement.isDisabled());
                uiSlot.setUiHidden(!slotElement.shouldRenderItem());
                uiSlot.setUiSlotSize(slotElement.resolveSlotSizeHint(16));
            }
        }
    }

    /**
     * 判断是否需要重新绑定（Document 中 slot 数量发生变化）。
     */
    public boolean shouldRebindSlotsFromDom(Document document) {
        if (document == null) return false;
        return countSlotElements(document) != lastBindSlotCount;
    }

    /**
     * 查找鼠标位置对应的菜单槽位索引。
     */
    public int findSlotIndexAt(double mouseX, double mouseY, int leftPos, int topPos) {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            Slot slotElement = binding.slotElement;
            if (!slotElement.shouldAcceptPointer()) continue;

            Position pos = Position.getOffset(slotElement);
            double ex = pos.x;
            double ey = pos.y;
            int size = slotElement.resolveSlotSizeHint(16);

            if (mouseX >= ex && mouseX < ex + size && mouseY >= ey && mouseY < ey + size) {
                return binding.globalIndex;
            }
        }
        return -1;
    }

    /**
     * 判断菜单槽位是否可交互。
     */
    public boolean isSlotPointerInteractable(net.minecraft.world.inventory.Slot slot) {
        if (slot == null) return false;
        int index = menu.slots.indexOf(slot);
        if (index < 0) return false;

        SlotBinding binding = bindingsByGlobalIndex.get(index);
        if (binding == null) return true; // 未绑定到 DOM 的槽位默认可交互
        return binding.slotElement.shouldAcceptPointer();
    }

    /**
     * 获取槽位的视觉属性。
     */
    public SlotVisual resolveSlotVisual(net.minecraft.world.inventory.Slot slot) {
        if (slot == null) return SlotVisual.DEFAULT;
        int index = menu.slots.indexOf(slot);
        if (index < 0) return SlotVisual.DEFAULT;

        SlotBinding binding = bindingsByGlobalIndex.get(index);
        if (binding == null) return SlotVisual.DEFAULT;

        Slot slotElement = binding.slotElement;
        return new SlotVisual(
                !slotElement.shouldRenderItem(),
                slotElement.isDisabled(),
                slotElement.shouldRenderItem(),
                slotElement.resolveSlotSizeHint(16),
                slotElement.resolveItemPadding(0),
                slotElement.resolveIconScale(1.0F),
                slotElement.resolveZIndex(0)
        );
    }

    /**
     * 获取未绑定到菜单的展示型 Slot 元素列表。
     */
    public List<Slot> getDisplaySlots() {
        return Collections.unmodifiableList(displaySlots);
    }

    /**
     * 清理所有绑定。
     */
    public void clear() {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            binding.slotElement.bindMcSlot(null);
        }
        bindingsByGlobalIndex.clear();
        displaySlots.clear();
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

    /**
     * 槽位视觉属性。
     */
    public record SlotVisual(
            boolean hidden,
            boolean disabled,
            boolean renderItem,
            int slotSize,
            int padding,
            float iconScale,
            int zIndex
    ) {
        public static final SlotVisual DEFAULT = new SlotVisual(false, false, true, 16, 0, 1.0F, 0);
    }

    private record SlotBinding(Slot slotElement, int globalIndex, int localIndex) {
    }
}
