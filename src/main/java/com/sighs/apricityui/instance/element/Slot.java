package com.sighs.apricityui.instance.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.ContentRenderNodeProvider;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.init.TextNode;
import com.sighs.apricityui.instance.slot.SlotDisplaySpec;
import com.sighs.apricityui.instance.slot.SlotExpressionCompiler;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.render.item.*;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 槽位 DOM 元素。
 * 绑定态消费菜单槽位快照，未绑定态展示表达式候选；两者共享同一 ItemDrawer 渲染路径。
 */
@ElementRegister(Slot.TAG_NAME)
public class Slot extends MinecraftElement implements ContentRenderNodeProvider {
    public static final String TAG_NAME = "SLOT";

    /**
     * 仅由 SlotDataBinder 维护的真实菜单绑定状态，模板不能声明或伪造。
     */
    private boolean isBound = false;
    private ItemRenderState boundItemRenderState = ItemRenderState.EMPTY;
    private ItemStack virtualStack = ItemStack.EMPTY;

    private SlotDisplaySpec displaySpec = SlotDisplaySpec.EMPTY;
    private String compiledSignature = "";
    private int candidateIndex = 0;
    private long nextRotateAtMillis = 0L;

    public Slot(Document document) {
        super(document, TAG_NAME);
    }

    // ── 菜单绑定状态 ────────────────────────────────────────────────

    private static EnumSet<InteractionCapability> parseInteractionCapabilities(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "unset".equals(normalized) || "auto".equals(normalized)) return null;

        EnumSet<InteractionCapability> result = EnumSet.noneOf(InteractionCapability.class);
        boolean hasKnownCapability = false;
        for (String token : normalized.split("\\s+")) {
            switch (token) {
                case "tooltip" -> {
                    result.add(InteractionCapability.TOOLTIP);
                    hasKnownCapability = true;
                }
                case "slot" -> {
                    result.add(InteractionCapability.SLOT);
                    hasKnownCapability = true;
                }
                case "none" -> {
                    return EnumSet.noneOf(InteractionCapability.class);
                }
                default -> {
                }
            }
        }
        return hasKnownCapability ? result : null;
    }

    /**
     * 当前元素是否已绑定到真实菜单槽位。
     */
    public boolean isBound() {
        return isBound;
    }

    /**
     * 由 SlotDataBinder 在建立绑定时调用。
     */
    public void bindToMenuSlot(ItemRenderState initialState) {
        isBound = true;
        virtualStack = ItemStack.EMPTY;
        updateBoundItemRenderState(initialState);
    }

    /**
     * 由 SlotDataBinder 在每帧菜单状态同步时调用。
     */
    public void updateBoundItemRenderState(ItemRenderState itemRenderState) {
        boundItemRenderState = itemRenderState == null ? ItemRenderState.EMPTY : itemRenderState;
    }

    /**
     * 由 SlotDataBinder 在文档刷新、Screen 销毁或重新绑定前调用。
     */
    public void clearMenuSlotBinding() {
        isBound = false;
        boundItemRenderState = ItemRenderState.EMPTY;
    }

    // ── 静态工具方法 ────────────────────────────────────────────────

    public static String furnaceFuelVirtualTagLiteral() {
        return SlotExpressionCompiler.furnaceFuelTagLiteral();
    }

    public static void clearCandidateCache() {
        SlotExpressionCompiler.clearTagCache();
    }

    public static String buildLiteralWithCount(String rawLiteral, int requestedCount) {
        return SlotExpressionCompiler.buildLiteralWithCount(rawLiteral, requestedCount);
    }

    // ── 属性解析 ────────────────────────────────────────────────────

    public int getRepeatCount() {
        Integer parsed = parsePositiveInt(getAttribute("repeat"));
        return parsed == null ? 1 : parsed;
    }

    public int getSlotIndex() {
        Integer parsed = parseInt(getFirstNonBlankAttribute("slot-index", "index"));
        return parsed == null ? -1 : parsed;
    }

    /**
     * Slot 的物品行为只由 interactive 控制，不再把 disabled 作为第二个开关。
     */
    @Override
    public boolean isDisabled() {
        return false;
    }

    public boolean canShowItemTooltip() {
        return resolveInteractionCapabilities().contains(InteractionCapability.TOOLTIP);
    }

    public boolean canOperateBoundMenuSlot() {
        return isBound && resolveInteractionCapabilities().contains(InteractionCapability.SLOT);
    }

    public boolean canReceiveSlotFocus() {
        return canOperateBoundMenuSlot();
    }

    public int resolveSlotSizeHint(int fallback) {
        Integer cssSize = parsePositiveInt(getCustomPropertyInherit("--aui-slot-size"));
        if (cssSize != null) return cssSize;

        int width = Size.parse(getComputedStyle().width);
        int height = Size.parse(getComputedStyle().height);
        int styleSize = Math.max(width, height);
        if (styleSize > 0) return styleSize;

        Integer attrSize = parsePositiveInt(getFirstNonBlankAttribute("size", "slot-size"));
        if (attrSize != null) return attrSize;

        return Math.max(1, fallback);
    }

    public boolean containsSlotPoint(double mouseX, double mouseY) {
        Position position = Position.of(this);
        int size = resolveSlotSizeHint(16);
        return mouseX >= position.x
                && mouseX < position.x + size
                && mouseY >= position.y
                && mouseY < position.y + size;
    }

    @Override
    public void tick() {
        super.tick();
        if (isBound) return;

        refreshDisplaySpecIfNeeded();
        if (!displaySpec.hasCandidates()) {
            virtualStack = ItemStack.EMPTY;
            return;
        }
        int size = displaySpec.candidates().size();
        if (candidateIndex < 0 || candidateIndex >= size) candidateIndex = 0;

        long now = System.currentTimeMillis();
        if (displaySpec.cycleEnabled() && size > 1 && !isHover) {
            if (nextRotateAtMillis <= 0L) {
                nextRotateAtMillis = now + displaySpec.cycleIntervalMs();
            } else if (now >= nextRotateAtMillis) {
                candidateIndex = (candidateIndex + 1) % size;
                nextRotateAtMillis = now + displaySpec.cycleIntervalMs();
            }
        }

        ItemStack stack = displaySpec.candidates().get(candidateIndex).copy();
        if (stack.getCount() <= 0) stack.setCount(1);
        virtualStack = stack;
    }

    public ItemRenderMode getItemRenderMode() {
        return ItemRenderMode.parse(getAttribute("render"));
    }

    public boolean rendersBackground() {
        return getItemRenderMode().rendersBackground();
    }

    public boolean rendersItem() {
        return getItemRenderMode().rendersItem();
    }

    public boolean shouldRenderBackground() {
        return rendersBackground();
    }

    public ItemRenderState getItemRenderState() {
        if (isBound) return boundItemRenderState;
        if (!rendersItem()) return ItemRenderState.EMPTY;
        return new ItemRenderState(
                virtualStack,
                null,
                false,
                false,
                ItemRenderContext.resolveCooldownProgress(virtualStack)
        );
    }

    public float resolveIconScale(float fallback) {
        Float cssScale = parsePositiveFloat(getCustomPropertyInherit("--aui-slot-icon-scale"));
        if (cssScale != null) return cssScale;
        Float attrScale = parsePositiveFloat(getAttribute("iconScale"));
        if (attrScale != null) return attrScale;
        return Math.max(0.01F, fallback);
    }

    public int resolveZIndex(int fallback) {
        Integer cssZ = parseInt(getCustomPropertyInherit("--aui-slot-z"));
        if (cssZ != null) return cssZ;
        Integer attrZ = parseInt(getFirstNonBlankAttribute("zIndex", "z"));
        if (attrZ != null) return attrZ;
        return fallback;
    }

    // ── 渲染与交互 ──────────────────────────────────────────────────

    @Override
    public List<RenderNode> createContentRenderNodes() {
        // 节点常驻于 paintList，状态与 render 属性在每帧读取，避免物品变化时频繁重建整个文档绘制队列。
        return List.of(
                new ItemRenderNode(this),
                new ItemGlintRenderNode(this),
                new ItemDecorationRenderNode(this)
        );
    }

    public String getBackgroundImageCandidate() {
        Background background = Background.of(this);
        String rawPath = background == null ? null : background.imagePath;
        if (rawPath == null || rawPath.isBlank() || "unset".equals(rawPath)) return null;
        return rawPath;
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        if (!shouldRenderBackground()
                && (phase == Base.RenderPhase.SHADOW
                || phase == Base.RenderPhase.BODY
                || phase == Base.RenderPhase.BORDER)) {
            return;
        }
        super.drawPhase(poseStack, phase);
    }

    @Override
    public boolean canFocus() {
        return canReceiveSlotFocus();
    }

    /**
     * 获取当前应显示的物品。
     * 绑定态读取菜单快照，未绑定态读取虚拟物品。
     */
    public ItemStack resolveDisplayStack() {
        ItemStack stack = getItemRenderState().stack();
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        return stack;
    }

    @Override
    public ItemStack getTooltipStack() {
        // 绑定态由 Screen 从真实菜单槽位读取 tooltip stack。
        if (isBound) return ItemStack.EMPTY;
        ItemStack stack = virtualStack;
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copy();
    }

    /**
     * 批量设置 recipe 生成槽位的公共元数据，避免重复触发 updateCSS。
     */
    public void applyRecipeSlotMeta(String className, String generatedTag) {
        setAttributesBatch(Map.of(
                "class", className == null ? "" : className,
                "data-generated", generatedTag == null ? "" : generatedTag
        ), true);
    }

    // ── 内部方法 ────────────────────────────────────────────────────

    private void refreshDisplaySpecIfNeeded() {
        boolean cycleEnabled = resolveCycleEnabled();
        long cycleInterval = resolveCycleIntervalMs();
        String expressionSource = resolveDisplayExpressionSource();
        String signature = expressionSource + "|cycle=" + cycleEnabled + "|interval=" + cycleInterval;
        if (signature.equals(compiledSignature)) return;

        compiledSignature = signature;
        displaySpec = SlotExpressionCompiler.compile(expressionSource, cycleEnabled, cycleInterval);
        candidateIndex = 0;
        nextRotateAtMillis = 0L;
    }

    private String resolveDisplayExpressionSource() {
        if (!childNodes.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (Node child : childNodes) {
                if (child instanceof TextNode textNode) {
                    builder.append(textNode.getTextContent());
                }
            }
            String fromNodes = builder.toString();
            if (!fromNodes.isBlank()) {
                return fromNodes;
            }
        }
        return innerText == null ? "" : innerText;
    }

    @Override
    public void renderTooltip(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!canShowItemTooltip()) return;
        ItemStack stack = getTooltipStack();
        if (stack.isEmpty()) return;
        guiGraphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
    }

    private EnumSet<InteractionCapability> resolveInteractionCapabilities() {
        EnumSet<InteractionCapability> cssCapabilities = parseInteractionCapabilities(
                getCustomPropertyInherit("--aui-slot-interactive")
        );
        if (cssCapabilities != null) return cssCapabilities;

        EnumSet<InteractionCapability> attributeCapabilities = parseInteractionCapabilities(getAttribute("interactive"));
        if (attributeCapabilities != null) return attributeCapabilities;

        return isBound
                ? EnumSet.of(InteractionCapability.TOOLTIP, InteractionCapability.SLOT)
                : EnumSet.of(InteractionCapability.TOOLTIP);
    }

    private boolean resolveCycleEnabled() {
        Boolean cssFlag = parseBooleanLike(getCustomPropertyInherit("--aui-slot-cycle"));
        if (cssFlag != null) return cssFlag;
        Boolean attrFlag = parseBooleanLike(getAttribute("cycle"));
        if (attrFlag != null) return attrFlag;
        return true;
    }

    private long resolveCycleIntervalMs() {
        Long cssInterval = parsePositiveLong(getCustomPropertyInherit("--aui-slot-cycle-interval"));
        if (cssInterval != null) return Math.max(200L, cssInterval);
        Long attrInterval = parsePositiveLong(getFirstNonBlankAttribute("cycle-interval", "rotate-interval"));
        if (attrInterval != null) return Math.max(200L, attrInterval);
        return SlotDisplaySpec.DEFAULT_CYCLE_INTERVAL_MS;
    }

    private String getFirstNonBlankAttribute(String... keys) {
        if (keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            String value = getAttribute(key);
            if (value == null || value.isBlank()) continue;
            return value;
        }
        return null;
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parsePositiveInt(String raw) {
        Integer parsed = parseInt(raw);
        return parsed != null && parsed > 0 ? parsed : null;
    }

    private static Long parsePositiveLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0L ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Float parsePositiveFloat(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            float parsed = Float.parseFloat(raw.trim());
            return parsed > 0.0F ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Boolean parseBooleanLike(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "unset".equals(normalized) || "auto".equals(normalized)) return null;
        return switch (normalized) {
            case "1", "true", "yes", "on", "enabled" -> true;
            case "0", "false", "no", "off", "disabled", "none" -> false;
            default -> null;
        };
    }

    private enum InteractionCapability {
        TOOLTIP,
        SLOT
    }
}
