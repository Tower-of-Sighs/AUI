package com.sighs.apricityui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.client.gui.pip.ApricityUiPipRenderState;
import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * Container screen hosting an AUI document (26.3 port).
 *
 * <p>The document itself is not drawn here: the fullscreen Picture-in-Picture
 * overlay rasterises every live document, exactly like {@link ApricityScreen}.
 * Ordering is the only subtlety — submission order is the z order of the GUI
 * render state, so this screen submits the UI PIP state itself between the
 * vanilla background and its extractor-drawn slot items, and
 * {@code Client.drawScreen} skips its own UI submission for
 * {@link AuiLinkedScreen}s. Vanilla slot rendering is cancelled by
 * {@code AbstractContainerScreenMixin}; menu slot items, display slot items
 * and tooltips are all extracted here.</p>
 */
public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> implements AuiLinkedScreen {
    /** {@code SDL_SCANCODE_KP_MINUS}; 26.3's {@link InputConstants} has no named constant for it. */
    private static final int KEY_NUMPAD_SUBTRACT = 86;

    private Document linkedDocument;
    private SlotDataBinder slotBinder;
    private ApricityUiPipRenderState.FloatingItemBatch floatingItems =
            new ApricityUiPipRenderState.FloatingItemBatch();

    /**
     * AUI-local replacement for the {@code clickedSlot}/{@code isSplittingStack}
     * fields 26.3 deleted from {@link AbstractContainerScreen}: the slot the
     * carried stack was picked up from, and whether that pickup was a right-click
     * split. Tracked in {@link #slotClicked} because vanilla's only remaining copy
     * of that information ({@code lastClickSlot}) is private.
     */
    private Slot dragSourceSlot;
    private boolean dragSourceSplitting;

    /**
     * AUI-local copy of 26.3's private {@code quickCraftingType} (0 = left-drag
     * spread, 1 = right-drag one per slot, 2 = creative clone). Mirrored from the
     * click that starts the drag, since {@code getQuickCraftPlaceCount} needs it to
     * render the quick-craft preview.
     */
    private int quickCraftType;

    public ApricityContainerScreen(ApricityContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title,
                Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledWidth()),
                Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight()));
    }

    private static boolean isControlModifier(int modifiers) {
        // SDL_KMOD_CTRL (left|right) since 26.3; the old GLFW value (2) is now MOD_SHIFT's bit.
        return (modifiers & InputConstants.MOD_CONTROL) != 0;
    }

    /**
     * Screen.hasControlDown() was removed in 26.1; check the physical Ctrl keys instead.
     * 26.3 reads SDL keyboard state by scancode through {@link InputConstants#isKeyDown(int)}.
     */
    private static boolean hasControlDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }

    public Document getLinkedDocument() {
        return linkedDocument;
    }

    public int getGuiLeft() {
        return leftPos;
    }

    public int getGuiTop() {
        return topPos;
    }

    public int findSlotIndexAt(double mouseX, double mouseY) {
        if (slotBinder == null) return -1;
        return slotBinder.findSlotIndexAt(mouseX, mouseY, leftPos, topPos);
    }

    public boolean isSlotPointerInteractable(Slot slot) {
        if (slotBinder == null) return false;
        return slotBinder.isSlotPointerInteractable(slot);
    }

    public boolean isSlotBound(Slot slot) {
        return slotBinder != null && slotBinder.isSlotBound(slot);
    }

    public boolean isBoundElementHovered(Slot slot, double mouseX, double mouseY) {
        return slotBinder != null && slotBinder.isBoundElementHovered(slot, mouseX, mouseY);
    }

    public boolean pruneInvalidQuickCraftSlot(Slot slot) {
        Set<Slot> quickCraftSlots = this.quickCraftSlots;
        if (!isQuickCrafting
                || quickCraftSlots == null
                || quickCraftSlots.size() <= 1
                || !quickCraftSlots.contains(slot)) {
            return false;
        }

        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()
                || (net.minecraft.world.inventory.AbstractContainerMenu.canItemQuickReplace(slot, carried, true)
                && menu.canDragTo(slot))) {
            return false;
        }

        quickCraftSlots.remove(slot);
        return true;
    }

    public void captureFloatingItem(ItemStack stack, int x, int y, String overlayText) {
        // 26.1 shifted the decorations up by 8 while a stack was being dragged from a
        // slot (vanilla's touch-drag path). 26.3 removed that machinery and draws the
        // carried stack's decorations unshifted, so the offset is always 0.
        floatingItems.add(stack, x, y, overlayText, 0);
    }

    /**
     * Records which slot the carried stack was picked up from, replacing the
     * {@code clickedSlot}/{@code draggingItem}/{@code isSplittingStack} fields that
     * 26.3 deleted. Vanilla applies the click to the menu synchronously, so this only
     * observes the resulting state: an empty hand that gained a stack is a pickup
     * (a placement or a swap into a full hand leaves the hand non-empty and is not a
     * new drag source).
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        int carriedBefore = menu.getCarried().getCount();
        super.slotClicked(slot, slotId, button, input);
        int carriedAfter = menu.getCarried().getCount();
        if (carriedAfter <= 0 || slot == null) {
            clearDragState();
        } else if (carriedBefore == 0) {
            dragSourceSlot = slot;
            // Right-click splits the stack, so the slot keeps the remainder and only a
            // whole pickup empties it (26.1's isSplittingStack).
            dragSourceSplitting = button == 1;
        }
    }

    private void clearDragState() {
        dragSourceSlot = null;
        dragSourceSplitting = false;
    }

    @Override
    protected void init() {
        super.init();

        // Screen.resize() calls init(); keep the live Document so resizing does not
        // rebuild the page. A closed/disposed Document is recreated normally.
        if (linkedDocument == null || !linkedDocument.isActive()) {
            linkedDocument = Document.create(menu.getTemplatePath());
            if (linkedDocument == null) return;
            if (slotBinder != null) {
                slotBinder.clear();
                slotBinder = null;
            }
        }
        if (linkedDocument == null) return;
        if (slotBinder == null) {
            slotBinder = new SlotDataBinder(menu);
            slotBinder.setDisplayStateResolver(this::resolveMenuSlotDisplayState);
            slotBinder.bindSlotsFromDocument(linkedDocument);
        }
        linkedDocument.applyViewport(false);
        slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, true);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (linkedDocument != null) {
            linkedDocument.applyViewport(true);
        }
    }

    @Override
    public void extractRenderState(@Nonnull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        floatingItems = new ApricityUiPipRenderState.FloatingItemBatch();
        // Keep the DOM-defined slot positions in sync (1.21.1 did this at the
        // top of render()).
        if (linkedDocument != null && slotBinder != null) {
            if (slotBinder.shouldRebindSlotsFromDom(linkedDocument)) {
                slotBinder.bindSlotsFromDocument(linkedDocument);
                slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, true);
            } else {
                slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, false);
            }
            slotBinder.syncBoundSlotStates();
            slotBinder.syncBoundSlotHoverStates(mouseX, mouseY);
        }

        // Vanilla contributes only its background and interaction bookkeeping.
        // Slot extraction is cancelled, while floating items are captured by the
        // mixin and appended to the same AUI PIP paint pass below.
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        ApricityGuiLayers.submitUi(guiGraphics, floatingItems);
        drawSlotHoverTooltipByElement(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@Nonnull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    }

    /**
     * Vanilla hovered-slot tooltip is replaced by the element-aware one below.
     */
    @Override
    protected void extractTooltip(@Nonnull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    }

    private SlotDataBinder.SlotItemState resolveMenuSlotDisplayState(Slot slot) {
        if (slot == null || !slot.isActive()) {
            return new SlotDataBinder.SlotItemState(ItemStack.EMPTY, null, false);
        }

        ItemStack renderStack = slot.getItem();
        if (slot == dragSourceSlot && !dragSourceSplitting && !menu.getCarried().isEmpty()) {
            // The whole stack is riding the cursor: 26.1 hid the source slot here
            // (clickedSlot + !isSplittingStack). 26.3 already emptied the slot in the
            // same click, so this keeps the state an explicit early-out instead of
            // letting the quick-craft preview below claim the slot. A split pickup
            // keeps its remainder in place, which is exactly what the menu reports.
            return new SlotDataBinder.SlotItemState(ItemStack.EMPTY, null, false);
        }

        ItemStack carried = menu.getCarried();
        Set<Slot> quickCraftSlots = this.quickCraftSlots;
        if (!isQuickCrafting
                || carried.isEmpty()
                || quickCraftSlots == null
                || !quickCraftSlots.contains(slot)) {
            return new SlotDataBinder.SlotItemState(renderStack, null, false);
        }
        if (quickCraftSlots.size() <= 1) {
            return new SlotDataBinder.SlotItemState(ItemStack.EMPTY, null, false);
        }
        if (!net.minecraft.world.inventory.AbstractContainerMenu.canItemQuickReplace(slot, carried, true)
                || !menu.canDragTo(slot)) {
            return new SlotDataBinder.SlotItemState(renderStack, null, false);
        }

        int baseCount = net.minecraft.world.inventory.AbstractContainerMenu.getQuickCraftPlaceCount(
                quickCraftSlots.size(),
                quickCraftType,
                carried
        );
        int existingCount = renderStack.isEmpty() ? 0 : renderStack.getCount();
        int maxStackSize = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
        int placeCount = baseCount + existingCount;
        String overlayText = null;
        if (placeCount > maxStackSize) {
            placeCount = maxStackSize;
            overlayText = net.minecraft.ChatFormatting.YELLOW + String.valueOf(maxStackSize);
        }
        return new SlotDataBinder.SlotItemState(carried.copyWithCount(placeCount), overlayText, true);
    }

    private void drawSlotHoverTooltipByElement(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (linkedDocument == null) return;

        List<Element> elements = linkedDocument.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (!(element instanceof com.sighs.apricityui.element.Slot slot)
                    || !slot.isHover
                    || !slot.canShowItemTooltip()) {
                continue;
            }

            Item item = SlotContentRules.getDisplayItem(slot);
            ItemStack stack = item == null ? ItemStack.EMPTY : item.getTooltipStack();
            if (stack.isEmpty() || !shouldShowTooltip(stack)) continue;
            item.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (!(element instanceof MinecraftElement minecraftElement)
                    || element instanceof com.sighs.apricityui.element.Slot
                    || !minecraftElement.isHover) {
                continue;
            }

            ItemStack stack = minecraftElement.getTooltipStack();
            if (stack.isEmpty() || !shouldShowTooltip(stack)) continue;
            minecraftElement.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        if (hoveredSlot != null && hoveredSlot.isActive() && isSlotPointerInteractable(hoveredSlot)) {
            ItemStack stack = hoveredSlot.getItem();
            if (!stack.isEmpty() && shouldShowTooltip(stack)) {
                guiGraphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                return;
            }
        }

        int slotIndex = findSlotIndexAt(mouseX, mouseY);
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;

        Slot menuSlot = menu.slots.get(slotIndex);
        if (!menuSlot.isActive()) return;
        ItemStack stack = menuSlot.getItem();
        if (stack.isEmpty() || !shouldShowTooltip(stack)) return;

        guiGraphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
    }

    private boolean shouldShowTooltip(ItemStack stack) {
        return menu.getCarried().isEmpty()
                || stack.getTooltipImage()
                .map(ClientTooltipComponent::create)
                .map(ClientTooltipComponent::showTooltipWithItemInHand)
                .orElse(false);
    }

    /**
     * Mirrors vanilla's quick-craft button → type mapping so the quick-craft preview
     * can be computed even though {@code quickCraftingType} is private in 26.3.
     * Vanilla sets the type inside {@code mouseClicked} when a click with a non-empty
     * hand starts the drag, so reading the state (and the button) afterwards is exact.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        if (isQuickCrafting) {
            quickCraftType = switch (event.button()) {
                case 1 -> 0;    // left drag: spread the stack across the dragged slots
                case 3 -> 1;    // right drag: one item per slot
                default -> 2;   // middle click / creative clone: full stacks
            };
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double delta) {
        if (hasControlDown() && handleViewportZoom(delta > 0)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalScroll, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // 26.3 {@code event.key()} is the SDL scancode, which is what
        // {@link InputConstants#KEY_*} stores.
        int keyCode = event.key();
        if (isControlModifier(event.modifiers())) {
            if (keyCode == InputConstants.KEY_EQUALS || keyCode == InputConstants.KEY_ADD) {
                return handleViewportZoom(true);
            }
            if (keyCode == InputConstants.KEY_MINUS || keyCode == KEY_NUMPAD_SUBTRACT) {
                return handleViewportZoom(false);
            }
            if (keyCode == InputConstants.KEY_0 || keyCode == InputConstants.KEY_NUMPAD0) {
                return resetViewportZoom();
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (linkedDocument == null) {
            Size.clearViewportOverride();
            super.onClose();
            return;
        }

        if (linkedDocument.body != null) {
            Event.triggerSingle(new Event(linkedDocument.body, "unload", false));
        }

        linkedDocument.remove();
        Size.clearViewportOverride();
        Cursor.resetToDefault();
        super.onClose();
    }

    @Override
    public void removed() {
        if (linkedDocument != null) {
            linkedDocument.remove();
            linkedDocument = null;
        }
        if (slotBinder != null) {
            slotBinder.clear();
            slotBinder = null;
        }
        clearDragState();
        Size.clearViewportOverride();
        super.removed();
    }

    public boolean handleViewportZoom(boolean zoomIn) {
        if (linkedDocument == null || !linkedDocument.handleViewportZoom(zoomIn)) return false;
        if (slotBinder != null) {
            slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, true);
        }
        return true;
    }

    public boolean resetViewportZoom() {
        if (linkedDocument == null || !linkedDocument.resetViewportZoom()) return false;
        if (slotBinder != null) {
            slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, true);
        }
        return true;
    }
}
