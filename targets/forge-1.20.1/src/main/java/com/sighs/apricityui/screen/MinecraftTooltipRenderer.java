package com.sighs.apricityui.screen;

import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.element.Stack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.style.Interaction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class MinecraftTooltipRenderer {
    private MinecraftTooltipRenderer() {
    }

    static boolean renderDocumentTooltip(GuiGraphics guiGraphics, Document document, int mouseX, int mouseY) {
        if (document == null) return false;

        Position screenMouse = new Position(mouseX, mouseY);
        if (!document.isManuallyRendered()
                && DocumentLayerOrder.hasPersistentScreenDocumentAt(Document.getAll(), document, screenMouse)) {
            return false;
        }

        Position documentMouse = document.screenToDocumentPosition(screenMouse);
        List<Element> elements = document.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (!(element instanceof Slot slot)
                    || !Interaction.isDisplayed(slot)
                    || !slot.isVisible
                    || !slot.canShowItemTooltip()
                    || !slot.containsSlotPoint(documentMouse.x, documentMouse.y)) {
                continue;
            }

            Stack stackElement = SlotContentRules.getDisplayStack(slot);
            ItemStack stack = stackElement == null ? ItemStack.EMPTY : stackElement.getTooltipStack();
            if (stack.isEmpty()) continue;
            stackElement.renderTooltip(guiGraphics, mouseX, mouseY);
            return true;
        }

        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (!(element instanceof MinecraftElement minecraftElement)
                    || element instanceof Slot
                    || !minecraftElement.isHover
                    || minecraftElement.getTooltipStack().isEmpty()) {
                continue;
            }
            minecraftElement.renderTooltip(guiGraphics, mouseX, mouseY);
            return true;
        }
        return false;
    }
}
