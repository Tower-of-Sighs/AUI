package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * scissor 遮罩与 CSS transform 的一致性（ore 主题 slider-thumb 的
 * translate(-50%,-50%) 曾让 inset 阴影被留在原地的 scissor 矩形裁掉）。
 * 轴对齐矩阵下遮罩矩形必须先变换到屏幕空间；旋转/错切则不能用 scissor。
 */
class MaskScissorTransformTest {
    @Test
    void translateAndScaleAreAxisAligned() {
        // identity / 纯平移 / 纯缩放的非对角项都为 0
        assertTrue(Mask.isAxisAligned2D(0, 0, 0, 0));
    }

    @Test
    void rotationAndShearAreNotAxisAligned() {
        float s = (float) Math.sin(Math.toRadians(45));
        // rotate(45deg)：m01=-sin，m10=sin
        assertFalse(Mask.isAxisAligned2D(-s, s, 0, 0));
        // z 剪切（顶点带绘制深度 z 时会偏移 x/y）
        assertFalse(Mask.isAxisAligned2D(0, 0, 0.5f, 0));
        assertFalse(Mask.isAxisAligned2D(0, 0, 0, 0.5f));
    }

    @Test
    void elementTransformSurvivesUnderScissorScaleBase() {
        // screen/overlay 文档：基底 = renderScale（快照于 pose.scale 之后），
        // pose = S(2) ∘ T(-14,-14)（slider-thumb 的 translate(-50%,-50%)）。
        // scissor 矩形必须只跟随局部增量 T(-14,-14)，留在 CSS 坐标系由
        // scissorScale 换算——几何与遮罩一起移动，阴影不再被裁。
        float[] local = Mask.divideAxisAligned(2, 0, 0, 2, 0, 0, -28, -28, 2, 2, 0, 0);
        assertEquals(1, local[0], 0.0001);
        assertEquals(1, local[1], 0.0001);
        assertEquals(-14, local[2], 0.0001);
        assertEquals(-14, local[3], 0.0001);
    }

    @Test
    void poseEqualToBaseLeavesMaskUntouched() {
        // 全屏资源管理器回归：没有元素 transform 时 pose 恰等于基底 S(2)，
        // L 必须是恒等——否则 renderScale 被 scissorScale 再乘一次（双重缩放）。
        float[] local = Mask.divideAxisAligned(2, 0, 0, 2, 0, 0, 0, 0, 2, 2, 0, 0);
        assertEquals(1, local[0], 0.0001);
        assertEquals(1, local[1], 0.0001);
        assertEquals(0, local[2], 0.0001);
        assertEquals(0, local[3], 0.0001);
    }

    @Test
    void rotatedElementRejectsScissor() {
        // pose = S(2) ∘ R(45°)：旋转无法表达为轴对齐矩形，返回 null 退回 stencil
        float s = (float) Math.sin(Math.toRadians(45));
        float c = (float) Math.cos(Math.toRadians(45));
        assertNull(Mask.divideAxisAligned(2 * c, 2 * s, -2 * s, 2 * c, 0, 0, 0, 0, 2, 2, 0, 0));
    }

    @Test
    void surfaceBaseIncludesOffsetAndScale() {
        // surface 剪辑的有效基底 = 建立时的 pose ∘ T(offset) ∘ S(scale)：
        // B_eff = T(100,50) ∘ S(3)，元素再叠 T(-14,-14) 后 L 必须仍是纯平移
        float[] local = Mask.divideAxisAligned(3, 0, 0, 3, 0, 0, 58, 8, 3, 3, 100, 50);
        assertEquals(1, local[0], 0.0001);
        assertEquals(-14, local[2], 0.0001);
        assertEquals(-14, local[3], 0.0001);
    }

    @Test
    void degenerateBaseRejectsScissor() {
        assertNull(Mask.divideAxisAligned(1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0));
    }

    @Test
    void translateMovesMaskWithThePaintedGeometry() {
        // slider-thumb：28x28 盒在 (174,22)，translate(-50%,-50%) = (-14,-14)
        AABB mask = Mask.transformAxisAligned(new AABB(174, 22, 24, 24), 1, 1, -14, -14);
        assertEquals(160, mask.x(), 0.001);
        assertEquals(8, mask.y(), 0.001);
        assertEquals(24, mask.width(), 0.001);
        assertEquals(24, mask.height(), 0.001);
    }

    @Test
    void scaleAndFlipResizeAroundOrigin() {
        AABB scaled = Mask.transformAxisAligned(new AABB(10, 20, 30, 40), 2, 3, 5, 6);
        assertEquals(25, scaled.x(), 0.001);
        assertEquals(66, scaled.y(), 0.001);
        assertEquals(60, scaled.width(), 0.001);
        assertEquals(120, scaled.height(), 0.001);

        AABB flipped = Mask.transformAxisAligned(new AABB(10, 20, 30, 40), -1, 1, 100, 0);
        assertEquals(60, flipped.x(), 0.001); // 100 - 40
        assertEquals(30, flipped.width(), 0.001);
    }
}
