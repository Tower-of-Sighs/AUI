package com.sighs.apricityui.screen;

import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.element.Container;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * 将 DOM 中的直接 Item 槽位内容与菜单槽位进行绑定和同步。
 */
public final class SlotDataBinder {
    private final ApricityContainerMenu menu;
    private final LinkedHashMap<Integer, SlotBinding> bindingsByGlobalIndex = new LinkedHashMap<>();
    private final ArrayList<Slot> displaySlots = new ArrayList<>();
    private int lastBindSlotCount = -1;
    private long lastBindGeneration = -1L;
    private double viewportScaleX = 1.0D;
    private double viewportScaleY = 1.0D;
    private DisplayStateResolver displayStateResolver =
            slot -> new SlotItemState(slot.getItem(), null, false);

    public SlotDataBinder(ApricityContainerMenu menu) {
        this.menu = Objects.requireNonNull(menu);
    }

    public void setDisplayStateResolver(DisplayStateResolver resolver) {
        displayStateResolver = resolver == null
                ? slot -> new SlotItemState(slot.getItem(), null, false)
                : resolver;
    }

    /**
     * 从 Document 中扫描 Slot 元素并绑定直接 Item 内容到菜单槽位。
     */
    public void bindSlotsFromDocument(Document document) {
        clear();
        if (document == null) return;

        boolean uiOnly = menu.getLayout().isUiOnly();
        for (Element element : document.getElements()) {
            if (!(element instanceof Slot slotElement)) continue;

            if (uiOnly) {
                displaySlots.add(slotElement);
                continue;
            }

            Container container = slotElement.findAncestor(Container.class);
            if (container == null) {
                displaySlots.add(slotElement);
                continue;
            }

            Item itemElement = directItem(slotElement);
            if (itemElement == null) {
                // Ingredient 仅作为展示内容，不能覆盖真实菜单槽位。
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

            SlotBinding binding = new SlotBinding(slotElement, itemElement, globalIndex, localIndex);
            bindingsByGlobalIndex.put(globalIndex, binding);
            slotElement.bindToMenuSlot(slotElement.isExplicitlyDisabled());
        }

        syncBoundSlotStates();
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
            if (binding.globalIndex() < 0 || binding.globalIndex() >= menu.slots.size()) continue;
            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(binding.globalIndex());

            Slot slotElement = binding.slotElement();
            Position position = Position.of(slotElement);
            int elementX = (int) Math.round(position.x * viewportScaleX) - leftPos;
            int elementY = (int) Math.round(position.y * viewportScaleY) - topPos;

            if (force || menuSlot.x != elementX || menuSlot.y != elementY) {
                // Slot#x / Slot#y made writable via META-INF/accesstransformer.cfg
                menuSlot.x = elementX;
                menuSlot.y = elementY;
            }

            if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                uiSlot.setUiDisabled(slotElement.isExplicitlyDisabled());
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
     * 按 DOM Slot 的实际几何位置查找鼠标位置对应的菜单槽位索引。
     * 不依赖文档 hitTest，因为全局 CSS 对 Slot 默认关闭了 pointer-events。
     */
    public int findSlotIndexAt(double mouseX, double mouseY, int leftPos, int topPos) {
        Position documentMouse = documentPositionAt(mouseX, mouseY);
        if (documentMouse == null) return -1;

        ArrayList<SlotBinding> bindings = new ArrayList<>(bindingsByGlobalIndex.values());
        for (int index = bindings.size() - 1; index >= 0; index--) {
            SlotBinding binding = bindings.get(index);
            Slot slotElement = binding.slotElement();
            if (slotElement.canOperateBoundMenuSlot()
                    && slotElement.containsSlotPoint(documentMouse.x, documentMouse.y)) {
                return binding.globalIndex();
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
        if (binding == null) return true;
        return binding.slotElement().canOperateBoundMenuSlot();
    }

    public boolean isSlotBound(net.minecraft.world.inventory.Slot slot) {
        return slot != null && bindingsByGlobalIndex.containsKey(menu.slots.indexOf(slot));
    }

    public boolean isBoundElementHovered(net.minecraft.world.inventory.Slot slot, double mouseX, double mouseY) {
        if (slot == null) return false;
        SlotBinding binding = bindingsByGlobalIndex.get(menu.slots.indexOf(slot));
        if (binding == null || !binding.slotElement().canOperateBoundMenuSlot()) return false;

        Position documentMouse = documentPositionAt(mouseX, mouseY);
        return documentMouse != null
                && binding.slotElement().containsSlotPoint(documentMouse.x, documentMouse.y);
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

        Slot slotElement = binding.slotElement();
        return new SlotVisual(
                !slotElement.shouldRenderItem(),
                slotElement.isDisabled(),
                slotElement.shouldRenderItem(),
                scaleSlotSize(slotElement.resolveSlotSizeHint(16)),
                (float) Math.max(0.01D, slotElement.resolveIconScale(1.0F) * viewportScaleX),
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
        SlotBinding binding = bindingsByGlobalIndex.get(menu.slots.indexOf(slot));
        return binding == null ? null : binding.slotElement();
    }

    public Item getBoundItem(net.minecraft.world.inventory.Slot slot) {
        if (slot == null) return null;
        SlotBinding binding = bindingsByGlobalIndex.get(menu.slots.indexOf(slot));
        return binding == null ? null : binding.itemElement();
    }

    /**
     * 清理所有绑定。
     */
    public void clear() {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            binding.slotElement().clearMenuSlotBinding();
            binding.itemElement().clearDrivenState(Item.Source.MENU);
        }
        bindingsByGlobalIndex.clear();
        displaySlots.clear();
    }

    /**
     * 每帧解析菜单、拖拽和快速合成状态，并写入绑定 Item 的统一显示状态。
     */
    public void syncBoundSlotStates() {
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            if (binding.globalIndex() < 0 || binding.globalIndex() >= menu.slots.size()) continue;

            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(binding.globalIndex());
            SlotItemState state = resolveDisplayState(menuSlot);
            boolean hidden = !menuSlot.isActive();
            boolean disabled = binding.slotElement().isExplicitlyDisabled();
            if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                hidden |= uiSlot.isUiHidden();
                disabled |= uiSlot.isUiDisabled();
            }

            binding.slotElement().updateBoundMenuState(disabled, hidden, state.ghost());
            binding.itemElement().setDrivenState(
                    state.stack(),
                    state.overlayText(),
                    hidden,
                    disabled,
                    Item.Source.MENU
            );
        }
    }

    /** Synchronizes DOM :hover state for bound slots from the screen pointer. */
    public void syncBoundSlotHoverStates(double mouseX, double mouseY) {
        Position documentMouse = documentPositionAt(mouseX, mouseY);
        for (SlotBinding binding : bindingsByGlobalIndex.values()) {
            Slot slotElement = binding.slotElement();
            boolean hovered = documentMouse != null
                    && slotElement.canOperateBoundMenuSlot()
                    && slotElement.containsSlotPoint(documentMouse.x, documentMouse.y);
            slotElement.setHover(hovered);
        }
    }

    private Position documentPositionAt(double mouseX, double mouseY) {
        if (bindingsByGlobalIndex.isEmpty()) return null;
        SlotBinding first = bindingsByGlobalIndex.values().iterator().next();
        Document document = first.slotElement().document;
        if (document == null) return null;
        return document.screenToDocumentPosition(new Position(mouseX, mouseY));
    }

    private int scaleSlotSize(int logicalSize) {
        return Math.max(1, (int) Math.round(Math.max(1, logicalSize) * viewportScaleX));
    }

    private static Item directItem(Slot slot) {
        return SlotContentRules.getSlotContent(slot) instanceof Item item ? item : null;
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
        int index = 0;
        for (Element element : document.getElements()) {
            if (!(element instanceof Container candidate)) continue;
            if (candidate == container) return "c" + index;
            index++;
        }
        return "c0";
    }

    private SlotItemState resolveDisplayState(net.minecraft.world.inventory.Slot menuSlot) {
        SlotItemState state = displayStateResolver.resolve(menuSlot);
        return state == null ? new SlotItemState(menuSlot.getItem(), null, false) : state;
    }

    @FunctionalInterface
    public interface DisplayStateResolver {
        SlotItemState resolve(net.minecraft.world.inventory.Slot slot);
    }

    public record SlotItemState(ItemStack stack, String overlayText, boolean ghost) {
        public SlotItemState {
            stack = stack == null ? ItemStack.EMPTY : stack;
        }
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

    private record SlotBinding(Slot slotElement, Item itemElement, int globalIndex, int localIndex) {
    }
}
