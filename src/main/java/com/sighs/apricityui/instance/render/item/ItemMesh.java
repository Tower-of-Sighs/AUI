package com.sighs.apricityui.instance.render.item;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 已解析的标准模型 mesh。
 * 每次缓存 miss 都按当前 stack 重新解析 render pass、RenderType 与 quad，避免动态模型复用旧状态。
 */
public final class ItemMesh {
    private final List<Pass> passes;

    private ItemMesh(List<Pass> passes) {
        this.passes = Collections.unmodifiableList(passes);
    }

    /**
     * GUI 与原版 ItemRenderer 的 direct 分支一致：每次按当前 stack 解析 render pass / RenderType。
     */
    static ItemMesh build(BakedModel model, ItemStack stack, int seed) {
        ArrayList<Pass> passes = new ArrayList<>();
        for (BakedModel passModel : model.getRenderPasses(stack, true)) {
            if (passModel == null) continue;
            List<Quad> quads = collectQuads(passModel, seed);
            if (quads.isEmpty()) continue;
            List<RenderType> renderTypes = passModel.getRenderTypes(stack, true);
            passes.add(new Pass(quads, renderTypes == null ? List.of() : List.copyOf(renderTypes)));
        }
        return new ItemMesh(passes);
    }

    static List<Quad> collectQuads(BakedModel model, int seed) {
        RandomSource random = RandomSource.create();
        ArrayList<Quad> quads = new ArrayList<>();
        long quadSeed = seed;
        for (Direction direction : Direction.values()) {
            random.setSeed(quadSeed);
            append(quads, model.getQuads(null, direction, random));
        }
        random.setSeed(quadSeed);
        append(quads, model.getQuads(null, null, random));
        return quads;
    }

    private static void append(List<Quad> target, List<BakedQuad> source) {
        if (source == null || source.isEmpty()) return;
        for (BakedQuad quad : source) {
            if (quad == null || quad.getSprite() == null) continue;
            target.add(new Quad(quad, quad.getSprite().atlasLocation()));
        }
    }

    public List<Pass> passes() {
        return passes;
    }

    public boolean isEmpty() {
        return passes.isEmpty();
    }

    public record Pass(List<Quad> quads, List<RenderType> renderTypes) {
        public Pass {
            quads = List.copyOf(quads);
            renderTypes = List.copyOf(renderTypes);
        }
    }

    public record Quad(BakedQuad bakedQuad, ResourceLocation atlasLocation) {
    }
}
