package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只改 transform 时不能把带生成伪元素的宿主整片标成 RELAYOUT。
 *
 * <p>回归 rewind_screen 节点树拖动掉帧：流程图画布每帧写一次 {@code .flow-world} 的
 * transform，样式重算会走到子树里每个元素的 {@code syncGeneratedPseudoElementsForStyleRecalc}。
 * 那时 {@code syncPseudoElement} 只要重推了一遍伪元素样式就无条件
 * {@code invalidatePseudoElementHostLayout()}，把宿主标成 RELAYOUT|REPAINT|REORDER；树节点用的
 * 是 ::before/::after 画的连线，于是每帧几百个节点带 RELAYOUT → {@code RenderQueue} 判成
 * 需要全量布局 → 每帧 {@code LayoutCommit.commit(document)}（实测 HUD 里 ly 恒为 1、帧尖峰
 * 74~112ms）。</p>
 */
class PseudoElementRelayoutTest {
    @Test
    void transformOnlyChangeDoesNotRelayoutGeneratedPseudoElementHosts() {
        Document document = TestDocumentFactory.createDocument();
        document.registerStylesheet(".dot::before{content:'';display:block;width:4px;height:4px;}", "<test>", 0);
        document.body.setAttribute("style", "margin:0;padding:0;");

        Element world = new Element(document, "div");
        world.setAttribute("style", "position:relative;width:200px;height:200px;"
                + "transform-origin:0 0;transform:translate(0px,0px);");
        document.body.appendChild(world);

        Element host = new Element(document, "div");
        host.setAttribute("class", "dot");
        host.setAttribute("style", "width:40px;height:40px;");
        world.appendChild(host);

        document.tickFrame();
        document.commitRenderState();
        host.clearDirtyFlags();
        world.clearDirtyFlags();

        // 只动祖先的 transform：不该让带 ::before 的宿主重排
        world.setInlineStyleProperty("transform", "translate(30px,20px)");
        document.flushPendingStyleUpdates();

        assertFalse(host.hasDirtyFlag(Drawer.RELAYOUT),
                "transform 变化不该把带生成伪元素的宿主标成 RELAYOUT");
        assertTrue(world.hasDirtyFlag(Drawer.COMMIT_LAYOUT),
                "transform 变化本身仍要走仅变换提交");
        assertFalse(world.hasDirtyFlag(Drawer.RELAYOUT),
                "transform 变化不该触发全量布局");
    }

    /** 伪元素内容/几何真的变了时，宿主的布局仍必须失效。 */
    @Test
    void pseudoElementGeometryChangeStillInvalidatesHostLayout() {
        Document document = TestDocumentFactory.createDocument();
        document.registerStylesheet(".dot::before{content:'';display:block;width:4px;height:4px;}", "<test>", 0);
        document.body.setAttribute("style", "margin:0;padding:0;");

        Element host = new Element(document, "div");
        host.setAttribute("class", "dot");
        host.setAttribute("style", "width:40px;height:40px;");
        document.body.appendChild(host);

        document.tickFrame();
        document.commitRenderState();
        host.clearDirtyFlags();

        // 改伪元素自己的宽度：宿主的布局必须跟着失效
        document.registerStylesheet(".dot::before{content:'';display:block;width:20px;height:4px;}", "<test-2>", 1);
        host.invalidateStyle();
        document.flushPendingStyleUpdates();

        assertTrue(host.hasDirtyFlag(Drawer.RELAYOUT),
                "伪元素几何变化必须让宿主重新布局");
    }
}
