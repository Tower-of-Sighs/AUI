package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.BodyRenderNodeProvider;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.slot.GenericStackExpressionCompiler;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackAdapters;
import com.sighs.apricityui.stack.GenericStackController;
import com.sighs.apricityui.stack.GenericStackController.Source;
import com.sighs.apricityui.stack.GenericStackType;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public abstract class GenericStackElement extends MinecraftElement implements BodyRenderNodeProvider {
    protected final GenericStackController controller = new GenericStackController();
    private String parsedSignature;
    private GenericStack parsedStack;

    protected GenericStackElement(Document document, String tagName) {
        super(document, tagName);
    }

    public GenericStackType<?> type() {
        return null;
    }

    public void setDrivenState(GenericStack stack, String nextOverlayText, boolean nextHidden,
                               boolean nextMenuDisabled, Source nextSource) {
        controller.setDrivenState(type(), stack, nextOverlayText, nextHidden, nextMenuDisabled, nextSource);
        requestRepaint();
    }

    public void clearDrivenState(Source expectedSource) {
        controller.clearDrivenState(expectedSource);
        requestRepaint();
    }

    public boolean accepts(GenericKey key) {
        return controller.accepts(type(), key);
    }

    public boolean isMenuBound() {
        return controller.source() == Source.MENU;
    }

    public boolean canShowStackTooltip() {
        Slot slot = findAncestor(Slot.class);
        if (slot != null) return slot.canShowItemTooltip();
        return resolveStandaloneInteraction().contains(InteractionCapability.TOOLTIP);
    }

    public boolean shouldPaintStack() {
        if (!controller.shouldPaint()) return false;
        Slot slot = findAncestor(Slot.class);
        return slot == null || (slot.shouldRenderItem() && !slot.isDisabled());
    }

    public GenericStack resolveGenericStack() {
        if (!shouldPaintStack()) return null;
        return currentStack();
    }

    public ItemStack resolveDisplayStack() {
        GenericStack stack = resolveGenericStack();
        return stack == null ? ItemStack.EMPTY : toDisplayItemStack(stack);
    }

    public String resolveOverlayText() {
        if (!shouldPaintStack()) return null;
        return controller.overlayText(currentStack());
    }

    @Override
    public ItemStack getTooltipStack() {
        return canShowStackTooltip() ? resolveDisplayStack() : ItemStack.EMPTY;
    }

    @Override
    public List<RenderNode> createBodyRenderNodes() {
        return List.of(
                new RenderNode.ElementBackgroundNode(this),
                new RenderNode.ItemNode(this, this::resolveRenderStack, this::shouldPaintStack,
                        this::resolveIconScale, this::resolveZIndex, true,
                        this::resolveOverlayText, () -> 0.0D)
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (controller.source() == Source.NONE) refreshParsedStack();
    }

    protected GenericStack parseLocalStack(String expression) {
        return GenericStackExpressionCompiler.parse(expression, type() == null ? getAttribute("type") : type().id(), parseAmountAttribute());
    }

    protected ItemStack toDisplayItemStack(GenericStack stack) {
        return GenericStackAdapters.toDisplayItemStack(stack);
    }

    protected long parseAmountAttribute() {
        String raw = getAttribute("amount");
        if (raw == null || raw.isBlank()) return 0L;
        try {
            return Math.max(0L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private ItemStack resolveRenderStack() {
        GenericStack stack = resolveGenericStack();
        return stack == null ? ItemStack.EMPTY : toDisplayItemStack(stack);
    }

    private GenericStack currentStack() {
        if (controller.source() == Source.NONE) refreshParsedStack();
        return controller.currentStack(parsedStack);
    }

    private void refreshParsedStack() {
        String expression = getTextContent() == null ? "" : getTextContent();
        String signature = expression + "|type=" + getAttribute("type") + "|amount=" + getAttribute("amount");
        if (signature.equals(parsedSignature)) return;
        parsedSignature = signature;
        parsedStack = parseLocalStack(expression);
        requestRepaint();
    }

    private double resolveIconScale() {
        Slot slot = findAncestor(Slot.class);
        return slot == null ? 1.0D : slot.resolveIconScale(1.0F);
    }

    private int resolveZIndex() {
        Slot slot = findAncestor(Slot.class);
        return slot == null ? 0 : slot.resolveZIndex(0);
    }

    private EnumSet<InteractionCapability> resolveStandaloneInteraction() {
        String raw = getAttribute("interactive");
        if (raw == null || raw.isBlank()) return EnumSet.of(InteractionCapability.TOOLTIP);
        EnumSet<InteractionCapability> result = EnumSet.noneOf(InteractionCapability.class);
        for (String token : raw.trim().toLowerCase(Locale.ROOT).split("[\\s,]+")) {
            switch (token) {
                case "1", "true", "yes", "on", "enabled", "all" -> {
                    result.add(InteractionCapability.TOOLTIP);
                    result.add(InteractionCapability.SLOT);
                }
                case "tooltip" -> result.add(InteractionCapability.TOOLTIP);
                case "slot" -> result.add(InteractionCapability.SLOT);
                case "0", "false", "no", "off", "disabled", "none" -> {
                    return EnumSet.noneOf(InteractionCapability.class);
                }
                default -> {
                }
            }
        }
        return result;
    }

    private enum InteractionCapability {
        TOOLTIP,
        SLOT
    }
}
