package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 裁剪剔除必须同时看"文档坐标系"和"祖先 transform 之后"两个矩形。
 *
 * <p>回归的是 rewind_screen 节点树里最深那张卡片整块不画的 bug：画布用 overflow:hidden +
 * 内层 .flow-world 的 translate/scale 把整棵树缩进可视区，卡片布局位置在 y≈730（换算到
 * 画布坐标系是 857，早已超出画布高度 634）。原来的剔除只比布局矩形，于是判定"在裁剪框外"
 * 而整张卡片不提交绘制——它其实被 transform 搬到了画布中间。</p>
 */
class TransformAwareCullTest {
    private static final int VIEWPORT_WIDTH = 800;
    private static final int VIEWPORT_HEIGHT = 600;

    @BeforeEach
    void useFixedViewport() {
        Size.setViewportOverride(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
    }

    @AfterEach
    void clearFixedViewport() {
        Size.clearViewportOverride();
    }

    private static final AABB CANVAS_CLIP = new AABB(0, 0, 200, 200);
    /** transform 后卡片落在 (10,0)-(60,20)，这就是此刻的 scissor。 */
    private static final AABB CANVAS_SCISSOR = new AABB(0, 0, 200, 200);

    @Test
    void contentTransformedIntoTheClipIsNotCulled() {
        Element card = buildCard(600);
        // 布局：y 600..640 在裁剪框 (0,0,200,200) 外；transform 后 y 0..20 在框内。
        assertTrue(Rect.of(card).getTransformedBounds() != null, "链上有 transform 时应能算出变换后包围盒");
        assertFalse(RenderNode.isFullyCulled(Rect.of(card), CANVAS_CLIP, CANVAS_SCISSOR),
                "变换后落进裁剪框的内容不能只按布局矩形剔除");
    }

    @Test
    void contentTransformedOutsideTheClipIsStillCulled() {
        Element card = buildCard(1200);
        // 布局 y 1200..1240、transform 后 y 300..320，两者都在裁剪框外。
        AABB scissor = new AABB(0, 0, 200, 200);
        assertTrue(RenderNode.isFullyCulled(Rect.of(card), CANVAS_CLIP, scissor),
                "两个坐标系都在框外时必须剔除");
    }

    /**
     * 最深的那个节点被剔除的真正触发点：它的逻辑裁剪框与祖先求交后高度归零（失效），
     * 而它变换后其实落在 scissor 里。失效的逻辑裁剪框不能当成"全剔"。
     */
    @Test
    void invalidDocSpaceClipDoesNotCullTransformedContent() {
        Element card = buildCard(600);
        AABB degenerateClip = new AABB(145, 860, 334, 0);
        assertFalse(degenerateClip.isValid(), "零高度裁剪框本身就是失效状态");
        assertFalse(RenderNode.isFullyCulled(Rect.of(card), degenerateClip, CANVAS_SCISSOR),
                "逻辑裁剪框失效时，带 transform 的内容必须按 scissor 判断");
    }

    /** 没有同坐标系的 scissor 可依据时，带 transform 的内容不剔除（保守，宁可多画）。 */
    @Test
    void transformedContentWithoutScissorIsNotCulled() {
        Element card = buildCard(600);
        assertFalse(RenderNode.isFullyCulled(Rect.of(card), CANVAS_CLIP, null));
    }

    @Test
    void contentWithoutTransformKeepsThePlainDocumentSpaceDecision() {
        Document document = TestDocumentFactory.createDocument();
        Element plain = new Element(document, "div");
        plain.setAttribute("style", "position:absolute;left:0;top:600px;width:100px;height:40px;");
        document.body.appendChild(plain);

        assertTrue(Rect.of(plain).getTransformedBounds() == null, "没有 transform 时不应额外算一遍矩阵");
        assertTrue(RenderNode.isFullyCulled(Rect.of(plain), CANVAS_CLIP));
    }

    /** 画布 200x200 + 内层世界 translate(0,-300) scale(0.5)，卡片布局 top=layoutTop。 */
    private static Element buildCard(double layoutTop) {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "margin:0;padding:0;");

        Element canvas = new Element(document, "div");
        canvas.setAttribute("style", "position:relative;width:200px;height:200px;overflow:hidden;");
        document.body.appendChild(canvas);

        Element world = new Element(document, "div");
        world.setAttribute("style", "position:absolute;left:0;top:0;transform-origin:0 0;"
                + "transform:translate(0px,-300px) scale(0.5);width:800px;height:400px;");
        canvas.appendChild(world);

        Element card = new Element(document, "div");
        card.setAttribute("style", "position:absolute;left:20px;top:" + layoutTop
                + "px;width:100px;height:40px;background:#336699;");
        world.appendChild(card);
        return card;
    }
}
