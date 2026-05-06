package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class ItemRender {
    private static final float ICON_SCALE_EPSILON = 0.0001F;

    public static void renderDocumentSlotItems(GuiGraphics guiGraphics, Document document) {
        if (guiGraphics == null || document == null) return;
        renderDisplaySlotItems(guiGraphics, document.getElements());
    }

    public static void renderDisplaySlotItems(GuiGraphics guiGraphics, Iterable<? extends Element> elements) {
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

            // 背景贴图在 Rect/ImageDrawer 路径里会按整数像素栅格落点；
            // 这里也使用同一套取整方式，避免 18x18 slot 在出现 0.5px 等小数坐标时，
            // 物品图标因为 round 而相对背景偏移 1px，导致视觉上不居中。
            int snappedBodyX = (int) body.x;
            int snappedBodyY = (int) body.y;
            int slotWidth = Math.max(1, (int) bodySize.width());
            int slotHeight = Math.max(1, (int) bodySize.height());
            int drawX = snappedBodyX + (int) Math.round((slotWidth - 16) / 2.0);
            int drawY = snappedBodyY + (int) Math.round((slotHeight - 16) / 2.0);

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

    private static void applyItemScaleTransform(GuiGraphics guiGraphics, int drawX, int drawY, float iconScale) {
        if (Math.abs(iconScale - 1.0F) <= ICON_SCALE_EPSILON) return;
        float centerX = drawX + 8.0F;
        float centerY = drawY + 8.0F;
        guiGraphics.pose().translate(centerX, centerY, 0.0D);
        guiGraphics.pose().scale(iconScale, iconScale, 1.0F);
        guiGraphics.pose().translate(-centerX, -centerY, 0.0D);
    }
}
