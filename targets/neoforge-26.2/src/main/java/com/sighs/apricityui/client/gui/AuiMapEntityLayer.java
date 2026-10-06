package com.sighs.apricityui.client.gui;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sighs.apricityui.chunkmap.AuiMapCamera;
import com.sighs.apricityui.neoforge.RenderService;
import com.sighs.apricityui.render.OutputTargets;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws world-sized entity models into the current map's native render target. */
public final class AuiMapEntityLayer {
    private static ProjectionMatrixBuffer projectionBuffer;
    private static FogRenderer fog;
    private static EntityModelSet headModels;
    private static SkullModel headWithHat, headWithoutHat;
    private static GpuTexture indicatorDepth;
    private static GpuTextureView indicatorDepthView;
    private static GpuTextureView indicatorColorView;

    private AuiMapEntityLayer() { }

    public static boolean visible(AuiMapCamera camera, AuiEntityPreview.Model model) {
        var offset = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(model.state()).getRenderOffset(model.state());
        float x = (float) (model.x() + offset.x() - camera.x());
        float y = (float) (model.y() + offset.y() - camera.y());
        float z = (float) (model.z() + offset.z() - camera.z());
        float radius = model.state().boundingBoxWidth * 0.5F;
        return new FrustumIntersection(camera.projectionMatrix().mul(camera.viewMatrix())).testAab(
                x - radius, y, z - radius, x + radius, y + model.state().boundingBoxHeight + 0.35F, z + radius);
    }

    public static void draw(AuiMapCamera camera, Collection<AuiEntityPreview.Model> models, RenderTarget target) {
        drawModels(camera, models, target);
        drawIndicators(camera, models, target);
    }

    public static void drawModels(AuiMapCamera camera, Collection<AuiEntityPreview.Model> models, RenderTarget target) {
        drawPass(camera, models, target, false);
    }

    public static void drawIndicators(AuiMapCamera camera, Collection<AuiEntityPreview.Model> models, RenderTarget target) {
        if (models.stream().noneMatch(model -> model.state() instanceof AvatarRenderState)) return;
        if (indicatorColorView == null || indicatorColorView.texture() != target.getColorTexture()
                || indicatorDepth.getWidth(0) != target.width || indicatorDepth.getHeight(0) != target.height) {
            if (indicatorColorView != null) indicatorColorView.close();
            if (indicatorDepthView != null) indicatorDepthView.close();
            if (indicatorDepth != null) indicatorDepth.close();
            indicatorDepth = RenderSystem.getDevice().createTexture("AUI map indicator depth",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
                    com.mojang.blaze3d.GpuFormat.D32_FLOAT, target.width, target.height, 1, 1);
            indicatorDepthView = RenderSystem.getDevice().createTextureView(indicatorDepth);
            // Keep depth-dependent framebuffer caches within the indicator's lifetime.
            indicatorColorView = RenderSystem.getDevice().createTextureView(target.getColorTexture());
        }
        RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(indicatorDepth, 0);
        drawPass(camera, models, target, true);
    }

    private static void drawPass(AuiMapCamera camera, Collection<AuiEntityPreview.Model> models, RenderTarget target,
                                 boolean indicatorsOnly) {
        if (models.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousProjectionType = RenderSystem.getProjectionType();
        var previousLights = RenderSystem.getShaderLights();
        var previousFog = RenderSystem.getShaderFog();
        var previousTarget = OutputTargets.rawCurrentTarget();
        var previousColor = RenderSystem.outputColorTextureOverride;
        var previousDepth = RenderSystem.outputDepthTextureOverride;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            if (projectionBuffer == null) projectionBuffer = new ProjectionMatrixBuffer("apricityui-map-entities");
            if (fog == null) fog = new FogRenderer();
            Matrix4f view = camera.viewMatrix();
            boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
            Matrix4f projection = camera.nativeProjectionMatrix(zeroToOne);
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection),
                    camera.mode().equals("street") ? ProjectionType.PERSPECTIVE : ProjectionType.ORTHOGRAPHIC);
            modelView.identity();
            RenderSystem.setShaderFog(fog.getBuffer(FogRenderer.FogMode.NONE));
            minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
            OutputTargets.setCurrent(target);
            RenderSystem.outputColorTextureOverride = indicatorsOnly ? indicatorColorView : target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = indicatorsOnly ? indicatorDepthView : target.getDepthTextureView();

            Vector3f eye = camera.eyeOffset();
            Quaternionf orientation = new Matrix4f(view).invert().getNormalizedRotation(new Quaternionf());
            CameraRenderState cameraState = new CameraRenderState();
            cameraState.initialized = true;
            cameraState.pos = new Vec3(camera.x() + eye.x, camera.y() + eye.y, camera.z() + eye.z);
            cameraState.orientation.set(orientation);
            cameraState.projectionMatrix.set(projection);
            cameraState.viewRotationMatrix.set(view).setTranslation(0, 0, 0);
            Vector3f forward = new Vector3f(eye).normalize().negate();
            cameraState.xRot = (float) Math.toDegrees(Math.atan2(-forward.y, Math.hypot(forward.x, forward.z)));
            cameraState.yRot = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
            SubmitNodeStorage nodes = new SubmitNodeStorage();
            var dispatcher = minecraft.getEntityRenderDispatcher();
            for (AuiEntityPreview.Model model : models) {
                if (!visible(camera, model)) continue;
                float halfWidth = model.state().boundingBoxWidth * 0.5F;
                if (camera.mode().equals("street") && cameraState.pos.x >= model.x() - halfWidth
                        && cameraState.pos.x <= model.x() + halfWidth && cameraState.pos.z >= model.z() - halfWidth
                        && cameraState.pos.z <= model.z() + halfWidth && cameraState.pos.y >= model.y()
                        && cameraState.pos.y <= model.y() + model.state().boundingBoxHeight) continue;
                PoseStack pose = new PoseStack();
                pose.mulPose(view);
                double dx = model.x() - camera.x(), dy = model.y() - camera.y(), dz = model.z() - camera.z();
                model.state().distanceToCameraSq = cameraState.pos.distanceToSqr(model.x(), model.y(), model.z());
                if (!indicatorsOnly) {
                    dispatcher.submit(model.state(), cameraState, dx, dy, dz, pose, nodes);
                    continue;
                }
                if (!(model.state() instanceof AvatarRenderState avatar)) continue;
                var renderOffset = dispatcher.getRenderer(model.state()).getRenderOffset(model.state());
                double headX = model.x() + renderOffset.x(), headZ = model.z() + renderOffset.z();
                double headY = model.y() + renderOffset.y() + model.state().boundingBoxHeight + 0.35;
                Vector3f tip = camera.project(headX, headY, headZ);
                Vector3f right = orientation.transform(new Vector3f(1, 0, 0));
                Vector3f unit = camera.project(headX + right.x, headY + right.y, headZ + right.z);
                float pixelsPerUnit = (float) Math.hypot(unit.x - tip.x, unit.y - tip.y);
                if (!Float.isFinite(pixelsPerUnit) || pixelsPerUnit <= 0) continue;
                float headScale = (float) (Math.clamp(0.5 * pixelsPerUnit, 16, 24) / pixelsPerUnit / 0.5);
                pose.translate(headX - camera.x(), headY - camera.y(), headZ - camera.z());
                pose.mulPose(orientation);
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.mulPose(Axis.XP.rotationDegrees(-15));
                pose.scale(-headScale, -headScale, headScale);
                SkullBlockRenderer.submitSkull(0, pose, nodes, LightCoordsUtil.FULL_BRIGHT, playerHead(minecraft, avatar.showHat),
                        SkullBlockRenderer.getPlayerSkinRenderType(avatar.skin.body().texturePath()), 0, null);
            }
            RenderService.INSTANCE.dispatchNativeFeatures(nodes);
        } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            OutputTargets.setCurrent(previousTarget);
            RenderSystem.setShaderFog(previousFog);
            RenderSystem.setShaderLights(previousLights);
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            modelView.popMatrix();
        }
    }

    public static void close() {
        if (indicatorColorView != null) { indicatorColorView.close(); indicatorColorView = null; }
        if (indicatorDepthView != null) { indicatorDepthView.close(); indicatorDepthView = null; }
        if (indicatorDepth != null) { indicatorDepth.close(); indicatorDepth = null; }
        if (projectionBuffer != null) { projectionBuffer.close(); projectionBuffer = null; }
        if (fog != null) { fog.close(); fog = null; }
        headModels = null; headWithHat = headWithoutHat = null;
    }

    private static SkullModel playerHead(Minecraft minecraft, boolean showHat) {
        EntityModelSet current = minecraft.getEntityModels();
        if (headModels != current) {
            headModels = current;
            headWithHat = new SkullModel(current.bakeLayer(ModelLayers.PLAYER_HEAD));
            headWithoutHat = new SkullModel(current.bakeLayer(ModelLayers.PLAYER_HEAD));
            headWithoutHat.root().getChild("head").getChild("hat").visible = false;
        }
        return showHat ? headWithHat : headWithoutHat;
    }
}
