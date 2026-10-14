package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 命中缓存增量重建（{@link HitTestCache#updateSubtrees}）后，子元素必须仍然排在祖先之前。
 *
 * <p>回归：模态遮罩是一张 {@code position:fixed;inset:0} 的 top-layer 全屏父元素，
 * 覆盖它自己的所有子元素。某次只标了 HITTEST 的增量重建会把子树条目插到祖先条目**之后**，
 * 于是全屏遮罩成为全页面命中的第一个候选——页面照常渲染、动画照常播放，但点击/滚轮/hover
 * 全部被遮罩吃掉（资源管理器开预览窗口时的"无法交互"）。</p>
 */
class HitTestCacheOrderTest {
    private static final String PATH = "test://hit-test-cache-order";
    private static final Position CARD_CENTER = new Position(200, 200);

    @BeforeEach
    void useFixedViewport() {
        Size.setViewportOverride(400, 400);
    }

    @AfterEach
    void clearFixedViewport() {
        Size.clearViewportOverride();
        Document.remove(PATH);
    }

    @Test
    void incrementalUpdateKeepsDescendantInFrontOfTopLayerOverlay() {
        Document document = buildDocument();
        Element card = document.querySelector("#card");

        assertSame(card, document.hitTest(CARD_CENTER), "全量提交后应命中卡片");

        // 只标 HITTEST：走增量命中重建，不触发整表重建。
        document.markDirty(card, Drawer.HITTEST);
        document.commitRenderStateForMotion();

        assertSame(card, document.hitTest(CARD_CENTER),
                "增量重建后仍须命中卡片，而不是盖在它上面的全屏 top-layer 遮罩");
    }

    private static Document buildDocument() {
        HTML.putTemple(PATH, """
                <html><head><meta name="aui-viewport" content="mode=fixed,width=400,height=400"></head>
                <body style="margin:0">
                  <div id="aside" style="position:absolute;left:0;top:0;width:40px;height:40px"></div>
                  <div id="overlay" style="position:fixed;inset:0;background:rgba(26,26,26,0.45)">
                    <div id="card" style="position:absolute;left:100px;top:100px;width:200px;height:200px"></div>
                  </div>
                </body></html>
                """);
        Document document = Document.create(PATH);
        Element overlay = document.querySelector("#overlay");
        overlay.setTopLayer(true);
        document.commitRenderState();
        return document;
    }
}
