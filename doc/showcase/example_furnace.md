# 一个使用 AUI 创建换皮熔炉的当前示例

## 页面模板

当前版本的模板不再把炉子槽位和玩家槽位静态写死在 HTML 里，而是：

1. 顶层 `container` 先声明绑定关系。
2. `block_entity` 容器显式声明 `size="3"`，让服务端模板编译阶段知道它有 3 个真实槽位。
3. 再在内联 KJS 中动态创建炉子 3 槽和玩家 36 槽。

`size="3"` 这一步不能省。原因是服务端在打开界面时会先按原始 HTML 编译模板容量，它看不到后续脚本动态追加的 `slot`。如果不给 `block_entity` 容器声明容量，动态创建出来的熔炉槽位只能显示，不能真正放物品。

```html
<body>
<div class="screen">
    <container id="block_entity" class="furnace-panel" primary="true" bind="block_entity" size="3">
        <div class="title">换皮熔炉QAQ</div>
    </container>

    <container id="player" class="player-panel" bind="player" layout="preset:player">
        <div class="title">玩家背包</div>
    </container>
</div>
</body>

<script>
    (function () {
        const SLOT_INPUT = 0;
        const SLOT_FUEL = 1;
        const SLOT_OUTPUT = 2;
        const DEFAULT_COOK_TOTAL_TIME = 5;

        function clearSlots(container) {
            if (!container || !container.children) return;
            for (let i = container.children.size() - 1; i >= 0; i--) {
                const child = container.children.get(i);
                if (child && child.tagName === "SLOT") {
                    child.remove();
                }
            }
        }

        function appendSlot(container, className, slotIndex, extraAttrs) {
            const slot = document.createElement("SLOT");
            slot.setAttribute("class", className);
            slot.setAttribute("mode", "bound");
            slot.setAttribute("index", String(slotIndex));
            slot.setAttribute("slot-index", String(slotIndex));
            slot.setAttribute("data-generated", "kjs");
            if (extraAttrs) {
                for (const key in extraAttrs) {
                    if (extraAttrs.hasOwnProperty(key) && extraAttrs[key] != null) {
                        slot.setAttribute(key, String(extraAttrs[key]));
                    }
                }
            }
            container.append(slot);
            return slot;
        }

        function ensureScriptNote(container) {
            if (!container) return;
            let note = document.getElementById("script_note");
            if (!note) {
                note = document.createElement("DIV");
                note.setAttribute("id", "script_note");
                note.setAttribute("class", "script-note");
                container.append(note);
            }
            note.innerText = "槽位结构改由 test.html 内联 KJS 生成；TestBlockEntity 仅保留方块注册、菜单绑定与真实熔炼逻辑。默认烹饪总时长示例值：" + DEFAULT_COOK_TOTAL_TIME + " tick。";
        }

        function buildFurnaceSlots() {
            const container = document.getElementById("block_entity");
            if (!container) return;
            clearSlots(container);
            appendSlot(container, "slot slot-input", SLOT_INPUT);
            appendSlot(container, "slot slot-fuel", SLOT_FUEL);
            appendSlot(container, "slot slot-output", SLOT_OUTPUT);
            ensureScriptNote(container);
        }

        function buildPlayerSlots() {
            const container = document.getElementById("player");
            if (!container) return;
            clearSlots(container);
            for (let slotIndex = 0; slotIndex < 36; slotIndex++) {
                appendSlot(container, "slot player-slot", slotIndex, {
                    part: slotIndex < 27 ? "inv" : "hotbar"
                });
            }
        }

        buildFurnaceSlots();
        buildPlayerSlots();
    })();
</script>

<style>
    body {
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
        color: #ece6dc;
    }

    .screen {
        width: 200px;
        height: 196px;
        padding: 6px;
        gap: 6px;
        box-sizing: border-box;
        background-color: rgba(33, 27, 23, 0.95);
        border: 1px solid #5f5348;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
    }

    container {
        position: relative;
        padding: 6px;
        border: 1px solid #6d6255;
        background-color: rgba(83, 72, 63, 0.35);
    }

    .title {
        height: 10px;
        line-height: 10px;
        color: #ffe9c3;
        margin-bottom: 4px;
    }

    .furnace-panel {
        width: 185px;
        height: 70px;
    }

    .player-panel {
        width: 185px;
        height: 105px;
        gap: 1px;
    }

    .slot-input {
        position: absolute;
        left: 16px;
        top: 22px;
    }

    .slot-fuel {
        position: absolute;
        left: 16px;
        top: 44px;
    }

    .slot-output {
        position: absolute;
        left: 123px;
        top: 33px;
    }

    .script-note {
        position: absolute;
        left: 44px;
        top: 21px;
        width: 68px;
        line-height: 1.25;
        font-size: 9px;
        color: #ccb69a;
        pointer-events: none;
    }
</style>
```

## Java 端

当前 `TestBlockEntity` 的打开方式依赖容器 `id` 与 `OpenBindPlan` 一一对应：

- `block_entity` 绑定方块实体库存
- `player` 绑定玩家背包

这里的容器名必须和 HTML 顶层 `container` 的 `id` 一致。

```java
private static final int SLOT_INPUT = 0;
private static final int SLOT_FUEL = 1;
private static final int SLOT_OUTPUT = 2;
private static final int SLOT_COUNT = 3;
private static final String DEMO_TEMPLATE_PATH = "test/test.html";

@Override
public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (level.isClientSide) return InteractionResult.SUCCESS;
    if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

    ApricityUIServerUtil.screen(DEMO_TEMPLATE_PATH)
        .primaryBind("block_entity").blockEntity(pos.getX(), pos.getY(), pos.getZ(), "")
            .bind("player").player()
            .open(serverPlayer);
    return InteractionResult.CONSUME;
}
```

## 熔炉逻辑仍然在 Java

这个示例没有把“真实熔炉行为”迁到 KJS。当前仍保留在 `FurnaceDemoBlockEntity` 中的内容包括：

- 输入槽只接受存在烧炼配方的物品
- 燃料槽只接受可燃物
- 服务端 `tickServer()` 推进燃烧与烧炼进度
- 输出槽堆叠判定
- 燃料余物回填或掉落
- NBT 持久化与 `ITEM_HANDLER` capability 暴露

关键常量也以 Java 为准：

```java
public static final class FurnaceDemoBlockEntity extends BlockEntity {
    private static final String NBT_ITEMS = "Items";
    private static final String NBT_BURN_TIME = "BurnTime";
    private static final String NBT_BURN_DURATION = "BurnDuration";
    private static final String NBT_COOK_PROGRESS = "CookProgress";
    private static final String NBT_COOK_TOTAL_TIME = "CookTotalTime";
    private static final int DEFAULT_COOK_TOTAL_TIME = 5;

    private final ItemStackHandler itemHandler = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (stack.isEmpty()) return false;
            return switch (slot) {
                case SLOT_INPUT -> hasSmeltingRecipe(stack);
                case SLOT_FUEL -> isFuel(stack);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    private LazyOptional<IItemHandler> itemHandlerCapability = LazyOptional.of(() -> itemHandler);
    private int litTime = 0;
    private int litDuration = 0;
    private int cookingProgress = 0;
    private int cookingTotalTime = DEFAULT_COOK_TOTAL_TIME;

    public FurnaceDemoBlockEntity(BlockPos pos, BlockState state) {
        super(DEMO_FURNACE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FurnaceDemoBlockEntity blockEntity) {
        if (level == null || level.isClientSide) return;
        blockEntity.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide) return;
        boolean changed = false;
        boolean wasLit = isLit();

        ItemStack input = itemHandler.getStackInSlot(SLOT_INPUT);
        ItemStack fuel = itemHandler.getStackInSlot(SLOT_FUEL);
        ItemStack output = itemHandler.getStackInSlot(SLOT_OUTPUT);
        Optional<SmeltingRecipe> recipeOptional = findSmeltingRecipe(input);
        SmeltingRecipe recipe = recipeOptional.orElse(null);
        boolean canSmelt = recipe != null && canSmelt(recipe, output);

        if (litTime > 0) {
            litTime--;
            changed = true;
        }

        if (canSmelt) {
            int resolvedCookTotalTime = getTotalCookTime(recipe);
            if (cookingTotalTime != resolvedCookTotalTime) {
                cookingTotalTime = resolvedCookTotalTime;
                changed = true;
            }
        } else if (cookingTotalTime != DEFAULT_COOK_TOTAL_TIME) {
            cookingTotalTime = DEFAULT_COOK_TOTAL_TIME;
            changed = true;
        }

        if (!isLit() && canSmelt && !fuel.isEmpty()) {
            if (consumeFuelAndStartBurn()) {
                changed = true;
            }
        }

        if (isLit() && canSmelt && recipe != null) {
            cookingProgress++;
            changed = true;
            if (cookingProgress >= cookingTotalTime) {
                cookingProgress = 0;
                if (doSmelt(recipe)) {
                    changed = true;
                }
            }
        } else {
            if (cookingProgress != 0) {
                cookingProgress = 0;
                changed = true;
            }
        }

        if (wasLit != isLit()) {
            changed = true;
        }

        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private Optional<SmeltingRecipe> findSmeltingRecipe(ItemStack inputStack) {
        if (level == null || inputStack == null || inputStack.isEmpty()) return Optional.empty();
        SimpleContainer probe = new SimpleContainer(1);
        probe.setItem(0, inputStack.copy());
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, probe, level);
    }

    private boolean hasSmeltingRecipe(ItemStack inputStack) {
        return findSmeltingRecipe(inputStack).isPresent();
    }

    private boolean isLit() {
        return litTime > 0;
    }

    private static boolean isFuel(ItemStack stack) {
        return net.minecraftforge.common.ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
    }

    private int getTotalCookTime(SmeltingRecipe recipe) {
        return DEFAULT_COOK_TOTAL_TIME;
    }

    private boolean canSmelt(SmeltingRecipe recipe, ItemStack output) {
        if (recipe == null || level == null) return false;
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        if (result.isEmpty()) return false;
        return canOutputAccept(output, result);
    }

    private boolean doSmelt(SmeltingRecipe recipe) {
        if (recipe == null || level == null) return false;

        ItemStack input = itemHandler.getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) return false;

        ItemStack output = itemHandler.getStackInSlot(SLOT_OUTPUT);
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        if (result.isEmpty()) return false;
        if (!canOutputAccept(output, result)) return false;

        itemHandler.extractItem(SLOT_INPUT, 1, false);
        if (output.isEmpty()) {
            itemHandler.setStackInSlot(SLOT_OUTPUT, result);
        } else {
            ItemStack merged = output.copy();
            merged.grow(result.getCount());
            itemHandler.setStackInSlot(SLOT_OUTPUT, merged);
        }
        return true;
    }

    private boolean consumeFuelAndStartBurn() {
        if (level == null) return false;

        ItemStack fuelStack = itemHandler.getStackInSlot(SLOT_FUEL);
        if (fuelStack.isEmpty()) return false;

        int burnValue = net.minecraftforge.common.ForgeHooks.getBurnTime(fuelStack, RecipeType.SMELTING);
        if (burnValue <= 0) return false;

        Item fuelItem = fuelStack.getItem();
        ItemStack remainder = ItemStack.EMPTY;
        if (fuelItem.hasCraftingRemainingItem()) {
            remainder = new ItemStack(fuelItem.getCraftingRemainingItem());
        }

        litTime = burnValue;
        litDuration = burnValue;

        itemHandler.extractItem(SLOT_FUEL, 1, false);
        if (!remainder.isEmpty()) {
            placeFuelRemainder(remainder);
        }
        return true;
    }

    private void placeFuelRemainder(ItemStack remainder) {
        if (level == null || remainder.isEmpty()) return;

        ItemStack currentFuel = itemHandler.getStackInSlot(SLOT_FUEL);
        if (currentFuel.isEmpty()) {
            itemHandler.setStackInSlot(SLOT_FUEL, remainder);
            return;
        }

        if (ItemStack.isSameItemSameTags(currentFuel, remainder)) {
            int maxCount = Math.min(currentFuel.getMaxStackSize(), itemHandler.getSlotLimit(SLOT_FUEL));
            int mergedCount = currentFuel.getCount() + remainder.getCount();
            if (mergedCount <= maxCount) {
                ItemStack merged = currentFuel.copy();
                merged.setCount(mergedCount);
                itemHandler.setStackInSlot(SLOT_FUEL, merged);
                return;
            }
        }

        // 燃料槽冲突时将余物丢到地面，避免直接吞物。
        Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, remainder);
    }

    private static boolean canOutputAccept(ItemStack output, ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) return false;
        if (output == null || output.isEmpty()) {
            return incoming.getCount() <= incoming.getMaxStackSize();
        }
        if (!ItemStack.isSameItemSameTags(output, incoming)) return false;
        int limit = Math.max(1, Math.min(output.getMaxStackSize(), 64));
        return output.getCount() + incoming.getCount() <= limit;
    }

    private SimpleContainer asContainerSnapshot() {
        SimpleContainer snapshot = new SimpleContainer(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            snapshot.setItem(i, itemHandler.getStackInSlot(i).copy());
        }
        return snapshot;
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains(NBT_ITEMS)) {
            itemHandler.deserializeNBT(tag.getCompound(NBT_ITEMS));
        }
        litTime = Math.max(0, tag.getInt(NBT_BURN_TIME));
        litDuration = Math.max(0, tag.getInt(NBT_BURN_DURATION));
        cookingProgress = Math.max(0, tag.getInt(NBT_COOK_PROGRESS));
        cookingTotalTime = DEFAULT_COOK_TOTAL_TIME;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(NBT_ITEMS, itemHandler.serializeNBT());
        tag.putInt(NBT_BURN_TIME, litTime);
        tag.putInt(NBT_BURN_DURATION, litDuration);
        tag.putInt(NBT_COOK_PROGRESS, cookingProgress);
        tag.putInt(NBT_COOK_TOTAL_TIME, cookingTotalTime);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!itemHandlerCapability.isPresent()) {
            itemHandlerCapability = LazyOptional.of(() -> itemHandler);
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCapability.invalidate();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCapability.cast();
        }
        return super.getCapability(capability, side);
    }
}
```

## 使用这个示例时的注意点

1. 如果你想把槽位用 KJS 动态创建出来，顶层 `container` 仍然要提供正确的 `id`。
2. 对于真实绑定容器，动态槽位之外还要保证服务端模板编译能推导出容量；最稳妥的方式就是像本例一样给 `block_entity` 写 `size="3"`。
3. `clearSlots(container)` 不能去掉，否则 `Document.refresh()` 重新执行内联脚本时会把旧槽位重复堆出来。
