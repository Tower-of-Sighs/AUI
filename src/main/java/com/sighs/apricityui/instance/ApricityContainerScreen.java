package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.container.layout.MenuLayoutSpec;
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
    private static final String PLAYER_HEADER_RUNTIME_STYLE = "grid-column:1 / span 9;";

    private final Document linkedDocument;
    private final ArrayList<Slot> boundSlots = new ArrayList<>();
    private final ArrayList<Slot> unboundSlots = new ArrayList<>();
    private final HashMap<Slot, Integer> boundGlobalIndexByElement = new HashMap<>();
    private final HashMap<Slot, Container> boundContainerByElement = new HashMap<>();
    private final IdentityHashMap<net.minecraft.world.inventory.Slot, Slot> boundElementByMenuSlot = new IdentityHashMap<>();
    private final IdentityHashMap<Element, String> playerHeaderBaseInlineStyleByElement = new IdentityHashMap<>();
    private final IdentityHashMap<Element, String> playerHeaderLastInlineStyleByElement = new IdentityHashMap<>();
    private final IdentityHashMap<Element, Boolean> playerHeaderExplicitGridColumnByElement = new IdentityHashMap<>();
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
        imageWidth = width;
        imageHeight = height;
        super.init();
        if (linkedDocument == null) return;

        bindSlotsFromDocument();
        syncAllSlotPositions(true);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        if (linkedDocument == null) return;

        Base.drawDocument(guiGraphics.pose(), linkedDocument);
        drawBoundSlotItems(guiGraphics);
        drawUnboundSlotItems(guiGraphics);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标题改为容器内节点渲染，不再固定绘制到屏幕左上角。
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (linkedDocument != null) {
            if (shouldRebindSlotsFromDom()) {
                bindSlotsFromDocument();
                syncAllSlotPositions(true);
            } else {
                syncAllSlotPositions(false);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawSlotHoverTooltipByElement(guiGraphics, mouseX, mouseY);
        drawDevToolsOverlay(guiGraphics);
        Cursor.drawPseudoCursor(guiGraphics.pose());
    }

    private void bindSlotsFromDocument() {
        if (linkedDocument == null) return;

        boundSlots.clear();
        unboundSlots.clear();
        boundGlobalIndexByElement.clear();
        boundContainerByElement.clear();
        boundElementByMenuSlot.clear();

        LinkedHashMap<String, Container> containerById = resolveTopLevelContainerMapping();
        normalizePlayerContainerHeaderLayout(containerById);

        IdentityHashMap<Container, String> containerIdByElement = new IdentityHashMap<>();
        for (Map.Entry<String, Container> entry : containerById.entrySet()) {
            containerIdByElement.put(entry.getValue(), entry.getKey());
        }

        HashMap<String, Integer> nextImplicitLocalIndex = new HashMap<>();
        HashSet<Integer> usedGlobalSlots = new HashSet<>();

        List<Element> snapshot = new ArrayList<>(linkedDocument.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Slot slot)) continue;
            if (slot.findAncestor(Recipe.class) != null) {
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

            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(globalSlotIndex);
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
            if (element instanceof Slot) count++;
        }
        return count;
    }

    private LinkedHashMap<String, Container> resolveTopLevelContainerMapping() {
        LinkedHashMap<String, Container> result = new LinkedHashMap<>();
        if (linkedDocument == null) return result;

        LinkedHashMap<String, Container> domContainerById = new LinkedHashMap<>();
        for (Element element : linkedDocument.getElements()) {
            if (!(element instanceof Container container)) continue;
            String normalizedId = normalizeContainerId(container.getAttribute("id"));
            if (normalizedId == null) continue;
            domContainerById.putIfAbsent(normalizedId, container);
        }

        List<String> containerIds = menu.getLayoutSpec().containerIds();
        for (String containerId : containerIds) {
            String normalizedId = normalizeContainerId(containerId);
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
    private void normalizePlayerContainerHeaderLayout(Map<String, Container> containerById) {
        if (containerById == null || containerById.isEmpty()) return;

        for (Map.Entry<String, Container> entry : containerById.entrySet()) {
            Container container = entry.getValue();
            if (container == null) continue;
            MenuLayoutSpec.ContainerLayout layout = menu.getLayoutSpec().findContainer(entry.getKey());
            if (layout == null || layout.bindType() == null || !"player".equals(layout.bindType().id())) continue;

            boolean beforeFirstSlot = true;
            for (Element child : new ArrayList<>(container.children)) {
                if (child == null) continue;

                boolean shouldApply = false;
                if (beforeFirstSlot && !(child instanceof Slot)) {
                    shouldApply = !hasExplicitGridColumn(child);
                }

                applyPlayerHeaderRuntimeStyle(child, shouldApply);
                if (child instanceof Slot) {
                    beforeFirstSlot = false;
                }
            }
        }
    }

    private boolean hasExplicitGridColumn(Element element) {
        if (element == null) return false;

        Boolean cachedBoolean = playerHeaderExplicitGridColumnByElement.get(element);
        if (cachedBoolean != null) {
            return cachedBoolean;
        }

        boolean explicit = false;
        String inlineStyle = element.getAttribute("style");
        if (containsGridColumnDeclaration(inlineStyle)) {
            explicit = true;
        } else {
            String computedGridColumn = element.getComputedStyle().gridColumn;
            explicit = computedGridColumn != null
                    && !computedGridColumn.isBlank()
                    && !"auto".equals(computedGridColumn)
                    && !"unset".equals(computedGridColumn);
        }
        playerHeaderExplicitGridColumnByElement.put(element, explicit);
        return explicit;
    }

    private static boolean containsGridColumnDeclaration(String styleText) {
        if (styleText == null || styleText.isBlank()) return false;
        return styleText.toLowerCase(Locale.ROOT).contains("grid-column");
    }

    private boolean applyPlayerHeaderRuntimeStyle(Element element, boolean applyHeaderSpan) {
        if (element == null) return false;

        String baseStyle = playerHeaderBaseInlineStyleByElement.get(element);
        if (baseStyle == null) {
            if (!applyHeaderSpan) return false;
            String raw = element.getAttribute("style");
            baseStyle = raw == null ? "" : raw;
            playerHeaderBaseInlineStyleByElement.put(element, baseStyle);
        }

        String runtimeStyle = applyHeaderSpan ? PLAYER_HEADER_RUNTIME_STYLE : "";
        String merged = mergeInlineStyle(baseStyle, runtimeStyle);
        String lastApplied = playerHeaderLastInlineStyleByElement.get(element);
        if (Objects.equals(lastApplied, merged)) return false;

        element.setAttribute("style", merged);
        playerHeaderLastInlineStyleByElement.put(element, merged);
        return true;
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
                boolean hidden = visual.hidden || !boundElement.isVisible || "none".equals(boundElement.getComputedStyle().display);

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

    private boolean isBoundSlotPointerEnabledByCss(net.minecraft.world.inventory.Slot menuSlot) {
        Slot boundElement = boundElementByMenuSlot.get(menuSlot);
        if (boundElement == null) return true;
        if (boundElement.findAncestor(Recipe.class) != null) return false;
        if (!boundElement.isVisible) return false;
        if ("none".equals(boundElement.getComputedStyle().display)) return false;
        if (!boundElement.isPointerEnabled) return false;
        return boundElement.shouldAcceptPointer();
    }

    private static String normalizeContainerId(String containerId) {
        if (containerId == null) return null;
        String normalized = containerId.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
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

    private void drawUnboundSlotItems(GuiGraphics guiGraphics) {
        if (linkedDocument == null) return;

        for (Slot slot : unboundSlots) {
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
            ItemStack stack = slot.resolveDisplayStack();
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
        playerHeaderBaseInlineStyleByElement.clear();
        playerHeaderLastInlineStyleByElement.clear();
        playerHeaderExplicitGridColumnByElement.clear();
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
