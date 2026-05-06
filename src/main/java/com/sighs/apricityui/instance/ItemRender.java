package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.render.AABB;
import com.sighs.apricityui.render.Mask;
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
        renderDisplaySlotItems(guiGraphics, document.getElements());
    }

    public static void renderDocumentSlotItems(GuiGraphicsExtractor guiGraphics, Document document) {
        renderDocumentUnboundSlotItems(guiGraphics, document);
    }

    public static void renderUnboundSlotItems(GuiGraphicsExtractor guiGraphics, Iterable<? extends Element> elements) {
        renderDisplaySlotItems(guiGraphics, elements);
    }

    public static void renderDisplaySlotItems(GuiGraphicsExtractor guiGraphics, Iterable<? extends Element> elements) {
        if (guiGraphics == null || elements == null) return;

        Font font = Minecraft.getInstance().font;
        for (Element element : elements) {
            if (!(element instanceof Slot slot)) continue;

            if (slot.hasView()) continue;

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
            withInheritedClip(slot, () -> {
                guiGraphics.nextStratum();
                guiGraphics.pose().pushMatrix();
                applyItemScaleTransform(guiGraphics, drawX, drawY, iconScale);
                guiGraphics.item(stack, drawX, drawY);
                guiGraphics.itemDecorations(font, stack, drawX, drawY);
                guiGraphics.pose().popMatrix();
            });
        }
    }

    public static void withInheritedClip(Slot slot, Runnable drawAction) {
        if (slot == null || drawAction == null) return;

        AABB inheritedClip = resolveInheritedClip(slot);
        if (inheritedClip == null) {
            drawAction.run();
            return;
        }

        AABB previousScissor = Mask.getCurrentScissor();
        AABB effectiveClip = previousScissor == null ? inheritedClip : previousScissor.intersection(inheritedClip);
        if (effectiveClip == null || !effectiveClip.isValid()) return;

        Mask.restoreScissor(effectiveClip);
        try {
            drawAction.run();
        } finally {
            Mask.restoreScissor(previousScissor);
        }
    }

    private static AABB resolveInheritedClip(Slot slot) {
        AABB resolved = null;
        for (Element current = slot; current != null; current = current.parentElement) {
            if (!Style.clipsOverflow(current.getComputedStyle())) continue;
            AABB currentClip = toBodyClip(current);
            resolved = resolved == null ? currentClip : resolved.intersection(currentClip);
            if (!resolved.isValid()) return resolved;
        }
        return resolved;
    }

    private static AABB toBodyClip(Element element) {
        Rect rect = Rect.of(element);
        Position body = rect.getBodyRectPosition();
        Size bodySize = rect.getBodyRectSize();
        return new AABB((float) body.x, (float) body.y, Math.max(0.0F, (float) bodySize.width()), Math.max(0.0F, (float) bodySize.height()));
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
