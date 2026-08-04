package com.sighs.apricityui.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.screen.SlotDataBinder;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FrameTimingHud;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.layout.Size;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.world.ItemRender;

public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> implements AuiLinkedScreen {
    private static final int QUICK_CRAFT_GHOST_COLOR = -2130706433;
    private static final float ICON_SCALE_EPSILON = 0.0001F;

    private Document linkedDocument;
    private SlotDataBinder slotBinder;

    public ApricityContainerScreen(ApricityContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title,
                Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledWidth()),
                Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight()));
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

    /** Draws the linked document and its slot items (was renderBg). */
    private void drawDocumentAndSlots(GuiGraphicsExtractor guiGraphics) {
        if (linkedDocument == null) return;

        ApricityViewport viewport = linkedDocument.getViewport();
        Client.setupGuiProjection();
        PoseStack pose = new PoseStack();
        pose.pushPose();
        Mask.pushScissorScale(viewport.scissorScale());
        try {
            pose.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
            Base.drawScreenDocument(pose, linkedDocument);
        } finally {
            Mask.popScissorScale();
            pose.popPose();
        }
        AuiServices.render().flushSharedBuffers();
        Mask.pushScissorScale(viewport.scissorScale());
        try {
            drawMenuSlotItems(guiGraphics);
        } finally {
            Mask.popScissorScale();
        }
        PoseStack itemPose = new PoseStack();
        itemPose.pushPose();
        Mask.pushScissorScale(viewport.scissorScale());
        try {
            itemPose.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
            drawDisplaySlotItems(guiGraphics);
        } finally {
            Mask.popScissorScale();
            itemPose.popPose();
        }
    }

    @Override
    public void extractRenderState(@Nonnull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Keep the DOM-defined slot positions in sync; the document itself is
        // drawn by the Picture-in-Picture overlay submitted from Client.drawScreen
        // during the vanilla GuiRenderer's render phase.
        if (linkedDocument != null && slotBinder != null) {
            if (slotBinder.shouldRebindSlotsFromDom(linkedDocument)) {
                slotBinder.bindSlotsFromDocument(linkedDocument);
                slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, true);
            } else {
                slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, false);
            }
        }

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        drawSlotHoverTooltipByElement(guiGraphics, mouseX, mouseY);
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    @Override
    protected void extractLabels(@Nonnull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
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
        int scanCode = event.scancode();
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

    private void drawMenuSlotItems(GuiGraphicsExtractor guiGraphics) {
        if (slotBinder == null) return;

        com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor accessor =
                (com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor) this;
        net.minecraft.world.inventory.Slot clicked = accessor.apricityui$getClickedSlot();
        ItemStack draggingItem = accessor.apricityui$getDraggingItem();
        boolean splitting = accessor.apricityui$isSplittingStack();
        Set<net.minecraft.world.inventory.Slot> quickCraftSlots = accessor.apricityui$getQuickCraftSlots();
        boolean quickCrafting = accessor.apricityui$isQuickCrafting();
        int quickCraftingType = accessor.apricityui$getQuickCraftingType();
        ItemStack carried = menu.getCarried();

        int quickCraftBasePlaceCount = 0;
        if (quickCrafting && !carried.isEmpty() && quickCraftSlots != null && quickCraftSlots.size() > 1) {
            quickCraftBasePlaceCount = AbstractContainerMenu.getQuickCraftPlaceCount(quickCraftSlots.size(), quickCraftingType, carried);
        }

        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            SlotDataBinder.SlotVisual visual = slotBinder.resolveSlotVisual(slot);
            if (visual.hidden() || visual.disabled() || !visual.renderItem()) continue;
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

            int drawX = leftPos + slot.x + (int) Math.round((visual.slotSize() - 16) / 2.0);
            int drawY = topPos + slot.y + (int) Math.round((visual.slotSize() - 16) / 2.0);

            com.sighs.apricityui.element.Slot slotElement = slotBinder.getBoundElement(slot);
            final ItemStack finalRenderStack = renderStack;
            final String finalOverlayText = overlayText;
            final boolean finalDrawQuickCraftGhost = drawQuickCraftGhost;
            final int finalDrawX = drawX;
            final int finalDrawY = drawY;
            Runnable drawAction = () -> {
                PoseStack pose = new PoseStack();
                pose.pushPose();
                pose.translate(0.0D, 0.0D, 100.0D + visual.zIndex());
                if (finalDrawQuickCraftGhost) {
                    int ghostSize = Math.max(1, Math.round(16.0F * visual.iconScale()));
                    int ghostX = Math.round(finalDrawX + 8.0F - ghostSize / 2.0F);
                    int ghostY = Math.round(finalDrawY + 8.0F - ghostSize / 2.0F);
                    guiGraphics.fill(ghostX, ghostY, ghostX + ghostSize, ghostY + ghostSize, QUICK_CRAFT_GHOST_COLOR);
                }
                applyItemScaleTransform(pose, finalDrawX, finalDrawY, visual.iconScale());
                guiGraphics.item(finalRenderStack, finalDrawX, finalDrawY, slot.x + slot.y * imageWidth);
                guiGraphics.itemDecorations(font, finalRenderStack, finalDrawX, finalDrawY, finalOverlayText);
                pose.popPose();
            };
            if (slotElement == null) {
                drawAction.run();
            } else {
                ItemRender.withInheritedClip(slotElement, drawAction);
            }
        }
    }

    private void drawDisplaySlotItems(GuiGraphicsExtractor guiGraphics) {
        if (slotBinder == null) return;
        ItemRender.renderDisplaySlotItems(guiGraphics, new ArrayList<>(slotBinder.getDisplaySlots()));
    }

    private void drawSlotHoverTooltipByElement(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
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
                guiGraphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                return;
            }
        }

        int slotIndex = findSlotIndexAt(mouseX, mouseY);
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;

        net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(slotIndex);
        if (!menuSlot.isActive()) return;
        ItemStack stack = menuSlot.getItem();
        if (stack.isEmpty()) return;

        guiGraphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
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

    private static void applyItemScaleTransform(PoseStack pose, int drawX, int drawY, float iconScale) {
        if (Math.abs(iconScale - 1.0F) <= ICON_SCALE_EPSILON) return;
        float centerX = drawX + 8.0F;
        float centerY = drawY + 8.0F;
        pose.translate(centerX, centerY, 0.0D);
        pose.scale(iconScale, iconScale, 1.0F);
        pose.translate(-centerX, -centerY, 0.0D);
    }

    private static boolean isControlModifier(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    /** Screen.hasControlDown() was removed in 26.1; check the physical Ctrl keys instead. */
    private static boolean hasControlDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
