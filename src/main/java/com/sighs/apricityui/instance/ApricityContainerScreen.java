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
import com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Interaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 带容器交互的 Screen，绑定/同步逻辑委托给 SlotDataBinder。
 */
public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> {
    private static final String DEVTOOLS_PATH = "devtools/index.html";

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
        if (slotBinder == null) return false;
        return slotBinder.canOperateSlot(slot);
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
        slotBinder.syncAllSlotPositions(leftPos, topPos, true);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        if (linkedDocument == null) return;

        Base.drawScreenDocument(
                guiGraphics.pose(),
                linkedDocument,
                floatingItemRenderNode == null ? List.of() : List.of(floatingItemRenderNode)
        );
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
        if (linkedDocument != null && slotBinder != null) {
            if (slotBinder.shouldRebindSlotsFromDom(linkedDocument)) {
                slotBinder.bindSlotsFromDocument(linkedDocument);
                slotBinder.syncAllSlotPositions(leftPos, topPos, true);
            } else {
                slotBinder.syncAllSlotPositions(leftPos, topPos, false);
            }
        }

        if (slotBinder != null) {
            slotBinder.updateBoundItemRenderStates((AbstractContainerScreenAccessor) this, menu.getCarried());
        }
        floatingItemRenderNode = createFloatingItemRenderNode(mouseX, mouseY);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawSlotHoverTooltipByElement(guiGraphics, mouseX, mouseY);
        drawDevToolsOverlay(guiGraphics);
        Cursor.drawPseudoCursor(guiGraphics);
        floatingItemRenderNode = null;
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

        int yOffset = draggingItem.isEmpty() ? 8 : 16;
        return new FloatingItemRenderNode(
                new ItemRenderState(
                        renderStack,
                        overlayText,
                        false,
                        false,
                        ItemRenderContext.resolveCooldownProgress(renderStack)
                ),
                mouseX - 8.0F,
                mouseY - yOffset
        );
    }

    private void drawSlotHoverTooltipByElement(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (linkedDocument == null || !menu.getCarried().isEmpty()) return;

        List<Element> elements = linkedDocument.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (element instanceof Item item) {
                if (!Interaction.isDisplayed(item)
                        || !item.isVisible
                        || !item.canShowItemTooltip()
                        || !item.containsItemPoint(mouseX, mouseY)) {
                    continue;
                }

                if (item.isMenuBound()) {
                    net.minecraft.world.inventory.Slot menuSlot = slotBinder == null ? null : slotBinder.getBoundMenuSlot(item);
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

    private void drawDevToolsOverlay(GuiGraphics guiGraphics) {
        var devToolsDocuments = Document.get(DEVTOOLS_PATH);
        if (devToolsDocuments.isEmpty()) return;

        Document devToolsDocument = devToolsDocuments.get(0);
        if (devToolsDocument == null || devToolsDocument.body == null) return;
        if (devToolsDocument == linkedDocument) return;
        Base.drawScreenDocument(guiGraphics.pose(), devToolsDocument);
    }

    @Override
    public void onClose() {
        if (linkedDocument == null) {
            super.onClose();
            return;
        }

        if (linkedDocument.body != null) {
            Event.triggerSingle(new Event(linkedDocument.body, "unload", false));
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
}
