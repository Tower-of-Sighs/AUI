package com.sighs.apricityui.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.chunkmap.AuiMapCamera;
import com.sighs.apricityui.chunkmap.AuiMapRenderState;
import com.sighs.apricityui.chunkmap.AuiMapDepthTarget;
import com.sighs.apricityui.forge.RenderService;
import com.sighs.apricityui.render.PoseMatrices;
import java.util.Collection;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/** Draws native entities and skin heads against the terrain's existing depth buffer. */
public final class AuiMapEntityLayer {
    private static EntityRenderDispatcher dispatcher;
    private static BufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private static EntityModelSet headModels;
    private static SkullModel headWithHat, headWithoutHat;
    private static final AuiMapDepthTarget indicatorDepth = new AuiMapDepthTarget();
    private static boolean drawing;
    private static final MapCamera VIEW = new MapCamera();

    private AuiMapEntityLayer() { }
    public static boolean isDrawing() { return drawing; }

    public static boolean visible(AuiMapCamera camera, AuiEntityPreview.Model model) {
        if (camera == null || model == null) return false;
        var offset = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(model.state())
                .getRenderOffset(model.state(), model.partialTicks());
        float x = (float) (model.x() + offset.x - camera.x());
        float y = (float) (model.y() + offset.y - camera.y());
        float z = (float) (model.z() + offset.z - camera.z());
        float radius = model.width() * 0.5F;
        return new FrustumIntersection(camera.projectionMatrix().mul(camera.viewMatrix())).testAab(
                x - radius, y, z - radius, x + radius, y + model.height() + 0.35F, z + radius);
    }

    public static void draw(AuiMapCamera camera, Collection<AuiEntityPreview.Model> models, RenderTarget target,
                            AuiMapDepthTarget depth) {
        drawModels(camera, models, target, depth);
        drawIndicators(camera, models, target);
    }

    public static void drawModels(AuiMapCamera camera, Collection<AuiEntityPreview.Model> models, RenderTarget target,
                                  AuiMapDepthTarget depth) {
        drawPass(camera, models, target, depth, false);
    }

    public static void drawIndicators(AuiMapCamera camera, Collection<AuiEntityPreview.Model> models, RenderTarget target) {
        if (models.stream().noneMatch(model -> model.state() instanceof AbstractClientPlayer)) return;
        drawPass(camera, models, target, indicatorDepth, true);
    }

    private static void drawPass(AuiMapCamera camera, Collection<AuiEntityPreview.Model> models, RenderTarget target,
                                 AuiMapDepthTarget depth, boolean indicatorsOnly) {
        if (models.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Draw map entities on the client thread");
        if (dispatcher == null) {
            dispatcher = new EntityRenderDispatcher(minecraft, minecraft.getTextureManager(), minecraft.getItemRenderer(),
                    minecraft.getBlockRenderer(), minecraft.font, minecraft.options, minecraft.getEntityModels());
            dispatcher.onResourceManagerReload(minecraft.getResourceManager());
            storage = new BufferBuilder(1536);
            buffers = MultiBufferSource.immediate(storage);
        }
        Matrix4f previousProjection = RenderService.INSTANCE.getProjectionMatrix();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try (var state = new AuiMapRenderState(target, depth)) {
            drawing = true;
            Matrix4f view = camera.viewMatrix();
            Quaternionf orientation = new Matrix4f(view).invert().getNormalizedRotation(new Quaternionf());
            Vector3f eye = camera.eyeOffset();
            VIEW.place(minecraft, camera, eye);
            dispatcher.prepare(minecraft.level, VIEW, null);
            dispatcher.overrideCameraOrientation(new com.mojang.math.Quaternion(
                    orientation.x(), orientation.y(), orientation.z(), orientation.w()));
            dispatcher.setRenderHitBoxes(false);
            RenderService.INSTANCE.setProjectionMatrix(camera.projectionMatrix());
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderFogStart(1_000_000);
            RenderSystem.setShaderFogEnd(1_000_001);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            if (indicatorsOnly) {
                GL11.glClearDepth(1);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            }
            minecraft.gameRenderer.lightTexture().turnOnLightLayer();
            for (AuiEntityPreview.Model model : models) {
                if (!visible(camera, model)) continue;
                double eyeX = camera.x() + eye.x, eyeY = camera.y() + eye.y, eyeZ = camera.z() + eye.z;
                float radius = model.width() * 0.5F;
                if (camera.mode().equals("street") && eyeX >= model.x() - radius && eyeX <= model.x() + radius
                        && eyeZ >= model.z() - radius && eyeZ <= model.z() + radius
                        && eyeY >= model.y() && eyeY <= model.y() + model.height()) continue;
                PoseStack pose = new PoseStack();
                PoseMatrices.mulPoseMatrix(pose, view);
                if (!indicatorsOnly) {
                    dispatcher.render(model.state(), model.x() - camera.x(), model.y() - camera.y(), model.z() - camera.z(),
                            model.state().getYRot(), model.partialTicks(), pose, buffers,
                            dispatcher.getPackedLightCoords(model.state(), model.partialTicks()));
                    continue;
                }
                if (!(model.state() instanceof AbstractClientPlayer player)) continue;
                var offset = dispatcher.getRenderer(player).getRenderOffset(player, model.partialTicks());
                double x = model.x() + offset.x, y = model.y() + offset.y + model.height() + 0.35, z = model.z() + offset.z;
                Vector3f tip = camera.project(x, y, z), right = orientation.transform(new Vector3f(1, 0, 0));
                Vector3f unit = camera.project(x + right.x, y + right.y, z + right.z);
                float pixels = (float) Math.hypot(unit.x - tip.x, unit.y - tip.y);
                if (!Float.isFinite(pixels) || pixels <= 0) continue;
                float scale = (float) (net.minecraft.util.Mth.clamp(0.5 * pixels, 16, 24) / pixels / 0.5);
                pose.translate(x - camera.x(), y - camera.y(), z - camera.z());
                PoseMatrices.mulPose(pose, orientation);
                pose.mulPose(com.mojang.math.Vector3f.YP.rotationDegrees(180));
                pose.mulPose(com.mojang.math.Vector3f.XP.rotationDegrees(-15));
                pose.scale(-scale, -scale, scale);
                SkullModel head = playerHead(minecraft, player.isModelPartShown(PlayerModelPart.HAT));
                head.setupAnim(0, 0, 0);
                head.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(player.getSkinTextureLocation())),
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            }
            buffers.endBatch();
        } finally {
            drawing = false;
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderService.INSTANCE.setProjectionMatrix(previousProjection);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    public static void close() {
        indicatorDepth.close();
        dispatcher = null; buffers = null;
        if (storage != null) { org.lwjgl.system.MemoryUtil.getAllocator(false).free(org.lwjgl.system.MemoryUtil.memAddress0(storage.buffer)); storage = null; }
        headModels = null; headWithHat = headWithoutHat = null;
    }

    private static SkullModel playerHead(Minecraft minecraft, boolean hat) {
        EntityModelSet current = minecraft.getEntityModels();
        if (headModels != current) {
            headModels = current;
            headWithHat = new SkullModel(current.bakeLayer(ModelLayers.PLAYER_HEAD));
            var withoutHat = current.bakeLayer(ModelLayers.PLAYER_HEAD);
            withoutHat.getChild("head").getChild("hat").visible = false;
            headWithoutHat = new SkullModel(withoutHat);
        }
        return hat ? headWithHat : headWithoutHat;
    }

    private static final class MapCamera extends Camera {
        void place(Minecraft minecraft, AuiMapCamera camera, Vector3f eye) {
            setup(minecraft.level, minecraft.getCameraEntity(), false, false, 0);
            Vector3f forward = new Vector3f(eye).normalize().negate();
            setRotation((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)),
                    (float) Math.toDegrees(Math.atan2(-forward.y, Math.hypot(forward.x, forward.z))));
            setPosition(camera.x() + eye.x, camera.y() + eye.y, camera.z() + eye.z);
        }
    }
}
