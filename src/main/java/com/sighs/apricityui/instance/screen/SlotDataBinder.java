package com.sighs.apricityui.instance.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.dom.SlotContentRules;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.element.Ingredient;
import com.sighs.apricityui.instance.element.Item;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.instance.render.item.ItemRenderContext;
import com.sighs.apricityui.instance.render.item.ItemRenderState;
import com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor;
import com.sighs.apricityui.mixin.accessor.SlotAccessor;
import com.sighs.apricityui.layout.Position;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * 将 DOM Slot 绑定到菜单 Slot，并把实时状态写入其直接 Item。
 */
public final class SlotDataBinder {
    private final ApricityContainerMenu menu;
    private final LinkedHashMap<Integer, SlotBinding> bindings = new LinkedHashMap<>();
    private long generation = -1L;
    private String fingerprint = "";
    private double viewportScaleX = 1.0d;
    private double viewportScaleY = 1.0d;

    public SlotDataBinder(ApricityContainerMenu menu) {
        this.menu = Objects.requireNonNull(menu);
    }

    private static void setItemState(Item item, ItemStack stack, String overlay, boolean ghost) {
        ItemStack safe = stack == null ? ItemStack.EMPTY : stack;
        item.setDrivenState(
                new ItemRenderState(safe, overlay, ghost, false, ItemRenderContext.resolveCooldownProgress(safe)),
                Item.Source.MENU
        );
    }

    private static String fingerprint(Document document) {
        StringBuilder out = new StringBuilder();
        for (Element element : document.getElements()) {
            if (element instanceof Slot slot) {
                out.append(System.identityHashCode(slot)).append(':').append(slot.getSlotIndex()).append(':');
                Element content = SlotContentRules.getSlotContent(slot);
                out.append(content == null ? 0 : System.identityHashCode(content)).append(';');
            }
        }
        return out.toString();
    }

    private static String implicitId(Document document, Container container) {
        int index = 0;
        for (Element element : document.getElements()) {
            if (element instanceof Container current) {
                if (current == container) return "c" + index;
                index++;
            }
        }
        return "c0";
    }

    public void bindSlotsFromDocument(Document document) {
        clear();
        if (document == null || menu.getLayout().isUiOnly()) return;

        for (Element element : document.getElements()) {
            if (!(element instanceof Slot slot)) continue;
            Element content = SlotContentRules.getSlotContent(slot);
            if (content instanceof Ingredient) continue;

            Item item = content instanceof Item direct ? direct : SlotContentRules.ensureDirectItem(slot);
            Container container = slot.findAncestor(Container.class);
            if (container == null || slot.getSlotIndex() < 0) continue;

            String id = container.getAttribute("id");
            if (id == null || id.isBlank()) id = implicitId(document, container);
            Integer global = menu.resolveGlobalSlotIndex(id, slot.getSlotIndex());
            if (global == null || global < 0 || global >= menu.slots.size()) continue;

            slot.bindToMenuSlot();
            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(global);
            setItemState(item, menuSlot.getItem(), null, false);
            bindings.put(global, new SlotBinding(slot, item, global, slot.getSlotIndex()));
        }

        generation = document.getRefreshGeneration();
        fingerprint = fingerprint(document);
    }

    public void syncAllSlotPositions(Document document, int leftPos, int topPos, boolean force) {
        if (document != null) {
            viewportScaleX = positiveScale(document.getViewportScaleX());
            viewportScaleY = positiveScale(document.getViewportScaleY());
        }

        for (SlotBinding binding : bindings.values()) {
            if (binding.globalIndex < 0 || binding.globalIndex >= menu.slots.size()) continue;
            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(binding.globalIndex);
            Position position = Position.of(binding.slot);
            int x = (int) Math.round(position.x * viewportScaleX) - leftPos;
            int y = (int) Math.round(position.y * viewportScaleY) - topPos;
            if (force || menuSlot.x != x || menuSlot.y != y) {
                ((SlotAccessor) menuSlot).setX(x);
                ((SlotAccessor) menuSlot).setY(y);
            }
        }
    }

    private static double positiveScale(double value) {
        return Double.isFinite(value) && value > 0.0d ? value : 1.0d;
    }

    public void updateBoundItemRenderStates(AbstractContainerScreenAccessor accessor, ItemStack carried) {
        for (SlotBinding binding : bindings.values()) {
            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(binding.globalIndex);
            setItemState(binding.item, menuSlot.getItem(), null, false);
        }
    }

    public boolean shouldRebindSlotsFromDom(Document document) {
        return document != null
                && (generation != document.getRefreshGeneration()
                || !Objects.equals(fingerprint, fingerprint(document)));
    }

    public int findOperableSlotIndexAt(double mouseX, double mouseY) {
        double documentX = mouseX / viewportScaleX;
        double documentY = mouseY / viewportScaleY;
        for (SlotBinding binding : bindings.values()) {
            if (binding.item.canOperateBoundMenuSlot()
                    && binding.item.containsItemPoint(documentX, documentY)) {
                return binding.globalIndex;
            }
        }
        return -1;
    }

    public boolean canOperateSlot(net.minecraft.world.inventory.Slot slot) {
        if (slot == null) return false;
        int index = menu.slots.indexOf(slot);
        SlotBinding binding = bindings.get(index);
        return binding == null || binding.item.canOperateBoundMenuSlot();
    }

    public boolean isSlotBound(net.minecraft.world.inventory.Slot slot) {
        return slot != null && bindings.containsKey(menu.slots.indexOf(slot));
    }

    public boolean isBoundElementHovered(net.minecraft.world.inventory.Slot slot, double mouseX, double mouseY) {
        if (slot == null) return false;
        SlotBinding binding = bindings.get(menu.slots.indexOf(slot));
        if (binding == null || !binding.item.canOperateBoundMenuSlot()) return false;
        return binding.item.containsItemPoint(mouseX / viewportScaleX, mouseY / viewportScaleY);
    }

    public net.minecraft.world.inventory.Slot getBoundMenuSlot(Item item) {
        if (item == null) return null;
        for (SlotBinding binding : bindings.values()) {
            if (binding.item == item) return menu.slots.get(binding.globalIndex);
        }
        return null;
    }

    public void clear() {
        for (SlotBinding binding : bindings.values()) {
            binding.slot.clearMenuSlotBinding();
            binding.item.clearDrivenState(Item.Source.MENU);
        }
        bindings.clear();
        generation = -1L;
        fingerprint = "";
        viewportScaleX = 1.0d;
        viewportScaleY = 1.0d;
    }

    private record SlotBinding(Slot slot, Item item, int globalIndex, int localIndex) {
    }
}
