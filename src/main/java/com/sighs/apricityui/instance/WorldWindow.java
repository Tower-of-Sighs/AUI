package com.sighs.apricityui.instance;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Position;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector4f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldWindow {
    static final List<WorldWindow> windows = new ArrayList<>();

    public Document document;
    @Setter
    private Vector3d position;
    private float yRot;
    private float xRot;
    @Setter
    private float scale; // 缩放比例: 1px 对应多少 Block
    private int width;
    private int height;
    private int maxDistance;

    public WorldWindow(String documentPath, Vector3d position, float width, float height, int maxDistance) {
        this.document = Document.createInWorld(documentPath);
        this.position = position;
        this.width = (int) width;
        this.height = (int) height;
        this.yRot = 0;
        this.xRot = 0;
        this.scale = 0.02f; // 默认缩放: 50px = 1 block
        this.maxDistance = maxDistance;
    }

    public void setRotation(float yRot, float xRot) {
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public void render(MatrixStack stack, Matrix4f projectionMatrix, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Vector3d cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        stack.pushPose();
        stack.translate(
                position.x - cameraPos.x,
                position.y - cameraPos.y,
                position.z - cameraPos.z
        );

        stack.mulPose(Vector3f.YP.rotationDegrees(180.0F - this.yRot));
        stack.mulPose(Vector3f.XP.rotationDegrees(this.xRot));

        stack.scale(scale, -scale, scale);
        stack.translate(-width / 2.0f, -height / 2.0f, 0);

        IRenderTypeBuffer.Impl bufferSource = mc.renderBuffers().bufferSource();

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Base.drawDocument(stack, document);

        bufferSource.endBatch();

        stack.popPose();
    }

    public static boolean contains(String path) {
        for (WorldWindow window : windows) {
            if (window.document.is(path)) return true;
        }
        return false;
    }

    public static boolean contains(WorldWindow window) {
        return windows.contains(window);
    }

    public static boolean cotains(String path) {
        for (WorldWindow window : windows) {
            if (window.document.is(path)) return true;
        }
        return false;
    }

    public static boolean cotains(WorldWindow window) {
        return windows.contains(window);
    }

    public static void addWindow(WorldWindow window) {
        windows.add(window);
    }

    public static void removeWindow(WorldWindow window) {
        window.document.remove();
        windows.remove(window);
    }

    public static void clear() {
        windows.forEach(WorldWindow::removeWindow);
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderWorldLastEvent event) {
        if (windows.isEmpty()) return;
        for (WorldWindow window : windows) {
            window.render(event.getMatrixStack(), event.getProjectionMatrix(), event.getPartialTicks());
        }
    }

    public Position getRealPos() {
        Minecraft mc = Minecraft.getInstance();
        // Position invalid = new Position(-1, -1);
        if (mc.player == null) return null;

        float partialTick = mc.getFrameTime();
        Vector3d rayOrigin = mc.player.getEyePosition(partialTick);
        Vector3d rayDir = mc.player.getViewVector(partialTick);

        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.setIdentity();
        modelMatrix.multiply(Matrix4f.createTranslateMatrix((float) position.x, (float) position.y, (float) position.z));
        modelMatrix.multiply(Vector3f.YP.rotationDegrees(180.0F - this.yRot));
        modelMatrix.multiply(Vector3f.XP.rotationDegrees(this.xRot));
        modelMatrix.multiply(Matrix4f.createScaleMatrix(scale, -scale, scale));

        Vector4f centerWorld = new Vector4f(0, 0, 0, 1);
        centerWorld.transform(modelMatrix);
        Vector4f normalWorld = new Vector4f(0, 0, 1, 0);
        normalWorld.transform(modelMatrix);
        Vector3d planeNormal = new Vector3d(normalWorld.x(), normalWorld.y(), normalWorld.z()).normalize();
        Vector3d planeCenter = new Vector3d(centerWorld.x(), centerWorld.y(), centerWorld.z());

        double denominator = planeNormal.dot(rayDir);

        if (Math.abs(denominator) < 1e-6) return null;

        Vector3d toCenter = planeCenter.subtract(rayOrigin);
        double t = toCenter.dot(planeNormal) / denominator;

        if (t < 0 || t > maxDistance) return null;

        Vector3d intersection = rayOrigin.add(rayDir.scale(t));
        Matrix4f inverseMatrix = modelMatrix.copy();
        inverseMatrix.invert();
        Vector4f localHit = new Vector4f((float) intersection.x, (float) intersection.y, (float) intersection.z, 1.0f);
        localHit.transform(inverseMatrix);

        double localX = localHit.x() + width / 2.0;
        double localY = localHit.y() + height / 2.0;

        if (localX >= 0 && localX <= width && localY >= 0 && localY <= height) {
            return new Position(localX, localY);
        }

        return null;
    }
}