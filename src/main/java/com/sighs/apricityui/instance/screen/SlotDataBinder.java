package com.sighs.apricityui.instance.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.mixin.accessor.SlotAccessor;
import com.sighs.apricityui.style.Position;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 将 DOM 中的 Slot 元素与菜单槽位进行绑定和同步。
 */
public final class SlotDataBinder {
    private final ApricityContainerMenu menu;
    private final LinkedHashMap<Integer, SlotBinding> bindingsByGlobalIndex = new LinkedHashMap<>();
    private final ArrayList<Slot> displaySlots = new ArrayList<>();
    private int lastBindSlotCount = -1;
    private long lastBindGeneration = -1L;
    private double viewportScaleX = 1.0d;
    private double viewportScaleY = 1.0d;

    public SlotDataBinder(ApricityContainerMenu menu) {
        this.menu = Objects.requireNonNull(menu);
    }

    /**
     * 从 Document 中扫描 Slot 元素并绑定到菜单槽位。
     */
    public void bindSlotsFromDocument(Document document) {
        clear();
        if (document == null) return;

        boolean uiOnly = menu.getLayout().isUiOnly();
        List<Element> elements = document.getElements();
        for (Element element : elements) {
            if (!(element instanceof Slot slotElement)) continue;

            // 纯 UI 页面没有真实菜单槽位可绑定，但 slot / recipe 预览仍应作为展示槽位参与物品渲染。
            if (uiOnly) {
                displaySlots.add(slotElement);
                continue;
            }

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

            // 注入 SlotView 到 Slot 元素
            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(globalIndex);
            slotElement.setView(createSlotView(menuSlot, slotElement));
        }

        lastBindSlotCount = countSlotElements(document);
        lastBindGeneration = document.getRefreshGeneration();
    }

    /**
     * 同步所有绑定槽位的屏幕坐标。
     */
    public void syncAllSlotPositions(Document document, int leftPos, int topPos, boolean force) {
        if (document != null) {
            viewportScaleX = document.getViewportScaleX();
            viewportScaleY = document.getViewportScaleY();
        }
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            if (binding.globalIndex < 0 || binding.globalIndex >= menu.slots.size()) continue;
            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(binding.globalIndex);

            Slot slotElement = binding.slotElement;
            Position pos = Position.of(slotElement);
            int elementX = (int) Math.round(pos.x * viewportScaleX) - leftPos;
            int elementY = (int) Math.round(pos.y * viewportScaleY) - topPos;

            if (force || menuSlot.x != elementX || menuSlot.y != elementY) {
                ((SlotAccessor) menuSlot).setX(elementX);
                ((SlotAccessor) menuSlot).setY(elementY);
            }

            // 同步 UiSlot 状态
            if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                uiSlot.setUiDisabled(slotElement.isDisabled());
                uiSlot.setUiHidden(!slotElement.shouldRenderItem());
                uiSlot.setUiSlotSize(scaleSlotSize(slotElement.resolveSlotSizeHint(16)));
            }
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
     * 查找鼠标位置对应的菜单槽位索引。
     */
    public int findSlotIndexAt(double mouseX, double mouseY, int leftPos, int topPos) {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            Slot slotElement = binding.slotElement;
            if (!slotElement.shouldAcceptPointer()) continue;

            Position pos = Position.of(slotElement);
            double ex = pos.x * viewportScaleX;
            double ey = pos.y * viewportScaleY;
            int size = scaleSlotSize(slotElement.resolveSlotSizeHint(16));

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
                scaleSlotSize(slotElement.resolveSlotSizeHint(16)),
                (float) Math.max(0.01d, slotElement.resolveIconScale(1.0F) * viewportScaleX),
                slotElement.resolveZIndex(0)
        );
    }

    /**
     * 获取未绑定到菜单的展示型 Slot 元素列表。
     */
    public List<Slot> getDisplaySlots() {
        return Collections.unmodifiableList(displaySlots);
    }

    public Slot getBoundElement(net.minecraft.world.inventory.Slot slot) {
        if (slot == null) return null;
        int index = menu.slots.indexOf(slot);
        if (index < 0) return null;
        SlotBinding binding = bindingsByGlobalIndex.get(index);
        return binding == null ? null : binding.slotElement;
    }

    /**
     * 清理所有绑定。
     */
    public void clear() {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            binding.slotElement.setView(null);
        }
        bindingsByGlobalIndex.clear();
        displaySlots.clear();
    }

    private int scaleSlotSize(int logicalSize) {
        return Math.max(1, (int) Math.round(Math.max(1, logicalSize) * viewportScaleX));
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

    private Slot.SlotView createSlotView(net.minecraft.world.inventory.Slot menuSlot, Slot slotElement) {
        return new Slot.SlotView() {
            @Override
            public ItemStack getDisplayStack() {
                return menuSlot.getItem();
            }

            @Override
            public boolean isDisabled() {
                if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                    return uiSlot.isUiDisabled();
                }
                return false;
            }

            @Override
            public boolean isHidden() {
                if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                    return uiSlot.isUiHidden();
                }
                return false;
            }

            @Override
            public int getSlotSize() {
                if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                    return uiSlot.getUiSlotSize();
                }
                return 16;
            }
        };
    }

    /**
     * 槽位视觉属性。
     */
    public record SlotVisual(
            boolean hidden,
            boolean disabled,
            boolean renderItem,
            int slotSize,
            float iconScale,
            int zIndex
    ) {
        public static final SlotVisual DEFAULT = new SlotVisual(false, false, true, 16, 1.0F, 0);
    }

    private record SlotBinding(Slot slotElement, int globalIndex, int localIndex) {
    }
}
