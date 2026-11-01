package com.sighs.apricityui.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.MatrixUtil;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Replays submitted entity geometry into a material-preserving preview mesh. */
final class NativeModelCapture extends SubmitNodeStorage {
    private final ModelVertexCapture output;
    private final Map<Integer, Geometry> groups = new TreeMap<>();
    private final TranslucentFeatureRenderPhase seeThrough = new TranslucentFeatureRenderPhase();

    NativeModelCapture(ModelVertexCapture output) { this.output = output; }

    @Override
    public SubmitNodeCollection order(int order) {
        return groups.computeIfAbsent(order, ignored -> new Geometry());
    }

    void finish() {
        groups.values().forEach(group -> group.draws.forEach(Runnable::run));
    }

    private static PoseStack copy(PoseStack original) {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(original.last().pose());
        pose.last().normal().set(original.last().normal());
        return pose;
    }

    private void quads(PoseStack pose, List<BakedQuad> quads, RenderType forcedType, int[] colors,
                       int light, int overlay, ItemDisplayContext context, FoilType foil) {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        PoseStack.Pose decalPose = foil == FoilType.SPECIAL ? pose.last().copy() : null;
        if (decalPose != null && context == ItemDisplayContext.GUI) MatrixUtil.mulComponentWise(decalPose.pose(), 0.5F);
        else if (decalPose != null && context.firstPerson()) MatrixUtil.mulComponentWise(decalPose.pose(), 0.75F);
        for (BakedQuad quad : quads) {
            BakedQuad.MaterialInfo material = quad.materialInfo();
            int tint = material.tintIndex();
            instance.setColor(material.isTinted() && tint >= 0 && tint < colors.length ? colors[tint] : -1);
            RenderType type = forcedType != null ? forcedType : switch (foil) {
                case NONE -> material.itemRenderType();
                case STANDARD -> material.itemGlintRenderType();
                case SPECIAL -> material.itemGlintSpecialRenderType();
            };
            VertexConsumer vertices = output.getBuffer(type);
            if (decalPose == null) vertices.putBakedQuad(pose.last(), quad, instance);
            else vertices.putBakedQuadWithGlint(pose.last(), quad, instance, decalPose);
        }
    }

    private final class Geometry extends SubmitNodeCollection {
        private final ArrayList<Runnable> draws = new ArrayList<>();

        private Geometry() { super(false, NativeModelCapture.this.seeThrough); }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type,
                                   int light, int overlay, int color, UvMapping uvMapping, int outline) {
            PoseStack saved = copy(pose);
            draws.add(() -> {
                model.setupAnim(state);
                VertexConsumer vertices = output.getBuffer(type);
                if (uvMapping != null) vertices = uvMapping.wrap(vertices);
                model.renderToBuffer(saved, vertices, light, overlay, color);
            });
        }

        @Override
        public void submitBlockModel(PoseStack pose, RenderType type, List<BlockStateModelPart> parts,
                                     int[] colors, int light, int overlay, int outline) {
            PoseStack saved = copy(pose);
            draws.add(() -> {
                for (BlockStateModelPart part : parts) {
                    quads(saved, part.getQuads(null), type, colors, light, overlay, ItemDisplayContext.NONE, FoilType.NONE);
                    for (Direction side : Direction.values())
                        quads(saved, part.getQuads(side), type, colors, light, overlay, ItemDisplayContext.NONE, FoilType.NONE);
                }
            });
        }

        @Override
        public void submitMultiLayerBlockModel(PoseStack pose, List<BlockStateModelPart> parts, boolean translucent,
                                               int[] colors, int light, int overlay, int outline) {
            PoseStack saved = copy(pose);
            draws.add(() -> {
                for (BlockStateModelPart part : parts) {
                    quads(saved, part.getQuads(null), null, colors, light, overlay, ItemDisplayContext.NONE, FoilType.NONE);
                    for (Direction side : Direction.values())
                        quads(saved, part.getQuads(side), null, colors, light, overlay, ItemDisplayContext.NONE, FoilType.NONE);
                }
            });
        }

        @Override
        public void submitBreakingBlockModel(PoseStack pose, List<BlockStateModelPart> parts, int progress, boolean translucent) {
            throw new IllegalArgumentException("Entity preview contains unsupported breaking-block geometry");
        }

        @Override
        public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline,
                               int[] colors, ItemQuads quads, FoilType foil) {
            PoseStack saved = copy(pose);
            draws.add(() -> NativeModelCapture.this.quads(saved, quads.all(), null, colors, light, overlay, context, foil));
        }

        @Override
        public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer renderer) {
            PoseStack saved = copy(pose);
            draws.add(() -> renderer.render(saved.last(), output.getBuffer(type)));
        }

        @Override
        public void submitMovingBlock(PoseStack pose, MovingBlockRenderState state, int outline) {
            throw new IllegalArgumentException("Entity preview contains unsupported moving-block geometry");
        }
    }
}
