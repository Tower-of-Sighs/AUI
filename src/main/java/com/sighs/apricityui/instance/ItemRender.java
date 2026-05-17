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

            if (!Style.isVisible(slot)) continue;
            if ("none".equals(slot.getComputedStyle().display)) continue;
            if (!slot.shouldRenderItem()) continue;

            Rect rect = Rect.of(slot);
            Position body = rect.getBodyRectPosition();
            Size bodySize = rect.getBodyRectSize();

            float slotWidth = Math.max(1.0F, (float) bodySize.width());
            float slotHeight = Math.max(1.0F, (float) bodySize.height());
            float drawX = (float) body.x + (slotWidth - 16.0F) / 2.0F;
            float drawY = (float) body.y + (slotHeight - 16.0F) / 2.0F;

            ItemStack stack = slot.resolveDisplayStack();
            if (stack.isEmpty()) continue;

            float iconScale = Math.max(0.01F, slot.resolveIconScale(1.0F));
            withInheritedClip(slot, () -> {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(drawX, drawY, 100.0D + slot.resolveZIndex(0));
                applyItemScaleTransform(guiGraphics, iconScale);
                guiGraphics.renderItem(stack, 0, 0);
                guiGraphics.renderItemDecorations(font, stack, 0, 0);
                guiGraphics.pose().popPose();
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

    private static void applyItemScaleTransform(GuiGraphics guiGraphics, float iconScale) {
        if (Math.abs(iconScale - 1.0F) <= ICON_SCALE_EPSILON) return;
        guiGraphics.pose().translate(8.0F, 8.0F, 0.0D);
        guiGraphics.pose().scale(iconScale, iconScale, 1.0F);
        guiGraphics.pose().translate(-8.0F, -8.0F, 0.0D);
    }
}
