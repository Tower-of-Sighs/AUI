package com.sighs.apricityui.world;

import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.AABB;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Interaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the {@link ItemStack}s of slot elements through the 26.1 GUI
 * extraction phase. Unlike 1.21.1 (immediate {@code GuiGraphics.renderItem}),
 * item states are collected and rasterised by the {@code GuiRenderer} later,
 * so clipping goes through the extractor's GUI-space scissor instead of AUI's
 * device-pixel mask scissor, and z-ordering is simply submission order.
 */
public final class ItemRender {
    private static final float ICON_SCALE_EPSILON = 0.0001F;

    private ItemRender() {
    }

    public static void renderDocumentSlotItems(GuiGraphicsExtractor guiGraphics, Document document) {
        if (guiGraphics == null || document == null) return;
        renderDisplaySlotItems(guiGraphics, document.getElements());
    }

    public static void renderDisplaySlotItems(GuiGraphicsExtractor guiGraphics, Iterable<? extends Element> elements) {
        if (guiGraphics == null || elements == null) return;

        Font font = Minecraft.getInstance().font;
        for (Element element : elements) {
            if (!(element instanceof Slot slot)) continue;

            if (slot.hasView()) continue;

            if (!Interaction.isDisplayed(slot)) continue;
            if (!Interaction.isVisible(slot)) continue;
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
            withInheritedClip(guiGraphics, slot, () -> {
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(drawX, drawY);
                applyItemScaleTransform(guiGraphics, iconScale);
                guiGraphics.item(stack, 0, 0);
                guiGraphics.itemDecorations(font, stack, 0, 0);
                guiGraphics.pose().popMatrix();
            });
        }
    }

    /**
     * Applies the overflow clip inherited from ancestor elements through the
     * extractor's scissor stack. The extractor scissor is GUI-space, so the
     * document-space clip is scaled by the active pose scale. Only uniform
     * scales produced by {@code Client.renderOverlaySlotItems} are expected.
     */
    public static void withInheritedClip(GuiGraphicsExtractor guiGraphics, Slot slot, Runnable drawAction) {
        if (guiGraphics == null) return;
        withInheritedClip(guiGraphics, slot, guiGraphics.pose().m00(), drawAction);
    }

    /**
     * Same as {@link #withInheritedClip(GuiGraphicsExtractor, Slot, Runnable)}
     * but with an explicit document-to-GUI scale, for callers whose draw action
     * runs in unscaled GUI coordinates (container-screen menu slots).
     */
    public static void withInheritedClip(GuiGraphicsExtractor guiGraphics, Slot slot, float docToGuiScale, Runnable drawAction) {
        if (guiGraphics == null || slot == null || drawAction == null) return;

        AABB inheritedClip = resolveInheritedClip(slot);
        if (inheritedClip == null) {
            drawAction.run();
            return;
        }
        if (!inheritedClip.isValid()) return;

        float scale = docToGuiScale;
        int x0 = (int) Math.floor(inheritedClip.x() * scale);
        int y0 = (int) Math.floor(inheritedClip.y() * scale);
        int x1 = (int) Math.ceil((inheritedClip.x() + inheritedClip.width()) * scale);
        int y1 = (int) Math.ceil((inheritedClip.y() + inheritedClip.height()) * scale);
        if (x1 <= x0 || y1 <= y0) return;

        guiGraphics.enableScissor(x0, y0, x1, y1);
        try {
            drawAction.run();
        } finally {
            guiGraphics.disableScissor();
        }
    }

    private static AABB resolveInheritedClip(Slot slot) {
        AABB resolved = null;
        for (Element current = slot; current != null; current = current.parentElement) {
            if (!Interaction.clipsOverflow(current.getComputedStyle())) continue;
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

    private static void applyItemScaleTransform(GuiGraphicsExtractor guiGraphics, float iconScale) {
        if (Math.abs(iconScale - 1.0F) <= ICON_SCALE_EPSILON) return;
        guiGraphics.pose().translate(8.0F, 8.0F);
        guiGraphics.pose().scale(iconScale, iconScale);
        guiGraphics.pose().translate(-8.0F, -8.0F);
    }
}
