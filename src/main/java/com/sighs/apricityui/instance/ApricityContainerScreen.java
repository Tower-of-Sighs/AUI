package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.element.MinecraftElement;
import com.sighs.apricityui.instance.element.Recipe;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor;
import com.sighs.apricityui.mixin.accessor.SlotAccessor;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> {
    private static final String DEVTOOLS_PATH = "devtools/index.html";
    private static final int QUICK_CRAFT_GHOST_COLOR = -2130706433;
    private static final float ICON_SCALE_EPSILON = 0.0001F;
    private static final int OFFSCREEN_SLOT_POS = -10000;

    private final Document linkedDocument;
    private final ArrayList<Slot> boundSlots = new ArrayList<>();
    private final ArrayList<Slot> virtualSlots = new ArrayList<>();
    private final HashMap<Slot, Integer> boundGlobalIndexByElement = new HashMap<>();
    private final HashMap<Slot, Container> boundContainerByElement = new HashMap<>();
    private boolean slotsBound = false;
    private boolean slotSyncDirty = true;

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
            net.minecraft.world.inventory.Slot slot = menu.slots.get(index);
            if (!isSlotPointerInteractable(slot)) continue;
            int slotSize = resolveSlotSize(slot);
            if (isHovering(slot.x, slot.y, slotSize, slotSize, mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    public boolean isSlotPointerInteractable(net.minecraft.world.inventory.Slot slot) {
        if (slot == null || !slot.isActive()) return false;
        if (!(slot instanceof ApricityContainerMenu.UiSlot uiSlot)) return true;
        return !uiSlot.isUiHidden()
                && !uiSlot.isUiDisabled()
                && uiSlot.isUiAcceptPointer();
    }

    @Override
    protected void init() {
        imageWidth = width;
        imageHeight = height;
        super.init();
        if (linkedDocument == null) return;

        ensureRecipePreviewSlots();
        bindSlotsFromDocument();
        syncAllSlotPositions(true);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        if (linkedDocument == null) return;

        Base.drawDocument(guiGraphics.pose(), linkedDocument);
        drawBoundSlotItems(guiGraphics);
        drawVirtualSlotItems(guiGraphics);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标题改为容器内节点渲染，不再固定绘制到屏幕左上角。
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (linkedDocument != null) {
            boolean previewChanged = ensureRecipePreviewSlots();
            boolean bindingsChanged = false;
            if (!slotsBound || previewChanged) {
                bindSlotsFromDocument();
                bindingsChanged = true;
            }
            syncAllSlotPositions(bindingsChanged);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawSlotHoverTooltipByElement(guiGraphics, mouseX, mouseY);
        drawDevToolsOverlay(guiGraphics);
        Cursor.drawPseudoCursor(guiGraphics.pose());
    }

    private void bindSlotsFromDocument() {
        if (linkedDocument == null) return;

        Slot.cleanupRuntimeGeneratedSlots(linkedDocument);
        boundSlots.clear();
        virtualSlots.clear();
        boundGlobalIndexByElement.clear();
        boundContainerByElement.clear();

        LinkedHashMap<String, Container> containerById = resolveTopLevelContainerMapping();
        injectImplicitPlayerSlots(containerById);
        expandBoundRepeatSlots();

        IdentityHashMap<Container, String> containerIdByElement = new IdentityHashMap<>();
        for (Map.Entry<String, Container> entry : containerById.entrySet()) {
            containerIdByElement.put(entry.getValue(), entry.getKey());
        }

        HashMap<String, Integer> nextImplicitLocalIndex = new HashMap<>();
        HashSet<Integer> usedGlobalSlots = new HashSet<>();

        List<Element> snapshot = new ArrayList<>(linkedDocument.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Slot slot)) continue;

            if (slot.isVirtualMode()) {
                slot.bindMcSlot(null);
                virtualSlots.add(slot);
                continue;
            }

            Container ownerContainer = slot.findAncestor(Container.class);
            if (ownerContainer == null) continue;
            String containerId = containerIdByElement.get(ownerContainer);
            if (containerId == null || containerId.isBlank()) continue;

            int localSlotIndex = slot.getSlotIndex();
            if (localSlotIndex < 0) {
                localSlotIndex = nextImplicitLocalIndex.getOrDefault(containerId, 0);
                slot.setAttribute("slot-index", String.valueOf(localSlotIndex));
            }
            int nextLocalSlotIndex = Math.max(nextImplicitLocalIndex.getOrDefault(containerId, 0), localSlotIndex + 1);
            nextImplicitLocalIndex.put(containerId, nextLocalSlotIndex);

            Integer globalSlotIndex = menu.resolveGlobalSlotIndex(containerId, localSlotIndex);
            if (globalSlotIndex == null) continue;
            if (globalSlotIndex < 0 || globalSlotIndex >= menu.slots.size()) continue;
            if (!usedGlobalSlots.add(globalSlotIndex)) continue;

            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(globalSlotIndex);
            slot.bindMcSlot(menuSlot);
            boundSlots.add(slot);
            boundGlobalIndexByElement.put(slot, globalSlotIndex);
            boundContainerByElement.put(slot, ownerContainer);
        }
        slotsBound = true;
        slotSyncDirty = true;
    }

    private void expandBoundRepeatSlots() {
        if (linkedDocument == null) return;

        List<Element> snapshot = new ArrayList<>(linkedDocument.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Slot slot)) continue;
            if (!slot.isBoundMode()) continue;
            if (slot.isRuntimeGeneratedRepeatCopy()) continue;
            if (slot.isPlayerAutoGenerated()) continue;

            int repeatCount = Math.max(1, slot.getRepeatCount());
            if (repeatCount <= 1) continue;

            int baseLocalSlotIndex = slot.getSlotIndex();
            for (int repeatIndex = 1; repeatIndex < repeatCount; repeatIndex++) {
                int localSlotIndex = baseLocalSlotIndex < 0 ? -1 : baseLocalSlotIndex + repeatIndex;
                slot.createRuntimeRepeatSlotNode(localSlotIndex, repeatIndex);
            }
        }
    }

    private LinkedHashMap<String, Container> resolveTopLevelContainerMapping() {
        LinkedHashMap<String, Container> result = new LinkedHashMap<>();
        if (linkedDocument == null) return result;

        ArrayList<Container> topLevelContainers = new ArrayList<>();
        for (Element element : linkedDocument.getElements()) {
            if (!(element instanceof Container container)) continue;
            if (container.hasAncestor(Container.class)) continue;
            topLevelContainers.add(container);
        }

        List<String> containerIds = menu.getDescriptor().getContainerIds();
        int count = Math.min(containerIds.size(), topLevelContainers.size());
        for (int index = 0; index < count; index++) {
            result.put(containerIds.get(index), topLevelContainers.get(index));
        }
        return result;
    }

    private void injectImplicitPlayerSlots(Map<String, Container> containerById) {
        if (linkedDocument == null || containerById == null || containerById.isEmpty()) return;

        for (Map.Entry<String, Container> entry : containerById.entrySet()) {
            String containerId = entry.getKey();
            Container container = entry.getValue();
            if (container == null) continue;
            if (!com.sighs.apricityui.instance.container.schema.ContainerSchema.Descriptor.isPlayerBind(
                    menu.getDescriptor().getContainerBindType(containerId))) {
                continue;
            }
            if (hasExplicitBoundSlotUnderContainer(container)) continue;

            List<Integer> localSlots = menu.getDescriptor().getContainerSlots(containerId);
            if (localSlots.isEmpty()) continue;
            for (Integer localSlotIndex : localSlots) {
                if (localSlotIndex == null || localSlotIndex < 0) continue;
                Slot autoSlot = new Slot(linkedDocument);
                autoSlot.applyImplicitPlayerMeta(localSlotIndex, localSlotIndex < 27 ? "inv" : "hotbar");
                container.append(autoSlot);
            }
        }
    }

    private boolean hasExplicitBoundSlotUnderContainer(Container container) {
        if (linkedDocument == null || container == null) return false;
        for (Element element : linkedDocument.getElements()) {
            if (!(element instanceof Slot slot)) continue;
            if (!slot.isBoundMode()) continue;
            if (slot.isPlayerAutoGenerated()) continue;
            if (slot.findAncestor(Container.class) == container) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldSyncSlotPositions(boolean force) {
        if (force || slotSyncDirty) return true;
        return linkedDocument != null && !linkedDocument.getDirtyElements().isEmpty();
    }

    private void syncAllSlotPositions(boolean force) {
        if (linkedDocument == null) return;
        if (!shouldSyncSlotPositions(force)) return;

        Drawer.flushUpdates(linkedDocument);

        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            setMenuSlotPosition(slot, OFFSCREEN_SLOT_POS, OFFSCREEN_SLOT_POS);
            if (slot instanceof ApricityContainerMenu.UiSlot uiSlot) {
                uiSlot.setUiHidden(true);
            }
        }

        LinkedHashMap<Container, ArrayList<Map.Entry<Slot, Integer>>> groupedEntries = new LinkedHashMap<>();
        for (Map.Entry<Slot, Integer> entry : boundGlobalIndexByElement.entrySet()) {
            Slot boundElement = entry.getKey();
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

        for (ArrayList<Map.Entry<Slot, Integer>> entries : groupedEntries.values()) {
            for (Map.Entry<Slot, Integer> entry : entries) {
                Slot boundElement = entry.getKey();
                int globalSlotIndex = entry.getValue();
                if (boundElement == null) continue;
                if (globalSlotIndex < 0 || globalSlotIndex >= menu.slots.size()) continue;

                net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(globalSlotIndex);
                SlotVisual visual = resolveSlotVisual(menuSlot);
                boolean hiddenByMode = visual.disabled;
                boolean hidden = hiddenByMode;

                boolean runtimeStyleChanged = applyBoundSlotRuntimeStyle(boundElement, hiddenByMode);
                if (runtimeStyleChanged) {
                    Drawer.flushUpdates(linkedDocument);
                }

                if (!hidden) {
                    boolean hiddenByStyle = !boundElement.isVisible || "none".equals(boundElement.getComputedStyle().display);
                    if (hiddenByStyle) {
                        hidden = true;
                        runtimeStyleChanged = applyBoundSlotRuntimeStyle(boundElement, true);
                        if (runtimeStyleChanged) {
                            Drawer.flushUpdates(linkedDocument);
                        }
                    }
                }

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

    private int resolveBoundSlotPixelSize(Slot boundElement, net.minecraft.world.inventory.Slot menuSlot) {
        if (boundElement == null) return 16;

        int fromStyle = Math.max(
                com.sighs.apricityui.style.Size.parse(boundElement.getComputedStyle().width),
                com.sighs.apricityui.style.Size.parse(boundElement.getComputedStyle().height)
        );
        if (fromStyle > 0) return fromStyle;

        Size box = Size.box(boundElement);
        int fromBox = (int) Math.round(Math.max(box.width(), box.height()));
        if (fromBox > 0) return fromBox;

        if (menuSlot instanceof ApricityContainerMenu.UiSlot uiSlot) {
            return Math.max(1, uiSlot.getUiSlotSize());
        }

        Container ownerContainer = boundContainerByElement.get(boundElement);
        if (ownerContainer == null) {
            ownerContainer = boundElement.findAncestor(Container.class);
            if (ownerContainer != null) {
                boundContainerByElement.put(boundElement, ownerContainer);
            }
        }
        if (ownerContainer != null) {
            return Math.max(1, ownerContainer.resolveSlotSizePx(16));
        }
        return 16;
    }

    private boolean ensureRecipePreviewSlots() {
        if (linkedDocument == null) return false;

        boolean changed = false;
        List<Element> snapshot = new ArrayList<>(linkedDocument.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Recipe recipe)) continue;
            changed |= recipe.ensurePreviewSlots();
        }
        return changed;
    }

    private void drawBoundSlotItems(GuiGraphics guiGraphics) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        net.minecraft.world.inventory.Slot clicked = accessor.apricityui$getClickedSlot();
        ItemStack draggingItem = accessor.apricityui$getDraggingItem();
        boolean splitting = accessor.apricityui$isSplittingStack();
        Set<net.minecraft.world.inventory.Slot> quickCraftSlots = accessor.apricityui$getQuickCraftSlots();
        boolean quickCrafting = accessor.apricityui$isQuickCrafting();
        int quickCraftingType = accessor.apricityui$getQuickCraftingType();
        ItemStack carried = menu.getCarried();

        int quickCraftBasePlaceCount = 0;
        if (quickCrafting && !carried.isEmpty() && quickCraftSlots != null && quickCraftSlots.size() > 1) {
            quickCraftBasePlaceCount = AbstractContainerMenu.getQuickCraftPlaceCount(quickCraftSlots, quickCraftingType, carried);
        }

        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            SlotVisual visual = resolveSlotVisual(slot);
            if (visual.hidden || visual.disabled || !visual.renderItem) continue;
            if (slot == null || !slot.isActive()) continue;

            ItemStack renderStack = slot.getItem();
            String overlayText = null;
            boolean drawQuickCraftGhost = false;

            if (slot == clicked && !draggingItem.isEmpty() && splitting && !renderStack.isEmpty()) {
                renderStack = renderStack.copyWithCount(renderStack.getCount() / 2);
            } else if (quickCrafting && quickCraftSlots != null && quickCraftSlots.contains(slot) && !carried.isEmpty()) {
                if (quickCraftSlots.size() <= 1) continue;
                if (AbstractContainerMenu.canItemQuickReplace(slot, carried, true) && menu.canDragTo(slot)) {
                    drawQuickCraftGhost = true;
                    int maxStackSize = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
                    int existingCount = slot.getItem().isEmpty() ? 0 : slot.getItem().getCount();
                    int placeCount = quickCraftBasePlaceCount + existingCount;
                    if (placeCount > maxStackSize) {
                        placeCount = maxStackSize;
                        overlayText = ChatFormatting.YELLOW + String.valueOf(maxStackSize);
                    }
                    renderStack = carried.copyWithCount(placeCount);
                }
            }

            if (renderStack.isEmpty()) continue;

            int padding = clampPadding(visual.slotSize, visual.padding);
            int renderAreaSize = Math.max(1, visual.slotSize - padding * 2);
            int drawX = leftPos + slot.x + padding + (int) Math.round((renderAreaSize - 16) / 2.0);
            int drawY = topPos + slot.y + padding + (int) Math.round((renderAreaSize - 16) / 2.0);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0D, 0.0D, 100.0D + visual.zIndex);
            if (drawQuickCraftGhost) {
                int ghostSize = Math.max(1, Math.round(16.0F * visual.iconScale));
                int ghostX = Math.round(drawX + 8.0F - ghostSize / 2.0F);
                int ghostY = Math.round(drawY + 8.0F - ghostSize / 2.0F);
                guiGraphics.fill(ghostX, ghostY, ghostX + ghostSize, ghostY + ghostSize, QUICK_CRAFT_GHOST_COLOR);
            }
            applyItemScaleTransform(guiGraphics, drawX, drawY, visual.iconScale);
            guiGraphics.renderItem(renderStack, drawX, drawY, slot.x + slot.y * imageWidth);
            guiGraphics.renderItemDecorations(font, renderStack, drawX, drawY, overlayText);
            guiGraphics.pose().popPose();
        }
    }

    private void drawVirtualSlotItems(GuiGraphics guiGraphics) {
        if (linkedDocument == null) return;

        for (Slot slot : virtualSlots) {
            if (slot == null) continue;
            if (!slot.isVisible) continue;
            if ("none".equals(slot.getComputedStyle().display)) continue;
            if (!slot.shouldRenderItem()) continue;

            Rect rect = Rect.of(slot);
            Position body = rect.getBodyRectPosition();
            Size bodySize = rect.getBodyRectSize();
            int slotWidth = Math.max(1, (int) Math.round(bodySize.width()));
            int slotHeight = Math.max(1, (int) Math.round(bodySize.height()));
            int padding = clampPadding(Math.min(slotWidth, slotHeight), slot.resolveItemPadding(0));
            int renderWidth = Math.max(1, slotWidth - padding * 2);
            int renderHeight = Math.max(1, slotHeight - padding * 2);
            int drawX = (int) Math.round(body.x + padding + (renderWidth - 16) / 2.0);
            int drawY = (int) Math.round(body.y + padding + (renderHeight - 16) / 2.0);
            ItemStack stack = slot.getMcSlot().getItem();
            if (stack.isEmpty()) continue;

            float iconScale = Math.max(0.01F, slot.resolveIconScale(1.0F));
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0D, 0.0D, 100.0D + slot.resolveZIndex(0));
            applyItemScaleTransform(guiGraphics, drawX, drawY, iconScale);
            guiGraphics.renderItem(stack, drawX, drawY);
            guiGraphics.renderItemDecorations(font, stack, drawX, drawY);
            guiGraphics.pose().popPose();
        }
    }

    private void drawSlotHoverTooltipByElement(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (linkedDocument == null) return;

        List<Element> elements = linkedDocument.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (!(element instanceof MinecraftElement minecraftElement)) continue;
            if (!minecraftElement.isHover) continue;

            ItemStack stack = minecraftElement.getTooltipStack();
            if (stack.isEmpty()) continue;
            minecraftElement.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        if (hoveredSlot != null && hoveredSlot.isActive() && isSlotPointerInteractable(hoveredSlot)) {
            ItemStack stack = hoveredSlot.getItem();
            if (!stack.isEmpty()) {
                guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
                return;
            }
        }

        int slotIndex = findSlotIndexAt(mouseX, mouseY);
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;

        net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(slotIndex);
        if (!menuSlot.isActive()) return;
        ItemStack stack = menuSlot.getItem();
        if (stack.isEmpty()) return;

        guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
    }

    private void drawDevToolsOverlay(GuiGraphics guiGraphics) {
        var devToolsDocuments = Document.get(DEVTOOLS_PATH);
        if (devToolsDocuments.isEmpty()) return;

        Document devToolsDocument = devToolsDocuments.get(0);
        if (devToolsDocument == null || devToolsDocument.body == null) return;
        if (devToolsDocument == linkedDocument) return;
        Base.drawDocument(guiGraphics.pose(), devToolsDocument);
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
        // Ensure the native cursor is restored when the Apricity screen closes.
        Cursor.resetToDefault();
        super.onClose();
    }

    @Override
    public void removed() {
        slotsBound = false;
        slotSyncDirty = true;
        boundSlots.clear();
        virtualSlots.clear();
        boundGlobalIndexByElement.clear();
        boundContainerByElement.clear();
        super.removed();
    }

    private SlotVisual resolveSlotVisual(net.minecraft.world.inventory.Slot slot) {
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

    private int resolveSlotSize(net.minecraft.world.inventory.Slot slot) {
        if (slot instanceof ApricityContainerMenu.UiSlot uiSlot) {
            return Math.max(1, uiSlot.getUiSlotSize());
        }
        return 16;
    }

    private boolean applyBoundSlotRuntimeStyle(
            Slot slotElement,
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

    private void setMenuSlotPosition(net.minecraft.world.inventory.Slot slot, int x, int y) {
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

    private static void applyItemScaleTransform(GuiGraphics guiGraphics, int drawX, int drawY, float iconScale) {
        if (Math.abs(iconScale - 1.0F) <= ICON_SCALE_EPSILON) return;
        float centerX = drawX + 8.0F;
        float centerY = drawY + 8.0F;
        guiGraphics.pose().translate(centerX, centerY, 0.0D);
        guiGraphics.pose().scale(iconScale, iconScale, 1.0F);
        guiGraphics.pose().translate(-centerX, -centerY, 0.0D);
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
