package com.sighs.apricityui.element;

import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.BodyRenderNodeProvider;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.slot.ItemStackExpressionCompiler;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 单一 Minecraft ItemStack 的 DOM 元素。
 *
 * <p>文本内容用于本地展示，Ingredient 与菜单绑定分别通过驱动状态覆盖本地解析结果。
 * 构造阶段保持为空，避免纯 DOM 场景触发 Minecraft 注册表访问。</p>
 */
@ElementRegister(Item.TAG_NAME)
public class Item extends MinecraftElement implements BodyRenderNodeProvider {
    public static final String TAG_NAME = "ITEM";

    static {
        Element.register(TAG_NAME, (document, tagName) -> new Item(document));
    }

    private Source source = Source.NONE;
    private Optional<ItemStack> drivenStack = Optional.empty();
    private String overlayText;
    private boolean hidden;
    private boolean menuDisabled;

    private String parsedSource;
    private Optional<ItemStack> parsedStack = Optional.empty();

    public Item(Document document) {
        super(document, TAG_NAME);
    }

    public void setDrivenState(
            ItemStack stack,
            String nextOverlayText,
            boolean nextHidden,
            boolean nextMenuDisabled,
            Source nextSource
    ) {
        drivenStack = copyStack(stack);
        overlayText = nextOverlayText;
        hidden = nextHidden;
        menuDisabled = nextMenuDisabled;
        source = nextSource == null ? Source.NONE : nextSource;
        requestRepaint();
    }

    public void setIngredientStack(ItemStack stack) {
        setDrivenState(stack, null, false, false, Source.INGREDIENT);
    }

    public void clearDrivenState(Source expectedSource) {
        if (expectedSource != null && source != expectedSource) return;
        source = Source.NONE;
        drivenStack = Optional.empty();
        overlayText = null;
        hidden = false;
        menuDisabled = false;
        requestRepaint();
    }

    public boolean isMenuBound() {
        return source == Source.MENU;
    }

    public boolean canOperateBoundMenuSlot() {
        Slot slot = findAncestor(Slot.class);
        return isMenuBound() && slot != null && slot.canOperateBoundMenuSlot();
    }

    public boolean canShowItemTooltip() {
        Slot slot = findAncestor(Slot.class);
        if (slot != null) return slot.canShowItemTooltip();
        return resolveStandaloneInteraction().contains(InteractionCapability.TOOLTIP);
    }

    public boolean shouldPaintItem() {
        if (hidden || (source == Source.MENU && menuDisabled)) return false;
        Slot slot = findAncestor(Slot.class);
        return slot == null || (slot.shouldRenderItem() && !slot.isDisabled());
    }

    public ItemStack resolveDisplayStack() {
        if (!shouldPaintItem()) return ItemStack.EMPTY;
        return currentStack().map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    public String resolveOverlayText() {
        return shouldPaintItem() ? overlayText : null;
    }

    @Override
    public ItemStack getTooltipStack() {
        if (!canShowItemTooltip()) return ItemStack.EMPTY;
        return resolveDisplayStack();
    }

    @Override
    public List<RenderNode> createBodyRenderNodes() {
        return List.of(
                new RenderNode.ElementBackgroundNode(this),
                new RenderNode.ItemNode(
                        this,
                        this::resolveDisplayStack,
                        this::shouldPaintItem,
                        this::resolveIconScale,
                        this::resolveZIndex,
                        true,
                        this::resolveOverlayText,
                        () -> 0.0D
                )
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (source == Source.NONE) refreshParsedStack();
    }

    private Optional<ItemStack> currentStack() {
        if (source != Source.NONE) return drivenStack;
        refreshParsedStack();
        return parsedStack;
    }

    private void refreshParsedStack() {
        String expression = getTextContent();
        if (expression == null) expression = "";
        if (expression.equals(parsedSource)) return;

        parsedSource = expression;
        parsedStack = copyStack(ItemStackExpressionCompiler.parse(expression));
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

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        EnumSet<InteractionCapability> result = EnumSet.noneOf(InteractionCapability.class);
        for (String token : normalized.split("[\\s,]+")) {
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

    private static Optional<ItemStack> copyStack(ItemStack stack) {
        return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(stack.copy());
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
