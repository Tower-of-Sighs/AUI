package com.sighs.apricityui.render;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 1.19.2 的 org.joml ↔ com.mojang.math 矩阵换算。
 *
 * <p>MC 到 1.19.3 才用上 JOML：在那之前 {@code RenderSystem.getProjectionMatrix()}、
 * {@code VertexConsumer#vertex(Matrix4f,..)}、{@code PoseStack.Pose} 用的都是 com.mojang.math
 * 的类型，而 AUI 的绘制代码一律按 org.joml 书写。两个类的内存布局完全一致（列主序的 16/9 个
 * float，平移落在 index 12/13/14），所以这里用 FloatBuffer 逐位搬运，而不是按字段名逐个对应
 * ——两边的 {@code m<i><j>} 行列含义正好相反，按名字对应很容易写反。</p>
 *
 * <p>本类只存在于 1.19.2 目标：{@link PoseMatrices} 的 1.19.2 版本就是本类的一层
 * PoseStack 适配。</p>
 */
public final class MatrixBridge {
    /** 转换用的临时缓冲：只在单次调用内使用，渲染线程独占，留 per-thread 只为稳妥。 */
    private static final ThreadLocal<FloatBuffer> SCRATCH =
            ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(16 * Float.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer());

    private MatrixBridge() {
    }

    private static FloatBuffer scratch() {
        FloatBuffer buffer = SCRATCH.get();
        buffer.clear();
        return buffer;
    }

    /**
     * 把 org.joml 矩阵的数值写进调用方给的 com.mojang.math 实例。
     *
     * <p>顶点提交等热路径用它复用同一个目标实例，避免每次换算都分配。安全前提是
     * {@code VertexConsumer#vertex(Matrix4f,..)} 会立刻用矩阵变换顶点、不留引用。</p>
     */
    public static com.mojang.math.Matrix4f toMojang(Matrix4f source, com.mojang.math.Matrix4f target) {
        FloatBuffer buffer = scratch();
        source.get(buffer);
        target.load(buffer);
        return target;
    }

    /** 换算到新分配的 com.mojang.math.Matrix4f。 */
    public static com.mojang.math.Matrix4f toMojang(Matrix4f source) {
        return toMojang(source, new com.mojang.math.Matrix4f());
    }

    /** 换算到新分配的 org.joml.Matrix4f。 */
    public static Matrix4f fromMojang(com.mojang.math.Matrix4f source) {
        FloatBuffer buffer = scratch();
        source.store(buffer);
        return new Matrix4f(buffer);
    }

    /** 把 org.joml 法线矩阵的数值写进调用方给的 com.mojang.math 实例。 */
    public static com.mojang.math.Matrix3f toMojang(Matrix3f source, com.mojang.math.Matrix3f target) {
        FloatBuffer buffer = scratch();
        source.get(buffer);
        target.load(buffer);
        return target;
    }

    /** 换算到新分配的 org.joml.Matrix3f。 */
    public static Matrix3f fromMojang(com.mojang.math.Matrix3f source) {
        FloatBuffer buffer = scratch();
        source.store(buffer);
        return new Matrix3f(buffer);
    }
}
