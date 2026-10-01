package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * {@code PoseStack} 矩阵读写的版本隔离层（1.19.2 实现）。
 *
 * <p>1.19.2 的 {@code PoseStack.Pose} 用的是 com.mojang.math.Matrix4f/Matrix3f，而 AUI 的
 * 绘制代码一律按 org.joml 书写，类型换算全部交给 {@link MatrixBridge}，这里只负责把 pose
 * 的读写接到它上面。</p>
 *
 * <p>{@link #of}/{@link #normal} 返回的是副本而不是 pose 里的那个矩阵，调用方按只读使用
 * （绘制时当矩阵参数）；需要写回 pose 的地方一律走 {@link #set} / {@link #setNormal} /
 * {@link #mulPoseMatrix}，它们只覆盖数值、不替换 {@code PoseStack.Pose} 持有的实例。</p>
 */
public final class PoseMatrices {
    private PoseMatrices() {
    }

    /** 当前 pose 的模型矩阵；返回的是副本，调用方只读。 */
    public static Matrix4f of(PoseStack poseStack) {
        return MatrixBridge.fromMojang(poseStack.last().pose());
    }

    /** 把 {@code matrix} 的数值写回当前 pose 的模型矩阵（替换而不是换引用）。 */
    public static void set(PoseStack poseStack, Matrix4f matrix) {
        MatrixBridge.toMojang(matrix, poseStack.last().pose());
    }

    /** 当前 pose 的法线矩阵；返回的是副本，调用方只读。 */
    public static Matrix3f normal(PoseStack poseStack) {
        return MatrixBridge.fromMojang(poseStack.last().normal());
    }

    /** 把 {@code normal} 的数值写回当前 pose 的法线矩阵（替换而不是换引用）。 */
    public static void setNormal(PoseStack poseStack, Matrix3f normal) {
        MatrixBridge.toMojang(normal, poseStack.last().normal());
    }

    /** 左乘一个模型矩阵。 */
    public static void mulPoseMatrix(PoseStack poseStack, Matrix4f matrix) {
        poseStack.mulPoseMatrix(MatrixBridge.toMojang(matrix));
    }

    /** 左乘一个旋转（当前 pose 的模型矩阵与法线矩阵同时更新）。 */
    public static void mulPose(PoseStack poseStack, Quaternionf quaternion) {
        // com.mojang.math.Quaternion 的 (i, j, k, r) 就是 org.joml 的 (x, y, z, w)。
        poseStack.mulPose(new com.mojang.math.Quaternion(
                quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w()));
    }
}
