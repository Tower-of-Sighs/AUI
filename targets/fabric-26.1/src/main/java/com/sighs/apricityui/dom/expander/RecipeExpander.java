package com.sighs.apricityui.dom.expander;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.element.Recipe;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.slot.IngredientExpressionCompiler;
import com.sighs.apricityui.slot.ItemStackExpressionCompiler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
 */
public final class RecipeExpander {
    public static void clearCache() {
        RecipeResolver.clearCache();
        IngredientExpressionCompiler.clearTagCache();
    }

    public static void expand(Document document) {
        if (document == null) return;
        ArrayList<Element> snapshot = new ArrayList<>(document.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Recipe recipe)) continue;
            recipe.previewState.update(recipe, System.nanoTime() / 1_000_000L, true);
        }
    }

    private static void renderPreview(Recipe recipe, RecipeResolver.DeclaredType type,
                                      RecipeResolver.ResolveResult preview, String message) {
        boolean changed = recipe.clearGeneratedRecipeSlots();
        changed |= setAttributeIfChanged(recipe, "data-recipe-type", type == null ? "" : type.id());
        changed |= setAttributeIfChanged(recipe, "data-recipe-layout",
                preview == null ? "" : preview.layoutKind().name().toLowerCase(Locale.ROOT));
        if (preview == null && !Objects.equals(recipe.getAttribute("data-recipe-error"), message)) {
            ApricityUI.LOGGER.warn("{}, type={}, output={}", message, recipe.getAttribute("type"), recipe.getOutputItemExpression());
        }
        changed |= setAttributeIfChanged(recipe, "data-recipe-error", preview == null ? message : "");
        if (preview != null) {
            EnumMap<RecipeResolver.PreviewRole, Integer> cursor = new EnumMap<>(RecipeResolver.PreviewRole.class);
            for (RecipeResolver.PreviewEntry entry : preview.entries()) {
                appendPreviewSlot(recipe.document, recipe, entry, nextRoleIndex(cursor, entry.role()));
                changed = true;
            }
        }
        if (changed) recipe.document.markDirty(recipe, Drawer.RELAYOUT | Drawer.REORDER);
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
            int roleIndex
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
                "interactive", "0",
                "pointer", "0",
                "style", "--aui-slot-interactive:0;"
        ), true);
        appendPreviewContent(document, slot, entry);
        recipe.append(slot);
    }

    private static void appendPreviewContent(Document document, Slot slot, RecipeResolver.PreviewEntry entry) {
        String expression = entry.slotExpression() == null ? "minecraft:air" : entry.slotExpression();
        if (usesIngredientContent(entry)) {
            com.sighs.apricityui.element.Ingredient ingredient =
                    new com.sighs.apricityui.element.Ingredient(document);
            ingredient.setTextContent(expression);
            SlotContentRules.ensureControlledItem(ingredient);
            slot.appendChild(ingredient);
            return;
        }

        Item item = new Item(document);
        item.setTextContent(expression);
        slot.appendChild(item);
    }

    private static boolean usesIngredientContent(RecipeResolver.PreviewEntry entry) {
        return entry.role() != RecipeResolver.PreviewRole.OUTPUT
                && !"minecraft:air".equals(ItemStackExpressionCompiler.normalize(entry.slotExpression()));
    }

    private static boolean setAttributeIfChanged(Recipe recipe, String key, String value) {
        String normalized = value == null ? "" : value;
        if (Objects.equals(recipe.getAttribute(key), normalized)) return false;
        recipe.setAttribute(key, normalized);
        return true;
    }

    public static final class PreviewState {
        private String expression;
        private String type;
        private Object level;
        private RecipeManager manager;
        private long revision = -1;
        private List<RecipeResolver.ResolveResult> candidates = List.of();
        private int index;
        private long nextRotateAt;
        private boolean paused;
        private boolean invalidated;

        public void invalidate() {
            invalidated = true;
        }

        public void update(Recipe recipe, long now, boolean force) {
            Minecraft minecraft = Minecraft.getInstance();
            Object currentLevel = minecraft == null ? null : minecraft.level;
            MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
            RecipeManager currentManager = server == null ? null : server.getRecipeManager();
            String currentExpression = recipe.getOutputItemExpression();
            String currentType = recipe.getAttribute("type");
            long currentRevision = RecipeResolver.revision;
            if (force || invalidated || level != currentLevel || manager != currentManager || revision != currentRevision
                    || !Objects.equals(expression, currentExpression) || !Objects.equals(type, currentType)) {
                level = currentLevel;
                manager = currentManager;
                revision = currentRevision;
                expression = currentExpression;
                type = currentType;
                index = 0;
                nextRotateAt = now + 1000L;
                paused = false;
                invalidated = false;
                RecipeResolver.DeclaredType declaredType = RecipeResolver.DeclaredType.fromRaw(type);
                Identifier outputItemId = recipe.parseOutputItemId();
                RecipeResolver.ResolveBatch batch;
                if (declaredType == null) {
                    batch = RecipeResolver.ResolveBatch.empty("Recipe preview skipped: missing/invalid type");
                } else if (outputItemId == null) {
                    batch = RecipeResolver.ResolveBatch.empty("Recipe preview skipped: invalid output item id in textContent");
                } else {
                    batch = RecipeResolver.resolve(outputItemId, declaredType);
                }
                candidates = batch.previews();
                renderPreview(recipe, declaredType, candidates.isEmpty() ? null : candidates.get(0), batch.message());
                return;
            }
            if (advance(now, recipe.isHover)) {
                renderPreview(recipe, RecipeResolver.DeclaredType.fromRaw(type), candidates.get(index), "");
            }
        }

        boolean advance(long now, boolean hovered) {
            if (hovered || paused) {
                nextRotateAt = now + 1000L;
                paused = hovered;
                return false;
            }
            if (candidates.size() <= 1 || now < nextRotateAt) return false;
            index = (index + 1) % candidates.size();
            nextRotateAt = now + 1000L;
            return true;
        }
    }

    /**
     * 解析客户端配方并生成 UI 预览槽位数据。
     */
    private static final class RecipeResolver {
        private static final String AIR_ITEM_LITERAL = "minecraft:air";
        private static final int MAX_CACHE_SIZE = 256;
        private static final LinkedHashMap<RecipeCacheKey, ResolveBatch> CACHE =
                new LinkedHashMap<RecipeCacheKey, ResolveBatch>(64, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<RecipeCacheKey, ResolveBatch> eldest) {
                        return size() > MAX_CACHE_SIZE;
                    }
                };
        private static volatile long revision;
        private static Object cachedLevel;
        private static RecipeManager cachedManager;

        private RecipeResolver() {
        }

        public static synchronized ResolveBatch resolve(Identifier outputItemId, DeclaredType declaredType) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null)
                return ResolveBatch.empty("Client level is not available");
            MinecraftServer server = minecraft.getSingleplayerServer();
            RecipeManager manager = server == null ? null : server.getRecipeManager();
            if (manager == null) return ResolveBatch.empty("Recipe manager is not available (singleplayer only)");
            if (cachedLevel != minecraft.level || cachedManager != manager) {
                CACHE.clear();
                cachedLevel = minecraft.level;
                cachedManager = manager;
            }
            RecipeCacheKey key = new RecipeCacheKey(outputItemId, declaredType);
            ResolveBatch cached = CACHE.get(key);
            if (cached != null) return cached;
            ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
            ArrayList<ResolveResult> previews = new ArrayList<>();
            manager.getRecipes().stream().filter(holder -> declaredType.matches(holder.value()))
                    .sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
                    .forEach(holder -> {
                        for (RecipeDisplay display : holder.value().display()) {
                            ItemStack result = display.result().resolveForFirstStack(context);
                            if (result.isEmpty() || !outputItemId.equals(BuiltInRegistries.ITEM.getKey(result.getItem())))
                                continue;
                            ResolveResult preview = buildPreview(display, declaredType, context);
                            if (!preview.entries().isEmpty()) previews.add(preview);
                        }
                    });
            ResolveBatch batch = previews.isEmpty() ? ResolveBatch.empty("No recipes match the output item and type")
                    : new ResolveBatch(List.copyOf(previews), "");
            CACHE.put(key, batch);
            return batch;
        }

        public static synchronized void clearCache() {
            CACHE.clear();
            cachedLevel = null;
            cachedManager = null;
            revision++;
        }

        private static ResolveResult buildPreview(RecipeDisplay display, DeclaredType declaredType, ContextMap context) {
            ArrayList<PreviewEntry> absoluteEntries = new ArrayList<>();
            LayoutKind layoutKind = declaredType.layoutKind();

            switch (declaredType) {
                case CRAFTING_SHAPED -> buildShapedCraftingEntries(display, absoluteEntries, context);
                case CRAFTING_SHAPELESS -> buildShapelessCraftingEntries(display, absoluteEntries, context);
                case SMELTING, BLASTING, SMOKING, CAMPFIRE_COOKING ->
                        buildCookingEntries(display, absoluteEntries, context);
                case STONECUTTING -> buildStonecuttingEntries(display, absoluteEntries, context);
                case SMITHING -> buildSmithingEntries(display, absoluteEntries, context);
                case FALLBACK -> buildFallbackEntries(display, absoluteEntries, context);
            }

            return new ResolveResult(layoutKind, List.copyOf(absoluteEntries));
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
                    IngredientExpressionCompiler.furnaceFuelTagLiteral(),
                    PreviewRole.FUEL
            );
            output.add(fuelEntry);

            PreviewEntry resultEntry = toEntryFromDisplay(furnace.result(), context, PreviewRole.OUTPUT);
            if (resultEntry != null) output.add(resultEntry);
        }

        private static void buildStonecuttingEntries(RecipeDisplay display, List<PreviewEntry> output, ContextMap context) {
            if (!(display instanceof StonecutterRecipeDisplay stonecutter)) {
                buildFallbackEntries(display, output, context);
                return;
            }
            PreviewEntry input = toIngredientEntry(stonecutter.input(), context, PreviewRole.INPUT);
            if (input != null) output.add(input);
            PreviewEntry result = toEntryFromDisplay(stonecutter.result(), context, PreviewRole.OUTPUT);
            if (result != null) output.add(result);
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
            if (display instanceof SmithingRecipeDisplay smithing)
                return List.of(smithing.template(), smithing.base(), smithing.addition());
            return List.of();
        }

        private static PreviewEntry toEntry(ItemStack stack, PreviewRole role) {
            if (stack == null || stack.isEmpty()) return null;
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) return null;
            String expression = ItemStackExpressionCompiler.serialize(stack);
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
            LinkedHashSet<String> expressions = new LinkedHashSet<>();
            for (ItemStack stack : items) {
                if (stack == null || stack.isEmpty()) continue;
                String expression = ItemStackExpressionCompiler.serialize(stack);
                if (!expression.isBlank() && !"minecraft:air".equals(expression)) {
                    expressions.add(expression);
                }
            }
            return String.join("|", expressions);
        }

        public record PreviewEntry(
                PreviewRole role,
                String slotExpression
        ) {
        }

        public record ResolveResult(LayoutKind layoutKind, List<PreviewEntry> entries) {
            public static ResolveResult empty () {
                return new ResolveResult(LayoutKind.FALLBACK, List.of());
            }
        }

        public record ResolveBatch(List<ResolveResult> previews, String message) {
            public static ResolveBatch empty (String message){
                return new ResolveBatch(List.of(), message);
            }
        }

        private record RecipeCacheKey(Identifier outputItemId, DeclaredType declaredType) {
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

            public static DeclaredType fromRaw(String raw) {
                if (raw == null || raw.isBlank()) return null;
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                for (DeclaredType value : values()) {
                    if (value.id.equals(normalized)) return value;
                }
                return null;
            }

            public String id() {
                return id;
            }

            public LayoutKind layoutKind() {
                return layoutKind;
            }

            abstract boolean matches(net.minecraft.world.item.crafting.Recipe<?> recipe);
        }


    }
}
