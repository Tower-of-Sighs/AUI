package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.StacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 基于 NeoForge Transfer API 的通用数据源（block_entity/entity）。
 */
public final class NeoForgeItemHandlerDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final ResourceHandler<ItemResource> handler;
    private final IndexModifier<ItemResource> slotModifier;
    private final Predicate<ServerPlayer> validityChecker;

    public NeoForgeItemHandlerDataSource(ContainerBindType bindType,
                                         ResourceHandler<ItemResource> handler,
                                         Predicate<ServerPlayer> validityChecker) {
        this.bindType = Objects.requireNonNull(bindType, "bindType");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.slotModifier = resolveSlotModifier(handler);
        this.validityChecker = validityChecker == null ? _ -> true : validityChecker;
    }

    @Override
    public ContainerBindType bindType() {
        return bindType;
    }

    @Override
    public int capacity() {
        return handler.size();
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y) {
        return new ResourceHandlerSlot(handler, slotModifier, slotIndex, x, y);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        return validityChecker.test(player);
    }

    private static IndexModifier<ItemResource> resolveSlotModifier(ResourceHandler<ItemResource> handler) {
        if (handler instanceof ItemStacksResourceHandler stacks) {
            return stacks::set;
        }

        if (handler instanceof StacksResourceHandler<?, ?> stacks) {
            @SuppressWarnings("unchecked")
            StacksResourceHandler<Object, ItemResource> typed = (StacksResourceHandler<Object, ItemResource>) stacks;
            return typed::set;
        }

        // 这里用 “先提取现有内容，再插入请求内容” 来模拟 set 操作
        // 这比直接调用修改器慢，但它同样适用于非 stack-based 的处理器
        return (index, resource, amount) -> {
            try (var tx = Transaction.openRoot()) {
                ItemResource existingResource = handler.getResource(index);
                int existingAmount = handler.getAmountAsInt(index);
                if (!existingResource.isEmpty() && existingAmount > 0) {
                    int extracted = handler.extract(index, existingResource, existingAmount, tx);
                    if (extracted != existingAmount) {
                        return;
                    }
                }

                if (!resource.isEmpty() && amount > 0) {
                    int inserted = handler.insert(index, resource, amount, tx);
                    if (inserted != amount) {
                        return;
                    }
                }

                tx.commit();
            }
        };
    }
}
