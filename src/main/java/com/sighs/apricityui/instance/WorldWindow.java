package com.sighs.apricityui.instance;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class WorldWindow {
    static final List<WorldWindow> windows = new ArrayList<>();

    public Document document;
    private Vec3 position;
    private float yRot;
    private float xRot;
    private float scale; // 缩放比例: 1px 对应多少 Block
    private final float width;
    private final float height;
    private final int maxDistance;

    public WorldWindow(String documentPath, Vec3 position, float width, float height, int maxDistance) {
        this.document = Document.createInWorld(documentPath);
        this.position = position;
        this.width = width;
        this.height = height;
        this.yRot = 0;
        this.xRot = 0;
        this.scale = 0.02f; // 默认缩放: 50px = 1 block
        this.maxDistance = maxDistance;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    protected Vec3 getPosition() {
        return position;
    }

    public void setRotation(float yRot, float xRot) {
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public void render(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(
                position.x - cameraPos.x,
                position.y - cameraPos.y,
                position.z - cameraPos.z
        );

        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - this.yRot));
        poseStack.mulPose(Vector3f.XP.rotationDegrees(this.xRot));

        poseStack.scale(scale, -scale, scale);
        poseStack.translate(-width / 2.0f, -height / 2.0f, 0);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Depth bias to avoid z-fighting with world geometry.
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0f, -1.0f);

        float desiredWorldStep = 0.0020f;
        float safeScale = Math.max(1.0e-4f, scale);
        float localStep = desiredWorldStep / safeScale;
        if (localStep > 0.2f) localStep = 0.2f;
        Base.pushDepthStep(localStep);
        Base.pushDepthMode(true);
        try {
            Base.drawDocument(poseStack, document);
        } finally {
            Base.popDepthMode();
            Base.popDepthStep();
        }

        bufferSource.endBatch();
        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();

        poseStack.popPose();
    }

    public static void addWindow(WorldWindow window) {
        windows.add(window);
    }

    public static void removeWindow(WorldWindow window) {
        window.document.remove();
        windows.remove(window);
    }

    public static void clear() {
        for (WorldWindow window : new ArrayList<>(windows)) {
            removeWindow(window);
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            if (windows.isEmpty()) return;

            for (WorldWindow window : windows) {
                window.render(event.getPoseStack(), event.getProjectionMatrix(), event.getPartialTick());
            }
        }
    }

    public Position getRealPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        float frameTime = mc.getFrameTime();
        Vec3 rayOrigin = mc.player.getEyePosition(frameTime);
        Vec3 rayDir = mc.player.getViewVector(frameTime);
        float yRadians = (float) Math.toRadians(180.0F - this.yRot);
        float xRadians = (float) Math.toRadians(this.xRot);
        Vec3 basisX = new Vec3(1.0D, 0.0D, 0.0D).yRot(yRadians).xRot(xRadians).normalize();
        Vec3 basisY = new Vec3(0.0D, -1.0D, 0.0D).yRot(yRadians).xRot(xRadians).normalize();
        Vec3 planeNormal = new Vec3(0.0D, 0.0D, 1.0D).yRot(yRadians).xRot(xRadians).normalize();
        Vec3 planeCenter = position;

        double denominator = planeNormal.dot(rayDir);
        if (Math.abs(denominator) < 1e-6) return null;

        Vec3 toCenter = planeCenter.subtract(rayOrigin);
        double t = toCenter.dot(planeNormal) / denominator;
        if (t < 0 || t > maxDistance) return null;

        Vec3 intersection = rayOrigin.add(rayDir.scale(t));
        Vec3 topLeft = planeCenter
                .subtract(basisX.scale(width * scale / 2.0D))
                .subtract(basisY.scale(height * scale / 2.0D));
        Vec3 relativeHit = intersection.subtract(topLeft);
        double localX = relativeHit.dot(basisX) / scale;
        double localY = relativeHit.dot(basisY) / scale;

        if (localX >= 0 && localX <= width && localY >= 0 && localY <= height) {
            return new Position(localX, localY);
        }

        return null;
    }
}
