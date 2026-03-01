package com.sighs.apricityui.instance.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一槽位元素：bound 与 virtual 都通过 mcSlot 单通路读取/写入展示物品。
 */
@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Slot extends MinecraftElement {
    public static final String TAG_NAME = "SLOT";
    public static final String MODE_BOUND = "bound";
    public static final String MODE_VIRTUAL = "virtual";
    public static final ResourceLocation FURNACE_FUEL_VIRTUAL_TAG = new ResourceLocation(ApricityUI.MODID, "furnace_fuels");
    private static final String GENERATED_REPEAT = "slot-repeat";
    private static final String GENERATED_REPEAT_RUNTIME_LEGACY = "slot-repeat-runtime";
    private static final String GENERATED_PLAYER_AUTO = "player-auto";
    private static final long DEFAULT_ROTATE_INTERVAL_MS = 1000L;
    private static final String TAG_CANDIDATE_CACHE_KEY = "minecraft-element:slot-tag-candidates";

    static {
        Element.register(TAG_NAME, (document, string) -> new Slot(document));
    }

    private final SimpleContainer virtualContainer = new SimpleContainer(1);
    private final net.minecraft.world.inventory.Slot virtualMcSlot = new net.minecraft.world.inventory.Slot(virtualContainer, 0, 0, 0);
    private net.minecraft.world.inventory.Slot mcSlot = virtualMcSlot;

    private String resolvedItemToken = "";
    private ResourceLocation resolvedTagId = null;
    private List<ItemStack> candidates = List.of();
    private int candidateIndex = 0;
    private long nextRotateAtMillis = 0L;

    public Slot(Document document) {
        super(document, TAG_NAME);
    }

    /**
     * 清理运行时自动生成的槽位（repeat/player-auto）。
     */
    public static void cleanupRuntimeGeneratedSlots(Document document) {
        if (document == null) return;
        List<Element> snapshot = new ArrayList<>(document.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Slot slot)) continue;
            if (!slot.isRuntimeGeneratedSlot()) continue;
            slot.remove();
        }
    }

    public static String furnaceFuelVirtualTagLiteral() {
        return "#" + FURNACE_FUEL_VIRTUAL_TAG;
    }

    public static void clearCandidateCache() {
        clearGlobalCache(TAG_CANDIDATE_CACHE_KEY);
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, List<ItemStack>> tagCandidateCache() {
        return (Map<ResourceLocation, List<ItemStack>>) computeGlobalCacheIfAbsent(
                TAG_CANDIDATE_CACHE_KEY,
                ConcurrentHashMap::new
        );
    }

    private static List<ItemStack> buildCandidatesByTag(ResourceLocation tagId) {
        ArrayList<ItemStack> result = new ArrayList<>();
        if (FURNACE_FUEL_VIRTUAL_TAG.equals(tagId)) {
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) <= 0) continue;
                result.add(stack);
            }
        } else {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (stack.is(tagKey)) result.add(stack);
            }
        }
        result.sort(Comparator.comparing(stack -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id == null ? "" : id.toString();
        }));
        return List.copyOf(result);
    }

    private static int parsePositiveInt(String raw, int fallback) {
        if (StringUtils.isNullOrEmptyEx(raw)) return fallback;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parsePositiveLong(String raw, long fallback) {
        if (StringUtils.isNullOrEmptyEx(raw)) return fallback;
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String getFirstNonBlankAttribute(String... keys) {
        if (keys == null) return null;
        for (String key : keys) {
            if (StringUtils.isNullOrEmptyEx(key)) continue;
            String value = getAttribute(key);
            if (StringUtils.isNullOrEmptyEx(value)) continue;
            return value;
        }
        return null;
    }

    public void bindMcSlot(net.minecraft.world.inventory.Slot slot) {
        mcSlot = slot == null ? virtualMcSlot : slot;
    }

    public net.minecraft.world.inventory.Slot getMcSlot() {
        return mcSlot == null ? virtualMcSlot : mcSlot;
    }

    private ApricityContainerMenu.UiSlot resolveUiSlot() {
        if (!(getMcSlot() instanceof ApricityContainerMenu.UiSlot uiSlot)) return null;
        return uiSlot;
    }

    public String getMode() {
        String rawMode = getAttribute("mode");
        if (StringUtils.isNotNullOrEmptyEx(rawMode)) {
            String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
            if (MODE_BOUND.equals(normalized)) return MODE_BOUND;
            if (MODE_VIRTUAL.equals(normalized)) return MODE_VIRTUAL;
        }
        return hasAncestor(Container.class) ? MODE_BOUND : MODE_VIRTUAL;
    }

    @Override
    public MinecraftBindingMode getBindingMode() {
        return MODE_BOUND.equals(getMode()) ? MinecraftBindingMode.BOUND : MinecraftBindingMode.VIRTUAL;
    }

    public int getRepeatCount() {
        return parsePositiveInt(getAttribute("repeat"), 1);
    }

    public boolean isGeneratedSlot() {
        String source = getGeneratedSourceTag();
        return GENERATED_REPEAT.equals(source)
                || GENERATED_REPEAT_RUNTIME_LEGACY.equals(source)
                || GENERATED_PLAYER_AUTO.equals(source);
    }

    public boolean isRuntimeGeneratedRepeatCopy() {
        return GENERATED_REPEAT.equals(getGeneratedSourceTag());
    }

    public boolean isPlayerAutoGenerated() {
        return GENERATED_PLAYER_AUTO.equals(getGeneratedSourceTag());
    }

    private String getGeneratedSourceTag() {
        return getAttribute("data-generated");
    }

    private boolean isRuntimeGeneratedSlot() {
        String source = getGeneratedSourceTag();
        return GENERATED_REPEAT.equals(source)
                || GENERATED_REPEAT_RUNTIME_LEGACY.equals(source)
                || GENERATED_PLAYER_AUTO.equals(source);
    }

    public int getSlotIndex() {
        String raw = getAttribute("slot-index");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public boolean isDisabled() {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundMode() && uiSlot != null) {
            return uiSlot.isUiDisabled();
        }
        return Boolean.parseBoolean(getAttribute("disabled"));
    }

    public boolean shouldAcceptPointer() {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundMode() && uiSlot != null) {
            return uiSlot.isUiAcceptPointer();
        }
        String pointerRaw = getFirstNonBlankAttribute("pointer");
        Boolean parsed = ContainerSchema.Descriptor.SlotVisualProfile.parsePointerFlag(pointerRaw);
        return parsed == null || parsed;
    }

    public int resolveSlotSizeHint(int fallback) {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundMode() && uiSlot != null) {
            return Math.max(1, uiSlot.getUiSlotSize());
        }
        Integer parsed = ContainerSchema.Descriptor.SlotVisualProfile.parsePositiveInt(getFirstNonBlankAttribute("size", "slot-size"));
        if (parsed == null) return Math.max(1, fallback);
        return parsed;
    }

    public boolean shouldRenderBackground() {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundMode() && uiSlot != null) {
            return uiSlot.isUiRenderBackground();
        }
        ContainerSchema.Descriptor.SlotVisualProfile.RenderRule parsed =
                ContainerSchema.Descriptor.SlotVisualProfile.parseRenderRule(getAttribute("render"));
        return parsed == null || parsed.renderBackground();
    }

    public boolean shouldRenderItem() {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundMode() && uiSlot != null) {
            return uiSlot.isUiRenderItem();
        }
        ContainerSchema.Descriptor.SlotVisualProfile.RenderRule parsed =
                ContainerSchema.Descriptor.SlotVisualProfile.parseRenderRule(getAttribute("render"));
        return parsed == null || parsed.renderItem();
    }

    public float resolveIconScale(float fallback) {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundMode() && uiSlot != null) {
            return Math.max(0.01F, uiSlot.getUiIconScale());
        }
        Float parsed = ContainerSchema.Descriptor.SlotVisualProfile.parsePositiveFloat(getFirstNonBlankAttribute("iconScale"));
        if (parsed == null) return Math.max(0.01F, fallback);
        return parsed;
    }

    public int resolveItemPadding(int fallback) {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundMode() && uiSlot != null) {
            return Math.max(0, uiSlot.getUiPadding());
        }
        Integer parsed = ContainerSchema.Descriptor.SlotVisualProfile.parseNonNegativeInt(getFirstNonBlankAttribute("padding"));
        if (parsed == null) return Math.max(0, fallback);
        return parsed;
    }

    public int resolveZIndex(int fallback) {
        ApricityContainerMenu.UiSlot uiSlot = resolveUiSlot();
        if (isBoundMode() && uiSlot != null) {
            return uiSlot.getUiZIndex();
        }
        Integer parsed = ContainerSchema.Descriptor.SlotVisualProfile.parseSignedInt(getFirstNonBlankAttribute("zIndex", "z"));
        if (parsed == null) return fallback;
        return parsed;
    }

    @Override
    public boolean canFocus() {
        return isBoundMode();
    }

    /**
     * 生成 repeat 展开的真实 slot 子节点，并挂载到文档树。
     */
    public Slot createRuntimeRepeatSlotNode(int localSlotIndex, int repeatIndex) {
        if (document == null || parentElement == null) return null;
        if (repeatIndex <= 0) return null;

        Slot clone = new Slot(document);
        LinkedHashMap<String, String> updates = new LinkedHashMap<>(getAttributes());
        updates.remove("id");
        updates.put("repeat", "1");
        updates.put("data-generated", GENERATED_REPEAT);
        updates.put("data-repeat-index", String.valueOf(repeatIndex));
        if (localSlotIndex >= 0) {
            updates.put("slot-index", String.valueOf(localSlotIndex));
        } else {
            updates.remove("slot-index");
        }
        clone.setAttributesBatch(updates, true);
        clone.innerText = innerText;

        parentElement.append(clone);
        clone.cssCache = new HashMap<>(cssCache);
        clone.getRenderer().computedStyle.clear();
        return clone;
    }

    public String getBackgroundImageCandidate() {
        com.sighs.apricityui.style.Background background = com.sighs.apricityui.style.Background.of(this);
        String rawPath = background == null ? null : background.imagePath;
        if (StringUtils.isNullOrEmptyEx(rawPath) || "unset".equals(rawPath)) return null;
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
        if (!isVirtualMode()) return;

        refreshCandidatesIfNeeded();
        if (candidates.isEmpty()) {
            getMcSlot().set(ItemStack.EMPTY);
            return;
        }

        if (candidateIndex < 0 || candidateIndex >= candidates.size()) {
            candidateIndex = 0;
        }

        long now = System.currentTimeMillis();
        long interval = Math.max(200L, parsePositiveLong(getAttribute("rotate-interval"), DEFAULT_ROTATE_INTERVAL_MS));
        if (candidates.size() > 1 && !isHover) {
            if (nextRotateAtMillis <= 0L) {
                nextRotateAtMillis = now + interval;
            } else if (now >= nextRotateAtMillis) {
                candidateIndex = (candidateIndex + 1) % candidates.size();
                nextRotateAtMillis = now + interval;
            }
        }

        ItemStack stack = candidates.get(candidateIndex).copy();
        if (stack.getCount() <= 0) {
            stack.setCount(1);
        }
        getMcSlot().set(stack);
    }

    public ItemStack resolveDisplayStack() {
        ItemStack stack = getMcSlot().getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copy();
    }

    @Override
    public ItemStack getTooltipStack() {
        if (!isVirtualMode()) return ItemStack.EMPTY;
        ItemStack stack = getMcSlot().getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return stack;
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
                "class", StringUtils.nullToEmpty(className),
                "data-generated", StringUtils.nullToEmpty(generatedTag)
        ), true);
    }

    /**
     * 批量设置 player 自动注入槽位的公共属性。
     */
    public void applyImplicitPlayerMeta(int localSlotIndex, String part) {
        setAttributesBatch(Map.of(
                "mode", MODE_BOUND,
                "slot-index", String.valueOf(Math.max(0, localSlotIndex)),
                "data-generated", GENERATED_PLAYER_AUTO,
                "part", part != null ? part : "inv"
        ), true);
    }

    private void refreshCandidatesIfNeeded() {
        String normalized = resolveItemLiteral();
        if (normalized.equals(resolvedItemToken)) return;

        resolvedItemToken = normalized;
        resolvedTagId = null;
        candidates = List.of();
        candidateIndex = 0;
        nextRotateAtMillis = 0L;

        if (normalized.isBlank()) return;
        if (normalized.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(normalized.substring(1));
            if (tagId == null) return;
            resolvedTagId = tagId;
            candidates = tagCandidateCache().computeIfAbsent(tagId, Slot::buildCandidatesByTag);
            return;
        }

        ItemStack parsed = parseItemLiteral(normalized);
        if (!parsed.isEmpty()) {
            candidates = List.of(parsed);
        }
    }

    private String resolveItemLiteral() {
        return normalizeItemLiteral(innerText);
    }

    private static String normalizeItemLiteral(String raw) {
        if (StringUtils.isNullOrEmpty(raw)) return "";
        String normalized = raw.trim();
        if (normalized.isBlank()) return "";
        if (normalized.length() >= 2) {
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            boolean quoted = (first == '"' && last == '"') || (first == '\'' && last == '\'');
            if (quoted) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }
        return normalized;
    }

    public static String buildLiteralWithCount(String rawLiteral, int requestedCount) {
        String normalized = normalizeItemLiteral(rawLiteral);
        if (normalized.isBlank()) return "";

        ItemStack parsed = parseItemLiteral(normalized);
        if (parsed.isEmpty()) return normalized;

        int safeCount = Math.max(1, Math.min(parsed.getMaxStackSize(), requestedCount));
        parsed.setCount(safeCount);
        CompoundTag stackTag = new CompoundTag();
        parsed.save(stackTag);
        return stackTag.toString();
    }

    private static ItemStack parseItemLiteral(String literal) {
        if (StringUtils.isNullOrEmptyEx(literal)) return ItemStack.EMPTY;

        if (literal.startsWith("{") && literal.endsWith("}")) {
            try {
                CompoundTag stackTag = TagParser.parseTag(literal);
                ItemStack parsed = ItemStack.of(stackTag);
                return parsed == null ? ItemStack.EMPTY : parsed;
            } catch (CommandSyntaxException ignored) {
                return ItemStack.EMPTY;
            }
        }

        int nbtStart = literal.indexOf('{');
        String itemLiteral = nbtStart >= 0 ? literal.substring(0, nbtStart).trim() : literal;
        if (itemLiteral.isBlank()) return ItemStack.EMPTY;

        ResourceLocation itemId = ResourceLocation.tryParse(itemLiteral);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item);

        if (nbtStart >= 0) {
            String nbtLiteral = literal.substring(nbtStart).trim();
            try {
                CompoundTag nbtTag = TagParser.parseTag(nbtLiteral);
                stack.setTag(nbtTag);
            } catch (CommandSyntaxException ignored) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }
}
