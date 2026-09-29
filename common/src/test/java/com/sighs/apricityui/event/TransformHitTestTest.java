package com.sighs.apricityui.event;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 命中测试必须走祖先 transform 的逆变换。
 *
 * <p>回归 rewind_screen 节点树的鼠标落点：画布用 {@code overflow:hidden} + 内层
 * {@code .flow-world} 的 translate/scale 把整棵树缩进可视区，命中盒却是按变换前的布局坐标
 * 算的。光标不做逆变换就直接比，鼠标停在卡片上会命中别的元素、或者谁也命不中——浏览器里
 * 同一份标记是正常的。</p>
 */
class TransformHitTestTest {
    private static final String PATH = "test://transform-hit-test";

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
    void hitTestFollowsAncestorTransform() {
        Document document = buildDocument();
        Element card = document.querySelector("#card");

        // 卡片布局在 (100,600)-(300,680)；scale(0.5) + translate(0,-200) 之后落在
        // (50,100)-(150,140)。鼠标停在这块上必须命中卡片。
        assertSame(card, document.hitTest(new Position(100, 120)),
                "变换后的视觉位置必须命中卡片");
        // 变换前的布局位置在画布外，不该命中卡片。
        assertNull(document.hitTest(new Position(100, 560)),
                "布局坐标那块不该命中卡片");
    }

    @Test
    void cursorPredicateFollowsAncestorTransform() {
        Document document = buildDocument();
        Element card = document.querySelector("#card");

        assertTrue(MouseEvent.checkCursor(card, new Position(100, 120)),
                "逆变换之后光标落在卡片局部盒子里");
        assertFalse(MouseEvent.checkCursor(card, new Position(100, 620)),
                "布局坐标那块不属于卡片");
    }

    /** 没有 transform 的元素行为不变（逆变换是空操作）。 */
    @Test
    void elementWithoutTransformKeepsPlainHitTesting() {
        Document document = buildDocument();
        Element plain = document.querySelector("#plain");

        assertTrue(MouseEvent.checkCursor(plain, new Position(20, 20)));
        assertFalse(MouseEvent.checkCursor(plain, new Position(300, 360)));
    }

    private static Document buildDocument() {
        HTML.putTemple(PATH, """
                <html><head><meta name="aui-viewport" content="mode=fixed,width=400,height=400"></head>
                <body style="margin:0">
                  <div id="plain" style="position:absolute;left:0;top:0;width:60px;height:60px"></div>
                  <div id="canvas" style="position:relative;width:400px;height:400px;overflow:hidden">
                    <div id="world" style="position:absolute;left:0;top:0;transform-origin:0 0;transform:translate(0px,-200px) scale(0.5);width:800px;height:800px">
                      <div id="card" style="position:absolute;left:100px;top:600px;width:200px;height:80px"></div>
                    </div>
                  </div>
                </body></html>
                """);
        Document document = Document.create(PATH);
        document.commitRenderState();
        return document;
    }
}
