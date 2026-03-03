package com.sighs.apricityui.instance.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.slot.SlotDisplaySpec;
import com.sighs.apricityui.instance.slot.SlotExpressionCompiler;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 绑定态展示真实菜单槽位，未绑定态展示表达式候选。
 */
@ElementRegister(Slot.TAG_NAME)
public class Slot extends MinecraftElement {
    public static final String TAG_NAME = "SLOT";

    private net.minecraft.world.inventory.Slot mcSlot = null;
    private ItemStack virtualStack = ItemStack.EMPTY;

    private SlotDisplaySpec displaySpec = SlotDisplaySpec.EMPTY;
    private String compiledSignature = "";
    private int candidateIndex = 0;
    private long nextRotateAtMillis = 0L;

    public Slot(Document document) {
        super(document, TAG_NAME);
    }

    public static String furnaceFuelVirtualTagLiteral() {
        return SlotExpressionCompiler.furnaceFuelTagLiteral();
    }

    public static void clearCandidateCache() {
        SlotExpressionCompiler.clearTagCache();
    }

    public static String buildLiteralWithCount(String rawLiteral, int requestedCount) {
        return SlotExpressionCompiler.buildLiteralWithCount(rawLiteral, requestedCount);
    }

    public void bindMcSlot(net.minecraft.world.inventory.Slot slot) {
        mcSlot = slot;
        if (slot != null) {
            virtualStack = ItemStack.EMPTY;
        }
    }

    public net.minecraft.world.inventory.Slot getMcSlot() {
        return mcSlot;
    }

    private boolean isBoundToMenuSlot() {
        return mcSlot != null;
    }

    private ApricityContainerMenu.UiSlot resolveUiSlot() {
        if (!(getMcSlot() instanceof ApricityContainerMenu.UiSlot uiSlot)) return null;
        return uiSlot;
    }

    public int getRepeatCount() {
        Integer parsed = parsePositiveInt(getAttribute("repeat"));
        return parsed == null ? 1 : parsed;
    }

    private String getGeneratedSourceTag() {
        return getAttribute("data-generated");
    }

    private boolean isRecipeSlot() {
        String generatedTag = getGeneratedSourceTag();
        if (generatedTag != null && generatedTag.startsWith("recipe")) return true;
        return hasAncestor(Recipe.class);
    }

    public int getSlotIndex() {
        Integer parsed = parseInt(getFirstNonBlankAttribute("slot-index", "index"));
        return parsed == null ? -1 : parsed;
    }

    public boolean isDisabled() {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundToMenuSlot() && uiSlot != null && uiSlot.isUiDisabled()) return true;
        Boolean disabledAttr = parseBooleanLike(getAttribute("disabled"));
        if (disabledAttr != null && disabledAttr) return true;
        return !resolveInteractive();
    }

    public boolean shouldAcceptPointer() {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundToMenuSlot() && uiSlot != null && !uiSlot.isUiAcceptPointer()) return false;
        return !isDisabled() && resolveInteractive();
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

        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundToMenuSlot() && uiSlot != null) return Math.max(1, uiSlot.getUiSlotSize());
        return Math.max(1, fallback);
    }

    public boolean shouldRenderBackground() {
        Boolean cssFlag = parseBooleanLike(getCustomPropertyInherit("--aui-slot-render-bg"));
        if (cssFlag != null) return cssFlag;
        Boolean attrFlag = parseBooleanLike(getAttribute("render-bg"));
        if (attrFlag != null) return attrFlag;

        String render = normalizeToken(getAttribute("render"));
        if ("item".equals(render) || "none".equals(render)) return false;
        if ("bg".equals(render) || "all".equals(render)) return true;

        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundToMenuSlot() && uiSlot != null) return uiSlot.isUiRenderBackground();
        return true;
    }

    public boolean shouldRenderItem() {
        Boolean cssFlag = parseBooleanLike(getCustomPropertyInherit("--aui-slot-render-item"));
        if (cssFlag != null) return cssFlag;
        Boolean attrFlag = parseBooleanLike(getAttribute("render-item"));
        if (attrFlag != null) return attrFlag;

        String render = normalizeToken(getAttribute("render"));
        if ("bg".equals(render) || "none".equals(render)) return false;
        if ("item".equals(render) || "all".equals(render)) return true;

        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundToMenuSlot() && uiSlot != null) return uiSlot.isUiRenderItem();
        return true;
    }

    public float resolveIconScale(float fallback) {
        Float cssScale = parsePositiveFloat(getCustomPropertyInherit("--aui-slot-icon-scale"));
        if (cssScale != null) return cssScale;
        Float attrScale = parsePositiveFloat(getAttribute("iconScale"));
        if (attrScale != null) return attrScale;
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundToMenuSlot() && uiSlot != null) return Math.max(0.01F, uiSlot.getUiIconScale());
        return Math.max(0.01F, fallback);
    }

    public int resolveItemPadding(int fallback) {
        Integer cssPadding = parseNonNegativeInt(getCustomPropertyInherit("--aui-slot-padding"));
        if (cssPadding != null) return cssPadding;
        Integer attrPadding = parseNonNegativeInt(getAttribute("padding"));
        if (attrPadding != null) return attrPadding;
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundToMenuSlot() && uiSlot != null) return Math.max(0, uiSlot.getUiPadding());
        return Math.max(0, fallback);
    }

    public int resolveZIndex(int fallback) {
        Integer cssZ = parseInt(getCustomPropertyInherit("--aui-slot-z"));
        if (cssZ != null) return cssZ;
        Integer attrZ = parseInt(getFirstNonBlankAttribute("zIndex", "z"));
        if (attrZ != null) return attrZ;
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundToMenuSlot() && uiSlot != null) return uiSlot.getUiZIndex();
        return fallback;
    }

    @Override
    public boolean canFocus() {
        return shouldAcceptPointer();
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
    public void tick() {
        super.tick();
        if (isBoundToMenuSlot()) return;

        refreshDisplaySpecIfNeeded();
        if (!displaySpec.hasCandidates()) {
            virtualStack = ItemStack.EMPTY;
            return;
        }
        if (!shouldRenderItem()) {
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

    public ItemStack resolveDisplayStack() {
        ItemStack stack = isBoundToMenuSlot() ? mcSlot.getItem() : virtualStack;
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copy();
    }

    @Override
    public ItemStack getTooltipStack() {
        if (isBoundToMenuSlot()) return ItemStack.EMPTY;
        ItemStack stack = virtualStack;
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copy();
    }

    @Override
    public void renderTooltip(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ItemStack stack = getTooltipStack();
        if (stack.isEmpty()) return;
        guiGraphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
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

    private void refreshDisplaySpecIfNeeded() {
        boolean cycleEnabled = resolveCycleEnabled();
        long cycleInterval = resolveCycleIntervalMs();
        String signature = (innerText == null ? "" : innerText) + "|cycle=" + cycleEnabled + "|interval=" + cycleInterval;
        if (signature.equals(compiledSignature)) return;

        compiledSignature = signature;
        displaySpec = SlotExpressionCompiler.compile(innerText, cycleEnabled, cycleInterval);
        candidateIndex = 0;
        nextRotateAtMillis = 0L;
    }

    private boolean resolveInteractive() {
        if (isRecipeSlot()) return false;
        Boolean cssFlag = parseBooleanLike(getCustomPropertyInherit("--aui-slot-interactive"));
        if (cssFlag != null) return cssFlag;
        Boolean attrFlag = parseBooleanLike(getAttribute("interactive"));
        if (attrFlag != null) return attrFlag;
        Boolean pointerFlag = parseBooleanLike(getAttribute("pointer"));
        if (pointerFlag != null) return pointerFlag;
        return isBoundToMenuSlot();
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

    private static String normalizeToken(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT);
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

    private static Integer parseNonNegativeInt(String raw) {
        Integer parsed = parseInt(raw);
        return parsed != null && parsed >= 0 ? parsed : null;
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
}
