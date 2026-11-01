package com.sighs.apricityui.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;

/** Replays submitted geometry into the preview mesh, retaining renderer order. */
final class NativeModelCapture extends SubmitNodeStorage {
    private final ModelVertexCapture output;
    private final Map<Integer, Geometry> groups = new TreeMap<>();

    NativeModelCapture(ModelVertexCapture output) { this.output = output; }

    @Override
    public SubmitNodeCollection order(int order) {
        return groups.computeIfAbsent(order, ignored -> new Geometry());
    }

    void finish() {
        for (Geometry group : groups.values()) {
            if (!group.getMovingBlockSubmits().isEmpty() || !group.getBreakingBlockModelSubmits().isEmpty())
                throw new IllegalArgumentException("Entity preview contains unsupported moving/breaking block geometry");
            group.draws.forEach(Runnable::run);
        }
    }

    private static PoseStack copy(PoseStack original) {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(original.last().pose());
        pose.last().normal().set(original.last().normal());
        return pose;
    }

    private VertexConsumer vertices(RenderType type, TextureAtlasSprite sprite) {
        VertexConsumer consumer = output.getBuffer(type);
        return sprite == null ? consumer : sprite.wrap(consumer);
    }

    private void quads(PoseStack pose, List<BakedQuad> quads, RenderType type, int[] colors,
                       int light, int overlay, boolean foil) {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        for (BakedQuad quad : quads) {
            int tint = quad.materialInfo().tintIndex();
            instance.setColor(tint >= 0 && tint < colors.length ? colors[tint] : -1);
            RenderType material = type == null ? quad.materialInfo().itemRenderType() : type;
            ItemFeatureRenderer.getFoilBuffer(output, material, true, foil).putBakedQuad(pose.last(), quad, instance);
        }
    }

    private final class Geometry extends SubmitNodeCollection {
        private final ArrayList<Runnable> draws = new ArrayList<>();

        Geometry() { super(NativeModelCapture.this); }

        @Override
        public void submitBlockModel(PoseStack pose, RenderType type, List<BlockStateModelPart> parts,
                                     int[] colors, int light, int overlay, int outline) {
            PoseStack saved = copy(pose);
            draws.add(() -> {
                for (BlockStateModelPart part : parts) {
                    quads(saved, part.getQuads(null), type, colors, light, overlay, false);
                    for (Direction side : Direction.values())
                        quads(saved, part.getQuads(side), type, colors, light, overlay, false);
                }
            });
        }

        @Override
        public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline,
                               int[] colors, List<BakedQuad> quads, FoilType foil) {
            PoseStack saved = copy(pose);
            draws.add(() -> quads(saved, quads, null, colors, light, overlay, foil != FoilType.NONE));
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type,
                                    int light, int overlay, int color, TextureAtlasSprite sprite,
                                    int outline, CrumblingOverlay crumbling) {
            PoseStack saved = copy(pose);
            draws.add(() -> {
                model.setupAnim(state);
                model.renderToBuffer(saved, vertices(type, sprite), light, overlay, color);
            });
        }

        @Override
        public void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light, int overlay,
                                    TextureAtlasSprite sprite, boolean sheeted, boolean foil, int color,
                                    CrumblingOverlay crumbling, int outline) {
            PoseStack saved = copy(pose);
            draws.add(() -> part.render(saved, vertices(type, sprite), light, overlay, color));
        }

        @Override
        public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer renderer) {
            PoseStack saved = copy(pose);
            draws.add(() -> renderer.render(saved.last(), output.getBuffer(type)));
        }
    }
}
