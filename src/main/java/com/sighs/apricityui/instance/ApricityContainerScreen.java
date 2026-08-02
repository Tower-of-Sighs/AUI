package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.instance.element.Item;
import com.sighs.apricityui.instance.element.MinecraftElement;
import com.sighs.apricityui.instance.render.item.FloatingItemRenderNode;
import com.sighs.apricityui.instance.render.item.ItemRenderContext;
import com.sighs.apricityui.instance.render.item.ItemRenderState;
import com.sighs.apricityui.instance.screen.SlotDataBinder;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FrameTimingHud;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.List;

public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> {
    private Document linkedDocument;
    private SlotDataBinder slotBinder;
    private FloatingItemRenderNode floatingItemRenderNode;

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

    public int findOperableSlotIndexAt(double mouseX, double mouseY) {
        if (slotBinder == null) return -1;
        return slotBinder.findOperableSlotIndexAt(mouseX, mouseY);
    }

    public boolean canOperateSlot(net.minecraft.world.inventory.Slot slot) {
        return slotBinder != null && slotBinder.canOperateSlot(slot);
    }

    public boolean isSlotBound(net.minecraft.world.inventory.Slot slot) {
        return slotBinder != null && slotBinder.isSlotBound(slot);
    }

    public boolean isBoundElementHovered(net.minecraft.world.inventory.Slot slot, double mouseX, double mouseY) {
        return slotBinder != null && slotBinder.isBoundElementHovered(slot, mouseX, mouseY);
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
        if (linkedDocument == null) return;

        ApricityViewport viewport = linkedDocument.getViewport();
        guiGraphics.pose().pushPose();
        Mask.pushScissorScale(viewport.scissorScale());
        try {
            guiGraphics.pose().scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
            Base.drawScreenDocument(
                    guiGraphics.pose(),
                    linkedDocument,
                    floatingItemRenderNode == null ? List.of() : List.of(floatingItemRenderNode)
            );
        } finally {
            Mask.popScissorScale();
            guiGraphics.pose().popPose();
        }
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 不绘制 Minecraft 默认标题；标题如有需要由模板普通 DOM 自行实现。
    }

    @Override
    protected void renderTooltip(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // super.render() 会调用此方法；统一在 AUI 文档绘制后由 drawSlotHoverTooltipByElement 处理。
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        FrameTimingHud.beginFrame();
        try {
            if (linkedDocument != null && slotBinder != null) {
                if (slotBinder.shouldRebindSlotsFromDom(linkedDocument)) {
                    slotBinder.bindSlotsFromDocument(linkedDocument);
                    slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, true);
                } else {
                    slotBinder.syncAllSlotPositions(linkedDocument, leftPos, topPos, false);
                }
                slotBinder.updateBoundItemRenderStates((AbstractContainerScreenAccessor) this, menu.getCarried());
            }

            floatingItemRenderNode = createFloatingItemRenderNode(mouseX, mouseY);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            drawSlotHoverTooltipByElement(guiGraphics, mouseX, mouseY);
            Client.drawPersistentScreenDocuments(guiGraphics, linkedDocument);
            com.sighs.apricityui.dev.resource.ResourcePreviewDialog.draw(guiGraphics.pose());
            Cursor.drawPseudoCursor(guiGraphics);
        } finally {
            floatingItemRenderNode = null;
            FrameTimingHud.endFrame(guiGraphics);
        }
    }

    private FloatingItemRenderNode createFloatingItemRenderNode(int mouseX, int mouseY) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        ItemStack draggingItem = accessor.apricityui$getDraggingItem();
        ItemStack renderStack = draggingItem.isEmpty() ? menu.getCarried() : draggingItem;
        if (renderStack.isEmpty()) return null;

        String overlayText = null;
        if (!draggingItem.isEmpty() && accessor.apricityui$isSplittingStack()) {
            renderStack = renderStack.copyWithCount((int) Math.ceil(renderStack.getCount() / 2.0F));
        } else if (accessor.apricityui$isQuickCrafting()
                && accessor.apricityui$getQuickCraftSlots() != null
                && accessor.apricityui$getQuickCraftSlots().size() > 1) {
            renderStack = renderStack.copyWithCount(accessor.apricityui$getQuickCraftingRemainder());
            if (renderStack.isEmpty()) {
                overlayText = net.minecraft.ChatFormatting.YELLOW + "0";
            }
        }

        Position mouse = linkedDocument == null
                ? new Position(mouseX, mouseY)
                : linkedDocument.screenToDocumentPosition(new Position(mouseX, mouseY));
        int yOffset = draggingItem.isEmpty() ? 8 : 16;
        return new FloatingItemRenderNode(
                new ItemRenderState(
                        renderStack,
                        overlayText,
                        false,
                        false,
                        ItemRenderContext.resolveCooldownProgress(renderStack)
                ),
                (float) mouse.x - 8.0F,
                (float) mouse.y - yOffset
        );
    }

    private void drawSlotHoverTooltipByElement(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (linkedDocument == null || !menu.getCarried().isEmpty()) return;
        Position documentMouse = linkedDocument.screenToDocumentPosition(new Position(mouseX, mouseY));

        List<Element> elements = linkedDocument.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (element instanceof Item item) {
                if (!Interaction.isDisplayed(item)
                        || !item.isVisible
                        || !item.canShowItemTooltip()
                        || !item.containsItemPoint(documentMouse.x, documentMouse.y)) {
                    continue;
                }

                if (item.isMenuBound()) {
                    net.minecraft.world.inventory.Slot menuSlot = slotBinder == null
                            ? null
                            : slotBinder.getBoundMenuSlot(item);
                    if (menuSlot == null || !menuSlot.isActive()) continue;
                    ItemStack stack = menuSlot.getItem();
                    if (stack.isEmpty()) continue;
                    guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
                    return;
                }

                ItemStack stack = item.getTooltipStack();
                if (stack.isEmpty()) continue;
                item.renderTooltip(guiGraphics, mouseX, mouseY);
                return;
            }

            if (!(element instanceof MinecraftElement minecraftElement)) continue;
            if (!minecraftElement.isHover) continue;

            ItemStack stack = minecraftElement.getTooltipStack();
            if (stack.isEmpty()) continue;
            minecraftElement.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        // 没有 DOM 映射的原版菜单槽位仍保留原版 tooltip 行为。
        if (hoveredSlot != null
                && !isSlotBound(hoveredSlot)
                && hoveredSlot.isActive()
                && canOperateSlot(hoveredSlot)) {
            ItemStack stack = hoveredSlot.getItem();
            if (!stack.isEmpty()) {
                guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
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
}
