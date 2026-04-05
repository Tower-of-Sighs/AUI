package com.sighs.apricityui.instance;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = ApricityUI.MOD_ID, value = Dist.CLIENT)
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

    public void render(PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(
                position.x - cameraPos.x,
                position.y - cameraPos.y,
                position.z - cameraPos.z
        );

        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180.0F - this.yRot)));
        poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(this.xRot)));

        poseStack.scale(scale, -scale, scale);
        poseStack.translate(-width / 2.0f, -height / 2.0f, 0);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.last().pose().set(poseStack.last().pose());
        poseStack.last().normal().set(poseStack.last().normal());

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
        for (WorldWindow window : List.copyOf(windows)) {
            removeWindow(window);
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            if (windows.isEmpty()) return;

            for (WorldWindow window : windows) {
                window.render(event.getPoseStack());
            }
        }
    }

    public Position getRealPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 rayOrigin = mc.player.getEyePosition(partialTick);
        Vec3 rayDir = mc.player.getViewVector(partialTick);

        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.translate((float) position.x, (float) position.y, (float) position.z);
        modelMatrix.rotate((float) Math.toRadians(180.0F - this.yRot), 0, 1, 0);
        modelMatrix.rotate((float) Math.toRadians(this.xRot), 1, 0, 0);
        modelMatrix.scale(scale, -scale, scale);

        Vector4f centerWorld = modelMatrix.transform(new Vector4f(0, 0, 0, 1));
        Vector4f normalWorld = new Vector4f(0, 0, 1, 0);
        modelMatrix.transform(normalWorld);
        Vec3 planeNormal = new Vec3(normalWorld.x, normalWorld.y, normalWorld.z).normalize();
        Vec3 planeCenter = new Vec3(centerWorld.x, centerWorld.y, centerWorld.z);

        double denominator = planeNormal.dot(rayDir);

        if (Math.abs(denominator) < 1e-6) return null;

        Vec3 toCenter = planeCenter.subtract(rayOrigin);
        double t = toCenter.dot(planeNormal) / denominator;

        if (t < 0 || t > maxDistance) return null;

        Vec3 intersection = rayOrigin.add(rayDir.scale(t));
        Matrix4f inverseMatrix = new Matrix4f(modelMatrix).invert();
        Vector4f localHit = new Vector4f((float) intersection.x, (float) intersection.y, (float) intersection.z, 1.0f);
        inverseMatrix.transform(localHit);

        double localX = localHit.x + width / 2.0;
        double localY = localHit.y + height / 2.0;

        if (localX >= 0 && localX <= width && localY >= 0 && localY <= height) {
            return new Position(localX, localY);
        }

        return null;
    }
}
