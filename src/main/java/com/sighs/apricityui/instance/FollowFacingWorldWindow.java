package com.sighs.apricityui.instance;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class FollowFacingWorldWindow extends WorldWindow {
    private Vec3 basePosition;

    private final float followFactor;

    public FollowFacingWorldWindow(String documentPath, Vec3 position, float width, float height, int maxDistance, float followFactor) {
        super(documentPath, position, width, height, maxDistance);
        this.basePosition = position;
        this.followFactor = Mth.clamp(followFactor, 0.0f, 1.0f);
    }

    @Override
    public void setPosition(Vec3 position) {
        this.basePosition = position;
        super.setPosition(position);
    }

    @Override
    public void render(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Vector3f lookVec = mc.getEntityRenderDispatcher().camera.getLookVector();
        Vec3 look = new Vec3(lookVec.x(), lookVec.y(), lookVec.z());
        if (basePosition == null) basePosition = getPosition();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 toBase = basePosition.subtract(cameraPos);
        double depth = toBase.dot(look);

        if (depth > 0) {
            Vec3 targetPos = cameraPos.add(look.scale(depth));
            Vec3 finalPos = basePosition.add(targetPos.subtract(basePosition).scale(followFactor));
            super.setPosition(finalPos);
            faceCamera(cameraPos, finalPos);
        } else {
            super.setPosition(basePosition);
            faceCamera(cameraPos, basePosition);
        }

        super.render(poseStack, projectionMatrix, partialTick);
    }

    private void faceCamera(Vec3 cameraPos, Vec3 windowPos) {
        Vec3 toCamera = cameraPos.subtract(windowPos);
        double horiz = Math.sqrt(toCamera.x * toCamera.x + toCamera.z * toCamera.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(toCamera.z, toCamera.x)) - 90.0);
        float pitch = (float) (Math.toDegrees(Math.atan2(toCamera.y, horiz)));
        // Flip to face the camera and correct vertical rotation direction.
        setRotation(yaw + 180.0f, -pitch);
    }
}
