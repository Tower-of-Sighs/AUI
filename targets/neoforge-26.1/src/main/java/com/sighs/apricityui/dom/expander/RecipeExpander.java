package com.sighs.apricityui.dom.expander;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.element.Recipe;
import com.sighs.apricityui.element.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.*;

/**
 * 在文档刷新阶段触发 recipe DOM 预览槽位生成。
 *
 * <p>NeoForge 26.1 不再向客户端下发完整配方表，客户端只拥有 recipe access（stonecutter 选择集等）。
 * 因此配方预览改为在本地世界（singleplayer）中通过 {@link MinecraftServer#getRecipeManager()} 读取配方，
 * 并使用新的 {@link RecipeDisplay}/{@link SlotDisplay} 体系解析成分与产物；多人服务器上无法解析时退化为空预览。</p>
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

        Identifier recipeId = recipe.parseRecipeIdFromInnerText();
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

    private static String buildPreviewSlotClassName(RecipeResolver.PreviewRole role) {
        String roleName = role.name().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> classNames = new LinkedHashSet<>();
        classNames.add("recipe-slot");
        classNames.add("recipe-slot-" + roleName);
        classNames.add("aui-recipe-slot");
        classNames.add(toRoleClass(role));
        classNames.add("aui-recipe-" + roleName);
        return String.join(" ", classNames);
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
        slot.applyRecipeSlotMeta(
                buildPreviewSlotClassName(entry.role()),
                "recipe-slot"
        );
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

        public static synchronized ResolveResult resolve(Identifier recipeId, DeclaredType declaredType) {
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
            Slot.clearCandidateCache();
        }

        private static ResolveResult buildPreview(Identifier recipeId, DeclaredType declaredType) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return ResolveResult.empty("Client level is not available");
            }

            // In 26.1 the client does not keep a full RecipeManager; use the local singleplayer server.
            MinecraftServer server = minecraft.getSingleplayerServer();
            RecipeManager recipeManager = server == null ? null : server.getRecipeManager();
            if (recipeManager == null) {
                return ResolveResult.empty("Recipe manager is not available (singleplayer only)");
            }

            ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipeKey = ResourceKey.create(Registries.RECIPE, recipeId);
            Optional<? extends RecipeHolder<?>> recipeOptional = recipeManager.byKey(recipeKey);
            if (recipeOptional.isEmpty()) {
                return ResolveResult.empty("Recipe not found");
            }

            net.minecraft.world.item.crafting.Recipe<?> recipe = recipeOptional.get().value();
            if (!declaredType.matches(recipe)) {
                return ResolveResult.empty("Recipe type mismatch: declared=%s, actual=%s"
                        .formatted(declaredType.id(), recipe.getClass().getSimpleName()));
            }

            ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
            ArrayList<PreviewEntry> absoluteEntries = new ArrayList<>();
            ArrayList<PreviewEntry> listEntries = new ArrayList<>();
            LayoutKind layoutKind = declaredType.layoutKind();

            RecipeDisplay display = null;
            List<RecipeDisplay> displays = recipe.display();
            if (displays != null && !displays.isEmpty()) {
                display = displays.get(0);
            }

            switch (declaredType) {
                case CRAFTING_SHAPED -> buildShapedCraftingEntries(display, absoluteEntries, context);
                case CRAFTING_SHAPELESS -> buildShapelessCraftingEntries(display, absoluteEntries, context);
                case SMELTING, BLASTING, SMOKING, CAMPFIRE_COOKING -> buildCookingEntries(display, absoluteEntries, context);
                case STONECUTTING -> buildStonecuttingEntries(display, recipeManager, absoluteEntries, listEntries, context);
                case SMITHING -> buildSmithingEntries(display, absoluteEntries, context);
                case FALLBACK -> buildFallbackEntries(display, absoluteEntries, context);
            }

            return new ResolveResult(layoutKind, List.copyOf(absoluteEntries), List.copyOf(listEntries), "");
        }

        private static void buildShapedCraftingEntries(
                RecipeDisplay display,
                List<PreviewEntry> output,
                ContextMap context
        ) {
            if (!(display instanceof ShapedCraftingRecipeDisplay shaped)) {
                buildFallbackEntries(display, output, context);
                return;
            }
            int width = Math.max(1, shaped.width());
            int height = Math.max(1, shaped.height());
            List<SlotDisplay> ingredients = shaped.ingredients();

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    SlotDisplay ingredient = SlotDisplay.Empty.INSTANCE;
                    int shapedIndex = row * width + col;
                    if (row < height && col < width && shapedIndex >= 0 && shapedIndex < ingredients.size()) {
                        ingredient = ingredients.get(shapedIndex);
                    }
                    output.add(toIngredientEntryOrAir(ingredient, context, PreviewRole.INPUT));
                }
            }

            output.add(toEntryOrAir(shaped.result(), context, PreviewRole.OUTPUT));
        }

        private static void buildShapelessCraftingEntries(
                RecipeDisplay display,
                List<PreviewEntry> output,
                ContextMap context
        ) {
            if (!(display instanceof ShapelessCraftingRecipeDisplay shapeless)) {
                buildFallbackEntries(display, output, context);
                return;
            }
            List<SlotDisplay> ingredients = shapeless.ingredients();
            for (int slot = 0; slot < 9; slot++) {
                SlotDisplay ingredient = slot < ingredients.size() ? ingredients.get(slot) : SlotDisplay.Empty.INSTANCE;
                output.add(toIngredientEntryOrAir(ingredient, context, PreviewRole.INPUT));
            }

            output.add(toEntryOrAir(shapeless.result(), context, PreviewRole.OUTPUT));
        }

        private static void buildCookingEntries(
                RecipeDisplay display,
                List<PreviewEntry> output,
                ContextMap context
        ) {
            if (!(display instanceof FurnaceRecipeDisplay furnace)) {
                buildFallbackEntries(display, output, context);
                return;
            }
            PreviewEntry inputEntry = toIngredientEntry(furnace.ingredient(), context, PreviewRole.INPUT);
            if (inputEntry != null) output.add(inputEntry);

            PreviewEntry fuelEntry = toLiteralEntry(
                    Slot.furnaceFuelVirtualTagLiteral(),
                    PreviewRole.FUEL
            );
            output.add(fuelEntry);

            PreviewEntry resultEntry = toEntryFromDisplay(furnace.result(), context, PreviewRole.OUTPUT);
            if (resultEntry != null) output.add(resultEntry);
        }

        private static void buildStonecuttingEntries(
                RecipeDisplay display,
                RecipeManager recipeManager,
                List<PreviewEntry> absoluteOutput,
                List<PreviewEntry> listOutput,
                ContextMap context
        ) {
            if (!(display instanceof StonecutterRecipeDisplay stonecutter)) {
                buildFallbackEntries(display, absoluteOutput, context);
                return;
            }
            PreviewEntry inputEntry = toIngredientEntry(stonecutter.input(), context, PreviewRole.INPUT);
            if (inputEntry != null) absoluteOutput.add(inputEntry);

            ArrayList<ItemStack> outputs = collectStonecuttingOutputs(stonecutter, recipeManager, context);
            if (outputs.isEmpty()) {
                ItemStack result = stonecutter.result().resolveForFirstStack(context);
                if (!result.isEmpty()) outputs.add(result.copy());
            }

            for (ItemStack stack : outputs) {
                PreviewEntry outputEntry = toEntry(stack, PreviewRole.OUTPUT);
                if (outputEntry != null) listOutput.add(outputEntry);
            }
        }

        private static ArrayList<ItemStack> collectStonecuttingOutputs(
                StonecutterRecipeDisplay recipe,
                RecipeManager recipeManager,
                ContextMap context
        ) {
            ArrayList<ItemStack> result = new ArrayList<>();
            ItemStack selectedInput = recipe.input().resolveForFirstStack(context);
            if (selectedInput.isEmpty()) return result;

            HashSet<Identifier> dedup = new HashSet<>();
            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                if (!(holder.value() instanceof net.minecraft.world.item.crafting.StonecutterRecipe candidateRecipe)) {
                    continue;
                }
                List<RecipeDisplay> displays = candidateRecipe.display();
                if (displays.isEmpty() || !(displays.get(0) instanceof StonecutterRecipeDisplay candidateDisplay)) {
                    continue;
                }
                ItemStack candidateInput = candidateDisplay.input().resolveForFirstStack(context);
                if (candidateInput.isEmpty()) continue;
                // Compare the input items (degraded vs. full ingredient testing).
                if (selectedInput.getItem() != candidateInput.getItem()) {
                    continue;
                }

                ItemStack candidateResult = candidateDisplay.result().resolveForFirstStack(context);
                if (candidateResult.isEmpty()) continue;
                Identifier itemId = BuiltInRegistries.ITEM.getKey(candidateResult.getItem());
                if (itemId == null || !dedup.add(itemId)) continue;
                result.add(candidateResult.copy());
            }

            result.sort((a, b) -> {
                Identifier ida = BuiltInRegistries.ITEM.getKey(a.getItem());
                Identifier idb = BuiltInRegistries.ITEM.getKey(b.getItem());
                String sa = ida == null ? "" : ida.toString();
                String sb = idb == null ? "" : idb.toString();
                return sa.compareTo(sb);
            });
            return result;
        }

        private static void buildSmithingEntries(
                RecipeDisplay display,
                List<PreviewEntry> output,
                ContextMap context
        ) {
            if (!(display instanceof SmithingRecipeDisplay smithing)) {
                buildFallbackEntries(display, output, context);
                return;
            }
            PreviewEntry template = toIngredientEntry(smithing.template(), context, PreviewRole.TEMPLATE);
            if (template != null) output.add(template);
            PreviewEntry base = toIngredientEntry(smithing.base(), context, PreviewRole.INPUT);
            if (base != null) output.add(base);
            PreviewEntry addition = toIngredientEntry(smithing.addition(), context, PreviewRole.ADDITION);
            if (addition != null) output.add(addition);

            PreviewEntry resultEntry = toEntryFromDisplay(smithing.result(), context, PreviewRole.OUTPUT);
            if (resultEntry != null) output.add(resultEntry);
        }

        private static void buildFallbackEntries(
                RecipeDisplay display,
                List<PreviewEntry> output,
                ContextMap context
        ) {
            List<SlotDisplay> ingredients = extractIngredientDisplays(display);
            int visualIndex = 0;
            for (SlotDisplay ingredient : ingredients) {
                PreviewEntry inputEntry = toIngredientEntry(ingredient, context, PreviewRole.INPUT);
                if (inputEntry == null) continue;
                output.add(inputEntry);
                visualIndex++;
                if (visualIndex >= 8) break;
            }

            if (display != null) {
                PreviewEntry outputEntry = toEntryFromDisplay(display.result(), context, PreviewRole.OUTPUT);
                if (outputEntry != null) output.add(outputEntry);
            }
        }

        private static List<SlotDisplay> extractIngredientDisplays(RecipeDisplay display) {
            if (display instanceof ShapedCraftingRecipeDisplay shaped) return shaped.ingredients();
            if (display instanceof ShapelessCraftingRecipeDisplay shapeless) return shapeless.ingredients();
            if (display instanceof FurnaceRecipeDisplay furnace) return List.of(furnace.ingredient());
            if (display instanceof StonecutterRecipeDisplay stonecutter) return List.of(stonecutter.input());
            if (display instanceof SmithingRecipeDisplay smithing) return List.of(smithing.template(), smithing.base(), smithing.addition());
            return List.of();
        }

        private static PreviewEntry toEntry(ItemStack stack, PreviewRole role) {
            if (stack == null || stack.isEmpty()) return null;
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

        private static PreviewEntry toEntryFromDisplay(SlotDisplay display, ContextMap context, PreviewRole role) {
            if (display == null) return null;
            ItemStack stack = display.resolveForFirstStack(context);
            if (stack == null || stack.isEmpty()) return null;
            return toEntry(stack, role);
        }

        private static PreviewEntry toEntryOrAir(SlotDisplay display, ContextMap context, PreviewRole role) {
            PreviewEntry direct = toEntryFromDisplay(display, context, role);
            if (direct != null) return direct;
            return toLiteralEntry(AIR_ITEM_LITERAL, role);
        }

        private static PreviewEntry toIngredientEntry(SlotDisplay display, ContextMap context, PreviewRole role) {
            if (display == null) return null;
            List<ItemStack> stacks = display.resolveForStacks(context);
            String expression = toIngredientExpression(stacks);
            if (expression.isBlank()) return null;
            return new PreviewEntry(role, expression);
        }

        private static PreviewEntry toIngredientEntryOrAir(SlotDisplay display, ContextMap context, PreviewRole role) {
            PreviewEntry direct = toIngredientEntry(display, context, role);
            if (direct != null) return direct;
            return toLiteralEntry(AIR_ITEM_LITERAL, role);
        }

        private static String toIngredientExpression(List<ItemStack> items) {
            if (items == null || items.isEmpty()) return "";
            try {
                if (items.size() == 1) {
                    ItemStack stack = items.get(0);
                    if (stack == null || stack.isEmpty()) return "";
                    Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (itemId == null) return "";
                    return Slot.buildLiteralWithCount(itemId.toString(), stack.getCount());
                }
                // Multiple items: create ingredient array
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) sb.append(",");
                    ItemStack stack = items.get(i);
                    if (stack == null || stack.isEmpty()) continue;
                    Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (itemId == null) continue;
                    sb.append(Slot.buildLiteralWithCount(itemId.toString(), stack.getCount()));
                }
                sb.append("]");
                return sb.toString();
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
                    return recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe;
                }
            },
            CRAFTING_SHAPELESS("crafting_shapeless", LayoutKind.CRAFTING_SHAPELESS) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe
                            && !(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe);
                }
            },
            SMELTING("smelting", LayoutKind.SMELTING_FAMILY) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe && recipe.getType() == RecipeType.SMELTING;
                }
            },
            BLASTING("blasting", LayoutKind.SMELTING_FAMILY) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe && recipe.getType() == RecipeType.BLASTING;
                }
            },
            SMOKING("smoking", LayoutKind.SMELTING_FAMILY) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe && recipe.getType() == RecipeType.SMOKING;
                }
            },
            CAMPFIRE_COOKING("campfire_cooking", LayoutKind.SMELTING_FAMILY) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe && recipe.getType() == RecipeType.CAMPFIRE_COOKING;
                }
            },
            STONECUTTING("stonecutting", LayoutKind.STONECUTTING) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof net.minecraft.world.item.crafting.StonecutterRecipe;
                }
            },
            SMITHING("smithing", LayoutKind.SMITHING) {
                @Override
                boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe) {
                    return recipe instanceof net.minecraft.world.item.crafting.SmithingRecipe;
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

        private record RecipeCacheKey(Identifier recipeId, DeclaredType declaredType) {
        }
    }
}
