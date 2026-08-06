package com.sighs.apricityui.screen;

import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.client.gui.pip.ApricityUiPipRenderState;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * Container screen hosting an AUI document (26.1 port).
 *
 * <p>The document itself is not drawn here: the fullscreen Picture-in-Picture
 * overlay rasterises every live document, exactly like {@link ApricityScreen}.
 * Ordering is the only subtlety — submission order is the z order of the 26.1
 * GUI renderer, so this screen submits the UI PIP state itself between the
 * vanilla background and its extractor-drawn slot items, and
 * {@code Client.drawScreen} skips its own UI submission for
 * {@link AuiLinkedScreen}s. Vanilla slot rendering is cancelled by
 * {@code AbstractContainerScreenMixin}; menu slot items, display slot items
 * and tooltips are all extracted here.</p>
 */
public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> implements AuiLinkedScreen {
    private Document linkedDocument;
    private SlotDataBinder slotBinder;
    private ApricityUiPipRenderState.FloatingItemBatch floatingItems =
            new ApricityUiPipRenderState.FloatingItemBatch();

    public ApricityContainerScreen(ApricityContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title,
                Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledWidth()),
                Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight()));
    }

    public Document getLinkedDocument() {
        return linkedDocument;
    }

    @SuppressWarnings("removal")
    public int getGuiLeft() {
        return leftPos;
    }

    @SuppressWarnings("removal")
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

    public void captureFloatingItem(ItemStack stack, int x, int y, String overlayText) {
        int decorationOffsetY = ((AbstractContainerScreenAccessor) this).apricityui$getDraggingItem().isEmpty() ? 0 : -8;
        floatingItems.add(stack, x, y, overlayText, decorationOffsetY);
    }

    @Override
    protected void init() {
        super.init();

        if (linkedDocument != null) {
            linkedDocument.remove();
            linkedDocument = null;
        }
        if (slotBinder != null) {
            slotBinder.clear();
            slotBinder = null;
        }

        linkedDocument = Document.create(menu.getTemplatePath());
        if (linkedDocument == null) return;
        linkedDocument.applyViewport(false);

        slotBinder = new SlotDataBinder(menu);
        slotBinder.setDisplayStateResolver(this::resolveMenuSlotDisplayState);
        slotBinder.bindSlotsFromDocument(linkedDocument);
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

        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        ItemStack renderStack = slot.getItem();
        ItemStack draggingItem = accessor.apricityui$getDraggingItem();
        if (slot == accessor.apricityui$getClickedSlot() && !draggingItem.isEmpty()) {
            if (!accessor.apricityui$isSplittingStack()) {
                return new SlotDataBinder.SlotItemState(ItemStack.EMPTY, null, false);
            }
            if (!renderStack.isEmpty()) {
                renderStack = renderStack.copyWithCount(renderStack.getCount() / 2);
            }
            return new SlotDataBinder.SlotItemState(renderStack, null, false);
        }

        ItemStack carried = menu.getCarried();
        Set<Slot> quickCraftSlots = accessor.apricityui$getQuickCraftSlots();
        if (!accessor.apricityui$isQuickCrafting()
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
                accessor.apricityui$getQuickCraftingType(),
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
            if (!(element instanceof MinecraftElement minecraftElement)) continue;
            if (!minecraftElement.isHover) continue;

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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double delta) {
        if (hasControlDown() && handleViewportZoom(delta > 0)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalScroll, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();
        if (isControlModifier(modifiers)) {
            if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
                return handleViewportZoom(true);
            }
            if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
                return handleViewportZoom(false);
            }
            if (keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0) {
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
        }
        if (slotBinder != null) {
            slotBinder.clear();
        }
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

    private static boolean isControlModifier(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    /**
     * Screen.hasControlDown() was removed in 26.1; check the physical Ctrl keys instead.
     */
    private static boolean hasControlDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
