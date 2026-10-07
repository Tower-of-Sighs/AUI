package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * {@code PoseStack} 矩阵读写的版本隔离层。
 *
 * <p>MC 直到 1.19.3 才用上 JOML：在此之前 {@code PoseStack.Pose} 里的 pose/normal 是
 * com.mojang.math.Matrix4f/Matrix3f，之后才是 org.joml.B。AUI 的绘制代码一律按 org.joml
 * 类型书写，于是「矩阵从 PoseStack 里取出来 / 写回去」这件事就带着版本差异。全部收敛到
 * 本文件之后，上层只需要调这里的方法：1.19.2 的目标在 {@code prepareCommonSources} 里排除
 * 本文件并给出一份用 float 逐位互转的同名实现，其余源码保持逐字不变。</p>
 *
 * <p>{@link #of} 返回的是 pose 里那个矩阵本身（1.19.3+ 语义），调用方按只读使用即可；
 * 需要改 pose 的地方走 {@link #set} / {@link #setNormal} / {@link #mulPoseMatrix}。</p>
 */
public final class PoseMatrices {
    private PoseMatrices() {
    }

    /** 当前 pose 的模型矩阵；调用方只读。 */
    public static Matrix4f of(PoseStack poseStack) {
        return poseStack.last().pose();
    }

    /** 把 {@code matrix} 的数值写回当前 pose 的模型矩阵（替换而不是换引用）。 */
    public static void set(PoseStack poseStack, Matrix4f matrix) {
        poseStack.last().pose().set(matrix);
    }

    /** 当前 pose 的法线矩阵；调用方只读。 */
    public static Matrix3f normal(PoseStack poseStack) {
        return poseStack.last().normal();
    }

    /** 把 {@code normal} 的数值写回当前 pose 的法线矩阵（替换而不是换引用）。 */
    public static void setNormal(PoseStack poseStack, Matrix3f normal) {
        poseStack.last().normal().set(normal);
    }

    /**
     * 左乘一个模型矩阵。
     *
     * <p>PoseStack.mulPoseMatrix 在 1.20.5 改名为 mulPose，而 {@code last().pose().mul(..)}
     * 在两个版本上语义一致，所以这里用后者作为默认实现。</p>
     */
    public static void mulPoseMatrix(PoseStack poseStack, Matrix4f matrix) {
        poseStack.last().pose().mul(matrix);
    }

    /**
     * Scales GUI coordinates without changing depth or item lighting normals.
     * PoseStack.scale(x, y, 1) also rescales normals, which changes the brightness
     * of GUI items when zooming. Layout scaling is not a 3D model transform.
     */
    public static void scale2D(PoseStack poseStack, float x, float y) {
        poseStack.last().pose().scale(x, y, 1.0F);
    }

    /** 左乘一个旋转（当前 pose 的模型矩阵与法线矩阵同时更新）。 */
    public static void mulPose(PoseStack poseStack, Quaternionf quaternion) {
        poseStack.mulPose(quaternion);
    }
}
