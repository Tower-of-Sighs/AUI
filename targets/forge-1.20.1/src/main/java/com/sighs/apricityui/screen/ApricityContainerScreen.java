package com.sighs.apricityui.screen;

import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.screen.SlotDataBinder;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.render.FrameTimingHud;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.viewport.ApricityViewport;

public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> implements AuiLinkedScreen {
    private Document linkedDocument;
    private SlotDataBinder slotBinder;
    private final List<RenderNode.ItemNode> floatingItemNodes = new ArrayList<>();

    public ApricityContainerScreen(ApricityContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
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
        if (slotBinder == null) return -1;
        return slotBinder.findSlotIndexAt(mouseX, mouseY, leftPos, topPos);
    }

    public boolean isSlotPointerInteractable(net.minecraft.world.inventory.Slot slot) {
        if (slotBinder == null) return false;
        return slotBinder.isSlotPointerInteractable(slot);
    }

    public boolean isSlotBound(net.minecraft.world.inventory.Slot slot) {
        return slotBinder != null && slotBinder.isSlotBound(slot);
    }

    public boolean isBoundElementHovered(net.minecraft.world.inventory.Slot slot, double mouseX, double mouseY) {
        return slotBinder != null && slotBinder.isBoundElementHovered(slot, mouseX, mouseY);
    }

    public boolean pruneInvalidQuickCraftSlot(net.minecraft.world.inventory.Slot slot) {
        if (!isQuickCrafting || quickCraftSlots == null || quickCraftSlots.size() <= 1 || !quickCraftSlots.contains(slot)) {
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

    public void captureFloatingItem(ItemStack stack, int relativeX, int relativeY, String overlayText) {
        if (linkedDocument == null || stack == null) return;
        if (stack.isEmpty() && (overlayText == null || overlayText.isBlank())) return;

        int screenX = relativeX + leftPos;
        int screenY = relativeY + topPos;
        ItemStack snapshot = stack.copy();
        com.sighs.apricityui.layout.Position position = linkedDocument.screenToDocumentPosition(
                new com.sighs.apricityui.layout.Position(screenX, screenY)
        );
        int decorationScreenOffset = draggingItem.isEmpty() ? 0 : -8;
        com.sighs.apricityui.layout.Position decorationPosition = linkedDocument.screenToDocumentPosition(
                new com.sighs.apricityui.layout.Position(screenX, screenY + decorationScreenOffset)
        );
        floatingItemNodes.add(RenderNode.ItemNode.positioned(
                () -> snapshot,
                position.x,
                position.y,
                1.0D,
                232,
                true,
                overlayText,
                decorationPosition.y - position.y,
                false
        ));
    }

    @Override
    protected void init() {
        imageWidth = width;
        imageHeight = height;
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
    public void resize(@Nonnull Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        if (linkedDocument != null) {
            linkedDocument.applyViewport(true);
        }
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    private void drawLinkedDocument(GuiGraphics guiGraphics) {
        if (linkedDocument == null) return;

        ApricityViewport viewport = linkedDocument.getViewport();
        guiGraphics.pose().pushPose();
        Mask.pushScissorScale(viewport.scissorScale());
        try {
            guiGraphics.pose().scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
            Base.drawScreenDocument(guiGraphics.pose(), linkedDocument, floatingItemNodes);
        } finally {
            Mask.popScissorScale();
            guiGraphics.pose().popPose();
        }
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderTooltip(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 统一由 AUI 文档绘制后的 Slot/MinecraftElement 路径处理 tooltip，避免原版重复绘制。
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        floatingItemNodes.clear();
        FrameTimingHud.beginFrame();
        try {
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

            super.render(guiGraphics, mouseX, mouseY, partialTick);
            drawLinkedDocument(guiGraphics);
            drawSlotHoverTooltipByElement(guiGraphics, mouseX, mouseY);
            Client.drawPersistentScreenDocuments(guiGraphics, linkedDocument);
            com.sighs.apricityui.dev.resource.ResourcePreviewDialog.draw(guiGraphics.pose());
            guiGraphics.flush();
            Cursor.drawPseudoCursor(guiGraphics.pose());
            guiGraphics.flush();
        } finally {
            floatingItemNodes.clear();
            FrameTimingHud.endFrame();
            Client.drawFrameTimingHud(guiGraphics);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hasControlDown() && handleViewportZoom(delta > 0)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private SlotDataBinder.SlotItemState resolveMenuSlotDisplayState(net.minecraft.world.inventory.Slot slot) {
        if (slot == null || !slot.isActive()) {
            return new SlotDataBinder.SlotItemState(ItemStack.EMPTY, null, false);
        }

        ItemStack renderStack = slot.getItem();
        if (slot == clickedSlot && !draggingItem.isEmpty()) {
            if (!isSplittingStack) {
                return new SlotDataBinder.SlotItemState(ItemStack.EMPTY, null, false);
            }
            if (!renderStack.isEmpty()) {
                renderStack = renderStack.copyWithCount(renderStack.getCount() / 2);
            }
            return new SlotDataBinder.SlotItemState(renderStack, null, false);
        }

        ItemStack carried = menu.getCarried();
        if (!isQuickCrafting || carried.isEmpty() || quickCraftSlots == null || !quickCraftSlots.contains(slot)) {
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
                quickCraftSlots,
                quickCraftingType,
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

    private void drawSlotHoverTooltipByElement(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (linkedDocument == null || !menu.getCarried().isEmpty()) return;

        Position screenMouse = new Position(mouseX, mouseY);
        if (DocumentLayerOrder.hasPersistentScreenDocumentAt(Document.getAll(), linkedDocument, screenMouse)) {
            return;
        }
        Position documentMouse = linkedDocument.screenToDocumentPosition(screenMouse);
        List<Element> elements = linkedDocument.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (!(element instanceof com.sighs.apricityui.element.Slot slot)) continue;
            if (!Interaction.isDisplayed(slot)
                    || !slot.isVisible
                    || !slot.canShowItemTooltip()
                    || !slot.containsSlotPoint(documentMouse.x, documentMouse.y)) {
                continue;
            }

            Item item = SlotContentRules.getDisplayItem(slot);
            ItemStack stack = item == null ? ItemStack.EMPTY : item.getTooltipStack();
            if (stack.isEmpty()) continue;
            item.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        // 普通 MinecraftElement 仍沿用 DOM hover 状态；Slot 不依赖该状态。
        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (!(element instanceof MinecraftElement minecraftElement)
                    || element instanceof com.sighs.apricityui.element.Slot
                    || !minecraftElement.isHover) {
                continue;
            }

            ItemStack stack = minecraftElement.getTooltipStack();
            if (stack.isEmpty()) continue;
            minecraftElement.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        // 若原版已经算出 hoveredSlot，绑定槽仍从对应 DOM Slot 的统一状态读取。
        if (hoveredSlot != null && hoveredSlot.isActive()) {
            com.sighs.apricityui.element.Slot boundElement =
                    slotBinder == null ? null : slotBinder.getBoundElement(hoveredSlot);
            Item boundItem = slotBinder == null ? null : slotBinder.getBoundItem(hoveredSlot);
            if (boundElement != null && boundElement.canShowItemTooltip() && boundItem != null) {
                ItemStack stack = boundItem.getTooltipStack();
                if (!stack.isEmpty()) {
                    boundItem.renderTooltip(guiGraphics, mouseX, mouseY);
                    return;
                }
            }

            if (boundElement == null) {
                ItemStack stack = hoveredSlot.getItem();
                if (!stack.isEmpty()) {
                    guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }
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
}
