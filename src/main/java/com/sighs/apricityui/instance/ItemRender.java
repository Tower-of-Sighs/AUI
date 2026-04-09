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

import java.util.ArrayList;
import java.util.List;

public final class ItemRender {
    private static final float ICON_SCALE_EPSILON = 0.0001F;
    private static final int QUICK_CRAFT_GHOST_COLOR = -2130706433;

    private ItemRender() {
    }

    public static void renderDocumentUnboundSlotItems(GuiGraphics guiGraphics, Document document) {
        if (guiGraphics == null || document == null) return;
        renderSlotItemRequests(guiGraphics, collectUnboundSlotItemRequests(document.getElements()));
    }

    public static void renderUnboundSlotItems(GuiGraphics guiGraphics, Iterable<? extends Element> elements) {
        renderSlotItemRequests(guiGraphics, collectUnboundSlotItemRequests(elements));
    }

    public static List<SlotItemRenderRequest> collectUnboundSlotItemRequests(Iterable<? extends Element> elements) {
        ArrayList<SlotItemRenderRequest> requests = new ArrayList<>();
        if (elements == null) return requests;

        for (Element element : elements) {
            if (!(element instanceof Slot slot)) continue;
            if (slot.getMcSlot() != null) continue;
            if (!slot.isVisible) continue;
            if ("none".equals(slot.getComputedStyle().display)) continue;
            if (!slot.shouldRenderItem()) continue;

            SlotItemRenderRequest request = createSlotItemRenderRequest(
                    slot,
                    slot.resolveDisplayStack(),
                    slot.resolveItemPadding(0),
                    slot.resolveIconScale(1.0F),
                    slot.resolveZIndex(0),
                    null,
                    null,
                    false
            );
            if (request != null) requests.add(request);
        }
        return requests;
    }

    public static SlotItemRenderRequest createBoundSlotItemRequest(
            Slot slotElement,
            ItemStack stack,
            String overlayText,
            Integer renderSeed,
            boolean drawQuickCraftGhost
    ) {
        return createSlotItemRenderRequest(
                slotElement,
                stack,
                slotElement == null ? 0 : slotElement.resolveItemPadding(0),
                slotElement == null ? 1.0F : slotElement.resolveIconScale(1.0F),
                slotElement == null ? 0 : slotElement.resolveZIndex(0),
                overlayText,
                renderSeed,
                drawQuickCraftGhost
        );
    }

    public static void renderSlotItemRequests(GuiGraphics guiGraphics, Iterable<SlotItemRenderRequest> requests) {
        if (guiGraphics == null || requests == null) return;

        Font font = Minecraft.getInstance().font;
        for (SlotItemRenderRequest request : requests) {
            if (request == null) continue;
            ItemStack stack = request.stack();
            if (stack == null || stack.isEmpty()) continue;

            AABB clipRect = request.clipRect();
            if (clipRect == null || !clipRect.isValid()) continue;

            boolean scissorApplied = false;
            guiGraphics.pose().pushPose();
            try {
                Mask.enableScissor(clipRect.x(), clipRect.y(), clipRect.width(), clipRect.height());
                scissorApplied = true;

                guiGraphics.pose().translate(0.0D, 0.0D, 100.0D + request.zIndex());
                if (request.drawQuickCraftGhost()) {
                    int ghostSize = Math.max(1, Math.round(16.0F * request.iconScale()));
                    int ghostX = Math.round(request.drawX() + 8.0F - ghostSize / 2.0F);
                    int ghostY = Math.round(request.drawY() + 8.0F - ghostSize / 2.0F);
                    guiGraphics.fill(ghostX, ghostY, ghostX + ghostSize, ghostY + ghostSize, QUICK_CRAFT_GHOST_COLOR);
                }

                applyItemScaleTransform(guiGraphics, request.drawX(), request.drawY(), request.iconScale());
                if (request.renderSeed() == null) {
                    guiGraphics.renderItem(stack, request.drawX(), request.drawY());
                } else {
                    guiGraphics.renderItem(stack, request.drawX(), request.drawY(), request.renderSeed());
                }
                guiGraphics.renderItemDecorations(font, stack, request.drawX(), request.drawY(), request.overlayText());
            } finally {
                guiGraphics.pose().popPose();
                if (scissorApplied) Mask.disableScissor();
            }
        }
    }

    private static SlotItemRenderRequest createSlotItemRenderRequest(
            Slot slot,
            ItemStack stack,
            int padding,
            float iconScale,
            int zIndex,
            String overlayText,
            Integer renderSeed,
            boolean drawQuickCraftGhost
    ) {
        if (slot == null || stack == null || stack.isEmpty()) return null;

        Rect rect = Rect.of(slot);
        Position body = rect.getBodyRectPosition();
        Size bodySize = rect.getBodyRectSize();

        int slotWidth = Math.max(1, (int) Math.round(bodySize.width()));
        int slotHeight = Math.max(1, (int) Math.round(bodySize.height()));
        int normalizedPadding = clampPadding(Math.min(slotWidth, slotHeight), padding);

        int renderWidth = Math.max(1, slotWidth - normalizedPadding * 2);
        int renderHeight = Math.max(1, slotHeight - normalizedPadding * 2);
        int drawX = (int) Math.round(body.x + normalizedPadding + (renderWidth - 16) / 2.0);
        int drawY = (int) Math.round(body.y + normalizedPadding + (renderHeight - 16) / 2.0);

        AABB clipRect = resolveItemClipRect(slot);
        if (clipRect == null || !clipRect.isValid()) return null;

        return new SlotItemRenderRequest(
                slot,
                stack.copy(),
                drawX,
                drawY,
                Math.max(0.01F, iconScale),
                zIndex,
                overlayText,
                renderSeed,
                drawQuickCraftGhost,
                clipRect
        );
    }

    /**
     * Item 是在 DOM 主绘制之后单独走 GuiGraphics 通道渲染的，因此这里需要自行补上 slot 与祖先链的矩形裁剪。
     */
    private static AABB resolveItemClipRect(Slot slot) {
        AABB clip = resolveBodyBounds(slot);
        if (!clip.isValid()) return null;

        Element current = slot;
        while (current != null) {
            Style style = current.getComputedStyle();
            if (Style.clipsOverflow(style.overflow)) {
                clip = clip.intersection(resolveBodyBounds(current));
            }
            if (style.clipPath != null && !"none".equals(style.clipPath)) {
                clip = clip.intersection(resolveClipPathBounds(current));
            }
            if (!clip.isValid()) return null;
            current = current.parentElement;
        }

        int screenWidth = (int) Size.getWindowSize().width();
        int screenHeight = (int) Size.getWindowSize().height();
        clip = clip.intersection(new AABB(0, 0, screenWidth, screenHeight));
        return clip.isValid() ? clip : null;
    }

    private static AABB resolveBodyBounds(Element element) {
        Rect rect = Rect.of(element);
        Position body = rect.getBodyRectPosition();
        Size bodySize = rect.getBodyRectSize();
        return new AABB((float) body.x, (float) body.y, (float) bodySize.width(), (float) bodySize.height());
    }

    private static AABB resolveClipPathBounds(Element element) {
        Rect rect = Rect.of(element);
        Position body = rect.getBodyRectPosition();
        Size bodySize = rect.getBodyRectSize();
        float x = (float) (body.x - rect.box.getBorderLeft());
        float y = (float) (body.y - rect.box.getBorderTop());
        float width = (float) (bodySize.width() + rect.box.getBorderHorizontal());
        float height = (float) (bodySize.height() + rect.box.getBorderVertical());
        return new AABB(x, y, width, height);
    }

    public record SlotItemRenderRequest(
            Slot slotElement,
            ItemStack stack,
            int drawX,
            int drawY,
            float iconScale,
            int zIndex,
            String overlayText,
            Integer renderSeed,
            boolean drawQuickCraftGhost,
            AABB clipRect
    ) {
    }

    private static int clampPadding(int slotSize, int padding) {
        int maxPadding = Math.max(0, (Math.max(1, slotSize) - 1) / 2);
        int normalized = Math.max(0, padding);
        return Math.min(normalized, maxPadding);
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
