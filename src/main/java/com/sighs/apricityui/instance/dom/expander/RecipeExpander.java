package com.sighs.apricityui.instance.dom.expander;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.element.Recipe;
import com.sighs.apricityui.instance.element.Slot;
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

/**
 * 在文档刷新阶段触发 recipe DOM 预览槽位生成。
 */
public final class RecipeExpander {
    public static void expand(Document document) {
        if (document == null) return;
        ArrayList<Element> snapshot = new ArrayList<>(document.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Recipe recipe)) continue;
            expandSingleRecipe(document, recipe);
        }
    }

    private static void expandSingleRecipe(Document document, Recipe recipe) {
        boolean changed = recipe.clearGeneratedRecipeSlots();

        RecipeResolver.DeclaredType declaredType = RecipeResolver.DeclaredType.fromRaw(recipe.getAttribute("type"));
        if (declaredType == null) {
            String message = "Recipe preview skipped: missing/invalid type";
            ApricityUI.LOGGER.warn("{}, type={}", message, recipe.getAttribute("type"));
            changed |= setAttributeIfChanged(recipe, "data-recipe-type", "");
            changed |= setAttributeIfChanged(recipe, "data-recipe-layout", "");
            changed |= setAttributeIfChanged(recipe, "data-recipe-error", message);
            if (changed) document.markDirty(recipe, Drawer.RELAYOUT);
            return;
        }

        changed |= setAttributeIfChanged(recipe, "data-recipe-type", declaredType.id());

        ResourceLocation recipeId = recipe.parseRecipeIdFromInnerText();
        if (recipeId == null) {
            String message = "Recipe preview skipped: invalid recipe id in innerText";
            ApricityUI.LOGGER.warn("{}, innerText={}", message, recipe.innerText);
            changed |= setAttributeIfChanged(recipe, "data-recipe-layout", "");
            changed |= setAttributeIfChanged(recipe, "data-recipe-error", message);
            if (changed) document.markDirty(recipe, Drawer.RELAYOUT);
            return;
        }

        RecipeResolver.ResolveResult preview = RecipeResolver.resolve(recipeId, declaredType);
        changed |= setAttributeIfChanged(
                recipe,
                "data-recipe-layout",
                preview.layoutKind().name().toLowerCase(Locale.ROOT)
        );

        if (preview.absoluteEntries().isEmpty() && preview.listEntries().isEmpty()) {
            changed |= setAttributeIfChanged(recipe, "data-recipe-error", preview.message());
            if (changed) document.markDirty(recipe, Drawer.RELAYOUT);
            return;
        }

        changed |= setAttributeIfChanged(recipe, "data-recipe-error", "");

        EnumMap<RecipeResolver.PreviewRole, Integer> roleOrderCursor = new EnumMap<>(RecipeResolver.PreviewRole.class);
        int appendedCount = 0;
        for (RecipeResolver.PreviewEntry entry : preview.absoluteEntries()) {
            appendPreviewSlot(document, recipe, entry, nextRoleIndex(roleOrderCursor, entry.role()), "absolute");
            appendedCount++;
        }
        if (preview.layoutKind() == RecipeResolver.LayoutKind.STONECUTTING) {
            int visibleCount = Math.min(RecipeResolver.STONECUTTING_LIST_VISIBLE_ROWS, preview.listEntries().size());
            for (int index = 0; index < visibleCount; index++) {
                RecipeResolver.PreviewEntry entry = preview.listEntries().get(index);
                appendPreviewSlot(document, recipe, entry, nextRoleIndex(roleOrderCursor, entry.role()), "list");
                appendedCount++;
            }
        }
        if (appendedCount > 0) {
            changed = true;
        }
        if (changed) {
            document.markDirty(recipe, Drawer.RELAYOUT);
        }
    }

    private static int nextRoleIndex(
            EnumMap<RecipeResolver.PreviewRole, Integer> roleOrderCursor,
            RecipeResolver.PreviewRole role
    ) {
        int next = roleOrderCursor.getOrDefault(role, 0);
        roleOrderCursor.put(role, next + 1);
        return next;
    }

    private static String toRoleClass(RecipeResolver.PreviewRole role) {
        if (role == RecipeResolver.PreviewRole.FUEL) return "aui-recipe-fuel";
        if (role == RecipeResolver.PreviewRole.OUTPUT) return "aui-recipe-output";
        return "aui-recipe-input";
    }

    private static void appendPreviewSlot(
            Document document,
            Recipe recipe,
            RecipeResolver.PreviewEntry entry,
            int roleIndex,
            String group
    ) {
        if (entry == null) return;
        Slot slot = new Slot(document);
        String roleName = entry.role().name().toLowerCase(Locale.ROOT);
        slot.applyRecipeSlotMeta(buildPreviewSlotClassName(entry.role(), roleName), "recipe-slot");
        slot.setAttributesBatch(Map.of(
                "data-role", roleName,
                "data-i", String.valueOf(Math.max(0, roleIndex)),
                "data-group", group == null ? "absolute" : group,
                "interactive", "0",
                "pointer", "0",
                "style", "--aui-slot-interactive:0;"
        ), true);
        slot.innerText = entry.slotExpression();
        recipe.append(slot);
    }

    private static String buildPreviewSlotClassName(RecipeResolver.PreviewRole role, String roleName) {
        LinkedHashSet<String> classNames = new LinkedHashSet<>();
        classNames.add("recipe-slot");
        classNames.add("recipe-slot-" + roleName);
        classNames.add("aui-recipe-slot");
        classNames.add(toRoleClass(role));
        classNames.add("aui-recipe-" + roleName);
        return String.join(" ", classNames);
    }

    private static boolean setAttributeIfChanged(Recipe recipe, String key, String value) {
        String normalized = value == null ? "" : value;
        if (Objects.equals(recipe.getAttribute(key), normalized)) return false;
        recipe.setAttribute(key, normalized);
        return true;
    }

    /**
     * 解析客户端配方并生成 UI 预览槽位数据。
     */
    private static final class RecipeResolver {
        public static final int STONECUTTING_LIST_VISIBLE_ROWS = 3;

        private static final String AIR_ITEM_LITERAL = "minecraft:air";
        private static final int MAX_CACHE_SIZE = 256;
        private static final LinkedHashMap<RecipeCacheKey, ResolveResult> CACHE =
                new LinkedHashMap<RecipeCacheKey, ResolveResult>(64, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<RecipeCacheKey, ResolveResult> eldest) {
                        return size() > MAX_CACHE_SIZE;
                    }
                };

        private RecipeResolver() {
        }

        public static synchronized ResolveResult resolve(ResourceLocation recipeId, DeclaredType declaredType) {
            if (recipeId == null || declaredType == null) {
                return ResolveResult.empty("Recipe cache key is invalid");
            }
            RecipeCacheKey cacheKey = new RecipeCacheKey(recipeId, declaredType);
            ResolveResult cached = CACHE.get(cacheKey);
            if (cached != null) return cached;
            ResolveResult resolved = buildPreview(recipeId, declaredType);
            CACHE.put(cacheKey, resolved);
            return resolved;
        }

        public static synchronized void clearCache() {
            CACHE.clear();
        }

        private static ResolveResult buildPreview(ResourceLocation recipeId, DeclaredType declaredType) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return ResolveResult.empty("Client level is not available");
            }

            RecipeManager recipeManager = minecraft.level.getRecipeManager();
            if (recipeManager == null) {
                return ResolveResult.empty("Recipe manager is not available");
            }

            Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> recipeOptional = recipeManager.byKey(recipeId);
            if (recipeOptional.isEmpty()) {
                return ResolveResult.empty("Recipe not found");
            }

            net.minecraft.world.item.crafting.Recipe<?> recipe = recipeOptional.get();
            if (!declaredType.matches(recipe)) {
                return ResolveResult.empty("Recipe type mismatch: declared=%s, actual=%s"
                        .formatted(declaredType.id(), recipe.getClass().getSimpleName()));
            }

            RegistryAccess registryAccess = minecraft.level.registryAccess();
            ArrayList<PreviewEntry> absoluteEntries = new ArrayList<>();
            ArrayList<PreviewEntry> listEntries = new ArrayList<>();
            LayoutKind layoutKind = declaredType.layoutKind();

            switch (declaredType) {
                case CRAFTING_SHAPED -> {
                    if (!(recipe instanceof ShapedRecipe shapedRecipe)) {
                        return ResolveResult.empty("Declared crafting_shaped but recipe is not ShapedRecipe");
                    }
                    buildShapedCraftingEntries(shapedRecipe, absoluteEntries, registryAccess);
                }
                case CRAFTING_SHAPELESS -> {
                    if (!(recipe instanceof CraftingRecipe craftingRecipe) || recipe instanceof ShapedRecipe) {
                        return ResolveResult.empty("Declared crafting_shapeless but recipe is not shapeless crafting");
                    }
                    buildShapelessCraftingEntries(craftingRecipe, absoluteEntries, registryAccess);
                }
                case SMELTING, BLASTING, SMOKING, CAMPFIRE_COOKING -> {
                    if (!(recipe instanceof AbstractCookingRecipe cookingRecipe)) {
                        return ResolveResult.empty("Declared cooking family but recipe is not AbstractCookingRecipe");
                    }
                    buildCookingEntries(cookingRecipe, absoluteEntries, registryAccess);
                }
                case STONECUTTING -> {
                    if (!(recipe instanceof StonecutterRecipe stonecutterRecipe)) {
                        return ResolveResult.empty("Declared stonecutting but recipe is not StonecutterRecipe");
                    }
                    buildStonecuttingEntries(stonecutterRecipe, recipeManager, absoluteEntries, listEntries, registryAccess);
                }
                case SMITHING -> {
                    if (!(recipe instanceof SmithingRecipe smithingRecipe)) {
                        return ResolveResult.empty("Declared smithing but recipe is not SmithingRecipe");
                    }
                    buildSmithingEntries(smithingRecipe, absoluteEntries, registryAccess);
                }
                case FALLBACK -> buildFallbackEntries(recipe, absoluteEntries, registryAccess);
            }

            return new ResolveResult(layoutKind, List.copyOf(absoluteEntries), List.copyOf(listEntries), "");
        }

        private static void buildShapedCraftingEntries(
                ShapedRecipe recipe,
                List<PreviewEntry> output,
                RegistryAccess registryAccess
        ) {
            int width = Math.max(1, recipe.getWidth());
            int height = Math.max(1, recipe.getHeight());
            List<Ingredient> ingredients = recipe.getIngredients();

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    Ingredient ingredient = Ingredient.EMPTY;
                    int shapedIndex = row * width + col;
                    if (row < height && col < width && shapedIndex >= 0 && shapedIndex < ingredients.size()) {
                        ingredient = ingredients.get(shapedIndex);
                    }
                    output.add(toIngredientEntryOrAir(ingredient, PreviewRole.INPUT));
                }
            }

            ItemStack result = recipe.getResultItem(registryAccess);
            output.add(toEntryOrAir(result, PreviewRole.OUTPUT));
        }

        private static void buildShapelessCraftingEntries(
                CraftingRecipe recipe,
                List<PreviewEntry> output,
                RegistryAccess registryAccess
        ) {
            List<Ingredient> ingredients = recipe.getIngredients();
            for (int slot = 0; slot < 9; slot++) {
                Ingredient ingredient = slot < ingredients.size() ? ingredients.get(slot) : Ingredient.EMPTY;
                output.add(toIngredientEntryOrAir(ingredient, PreviewRole.INPUT));
            }

            ItemStack result = recipe.getResultItem(registryAccess);
            output.add(toEntryOrAir(result, PreviewRole.OUTPUT));
        }

        private static void buildCookingEntries(
                AbstractCookingRecipe recipe,
                List<PreviewEntry> output,
                RegistryAccess registryAccess
        ) {
            List<Ingredient> ingredients = recipe.getIngredients();
            if (!ingredients.isEmpty()) {
                PreviewEntry inputEntry = toIngredientEntry(ingredients.get(0), PreviewRole.INPUT);
                if (inputEntry != null) output.add(inputEntry);
            }

            PreviewEntry fuelEntry = toLiteralEntry(
                    Slot.furnaceFuelVirtualTagLiteral(),
                    PreviewRole.FUEL
            );
            output.add(fuelEntry);

            ItemStack result = recipe.getResultItem(registryAccess);
            PreviewEntry resultEntry = toEntry(result, PreviewRole.OUTPUT);
            if (resultEntry != null) output.add(resultEntry);
        }

        private static void buildStonecuttingEntries(
                StonecutterRecipe recipe,
                RecipeManager recipeManager,
                List<PreviewEntry> absoluteOutput,
                List<PreviewEntry> listOutput,
                RegistryAccess registryAccess
        ) {
            List<Ingredient> ingredients = recipe.getIngredients();
            if (!ingredients.isEmpty()) {
                PreviewEntry inputEntry = toIngredientEntry(ingredients.get(0), PreviewRole.INPUT);
                if (inputEntry != null) absoluteOutput.add(inputEntry);
            }

            ArrayList<ItemStack> outputs = collectStonecuttingOutputs(recipe, recipeManager, registryAccess);
            if (outputs.isEmpty()) {
                ItemStack result = recipe.getResultItem(registryAccess);
                if (!result.isEmpty()) outputs.add(result.copy());
            }

            for (ItemStack stack : outputs) {
                PreviewEntry outputEntry = toEntry(stack, PreviewRole.OUTPUT);
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
                List<PreviewEntry> output,
                RegistryAccess registryAccess
        ) {
            List<Ingredient> ingredients = recipe.getIngredients();
            int limit = Math.min(3, ingredients.size());
            for (int index = 0; index < limit; index++) {
                Ingredient ingredient = ingredients.get(index);
                PreviewRole role = switch (index) {
                    case 0 -> PreviewRole.TEMPLATE;
                    case 1 -> PreviewRole.INPUT;
                    case 2 -> PreviewRole.ADDITION;
                    default -> PreviewRole.INPUT;
                };
                PreviewEntry entry = toIngredientEntry(ingredient, role);
                if (entry != null) output.add(entry);
            }

            ItemStack result = recipe.getResultItem(registryAccess);
            PreviewEntry resultEntry = toEntry(result, PreviewRole.OUTPUT);
            if (resultEntry != null) output.add(resultEntry);
        }

        private static void buildFallbackEntries(
                net.minecraft.world.item.crafting.Recipe<?> recipe,
                List<PreviewEntry> output,
                RegistryAccess registryAccess
        ) {
            List<Ingredient> ingredients = recipe.getIngredients();
            int visualIndex = 0;
            for (Ingredient ingredient : ingredients) {
                PreviewEntry inputEntry = toIngredientEntry(ingredient, PreviewRole.INPUT);
                if (inputEntry == null) continue;
                output.add(inputEntry);
                visualIndex++;
                if (visualIndex >= 8) break;
            }

            ItemStack result = recipe.getResultItem(registryAccess);
            PreviewEntry outputEntry = toEntry(result, PreviewRole.OUTPUT);
            if (outputEntry != null) output.add(outputEntry);
        }

        private static ItemStack pickDisplayStack(Ingredient ingredient) {
            if (ingredient == null) return ItemStack.EMPTY;
            ItemStack[] options = ingredient.getItems();
            if (options == null) return ItemStack.EMPTY;
            for (ItemStack candidate : options) {
                if (candidate == null || candidate.isEmpty()) continue;
                return candidate.copy();
            }
            return ItemStack.EMPTY;
        }

        private static PreviewEntry toEntry(ItemStack stack, PreviewRole role) {
            if (stack == null || stack.isEmpty()) return null;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) return null;
            int count = Math.max(1, stack.getCount());
            String expression = Slot.buildLiteralWithCount(itemId.toString(), count);
            return new PreviewEntry(role, expression);
        }

        private static PreviewEntry toLiteralEntry(
                String slotExpression,
                PreviewRole role
        ) {
            if (slotExpression == null || slotExpression.isBlank()) return null;
            return new PreviewEntry(role, slotExpression);
        }

        private static PreviewEntry toEntryOrAir(ItemStack stack, PreviewRole role) {
            PreviewEntry direct = toEntry(stack, role);
            if (direct != null) return direct;
            return toLiteralEntry(AIR_ITEM_LITERAL, role);
        }

        private static PreviewEntry toIngredientEntry(Ingredient ingredient, PreviewRole role) {
            String expression = toIngredientExpression(ingredient);
            if (expression.isBlank()) return null;
            return new PreviewEntry(role, expression);
        }

        private static PreviewEntry toIngredientEntryOrAir(Ingredient ingredient, PreviewRole role) {
            PreviewEntry direct = toIngredientEntry(ingredient, role);
            if (direct != null) return direct;
            return toLiteralEntry(AIR_ITEM_LITERAL, role);
        }

        private static String toIngredientExpression(Ingredient ingredient) {
            if (ingredient == null || ingredient.isEmpty()) return "";
            try {
                return ingredient.toJson().toString();
            } catch (Exception ignored) {
                return "";
            }
        }

        public enum PreviewRole {
            INPUT,
            FUEL,
            TEMPLATE,
            ADDITION,
            OUTPUT
        }

        public enum LayoutKind {
            CRAFTING_SHAPED,
            CRAFTING_SHAPELESS,
            SMELTING_FAMILY,
            STONECUTTING,
            SMITHING,
            FALLBACK
        }

        public enum DeclaredType {
            CRAFTING_SHAPED("crafting_shaped", LayoutKind.CRAFTING_SHAPED) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof ShapedRecipe;
                }
            },
            CRAFTING_SHAPELESS("crafting_shapeless", LayoutKind.CRAFTING_SHAPELESS) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof CraftingRecipe && !(recipe instanceof ShapedRecipe);
                }
            },
            SMELTING("smelting", LayoutKind.SMELTING_FAMILY) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof AbstractCookingRecipe && recipe.getType() == RecipeType.SMELTING;
                }
            },
            BLASTING("blasting", LayoutKind.SMELTING_FAMILY) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof AbstractCookingRecipe && recipe.getType() == RecipeType.BLASTING;
                }
            },
            SMOKING("smoking", LayoutKind.SMELTING_FAMILY) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof AbstractCookingRecipe && recipe.getType() == RecipeType.SMOKING;
                }
            },
            CAMPFIRE_COOKING("campfire_cooking", LayoutKind.SMELTING_FAMILY) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof AbstractCookingRecipe && recipe.getType() == RecipeType.CAMPFIRE_COOKING;
                }
            },
            STONECUTTING("stonecutting", LayoutKind.STONECUTTING) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof StonecutterRecipe;
                }
            },
            SMITHING("smithing", LayoutKind.SMITHING) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof SmithingRecipe;
                }
            },
            FALLBACK("fallback", LayoutKind.FALLBACK) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe != null;
                }
            };

            private final String id;
            private final LayoutKind layoutKind;

            DeclaredType(String id, LayoutKind layoutKind) {
                this.id = id;
                this.layoutKind = layoutKind;
            }

            public String id() {
                return id;
            }

            public LayoutKind layoutKind() {
                return layoutKind;
            }

            abstract boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe);

            public static DeclaredType fromRaw(String raw) {
                if (raw == null || raw.isBlank()) return null;
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                for (DeclaredType value : values()) {
                    if (value.id.equals(normalized)) return value;
                }
                return null;
            }
        }

        public record PreviewEntry(
                PreviewRole role,
                String slotExpression
        ) {
        }

        public record ResolveResult(
                LayoutKind layoutKind,
                List<PreviewEntry> absoluteEntries,
                List<PreviewEntry> listEntries,
                String message
        ) {
            public static ResolveResult empty(String message) {
                return new ResolveResult(LayoutKind.FALLBACK, List.of(), List.of(), message == null ? "" : message);
            }
        }

        private record RecipeCacheKey(ResourceLocation recipeId, DeclaredType declaredType) {
        }

        @Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
        public static class ForgeEvents {
            @SubscribeEvent
            public static void onRecipesUpdated(RecipesUpdatedEvent event) {
                clearCache();
                Slot.clearCandidateCache();
            }
        }
    }
}
