package com.sighs.apricityui.instance.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Recipe extends MinecraftElement {
    public static final String TAG_NAME = "RECIPE";
    private static final int DEFAULT_SLOT_SIZE = 16;
    private static final int DEFAULT_GAP = 2;
    private static final int STONECUTTING_LIST_VISIBLE_ROWS = 3;
    private static final String PREVIEW_CACHE_KEY = "minecraft-element:recipe-preview-cache";

    static {
        Element.register(TAG_NAME, (document, string) -> new Recipe(document));
    }

    private boolean previewInitialized = false;

    public Recipe(Document document) {
        super(document, TAG_NAME);
    }

    private static String appendStyle(String style, String declaration) {
        if (style == null || style.isBlank()) return declaration;
        String trimmed = style.trim();
        if (trimmed.endsWith(";")) return trimmed + declaration;
        return trimmed + ";" + declaration;
    }

    private static int parsePositive(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseNonNegativePx(String raw, int fallback) {
        int parsed = com.sighs.apricityui.style.Size.parse(raw);
        return parsed >= 0 ? parsed : Math.max(0, fallback);
    }

    private int resolveColumnGapPx() {
        int sharedGap = parseNonNegativePx(getComputedStyle().gap, DEFAULT_GAP);
        return parseNonNegativePx(getComputedStyle().columnGap, sharedGap);
    }

    private int resolveRowGapPx() {
        int sharedGap = parseNonNegativePx(getComputedStyle().gap, DEFAULT_GAP);
        return parseNonNegativePx(getComputedStyle().rowGap, sharedGap);
    }

    // 预览缓存中的坐标使用默认步长，渲染时按当前 CSS gap 等比映射。
    private static int remapPreviewCoordinate(int raw, int sourceStep, int targetStep) {
        int normalized = Math.max(0, raw);
        if (sourceStep <= 0 || targetStep <= 0) return normalized;
        if (normalized % sourceStep != 0) return normalized;
        return normalized / sourceStep * targetStep;
    }

    private static RecipePreview buildPreview(ResourceLocation recipeId, RecipeDeclaredType declaredType) {
        if (declaredType == null) {
            return RecipePreview.empty("Recipe type is required");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return RecipePreview.empty("Client level is not available");
        }

        RecipeManager recipeManager = minecraft.level.getRecipeManager();
        if (recipeManager == null) {
            return RecipePreview.empty("Recipe manager is not available");
        }

        Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> recipeOptional = recipeManager.byKey(recipeId);
        if (recipeOptional.isEmpty()) {
            return RecipePreview.empty("Recipe not found");
        }

        net.minecraft.world.item.crafting.Recipe<?> recipe = recipeOptional.get();
        if (!declaredType.matches(recipe)) {
            return RecipePreview.empty("Recipe type mismatch: declared=%s, actual=%s"
                    .formatted(declaredType.id(), recipe.getClass().getSimpleName()));
        }

        RegistryAccess registryAccess = minecraft.level.registryAccess();
        int step = DEFAULT_SLOT_SIZE + DEFAULT_GAP;
        ArrayList<PreviewEntry> absoluteEntries = new ArrayList<>();
        ArrayList<PreviewEntry> listEntries = new ArrayList<>();
        RecipeLayoutKind layoutKind = declaredType.layoutKind();

        switch (declaredType) {
            case CRAFTING_SHAPED -> {
                if (!(recipe instanceof ShapedRecipe shapedRecipe)) {
                    return RecipePreview.empty("Declared crafting_shaped but recipe is not ShapedRecipe");
                }
                buildShapedCraftingEntries(shapedRecipe, step, absoluteEntries, registryAccess);
            }
            case CRAFTING_SHAPELESS -> {
                if (!(recipe instanceof CraftingRecipe craftingRecipe) || recipe instanceof ShapedRecipe) {
                    return RecipePreview.empty("Declared crafting_shapeless but recipe is not shapeless crafting");
                }
                buildShapelessCraftingEntries(craftingRecipe, step, absoluteEntries, registryAccess);
            }
            case SMELTING, BLASTING, SMOKING, CAMPFIRE_COOKING -> {
                if (!(recipe instanceof AbstractCookingRecipe cookingRecipe)) {
                    return RecipePreview.empty("Declared cooking family but recipe is not AbstractCookingRecipe");
                }
                buildCookingEntries(cookingRecipe, step, absoluteEntries, registryAccess);
            }
            case STONECUTTING -> {
                if (!(recipe instanceof StonecutterRecipe stonecutterRecipe)) {
                    return RecipePreview.empty("Declared stonecutting but recipe is not StonecutterRecipe");
                }
                buildStonecuttingEntries(stonecutterRecipe, recipeManager, step, absoluteEntries, listEntries, registryAccess);
            }
            case SMITHING -> {
                if (!(recipe instanceof SmithingRecipe smithingRecipe)) {
                    return RecipePreview.empty("Declared smithing but recipe is not SmithingRecipe");
                }
                buildSmithingEntries(smithingRecipe, step, absoluteEntries, registryAccess);
            }
            case FALLBACK -> buildFallbackEntries(recipe, step, absoluteEntries, registryAccess);
        }

        return new RecipePreview(layoutKind, List.copyOf(absoluteEntries), List.copyOf(listEntries), "");
    }

    private static void buildShapedCraftingEntries(
            ShapedRecipe recipe,
            int step,
            List<PreviewEntry> output,
            RegistryAccess registryAccess
    ) {
        int width = Math.max(1, recipe.getWidth());
        int height = Math.max(1, recipe.getHeight());
        List<Ingredient> ingredients = recipe.getIngredients();

        int maxCount = Math.min(ingredients.size(), width * height);
        for (int index = 0; index < maxCount; index++) {
            Ingredient ingredient = ingredients.get(index);
            ItemStack stack = pickDisplayStack(ingredient);
            int x = (index % width) * step;
            int y = (index / width) * step;
            PreviewEntry entry = toEntry(stack, x, y, PreviewRole.INPUT);
            if (entry != null) output.add(entry);
        }

        ItemStack result = recipe.getResultItem(registryAccess);
        PreviewEntry resultEntry = toEntry(result, step * 4, step, PreviewRole.OUTPUT);
        if (resultEntry != null) output.add(resultEntry);
    }

    private static void buildShapelessCraftingEntries(
            CraftingRecipe recipe,
            int step,
            List<PreviewEntry> output,
            RegistryAccess registryAccess
    ) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int visualIndex = 0;
        for (Ingredient ingredient : ingredients) {
            ItemStack stack = pickDisplayStack(ingredient);
            if (stack.isEmpty()) continue;

            int x = (visualIndex % 3) * step;
            int y = (visualIndex / 3) * step;
            PreviewEntry entry = toEntry(stack, x, y, PreviewRole.INPUT);
            if (entry != null) output.add(entry);
            visualIndex++;
            if (visualIndex >= 9) break;
        }

        ItemStack result = recipe.getResultItem(registryAccess);
        PreviewEntry resultEntry = toEntry(result, step * 4, step, PreviewRole.OUTPUT);
        if (resultEntry != null) output.add(resultEntry);
    }

    private static void buildCookingEntries(
            AbstractCookingRecipe recipe,
            int step,
            List<PreviewEntry> output,
            RegistryAccess registryAccess
    ) {
        List<Ingredient> ingredients = recipe.getIngredients();
        if (!ingredients.isEmpty()) {
            ItemStack input = pickDisplayStack(ingredients.get(0));
            PreviewEntry inputEntry = toEntry(input, 0, 0, PreviewRole.INPUT);
            if (inputEntry != null) output.add(inputEntry);
        }

        PreviewEntry fuelEntry = toLiteralEntry(
                Slot.furnaceFuelVirtualTagLiteral(),
                1,
                0,
                step * 2,
                PreviewRole.FUEL
        );
        output.add(fuelEntry);

        ItemStack result = recipe.getResultItem(registryAccess);
        PreviewEntry resultEntry = toEntry(result, step * 3, step, PreviewRole.OUTPUT);
        if (resultEntry != null) output.add(resultEntry);
    }

    private static void buildStonecuttingEntries(
            StonecutterRecipe recipe,
            RecipeManager recipeManager,
            int step,
            List<PreviewEntry> absoluteOutput,
            List<PreviewEntry> listOutput,
            RegistryAccess registryAccess
    ) {
        List<Ingredient> ingredients = recipe.getIngredients();
        if (!ingredients.isEmpty()) {
            ItemStack input = pickDisplayStack(ingredients.get(0));
            PreviewEntry inputEntry = toEntry(input, 0, step, PreviewRole.INPUT);
            if (inputEntry != null) absoluteOutput.add(inputEntry);
        }

        ArrayList<ItemStack> outputs = collectStonecuttingOutputs(recipe, recipeManager, registryAccess);
        if (outputs.isEmpty()) {
            ItemStack result = recipe.getResultItem(registryAccess);
            if (!result.isEmpty()) outputs.add(result.copy());
        }

        for (ItemStack stack : outputs) {
            PreviewEntry outputEntry = toEntry(stack, 0, 0, PreviewRole.OUTPUT);
            if (outputEntry != null) listOutput.add(outputEntry);
        }
    }

    private static ArrayList<ItemStack> collectStonecuttingOutputs(
            StonecutterRecipe recipe,
            RecipeManager recipeManager,
            RegistryAccess registryAccess
    ) {
        ArrayList<ItemStack> result = new ArrayList<>();
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return result;

        Ingredient selectedIngredient = ingredients.get(0);
        ItemStack selectedInput = pickDisplayStack(selectedIngredient);
        if (selectedInput.isEmpty()) return result;

        HashSet<ResourceLocation> dedup = new HashSet<>();
        List<StonecutterRecipe> candidates = recipeManager.getAllRecipesFor(RecipeType.STONECUTTING);
        for (StonecutterRecipe candidateRecipe : candidates) {
            List<Ingredient> candidateIngredients = candidateRecipe.getIngredients();
            if (candidateIngredients.isEmpty()) continue;
            Ingredient candidateIngredient = candidateIngredients.get(0);
            if (!(candidateIngredient.test(selectedInput) || selectedIngredient.test(pickDisplayStack(candidateIngredient)))) {
                continue;
            }

            ItemStack candidateResult = candidateRecipe.getResultItem(registryAccess);
            if (candidateResult.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(candidateResult.getItem());
            if (itemId == null || !dedup.add(itemId)) continue;
            result.add(candidateResult.copy());
        }

        result.sort((a, b) -> {
            ResourceLocation ida = BuiltInRegistries.ITEM.getKey(a.getItem());
            ResourceLocation idb = BuiltInRegistries.ITEM.getKey(b.getItem());
            String sa = ida == null ? "" : ida.toString();
            String sb = idb == null ? "" : idb.toString();
            return sa.compareTo(sb);
        });
        return result;
    }

    private static void buildSmithingEntries(
            SmithingRecipe recipe,
            int step,
            List<PreviewEntry> output,
            RegistryAccess registryAccess
    ) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int limit = Math.min(3, ingredients.size());
        for (int index = 0; index < limit; index++) {
            ItemStack stack = pickDisplayStack(ingredients.get(index));
            PreviewRole role = switch (index) {
                case 0 -> PreviewRole.TEMPLATE;
                case 1 -> PreviewRole.INPUT;
                case 2 -> PreviewRole.ADDITION;
                default -> PreviewRole.INPUT;
            };
            PreviewEntry entry = toEntry(stack, 0, index * step, role);
            if (entry != null) output.add(entry);
        }

        ItemStack result = recipe.getResultItem(registryAccess);
        PreviewEntry resultEntry = toEntry(result, step * 4, step, PreviewRole.OUTPUT);
        if (resultEntry != null) output.add(resultEntry);
    }

    private static void buildFallbackEntries(
            net.minecraft.world.item.crafting.Recipe<?> recipe,
            int step,
            List<PreviewEntry> output,
            RegistryAccess registryAccess
    ) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int visualIndex = 0;
        for (Ingredient ingredient : ingredients) {
            ItemStack input = pickDisplayStack(ingredient);
            if (input.isEmpty()) continue;
            PreviewEntry inputEntry = toEntry(input, (visualIndex % 4) * step, (visualIndex / 4) * step, PreviewRole.INPUT);
            if (inputEntry != null) output.add(inputEntry);
            visualIndex++;
            if (visualIndex >= 8) break;
        }

        ItemStack result = recipe.getResultItem(registryAccess);
        PreviewEntry outputEntry = toEntry(result, step * 5, step, PreviewRole.OUTPUT);
        if (outputEntry != null) output.add(outputEntry);
    }

    private static ItemStack pickDisplayStack(Ingredient ingredient) {
        if (ingredient == null) return ItemStack.EMPTY;
        ItemStack[] options = ingredient.getItems();
        if (options == null || options.length == 0) return ItemStack.EMPTY;
        for (ItemStack candidate : options) {
            if (candidate == null || candidate.isEmpty()) continue;
            return candidate.copy();
        }
        return ItemStack.EMPTY;
    }

    private static PreviewEntry toEntry(ItemStack stack, int x, int y, PreviewRole role) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) return null;
        int count = Math.max(1, stack.getCount());
        return new PreviewEntry(role, itemId.toString(), count, x, y);
    }

    private static PreviewEntry toLiteralEntry(
            String itemLiteral,
            int count,
            int x,
            int y,
            PreviewRole role
    ) {
        return new PreviewEntry(role, itemLiteral, Math.max(1, count), x, y);
    }

    /**
     * 确保 recipe 元素已根据 innerText + type 生成内部 virtual slot。
     * <p>
     * 该方法幂等，生命周期内只会初始化一次。
     */
    public boolean ensurePreviewSlots() {
        if (previewInitialized) return false;
        previewInitialized = true;
        boolean changed = false;

        RecipeDeclaredType declaredType = RecipeDeclaredType.fromRaw(getAttribute("type"));
        if (declaredType == null) {
            String message = "Recipe preview skipped: missing/invalid type";
            ApricityUI.LOGGER.warn("{}, type={}", message, getAttribute("type"));
            setAttribute("data-recipe-error", message);
            return true;
        }
        setAttribute("data-recipe-type", declaredType.id());
        changed = true;

        ResourceLocation recipeId = parseRecipeIdFromInnerText();
        if (recipeId == null) {
            String message = "Recipe preview skipped: invalid recipe id in innerText";
            ApricityUI.LOGGER.warn("{}, innerText={}", message, innerText);
            setAttribute("data-recipe-error", message);
            return true;
        }

        changed |= ensurePositionedContainer();
        changed |= clearGeneratedElements();

        RecipePreview preview = RecipePreviewCache.resolve(new RecipeCacheKey(recipeId, declaredType));
        if (preview.absoluteEntries().isEmpty() && preview.listEntries().isEmpty()) {
            if (!preview.message().isBlank()) {
                setAttribute("data-recipe-error", preview.message());
                changed = true;
            }
            return changed;
        }
        if (!getAttribute("data-recipe-error").isBlank()) {
            setAttribute("data-recipe-error", "");
            changed = true;
        }

        int slotSize = parsePositive(getAttribute("slot-size"), DEFAULT_SLOT_SIZE);
        int columnGap = resolveColumnGapPx();
        int rowGap = resolveRowGapPx();
        int sourceStep = DEFAULT_SLOT_SIZE + DEFAULT_GAP;
        int columnStep = slotSize + columnGap;
        int rowStep = slotSize + rowGap;
        ArrayList<PreviewPlacement> placements = new ArrayList<>();

        for (PreviewEntry entry : preview.absoluteEntries()) {
            int mappedX = remapPreviewCoordinate(entry.x(), sourceStep, columnStep);
            int mappedY = remapPreviewCoordinate(entry.y(), sourceStep, rowStep);
            placements.add(appendPreviewSlot(entry, slotSize, mappedX, mappedY));
        }
        if (preview.layoutKind() == RecipeLayoutKind.STONECUTTING) {
            int listX = columnStep * 3;
            int visibleCount = Math.min(STONECUTTING_LIST_VISIBLE_ROWS, preview.listEntries().size());
            for (int index = 0; index < visibleCount; index++) {
                PreviewEntry entry = preview.listEntries().get(index);
                int slotY = index * rowStep;
                placements.add(appendPreviewSlot(entry, slotSize, listX, slotY));
            }
        }
        PreviewBounds previewBounds = computePreviewBounds(placements, slotSize);
        changed |= ensureRuntimeSize(previewBounds);
        if (!placements.isEmpty()) {
            changed = true;
        }
        if (document != null) {
            document.markDirty(this, Drawer.RELAYOUT);
            changed = true;
        }
        return changed;
    }

    private boolean clearGeneratedElements() {
        boolean changed = false;
        List<Element> childrenSnapshot = new ArrayList<>(children);
        for (Element child : childrenSnapshot) {
            String generatedTag = child.getAttribute("data-generated");
            if (generatedTag == null || generatedTag.isBlank()) continue;
            if (!generatedTag.startsWith("recipe")) continue;
            child.remove();
            changed = true;
        }
        return changed;
    }

    private PreviewPlacement appendPreviewSlot(PreviewEntry entry, int slotSize, int x, int y) {
        Slot slot = new Slot(document);
        slot.applyRecipeSlotMeta(
                "recipe-slot recipe-slot-" + entry.role().name().toLowerCase(Locale.ROOT),
                "recipe-generated"
        );
        slot.setAttributesBatch(Map.of(
                "mode", Slot.MODE_VIRTUAL,
                "data-role", entry.role().name().toLowerCase(Locale.ROOT),
                "data-preview-x", String.valueOf(x),
                "data-preview-y", String.valueOf(y),
                "data-preview-absolute", "true",
                "style", "position:absolute;display:block;left:%dpx;top:%dpx;width:%dpx;height:%dpx;"
                        .formatted(x, y, slotSize, slotSize)
        ), true);
        slot.innerText = Slot.buildLiteralWithCount(entry.itemLiteral(), Math.max(1, entry.count()));
        append(slot);
        return new PreviewPlacement(x, y, slotSize);
    }

    private boolean ensurePositionedContainer() {
        String inlineStyle = getAttribute("style");
        String normalized = inlineStyle == null ? "" : inlineStyle.trim();
        String merged = normalized;
        boolean changed = false;

        if (!containsCssProperty(normalized, "position")) {
            merged = appendStyle(merged, "position:relative;");
            changed = true;
        }
        if (!containsCssProperty(normalized, "overflow")) {
            merged = appendStyle(merged, "overflow:hidden;");
            changed = true;
        }

        if (changed) {
            setAttribute("style", merged);
        }
        return changed;
    }

    private boolean ensureRuntimeSize(PreviewBounds bounds) {
        if (bounds == null) return false;
        String inlineStyle = getAttribute("style");
        String normalized = inlineStyle == null ? "" : inlineStyle.trim();
        String merged = normalized;
        boolean changed = false;

        if (!containsCssProperty(normalized, "width")) {
            merged = appendStyle(merged, "width:%dpx;".formatted(Math.max(1, bounds.width())));
            changed = true;
        }
        if (!containsCssProperty(normalized, "height")) {
            merged = appendStyle(merged, "height:%dpx;".formatted(Math.max(1, bounds.height())));
            changed = true;
        }
        if (changed) {
            setAttribute("style", merged);
        }
        return changed;
    }

    private static boolean containsCssProperty(String inlineStyle, String property) {
        if (inlineStyle == null || inlineStyle.isBlank() || property == null || property.isBlank()) return false;
        String expected = property.trim().toLowerCase(Locale.ROOT);
        String[] declarations = inlineStyle.split(";");
        for (String declaration : declarations) {
            if (declaration == null || declaration.isBlank()) continue;
            int split = declaration.indexOf(':');
            if (split < 0) continue;
            String key = declaration.substring(0, split).trim().toLowerCase(Locale.ROOT);
            if (expected.equals(key)) return true;
        }
        return false;
    }

    private ResourceLocation parseRecipeIdFromInnerText() {
        String normalized = normalizeRecipeIdLiteral(innerText);
        if (normalized.isBlank()) return null;
        return ResourceLocation.tryParse(normalized);
    }

    private static String normalizeRecipeIdLiteral(String raw) {
        if (raw == null) return "";
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

    private static PreviewBounds computePreviewBounds(List<PreviewPlacement> placements, int fallbackSlotSize) {
        int slotSizeFallback = Math.max(1, fallbackSlotSize);
        if (placements == null || placements.isEmpty()) {
            return new PreviewBounds(slotSizeFallback, slotSizeFallback);
        }

        int maxRight = 0;
        int maxBottom = 0;
        for (PreviewPlacement placement : placements) {
            if (placement == null) continue;
            int slotSize = Math.max(1, placement.slotSize());
            int right = Math.max(0, placement.x()) + slotSize;
            int bottom = Math.max(0, placement.y()) + slotSize;
            if (right > maxRight) maxRight = right;
            if (bottom > maxBottom) maxBottom = bottom;
        }
        return new PreviewBounds(Math.max(slotSizeFallback, maxRight), Math.max(slotSizeFallback, maxBottom));
    }

    public enum PreviewRole {
        INPUT,
        FUEL,
        TEMPLATE,
        ADDITION,
        OUTPUT
    }

    private enum RecipeLayoutKind {
        CRAFTING_SHAPED,
        CRAFTING_SHAPELESS,
        SMELTING_FAMILY,
        STONECUTTING,
        SMITHING,
        FALLBACK
    }

    public enum RecipeDeclaredType {
        CRAFTING_SHAPED("crafting_shaped", RecipeLayoutKind.CRAFTING_SHAPED) {
            @Override
            boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                return recipe instanceof ShapedRecipe;
            }
        },
        CRAFTING_SHAPELESS("crafting_shapeless", RecipeLayoutKind.CRAFTING_SHAPELESS) {
            @Override
            boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                return recipe instanceof CraftingRecipe && !(recipe instanceof ShapedRecipe);
            }
        },
        SMELTING("smelting", RecipeLayoutKind.SMELTING_FAMILY) {
            @Override
            boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                return recipe instanceof AbstractCookingRecipe && recipe.getType() == RecipeType.SMELTING;
            }
        },
        BLASTING("blasting", RecipeLayoutKind.SMELTING_FAMILY) {
            @Override
            boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                return recipe instanceof AbstractCookingRecipe && recipe.getType() == RecipeType.BLASTING;
            }
        },
        SMOKING("smoking", RecipeLayoutKind.SMELTING_FAMILY) {
            @Override
            boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                return recipe instanceof AbstractCookingRecipe && recipe.getType() == RecipeType.SMOKING;
            }
        },
        CAMPFIRE_COOKING("campfire_cooking", RecipeLayoutKind.SMELTING_FAMILY) {
            @Override
            boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                return recipe instanceof AbstractCookingRecipe && recipe.getType() == RecipeType.CAMPFIRE_COOKING;
            }
        },
        STONECUTTING("stonecutting", RecipeLayoutKind.STONECUTTING) {
            @Override
            boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                return recipe instanceof StonecutterRecipe;
            }
        },
        SMITHING("smithing", RecipeLayoutKind.SMITHING) {
            @Override
            boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                return recipe instanceof SmithingRecipe;
            }
        },
        FALLBACK("fallback", RecipeLayoutKind.FALLBACK) {
            @Override
            boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                return recipe != null;
            }
        };

        private final String id;
        private final RecipeLayoutKind layoutKind;

        RecipeDeclaredType(String id, RecipeLayoutKind layoutKind) {
            this.id = id;
            this.layoutKind = layoutKind;
        }

        public String id() {
            return id;
        }

        public RecipeLayoutKind layoutKind() {
            return layoutKind;
        }

        abstract boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe);

        static RecipeDeclaredType fromRaw(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (RecipeDeclaredType value : values()) {
                if (value.id.equals(normalized)) return value;
            }
            return null;
        }
    }

    private record RecipePreview(
            RecipeLayoutKind layoutKind,
            List<PreviewEntry> absoluteEntries,
            List<PreviewEntry> listEntries,
            String message
    ) {
        private static RecipePreview empty(String message) {
            return new RecipePreview(RecipeLayoutKind.FALLBACK, List.of(), List.of(), message == null ? "" : message);
        }
    }

    private static final class RecipePreviewCache {
        private static final int MAX_CACHE_SIZE = 256;

        @SuppressWarnings("unchecked")
        private static LinkedHashMap<RecipeCacheKey, RecipePreview> cache() {
            return (LinkedHashMap<RecipeCacheKey, RecipePreview>) computeGlobalCacheIfAbsent(
                    PREVIEW_CACHE_KEY,
                    () -> new LinkedHashMap<RecipeCacheKey, RecipePreview>(64, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<RecipeCacheKey, RecipePreview> eldest) {
                            return size() > MAX_CACHE_SIZE;
                        }
                    }
            );
        }

        /**
         * 读取或构建配方预览缓存。
         */
        private static synchronized RecipePreview resolve(RecipeCacheKey cacheKey) {
            if (cacheKey == null || cacheKey.recipeId() == null || cacheKey.declaredType() == null) {
                return RecipePreview.empty("Recipe cache key is invalid");
            }
            LinkedHashMap<RecipeCacheKey, RecipePreview> cache = cache();
            RecipePreview cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            RecipePreview preview = buildPreview(cacheKey.recipeId(), cacheKey.declaredType());
            cache.put(cacheKey, preview);
            return preview;
        }

        /**
         * 清空本地预览缓存。
         */
        private static synchronized void clear() {
            clearGlobalCache(PREVIEW_CACHE_KEY);
        }
    }

    public record PreviewEntry(
            PreviewRole role,
            String itemLiteral,
            int count,
            int x,
            int y
    ) {
    }

    private record PreviewBounds(int width, int height) {
    }

    private record PreviewPlacement(int x, int y, int slotSize) {
    }

    private record RecipeCacheKey(ResourceLocation recipeId, RecipeDeclaredType declaredType) {
    }

    @Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onRecipesUpdated(RecipesUpdatedEvent event) {
            // 配方重载后清缓存，当前设计要求重新打开 Screen 才会刷新展示。
            RecipePreviewCache.clear();
            Slot.clearCandidateCache();
        }
    }
}
