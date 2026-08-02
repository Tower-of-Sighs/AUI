package com.sighs.apricityui.instance.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.ItemDrawer;
import com.sighs.apricityui.instance.render.item.ItemRenderContext;
import com.sighs.apricityui.instance.render.item.ItemRenderState;
import com.sighs.apricityui.instance.slot.ItemStackExpressionCompiler;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * 单一 Minecraft ItemStack 的 DOM 元素。
 */
@ElementRegister(Item.TAG_NAME)
public class Item extends MinecraftElement {
    public static final String TAG_NAME = "ITEM";
    private Source source = Source.NONE;
    private ItemRenderState drivenState = ItemRenderState.EMPTY;
    private String parsedSource = null;
    private ItemStack parsedStack = ItemStack.EMPTY;
    private String invalidInteractive = null;
    public Item(Document document) {
        super(document, TAG_NAME);
    }

    public void setDrivenState(ItemRenderState state, Source source) {
        this.drivenState = state == null ? ItemRenderState.EMPTY : state;
        this.source = source == null ? Source.NONE : source;
        requestRepaint();
    }

    public void setIngredientStack(ItemStack stack) {
        ItemStack safe = stack == null ? ItemStack.EMPTY : stack.copy();
        setDrivenState(new ItemRenderState(safe, null, false, false, ItemRenderContext.resolveCooldownProgress(safe)), Source.INGREDIENT);
    }

    public void clearDrivenState(Source expectedSource) {
        if (expectedSource != null && source != expectedSource) return;
        source = Source.NONE;
        drivenState = ItemRenderState.EMPTY;
        requestRepaint();
    }

    public boolean isMenuBound() {
        return source == Source.MENU;
    }

    public ItemRenderState getItemRenderState() {
        if (source != Source.NONE) return drivenState;
        refreshParsedStack();
        return new ItemRenderState(parsedStack, null, false, false, ItemRenderContext.resolveCooldownProgress(parsedStack));
    }

    @Override
    public ItemStack getTooltipStack() {
        ItemStack stack = getItemRenderState().stack();
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    public boolean canShowItemTooltip() {
        return interaction().tooltip;
    }

    public boolean canOperateBoundMenuSlot() {
        return isMenuBound() && interaction().slot;
    }

    public boolean containsItemPoint(double mouseX, double mouseY) {
        Position position = Position.of(this);
        int width = Size.parse(getComputedStyle().width);
        int height = Size.parse(getComputedStyle().height);
        width = width > 0 ? width : 16;
        height = height > 0 ? height : 16;
        return mouseX >= position.x && mouseX < position.x + width
                && mouseY >= position.y && mouseY < position.y + height;
    }

    @Override
    public boolean canFocus() {
        return !isMenuBound() && super.canFocus();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        super.drawPhase(poseStack, phase);
        if (phase != Base.RenderPhase.BODY) return;

        ItemRenderState state = getItemRenderState();
        if (state.hidden() || state.isEmpty()) return;

        Rect rect = Rect.of(this);
        Position position = rect.getContentPosition();
        Size size = Box.of(this).innerSize();
        float width = Math.max(0.0F, (float) size.width());
        float height = Math.max(0.0F, (float) size.height());
        if (width <= 0.0F || height <= 0.0F) return;

        float scale = Math.max(0.01F, Math.min(width, height) / 16.0F);
        float drawSize = 16.0F * scale;
        float x = (float) position.x + (width - drawSize) * 0.5F;
        float y = (float) position.y + (height - drawSize) * 0.5F;

        poseStack.pushPose();
        poseStack.translate(x, y, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        try {
            ItemDrawer.drawAll(poseStack, state, ItemRenderContext.forGui(state.stack()));
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (source == Source.NONE) refreshParsedStack();
    }

    private void refreshParsedStack() {
        String current = getTextContent();
        if (current == null) current = "";
        if (current.equals(parsedSource)) return;
        parsedSource = current;
        parsedStack = ItemStackExpressionCompiler.parse(current);
        requestRepaint();
    }

    private Interaction interaction() {
        String raw = getAttribute("interactive");
        if (raw == null || raw.isBlank()) {
            return source == Source.MENU ? Interaction.ALL : Interaction.TOOLTIP;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "tooltip" -> Interaction.TOOLTIP;
            case "slot" -> Interaction.SLOT;
            case "all" -> Interaction.ALL;
            case "none" -> Interaction.NONE;
            default -> {
                if (!value.equals(invalidInteractive)) invalidInteractive = value;
                yield Interaction.NONE;
            }
        };
    }

    public enum Source {NONE, INGREDIENT, MENU}

    private record Interaction(boolean tooltip, boolean slot) {
        private static final Interaction NONE = new Interaction(false, false);
        private static final Interaction TOOLTIP = new Interaction(true, false);
        private static final Interaction SLOT = new Interaction(false, true);
        private static final Interaction ALL = new Interaction(true, true);
    }
}
