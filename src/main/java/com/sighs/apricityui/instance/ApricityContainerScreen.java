package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.element.ApricityRecipe;
import com.sighs.apricityui.instance.element.ApricitySlot;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.mixin.accessor.SlotAccessor;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.util.common.NormalizeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.*;

public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> {
    private static final String DEVTOOLS_PATH = "devtools/index.html";
    private static final int QUICK_CRAFT_GHOST_COLOR = -2130706433;
    private static final float ICON_SCALE_EPSILON = 0.0001F;
    private static final int OFFSCREEN_SLOT_POS = -10000;

    private final Document linkedDocument;
    private final ArrayList<ApricitySlot> boundSlots = new ArrayList<>();
    private final ArrayList<ApricitySlot> unboundSlots = new ArrayList<>();
    private final HashMap<ApricitySlot, Integer> boundGlobalIndexByElement = new HashMap<>();
    private final HashMap<ApricitySlot, Container> boundContainerByElement = new HashMap<>();
    private final IdentityHashMap<Slot, ApricitySlot> boundElementByMenuSlot = new IdentityHashMap<>();
    private boolean slotsBound = false;
    private boolean slotSyncDirty = true;
    private int lastKnownDomSlotCount = -1;

    public ApricityContainerScreen(ApricityContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        linkedDocument = Document.create(menu.getTemplatePath());
    }

    public ApricityContainerScreen(String path) {
        this(ApricityContainerMenu.createClientOnly(Minecraft.getInstance().player.getInventory(), path),
                Minecraft.getInstance().player.getInventory(),
                Component.literal("ApricityScreen"));
    }

    public Document getLinkedDocument() {
        return linkedDocument;
    }

    public int getGuiLeft() {
        return super.getGuiLeft();
    }

    public int getGuiTop() {
        return super.getGuiTop();
    }

    public int findSlotIndexAt(double mouseX, double mouseY) {
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (!isSlotPointerInteractable(slot)) continue;
            int slotSize = resolveSlotSize(slot);
            if (isHovering(slot.x, slot.y, slotSize, slotSize, mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    public boolean isSlotPointerInteractable(Slot slot) {
        if (slot == null || !slot.isActive()) return false;
        if (!(slot instanceof ApricityContainerMenu.UiSlot uiSlot)) {
            return isBoundSlotPointerEnabledByCss(slot);
        }
        return !uiSlot.isUiHidden()
                && !uiSlot.isUiDisabled()
                && uiSlot.isUiAcceptPointer()
                && isBoundSlotPointerEnabledByCss(slot);
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = 0;
        this.topPos = 0;
        if (linkedDocument == null) return;

        bindSlotsFromDocument();
        syncAllSlotPositions(true);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        if (linkedDocument != null) {
            if (shouldRebindSlotsFromDom()) {
                bindSlotsFromDocument();
                syncAllSlotPositions(true);
            } else {
                syncAllSlotPositions(false);
            }
        }

        super.extractContents(guiGraphics, mouseX, mouseY, a);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        // 标题改为容器内节点渲染，不再固定绘制到屏幕左上角。
    }

    private void bindSlotsFromDocument() {
        if (linkedDocument == null) return;

        boundSlots.clear();
        unboundSlots.clear();
        boundGlobalIndexByElement.clear();
        boundContainerByElement.clear();
        boundElementByMenuSlot.clear();

        LinkedHashMap<String, Container> containerById = resolveTopLevelContainerMapping();
        IdentityHashMap<Container, String> containerIdByElement = new IdentityHashMap<>();
        for (Map.Entry<String, Container> entry : containerById.entrySet()) {
            containerIdByElement.put(entry.getValue(), entry.getKey());
        }

        HashMap<String, Integer> nextImplicitLocalIndex = new HashMap<>();
        HashSet<Integer> usedGlobalSlots = new HashSet<>();

        List<Element> snapshot = new ArrayList<>(linkedDocument.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof ApricitySlot slot)) continue;
            if (slot.findAncestor(ApricityRecipe.class) != null) {
                slot.bindMcSlot(null);
                unboundSlots.add(slot);
                continue;
            }

            Container ownerContainer = slot.findAncestor(Container.class);
            if (ownerContainer == null) {
                slot.bindMcSlot(null);
                unboundSlots.add(slot);
                continue;
            }
            String containerId = containerIdByElement.get(ownerContainer);
            if (containerId == null || containerId.isBlank()) {
                slot.bindMcSlot(null);
                unboundSlots.add(slot);
                continue;
            }

            int localSlotIndex = slot.getSlotIndex();
            if (localSlotIndex < 0) {
                localSlotIndex = nextImplicitLocalIndex.getOrDefault(containerId, 0);
                slot.setAttribute("slot-index", String.valueOf(localSlotIndex));
            }
            int nextLocalSlotIndex = Math.max(nextImplicitLocalIndex.getOrDefault(containerId, 0), localSlotIndex + 1);
            nextImplicitLocalIndex.put(containerId, nextLocalSlotIndex);

            Integer globalSlotIndex = menu.resolveGlobalSlotIndex(containerId, localSlotIndex);
            if (globalSlotIndex == null
                    || globalSlotIndex < 0
                    || globalSlotIndex >= menu.slots.size()
                    || !usedGlobalSlots.add(globalSlotIndex)) {
                slot.bindMcSlot(null);
                unboundSlots.add(slot);
                continue;
            }

            Slot menuSlot = menu.slots.get(globalSlotIndex);
            slot.bindMcSlot(menuSlot);
            boundSlots.add(slot);
            boundGlobalIndexByElement.put(slot, globalSlotIndex);
            boundContainerByElement.put(slot, ownerContainer);
            boundElementByMenuSlot.put(menuSlot, slot);
        }
        slotsBound = true;
        slotSyncDirty = true;
        lastKnownDomSlotCount = countDomSlotElements();
    }

    private boolean shouldRebindSlotsFromDom() {
        if (!slotsBound) return true;
        if (linkedDocument == null) return false;
        int currentSlotCount = countDomSlotElements();
        if (currentSlotCount < 0) return false;
        if (currentSlotCount != lastKnownDomSlotCount) return true;
        return currentSlotCount != (boundSlots.size() + unboundSlots.size());
    }

    private int countDomSlotElements() {
        if (linkedDocument == null) return -1;
        int count = 0;
        for (Element element : linkedDocument.getElements()) {
            if (element instanceof ApricitySlot) count++;
        }
        return count;
    }

    private LinkedHashMap<String, Container> resolveTopLevelContainerMapping() {
        LinkedHashMap<String, Container> result = new LinkedHashMap<>();
        if (linkedDocument == null) return result;

        LinkedHashMap<String, Container> domContainerById = new LinkedHashMap<>();
        for (Element element : linkedDocument.getElements()) {
            if (!(element instanceof Container container)) continue;
            String normalizedId = NormalizeUtil.normalizeContainerId(container.getAttribute("id"));
            if (normalizedId == null) continue;
            domContainerById.putIfAbsent(normalizedId, container);
        }

        List<String> containerIds = menu.getLayoutSpec().containerIds();
        for (String containerId : containerIds) {
            String normalizedId = NormalizeUtil.normalizeContainerId(containerId);
            if (normalizedId == null) continue;
            Container matched = domContainerById.get(normalizedId);
            if (matched == null) continue;
            result.put(normalizedId, matched);
        }
        return result;
    }

    /**
     * 玩家容器头部（第一个 slot 之前的非 slot 直接子节点）强制跨满 9 列，
     * 避免标题占用首格导致首行槽位只剩 8 列。
     */
    private boolean shouldSyncSlotPositions(boolean force) {
        if (force || slotSyncDirty) return true;
        return linkedDocument != null && !linkedDocument.getDirtyElements().isEmpty();
    }

    private void syncAllSlotPositions(boolean force) {
        if (linkedDocument == null) return;
        if (!shouldSyncSlotPositions(force)) return;

        Drawer.flushUpdates(linkedDocument);

        for (Slot slot : menu.slots) {
            setMenuSlotPosition(slot, OFFSCREEN_SLOT_POS, OFFSCREEN_SLOT_POS);
            if (slot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                uiSlot.setUiHidden(true);
            }
        }

        LinkedHashMap<Container, ArrayList<Map.Entry<ApricitySlot, Integer>>> groupedEntries = new LinkedHashMap<>();
        for (Map.Entry<ApricitySlot, Integer> entry : boundGlobalIndexByElement.entrySet()) {
            ApricitySlot boundElement = entry.getKey();
            if (boundElement == null) continue;
            Container ownerContainer = boundContainerByElement.get(boundElement);
            if (ownerContainer == null) {
                ownerContainer = boundElement.findAncestor(Container.class);
                if (ownerContainer != null) {
                    boundContainerByElement.put(boundElement, ownerContainer);
                }
            }
            if (ownerContainer == null) continue;
            groupedEntries.computeIfAbsent(ownerContainer, ignored -> new ArrayList<>()).add(entry);
        }

        for (ArrayList<Map.Entry<ApricitySlot, Integer>> entries : groupedEntries.values()) {
            for (Map.Entry<ApricitySlot, Integer> entry : entries) {
                ApricitySlot boundElement = entry.getKey();
                int globalSlotIndex = entry.getValue();
                if (boundElement == null) continue;
                if (globalSlotIndex < 0 || globalSlotIndex >= menu.slots.size()) continue;

                Slot menuSlot = menu.slots.get(globalSlotIndex);
                boolean hidden = !boundElement.isVisible || "none".equals(boundElement.getComputedStyle().display);

                int slotSize = resolveBoundSlotPixelSize(boundElement, menuSlot);
                if (hidden) {
                    setMenuSlotPosition(menuSlot, OFFSCREEN_SLOT_POS, OFFSCREEN_SLOT_POS);
                } else {
                    Position screenPos = Position.of(boundElement);
                    int screenX = (int) Math.round(screenPos.x);
                    int screenY = (int) Math.round(screenPos.y);
                    setMenuSlotPosition(menuSlot, screenX - leftPos, screenY - topPos);
                }

                if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                    uiSlot.setUiSlotSize(slotSize);
                    uiSlot.setUiHidden(hidden);
                }
            }
        }

        slotSyncDirty = false;
    }

    private int resolveBoundSlotPixelSize(ApricitySlot boundElement, Slot menuSlot) {
        if (boundElement != null) {
            int fromElement = boundElement.resolveSlotSizeHint(16);
            if (fromElement > 0) return fromElement;
        }

        if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
            return Math.max(1, uiSlot.getUiSlotSize());
        }

        Container ownerContainer = boundContainerByElement.get(boundElement);
        if (ownerContainer == null) {
            if (boundElement != null) {
                ownerContainer = boundElement.findAncestor(Container.class);
            }
            if (ownerContainer != null) {
                boundContainerByElement.put(boundElement, ownerContainer);
            }
        }
        if (ownerContainer != null) {
            return Math.max(1, ownerContainer.resolveSlotSizePx(16));
        }
        return 16;
    }

    private boolean isBoundSlotPointerEnabledByCss(Slot menuSlot) {
        ApricitySlot boundElement = boundElementByMenuSlot.get(menuSlot);
        if (boundElement == null) return true;
        if (boundElement.findAncestor(ApricityRecipe.class) != null) return false;
        if (!boundElement.isVisible) return false;
        if ("none".equals(boundElement.getComputedStyle().display)) return false;
        return boundElement.shouldAcceptPointer();
    }

    @Override
    public void onClose() {
        if (linkedDocument == null) {
            super.onClose();
            return;
        }

        if (linkedDocument.body != null) {
            linkedDocument.body.triggerEvent(currentEvent -> {
                if ("unload".equals(currentEvent.type) && currentEvent.listener != null) {
                    try {
                        currentEvent.listener.accept(currentEvent);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        linkedDocument.remove();
        Cursor.resetToDefault();
        super.onClose();
    }

    @Override
    public void removed() {
        // 兜底清理：某些关闭路径可能不经过 onClose，确保绑定文档不会残留。
        if (linkedDocument != null) {
            linkedDocument.remove();
        }
        slotsBound = false;
        slotSyncDirty = true;
        lastKnownDomSlotCount = -1;
        boundSlots.clear();
        unboundSlots.clear();
        boundGlobalIndexByElement.clear();
        boundContainerByElement.clear();
        boundElementByMenuSlot.clear();
        super.removed();
    }

    private SlotVisual resolveSlotVisual(Slot slot) {
        if (!(slot instanceof ApricityContainerMenu.UiSlot uiSlot)) {
            return new SlotVisual(16, false, true, true, 1.0F, 0, 0, false);
        }
        return new SlotVisual(
                Math.max(1, uiSlot.getUiSlotSize()),
                uiSlot.isUiDisabled(),
                uiSlot.isUiRenderItem(),
                uiSlot.isUiAcceptPointer(),
                Math.max(0.01F, uiSlot.getUiIconScale()),
                Math.max(0, uiSlot.getUiPadding()),
                uiSlot.getUiZIndex(),
                uiSlot.isUiHidden()
        );
    }

    private int resolveSlotSize(Slot slot) {
        if (slot instanceof ApricityContainerMenu.UiSlot uiSlot) {
            return Math.max(1, uiSlot.getUiSlotSize());
        }
        return 16;
    }

    private boolean applyBoundSlotRuntimeStyle(
            ApricitySlot slotElement,
            boolean hidden
    ) {
        if (slotElement == null) return false;
        String baseStyle = (String) slotElement.getRuntimeCache("bound-base-inline-style");
        if (baseStyle == null) {
            String raw = slotElement.getAttribute("style");
            baseStyle = raw == null ? "" : raw;
            slotElement.putRuntimeCache("bound-base-inline-style", baseStyle);
        }

        String runtimeStyle = hidden ? "display:none;" : "";
        String merged = mergeInlineStyle(baseStyle, runtimeStyle);

        String lastApplied = (String) slotElement.getRuntimeCache("bound-last-inline-style");
        if (Objects.equals(lastApplied, merged)) return false;
        slotElement.setAttribute("style", merged);
        slotElement.putRuntimeCache("bound-last-inline-style", merged);
        slotElement.putRuntimeCache("bound-runtime-layout-managed", hidden);
        return true;
    }

    private static String mergeInlineStyle(String baseStyle, String runtimeStyle) {
        String base = baseStyle == null ? "" : baseStyle.trim();
        String runtime = runtimeStyle == null ? "" : runtimeStyle.trim();
        if (base.isEmpty()) return runtime;
        if (runtime.isEmpty()) return base;
        if (base.endsWith(";")) return base + runtime;
        return base + ";" + runtime;
    }

    private void setMenuSlotPosition(Slot slot, int x, int y) {
        if (slot == null) return;
        SlotAccessor accessor = (SlotAccessor) slot;
        accessor.setX(x);
        accessor.setY(y);
    }

    private static int clampPadding(int slotSize, int padding) {
        int maxPadding = Math.max(0, (Math.max(1, slotSize) - 1) / 2);
        int normalized = Math.max(0, padding);
        return Math.min(normalized, maxPadding);
    }

    private record SlotVisual(
            int slotSize,
            boolean disabled,
            boolean renderItem,
            boolean acceptPointer,
            float iconScale,
            int padding,
            int zIndex,
            boolean hidden
    ) {
    }
}
