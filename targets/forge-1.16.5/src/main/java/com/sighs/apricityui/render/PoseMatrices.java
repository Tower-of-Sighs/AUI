package com.sighs.apricityui.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PoseMatrices {
    private PoseMatrices() { }
    public static Matrix4f of(MatrixStack pose) { return MatrixBridge.fromMojang(pose.last().pose()); }
    public static void set(MatrixStack pose, Matrix4f matrix) { MatrixBridge.toMojang(matrix, pose.last().pose()); }
    public static Matrix3f normal(MatrixStack pose) { return MatrixBridge.fromMojang(pose.last().normal()); }
    public static void setNormal(MatrixStack pose, Matrix3f normal) { MatrixBridge.toMojang(normal, pose.last().normal()); }
    public static void mulPoseMatrix(MatrixStack pose, Matrix4f matrix) { pose.last().pose().multiply(MatrixBridge.toMojang(matrix)); }
    public static void scale2D(MatrixStack pose, float x, float y) { mulPoseMatrix(pose, new Matrix4f().scaling(x, y, 1)); }
    public static void mulPose(MatrixStack pose, Quaternionf quaternion) {
        pose.mulPose(new net.minecraft.util.math.vector.Quaternion(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w()));
    }
}
