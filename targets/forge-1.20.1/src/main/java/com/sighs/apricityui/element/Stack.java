package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.BodyRenderNodeProvider;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.slot.GenericStackExpressionCompiler;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Displays one resource stack without imposing a resource type. */
@ElementRegister(Stack.TAG_NAME)
public class Stack extends MinecraftElement implements BodyRenderNodeProvider {
    public static final String TAG_NAME = "STACK";

    static {
        Element.register(TAG_NAME, (document, tagName) -> new Stack(document));
    }

    private Source source = Source.NONE;
    private GenericStack drivenStack;
    private String overlayText;
    private boolean hidden;
    private boolean menuDisabled;
    private String parsedSignature;
    private GenericStack parsedStack;

    public Stack(Document document) {
        this(document, TAG_NAME);
    }

    protected Stack(Document document, String tagName) {
        super(document, tagName);
    }

    public void setDrivenState(GenericStack stack, String nextOverlayText, boolean nextHidden,
                               boolean nextMenuDisabled, Source nextSource) {
        drivenStack = accepts(stack == null ? null : stack.what()) ? stack : null;
        overlayText = nextOverlayText;
        hidden = nextHidden;
        menuDisabled = nextMenuDisabled;
        source = nextSource == null ? Source.NONE : nextSource;
        requestRepaint();
    }

    public void setIngredientStack(GenericStack stack) {
        setDrivenState(stack, null, false, false, Source.INGREDIENT);
    }

    public void clearDrivenState(Source expectedSource) {
        if (expectedSource != null && source != expectedSource) return;
        source = Source.NONE;
        drivenStack = null;
        overlayText = null;
        hidden = false;
        menuDisabled = false;
        requestRepaint();
    }

    public boolean accepts(GenericKey key) {
        return key != null;
    }

    public boolean isMenuBound() {
        return source == Source.MENU;
    }

    public boolean canShowStackTooltip() {
        Slot slot = findAncestor(Slot.class);
        if (slot != null) return slot.canShowItemTooltip();
        return resolveStandaloneInteraction().contains(InteractionCapability.TOOLTIP);
    }

    public boolean shouldPaintStack() {
        if (hidden || (source == Source.MENU && menuDisabled)) return false;
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
        if (overlayText != null) return overlayText;
        GenericStack stack = currentStack();
        return stack == null ? null : defaultOverlayText(stack);
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
        if (source == Source.NONE) refreshParsedStack();
    }

    protected GenericStack parseLocalStack(String expression) {
        return GenericStackExpressionCompiler.parse(expression, getAttribute("type"), parseAmountAttribute());
    }

    protected ItemStack toDisplayItemStack(GenericStack stack) {
        return GenericStack.wrapInItemStack(stack);
    }

    protected String defaultOverlayText(GenericStack stack) {
        return stack.overlayText();
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
        if (source != Source.NONE) return drivenStack;
        refreshParsedStack();
        return parsedStack;
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

    public enum Source {
        NONE,
        INGREDIENT,
        MENU
    }

    private enum InteractionCapability {
        TOOLTIP,
        SLOT
    }
}
