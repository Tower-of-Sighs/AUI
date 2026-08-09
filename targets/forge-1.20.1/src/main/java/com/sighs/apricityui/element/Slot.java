package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.BodyRenderNodeProvider;
import com.sighs.apricityui.render.ForegroundRenderNodeProvider;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 槽位 DOM 背景壳。
 *
 * <p>Slot 只负责背景、几何、菜单索引和菜单交互能力；实际物品由直接 Item
 * 或 Ingredient 控制的 Item 渲染。</p>
 */
@ElementRegister(Slot.TAG_NAME)
public class Slot extends MinecraftElement implements BodyRenderNodeProvider, ForegroundRenderNodeProvider {
    public static final String TAG_NAME = "SLOT";

    static {
        Element.register(TAG_NAME, (document, tagName) -> new Slot(document));
    }

    private static final ThreadLocal<Set<Slot>> INTERACTIVE_RESOLUTION =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private boolean bound;
    private boolean boundDisabled;
    private boolean boundHidden;
    private boolean boundGhost;

    public Slot(Document document) {
        super(document, TAG_NAME);
    }

    // ── 菜单绑定状态 ────────────────────────────────────────────────

    public boolean isBound() {
        return bound;
    }

    public void bindToMenuSlot(boolean initialDisabled) {
        bound = true;
        boundDisabled = initialDisabled;
        boundHidden = false;
        boundGhost = false;
    }

    public void updateBoundMenuState(boolean nextDisabled, boolean nextHidden, boolean nextGhost) {
        boundDisabled = nextDisabled;
        boundHidden = nextHidden;
        boundGhost = nextGhost;
    }

    public void updateBoundMenuState(boolean nextDisabled, boolean nextGhost) {
        updateBoundMenuState(nextDisabled, false, nextGhost);
    }

    public void updateBoundMenuState(boolean nextDisabled) {
        updateBoundMenuState(nextDisabled, false, false);
    }

    public void clearMenuSlotBinding() {
        bound = false;
        boundDisabled = false;
        boundHidden = false;
        boundGhost = false;
        setHover(false);
    }

    public boolean isExplicitlyDisabled() {
        if (!hasAttribute("disabled")) return false;
        Boolean disabledAttribute = parseBooleanLike(getAttribute("disabled"));
        return disabledAttribute == null || disabledAttribute;
    }

    @Override
    public boolean isDisabled() {
        return boundDisabled || isExplicitlyDisabled();
    }

    public boolean canShowItemTooltip() {
        return resolveInteractionCapabilities().contains(InteractionCapability.TOOLTIP);
    }

    public boolean canOperateBoundMenuSlot() {
        return bound && !isDisabled()
                && resolveInteractionCapabilities().contains(InteractionCapability.SLOT);
    }

    public boolean canReceiveSlotFocus() {
        return canOperateBoundMenuSlot();
    }

    public boolean shouldAcceptPointer() {
        return canOperateBoundMenuSlot();
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

    public boolean shouldRenderBackground() {
        Boolean cssFlag = parseBooleanLike(getCustomPropertyInherit("--aui-slot-render-bg"));
        if (cssFlag != null) return cssFlag;
        Boolean attrFlag = parseBooleanLike(getAttribute("render-bg"));
        if (attrFlag != null) return attrFlag;

        return switch (normalizeToken(getAttribute("render"))) {
            case "item", "none" -> false;
            default -> true;
        };
    }

    public boolean shouldRenderItem() {
        Boolean cssFlag = parseBooleanLike(getCustomPropertyInherit("--aui-slot-render-item"));
        if (cssFlag != null) return cssFlag;
        Boolean attrFlag = parseBooleanLike(getAttribute("render-item"));
        if (attrFlag != null) return attrFlag;

        return switch (normalizeToken(getAttribute("render"))) {
            case "bg", "none" -> false;
            default -> true;
        };
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
        return attrZ == null ? fallback : attrZ;
    }

    // ── 渲染与交互 ──────────────────────────────────────────────────

    @Override
    public boolean canFocus() {
        return canReceiveSlotFocus();
    }

    public String getBackgroundImageCandidate() {
        Background background = Background.of(this);
        String rawPath = background == null ? null : background.imagePath;
        if (rawPath == null || rawPath.isBlank() || "unset".equals(rawPath)) return null;
        return rawPath;
    }

    @Override
    public List<RenderNode> createBodyRenderNodes() {
        return List.of(new RenderNode.ElementBackgroundNode(this));
    }

    @Override
    public List<RenderNode> createForegroundRenderNodes() {
        return List.of(new RenderNode.ElementForegroundNode(this, this::drawForegroundMask));
    }

    /**
     * 原版槽位高亮和快速合成预览都属于前景层：它们必须高于 Slot 的 Item 子节点，
     * 但低于鼠标跟随的浮动物品。
     */
    public boolean shouldRenderForegroundMask() {
        return canOperateBoundMenuSlot()
                && !boundHidden
                && (shouldRenderBackground() || shouldRenderItem())
                && isVisible
                && Interaction.isDisplayed(this)
                && (isHover || boundGhost);
    }

    private void drawForegroundMask(PoseStack poseStack) {
        if (!shouldRenderForegroundMask()) return;
        Position position = Position.of(this);
        int size = resolveSlotSizeHint(16);
        poseStack.translate(0.0F, 0.0F, Base.getGuiItemForegroundZ());
        Graph.beginLayeredBatch();
        try {
            Graph.drawFillRect(
                    poseStack.last().pose(),
                    (float) position.x,
                    (float) position.y,
                    (float) (position.x + size),
                    (float) (position.y + size),
                    0x80FFFFFF
            );
        } finally {
            Graph.endBatch();
        }
    }

    @Override
    public void drawBackgroundOnly(PoseStack poseStack) {
        if (!shouldRenderBackground()) return;
        super.drawBackgroundOnly(poseStack);
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
    public ItemStack getTooltipStack() {
        if (!canShowItemTooltip() || !shouldRenderItem()) return ItemStack.EMPTY;
        Item item = SlotContentRules.getDisplayItem(this);
        return item == null ? ItemStack.EMPTY : item.getTooltipStack();
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

    private boolean isRecipeSlot() {
        String generatedTag = getAttribute("data-generated");
        if (generatedTag != null && generatedTag.startsWith("recipe")) return true;
        return hasAncestor(Recipe.class);
    }

    private EnumSet<InteractionCapability> resolveInteractionCapabilities() {
        Set<Slot> resolving = INTERACTIVE_RESOLUTION.get();
        if (!resolving.add(this)) return resolveInteractionCapabilitiesWithoutCss();
        try {
            if (isRecipeSlot()) return EnumSet.noneOf(InteractionCapability.class);

            EnumSet<InteractionCapability> attributeCapabilities = parseInteractionCapabilities(getAttribute("interactive"));
            if (attributeCapabilities != null) return attributeCapabilities;

            EnumSet<InteractionCapability> pointerCapabilities = parseInteractionCapabilities(getAttribute("pointer"));
            if (pointerCapabilities != null) return pointerCapabilities;

            EnumSet<InteractionCapability> cssCapabilities = parseInteractionCapabilities(
                    getCustomPropertyInherit("--aui-slot-interactive")
            );
            if (cssCapabilities != null) return cssCapabilities;
            return resolveInteractionCapabilitiesWithoutCss();
        } finally {
            resolving.remove(this);
            if (resolving.isEmpty()) INTERACTIVE_RESOLUTION.remove();
        }
    }

    private EnumSet<InteractionCapability> resolveInteractionCapabilitiesWithoutCss() {
        if (isRecipeSlot()) return EnumSet.noneOf(InteractionCapability.class);

        EnumSet<InteractionCapability> attributeCapabilities = parseInteractionCapabilities(getAttribute("interactive"));
        if (attributeCapabilities != null) return attributeCapabilities;

        EnumSet<InteractionCapability> pointerCapabilities = parseInteractionCapabilities(getAttribute("pointer"));
        if (pointerCapabilities != null) return pointerCapabilities;

        return bound
                ? EnumSet.of(InteractionCapability.TOOLTIP, InteractionCapability.SLOT)
                : EnumSet.of(InteractionCapability.TOOLTIP);
    }

    private String getFirstNonBlankAttribute(String... keys) {
        if (keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            String value = getAttribute(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String normalizeToken(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
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

    private static Float parsePositiveFloat(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            float parsed = Float.parseFloat(raw.trim());
            return parsed > 0.0F ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static EnumSet<InteractionCapability> parseInteractionCapabilities(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "unset".equals(normalized) || "auto".equals(normalized)) return null;

        EnumSet<InteractionCapability> result = EnumSet.noneOf(InteractionCapability.class);
        boolean hasKnownCapability = false;
        for (String token : normalized.split("[\\s,]+")) {
            switch (token) {
                case "1", "true", "yes", "on", "enabled" -> {
                    result.add(InteractionCapability.TOOLTIP);
                    result.add(InteractionCapability.SLOT);
                    hasKnownCapability = true;
                }
                case "0", "false", "no", "off", "disabled", "none" -> {
                    return EnumSet.noneOf(InteractionCapability.class);
                }
                case "tooltip" -> {
                    result.add(InteractionCapability.TOOLTIP);
                    hasKnownCapability = true;
                }
                case "slot" -> {
                    result.add(InteractionCapability.SLOT);
                    hasKnownCapability = true;
                }
                default -> {
                }
            }
        }
        return hasKnownCapability ? result : null;
    }

    private enum InteractionCapability {
        TOOLTIP,
        SLOT
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
}
