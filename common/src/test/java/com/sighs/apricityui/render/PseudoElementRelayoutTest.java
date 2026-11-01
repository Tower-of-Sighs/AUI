package com.sighs.apricityui.render;

import com.sighs.apricityui.layout.LayoutCommit;
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

    @Test
    void absolutePseudoElementSizeTransitionUsesSubtreeLayoutCommit() {
        Document document = TestDocumentFactory.createDocument();
        document.registerStylesheet(".card::after{content:'';position:absolute;right:0;top:0;"
                + "width:0;height:12px;transition:width 200ms linear -100ms;}"
                + ".card:hover::after{width:12px;clip-path:polygon(100% 0,0 0,100% 100%);}", "<test>", 0);
        document.body.setAttribute("style", "margin:0;padding:0;width:300px;height:200px;");

        Element host = new Element(document, "div");
        host.setAttribute("class", "card");
        host.setAttribute("style", "position:relative;width:80px;height:80px;");
        document.body.appendChild(host);
        document.tickFrame();
        document.commitRenderState();

        Element pseudo = host.getRenderChildren().stream()
                .filter(Element::isPseudoElement)
                .findFirst()
                .orElseThrow();
        host.clearDirtyFlags();
        pseudo.clearDirtyFlags();

        host.setHover(true);
        document.flushPendingStyleUpdates();

        assertFalse(host.hasDirtyFlag(Drawer.RELAYOUT),
                "an out-of-flow pseudo box transition must not relayout its host");
        assertFalse(pseudo.hasDirtyFlag(Drawer.RELAYOUT),
                "the active pseudo transition is committed through its motion subtree");

        com.sighs.apricityui.style.StyleFrameCache.begin();
        try {
            assertTrue(document.stepMotionRender());
        } finally {
            com.sighs.apricityui.style.StyleFrameCache.end();
        }
        assertTrue(document.drainMotionSubtreeLayoutRoots().contains(pseudo),
                "absolute pseudo geometry must still receive a targeted layout commit");
        assertFalse(document.drainMotionLayoutRoots().contains(pseudo),
                "absolute pseudo geometry must not request a full-flow layout commit");
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
