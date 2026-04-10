package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public final class ItemRender {
    private static final float ICON_SCALE_EPSILON = 0.0001F;

    public static void renderDocumentUnboundSlotItems(GuiGraphicsExtractor guiGraphics, Document document) {
        if (guiGraphics == null || document == null) return;
        renderUnboundSlotItems(guiGraphics, document.getElements());
    }

    public static void renderUnboundSlotItems(GuiGraphicsExtractor guiGraphics, Iterable<? extends Element> elements) {
        if (guiGraphics == null || elements == null) return;

        Font font = Minecraft.getInstance().font;
        for (Element element : elements) {
            if (!(element instanceof Slot slot)) continue;

            if (slot.getMcSlot() != null) continue;

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
            guiGraphics.nextStratum();
            guiGraphics.pose().pushMatrix();
            applyItemScaleTransform(guiGraphics, drawX, drawY, iconScale);
            guiGraphics.item(stack, drawX, drawY);
            guiGraphics.itemDecorations(font, stack, drawX, drawY);
            guiGraphics.pose().popMatrix();
        }
    }

    private static int clampPadding(int slotSize, int padding) {
        int maxPadding = Math.max(0, (Math.max(1, slotSize) - 1) / 2);
        int normalized = Math.max(0, padding);
        return Math.min(normalized, maxPadding);
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
