# 一个使用 AUI 创建一个换皮熔炉的示例

```html
<body>
<div class="screen">
    <container class="furnace-panel" primary="true" bind="block_entity">
        <div class="title">Java BlockEntity 熔炉演示</div>

        <slot class="slot slot-input" mode="bound" slot-index="0"></slot>
        <slot class="slot slot-fuel" mode="bound" slot-index="1"></slot>
        <slot class="slot slot-output" mode="bound" slot-index="2"></slot>
        <div class="hint">放入待熔炼物品</div>
        <div class="stats">I:0 F:0 O:0</div>
    </container>

    <container class="player-panel" bind="player" layout="preset:player">
        <div class="title">玩家背包</div>
        <slot class="slot" mode="bound" repeat="36" slot-index="0"></slot>
    </container>
</div>
</body>

<script>
(() => {
    const UI_POLL_MS = 300
    const STATE_COLOR = {
        idle: '#cdb79c',
        ready: '#b5e3a2',
        warn: '#f0c28a',
        error: '#ef9a9a'
    }

    let inputSlot = null
    let fuelSlot = null
    let outputSlot = null
    let hintNode = null
    let statsNode = null
    let timer = null

    let ForgeHooks = null
    let RecipeType = null
    try {
        ForgeHooks = Java.loadClass('net.minecraftforge.common.ForgeHooks')
        RecipeType = Java.loadClass('net.minecraft.world.item.crafting.RecipeType')
    } catch (ignored) {}

    function isDocumentAlive() {
        try {
            const docs = ApricityUI.getAllDocument()
            return docs != null && docs.contains(document)
        } catch (ignored) {
            return true
        }
    }

    function stackOf(slot) {
        if (slot == null || typeof slot.getItem !== 'function') return null
        try {
            return slot.getItem()
        } catch (ignored) {
            return null
        }
    }

    function hasStack(stack) {
        return stack != null && typeof stack.isEmpty === 'function' && !stack.isEmpty()
    }

    function countOf(stack) {
        if (!hasStack(stack)) return 0
        try {
            return Math.max(0, stack.getCount())
        } catch (ignored) {
            return 0
        }
    }

    function isFuel(stack) {
        if (!hasStack(stack)) return false
        if (ForgeHooks == null || RecipeType == null) return true
        try {
            return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0
        } catch (ignored) {
            return false
        }
    }

    function outputFull(stack) {
        if (!hasStack(stack)) return false
        try {
            return stack.getCount() >= stack.getMaxStackSize()
        } catch (ignored) {
            return false
        }
    }

    function setHint(text, colorKey) {
        if (hintNode == null) return
        hintNode.innerText = text
        const color = STATE_COLOR[colorKey] || STATE_COLOR.idle
        hintNode.setAttribute('style', `color: ${color}; font-size: 8px;`)
    }

    function setStats(input, fuel, output) {
        if (statsNode == null) return
        statsNode.innerText = `I:${countOf(input)} F:${countOf(fuel)} O:${countOf(output)}`
    }

    function updateHint() {
        if (!isDocumentAlive()) {
            if (timer != null) timer.cancel()
            return
        }
        if (inputSlot == null || fuelSlot == null || outputSlot == null || hintNode == null) return

        const input = stackOf(inputSlot)
        const fuel = stackOf(fuelSlot)
        const output = stackOf(outputSlot)
        setStats(input, fuel, output)

        if (!hasStack(input)) {
            setHint('放入待熔炼物品', 'idle')
            return
        }
        if (!hasStack(fuel)) {
            setHint('缺少燃料', 'warn')
            return
        }
        if (!isFuel(fuel)) {
            setHint('当前物品不能作为燃料', 'error')
            return
        }
        if (outputFull(output)) {
            setHint('输出槽已满', 'warn')
            return
        }
        setHint('服务端自动熔炼中…', 'ready')
    }

    function init() {
        inputSlot = document.querySelector('.slot-input')
        fuelSlot = document.querySelector('.slot-fuel')
        outputSlot = document.querySelector('.slot-output')
        hintNode = document.querySelector('.hint')
        statsNode = document.querySelector('.stats')
        if (hintNode == null) return

        updateHint()
        timer = window.setInterval(() => updateHint(), UI_POLL_MS)
    }

    init()
})()
</script>

<style>
    body {
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
        font-family: lxgw;
        color: #ece6dc;
        font-size: 11px;
    }

    .screen {
        width: 184px;
        height: 174px;
        padding: 8px;
        gap: 6px;
        background-color: rgba(33, 27, 23, 0.95);
        border: 1px solid #5f5348;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
    }

    container {
        position: relative;
        width: 100%;
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
        width: 165px;
        height: 64px;
    }

    .player-panel {
        width: 165px;
        height: 92px;
        gap: 1px;
    }

    .player-panel slot {
        width: 16px;
        height: 16px;
    }

    .slot {
        width: 18px;
        height: 18px;
        background-color: rgba(0, 0, 0, 0.25);
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

    .hint {
        position: absolute;
        left: 42px;
        top: 46px;
        color: #cdb79c;
        font-size: 8px;
    }

    .stats {
        position: absolute;
        left: 42px;
        top: 56px;
        color: #9f9588;
        font-size: 7px;
    }
</style>
```

```java
package com.sighs.apricityui.dev;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 开发期测试熔炉：将测试用方块/方块实体/自动熔炼逻辑集中在一个文件。
 */
public final class TestBlockEntity {
    private static final int SLOT_INPUT = 0;
    private static final int SLOT_FUEL = 1;
    private static final int SLOT_OUTPUT = 2;
    private static final int SLOT_COUNT = 3;
    private static final String DEMO_TEMPLATE_PATH = "test/test.html";

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ApricityUI.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ApricityUI.MODID);

    public static final RegistryObject<Block> DEMO_FURNACE_BLOCK = BLOCKS.register(
            "demo_furnace",
            () -> new FurnaceDemoBlock(BlockBehaviour.Properties.copy(Blocks.STONE))
    );
    public static final RegistryObject<BlockEntityType<FurnaceDemoBlockEntity>> DEMO_FURNACE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "demo_furnace",
            () -> BlockEntityType.Builder.of(FurnaceDemoBlockEntity::new, DEMO_FURNACE_BLOCK.get()).build(null)
    );

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }

    public static final class FurnaceDemoBlock extends BaseEntityBlock implements EntityBlock {
        public FurnaceDemoBlock(Properties properties) {
            super(properties);
        }

        @Override
        public @NotNull RenderShape getRenderShape(@NotNull BlockState blockState) {
            return RenderShape.MODEL;
        }

        @Override
        public @NotNull BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
            return new FurnaceDemoBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? null : createTickerHelper(type, DEMO_FURNACE_BLOCK_ENTITY.get(), FurnaceDemoBlockEntity::serverTick);
        }

        @Override
        public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            OpenBindPlan plan = ApricityUI.bind()
                    .primaryBlockEntity(pos.getX(), pos.getY(), pos.getZ(), "")
                    .containerIndexPlayer(1)
                    .build();
            ApricityUI.openScreen(serverPlayer, DEMO_TEMPLATE_PATH, plan);
            return InteractionResult.CONSUME;
        }

        @Override
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock())) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof FurnaceDemoBlockEntity demoBlockEntity) {
                    Containers.dropContents(level, pos, demoBlockEntity.asContainerSnapshot());
                    level.updateNeighbourForOutputSignal(pos, this);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

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
        public void load(CompoundTag tag) {
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
        protected void saveAdditional(CompoundTag tag) {
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
}
```