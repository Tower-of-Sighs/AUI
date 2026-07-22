package com.sighs.apricityui.render.item;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 仅在单次 AUI 文档绘制期间缓存完整 ItemMesh。
 * 动态模型可能随时间、上下文或 stack NBT 改变；跨帧缓存 quad 会造成陈旧渲染，因此帧结束即清空。
 */
public final class ItemMeshCache {
    private static final Map<BakedModel, Map<MeshKey, ItemMesh>> FRAME_MESH_CACHE = new IdentityHashMap<>();

    private ItemMeshCache() {
    }

    public static synchronized void beginFrame() {
        FRAME_MESH_CACHE.clear();
    }

    public static synchronized void endFrame() {
        FRAME_MESH_CACHE.clear();
    }

    public static synchronized ItemMesh getOrBuild(BakedModel model, ItemStack stack, int seed) {
        if (model == null) return null;
        ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
        MeshKey key = MeshKey.of(safeStack, seed);
        Map<MeshKey, ItemMesh> byStack = FRAME_MESH_CACHE.computeIfAbsent(model, ignored -> new HashMap<>());
        return byStack.computeIfAbsent(key, ignored -> ItemMesh.build(model, safeStack, seed));
    }

    public static synchronized void clear() {
        FRAME_MESH_CACHE.clear();
    }

    private record MeshKey(int seed, String visualStack) {
        private static MeshKey of(ItemStack stack, int seed) {
            if (stack == null || stack.isEmpty()) return new MeshKey(seed, "empty");
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            CompoundTag serialized = new CompoundTag();
            normalized.save(serialized);
            return new MeshKey(seed, serialized.toString());
        }
    }
}
