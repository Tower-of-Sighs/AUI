package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.element.MinecraftElement;
import com.sighs.apricityui.instance.screen.SlotDataBinder;
import com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 带容器交互的 Screen，绑定/同步逻辑委托给 SlotDataBinder。
 */
public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> {
    private static final String DEVTOOLS_PATH = "devtools/index.html";
    private static final int QUICK_CRAFT_GHOST_COLOR = -2130706433;
    private static final float ICON_SCALE_EPSILON = 0.0001F;

    private Document linkedDocument;
    private SlotDataBinder slotBinder;

    public ApricityContainerScreen(ApricityContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
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

        // 窗口 resize 会重新调用 init()，需要先清理旧 Document 避免残留
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

        slotBinder = new SlotDataBinder(menu);
        slotBinder.bindSlotsFromDocument(linkedDocument);
        slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, true);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (linkedDocument == null) return;

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        drawMenuSlotItems(guiGraphics);
        drawDisplaySlotItems(guiGraphics);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // 不绘制 Minecraft 默认标题；标题如有需要由模板普通 DOM 自行实现。
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
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
        drawDevToolsOverlay(guiGraphics);
    }

    private void drawMenuSlotItems(GuiGraphicsExtractor guiGraphics) {
        if (slotBinder == null) return;

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

            com.sighs.apricityui.instance.element.Slot slotElement = slotBinder.getBoundElement(slot);
            final ItemStack finalRenderStack = renderStack;
            final String finalOverlayText = overlayText;
            final boolean finalDrawQuickCraftGhost = drawQuickCraftGhost;
            final int finalDrawX = drawX;
            final int finalDrawY = drawY;
            Runnable drawAction = () -> {
                guiGraphics.pose().pushMatrix();
                guiGraphics.nextStratum();
                if (finalDrawQuickCraftGhost) {
                    int ghostSize = Math.max(1, Math.round(16.0F * visual.iconScale()));
                    int ghostX = Math.round(finalDrawX + 8.0F - ghostSize / 2.0F);
                    int ghostY = Math.round(finalDrawY + 8.0F - ghostSize / 2.0F);
                    guiGraphics.fill(ghostX, ghostY, ghostX + ghostSize, ghostY + ghostSize, QUICK_CRAFT_GHOST_COLOR);
                }
                applyItemScaleTransform(guiGraphics, finalDrawX, finalDrawY, visual.iconScale());
                guiGraphics.item(finalRenderStack, finalDrawX, finalDrawY, slot.x + slot.y * imageWidth);
                guiGraphics.itemDecorations(font, finalRenderStack, finalDrawX, finalDrawY, finalOverlayText);
                guiGraphics.pose().popMatrix();
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

    private void drawDevToolsOverlay(GuiGraphicsExtractor guiGraphics) {
        var devToolsDocuments = Document.get(DEVTOOLS_PATH);
        if (devToolsDocuments.isEmpty()) return;

        Document devToolsDocument = devToolsDocuments.get(0);
        if (devToolsDocument == null || devToolsDocument.body == null) return;
        if (devToolsDocument == linkedDocument) return;
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
        if (linkedDocument != null) {
            linkedDocument.remove();
        }
        if (slotBinder != null) {
            slotBinder.clear();
        }
        super.removed();
    }

    private static void applyItemScaleTransform(GuiGraphicsExtractor guiGraphics, int drawX, int drawY, float iconScale) {
        if (Math.abs(iconScale - 1.0F) <= ICON_SCALE_EPSILON) return;
        float centerX = drawX + 8.0F;
        float centerY = drawY + 8.0F;
        guiGraphics.pose().translate(centerX, centerY);
        guiGraphics.pose().scale(iconScale, iconScale);
        guiGraphics.pose().translate(-centerX, -centerY);
    }
}
