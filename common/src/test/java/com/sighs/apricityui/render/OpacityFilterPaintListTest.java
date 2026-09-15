package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue#94 回归：grid 容器里 opacity&lt;1 的卡片走离屏 FBO 合成路径。
 * 无论全量重建还是热重载触发的增量拼接，FilterPushNode/FilterPopNode
 * 都必须成对包裹各自子树——一旦错配，子树内容会画进永远不被合成回屏幕的
 * FBO，表现为卡片整张或局部缺失。
 */
class OpacityFilterPaintListTest {

    private static Element box(Document document, String style) {
        Element element = new Element(document, "div");
        if (style != null && !style.isBlank()) element.setAttribute("style", style);
        return element;
    }

    /** 复刻 issue#94 最小复现：grid + 三张 opacity 卡 + 一张普通卡，卡内有图标/文字子节点。 */
    private static Element buildGrid(Document document) {
        Element grid = box(document, "display: grid;");
        for (int i = 0; i < 3; i++) {
            grid.appendChild(buildCard(document, "opacity: 0.58;"));
        }
        grid.appendChild(buildCard(document, null));
        document.body.appendChild(grid);
        return grid;
    }

    private static Element buildCard(Document document, String style) {
        Element card = box(document, "display: flex; border: 1px solid #d6c8b1;"
                + (style == null ? "" : style));
        Element mark = box(document, "background-color: #3f6d60;");
        Element copy = box(document, "display: flex;");
        card.appendChild(mark);
        card.appendChild(copy);
        return card;
    }

    private static void rebuild(Document document) {
        document.markDirty(document.body, Drawer.REORDER);
        document.commitRenderState();
    }

    /** 遍历 paint list，断言 filter 栈深度永不为负且最终归零（push/pop 全局配平）。 */
    private static void assertFilterStackBalanced(List<RenderNode> nodes) {
        int depth = 0;
        for (RenderNode node : nodes) {
            if (node instanceof RenderNode.FilterPushNode) depth++;
            if (node instanceof RenderNode.FilterPopNode) depth--;
            assertTrue(depth >= 0, "filter pop without matching push (stack underflow)");
        }
        assertEquals(0, depth, "filter push/pop not balanced: residual depth=" + depth);
    }

    private static long countNodes(List<RenderNode> nodes, Element target, boolean push) {
        return nodes.stream()
                .filter(n -> {
                    if (push) return n instanceof RenderNode.FilterPushNode p && p.target() == target;
                    return n instanceof RenderNode.FilterPopNode p && p.target() == target;
                })
                .count();
    }

    private static void assertCardWrappedByFilter(List<RenderNode> nodes, Element card) {
        int push = -1;
        int pop = -1;
        for (int i = 0; i < nodes.size(); i++) {
            RenderNode node = nodes.get(i);
            if (node instanceof RenderNode.FilterPushNode p && p.target() == card) push = i;
            if (node instanceof RenderNode.FilterPopNode p && p.target() == card) pop = i;
        }
        assertEquals(1, countNodes(nodes, card, true), "opacity card must have exactly one filter push");
        assertEquals(1, countNodes(nodes, card, false), "opacity card must have exactly one filter pop");
        assertTrue(push >= 0 && pop > push,
                "filter push must precede pop: push=" + push + " pop=" + pop);
        // 卡片子树的所有节点必须落在 push..pop 区间内，否则部分内容逃出合成层。
        for (int i = 0; i < nodes.size(); i++) {
            Element target = RenderNode.getRenderNodeTarget(nodes.get(i));
            if (target != null && target != card && RenderNode.isSameOrDescendant(target, card)) {
                assertTrue(i > push && i < pop,
                        "card descendant node escaped the filter layer at index " + i);
            }
        }
    }

    @Test
    void opacityCardsGetBalancedFilterPairAndPlainCardGetsNone() {
        Document document = TestDocumentFactory.createDocument();
        Element grid = buildGrid(document);
        rebuild(document);

        List<RenderNode> paintList = document.getPaintList();
        assertFilterStackBalanced(paintList);

        List<Element> cards = grid.getChildren();
        for (int i = 0; i < 3; i++) {
            assertCardWrappedByFilter(paintList, cards.get(i));
        }

        Element plainCard = cards.get(3);
        assertEquals(0, countNodes(paintList, plainCard, true),
                "non-opacity card must not enter the offscreen compositing path");
        assertEquals(0, countNodes(paintList, plainCard, false),
                "non-opacity card must not enter the offscreen compositing path");

        assertEquals(0.58f, Filter.getFilterOf(cards.get(0)).opacity(), 0.0001f,
                "composited opacity must reflect the computed style");
    }

    @Test
    void incrementalReorderKeepsFilterPairBalanced() {
        Document document = TestDocumentFactory.createDocument();
        Element grid = buildGrid(document);
        rebuild(document);

        // 必须标记卡片的【子元素】：findNearestStackingContext 才会解析到卡片
        // （opacity 创建层叠上下文），从而真正走 updateGlobalPaintList 的整段拼接。
        // 标记卡片自身会一路上溯到 documentElement，退化成全量重建，测不到拼接。
        for (int i = 0; i < 3; i++) {
            Element card = grid.getChildren().get(i);
            for (Element child : card.getChildren()) {
                document.markDirty(child, Drawer.REORDER);
                document.commitRenderState();

                List<RenderNode> paintList = document.getPaintList();
                assertFilterStackBalanced(paintList);
                for (int c = 0; c < 3; c++) {
                    assertCardWrappedByFilter(paintList, grid.getChildren().get(c));
                }
            }
        }
    }

    /** 走真实模板 + {@code refresh()}（DebugReloadWatcher 的热重载路径），验证刷新后合成层仍配对。 */
    @Test
    void templateRefreshKeepsFilterPairsBalanced() {
        String path = "test://issue94-reload";
        HTML.putTemple(path, """
                <html>
                  <head><meta name="aui-viewport" content="mode=browser"></head>
                  <body>
                    <style>
                      .grid { display: grid; }
                      .card { display: flex; border: 1px solid #d6c8b1; }
                      .muted { opacity: 0.58; }
                    </style>
                    <div class="grid">
                      <div class="card muted"><div class="mark">a</div><div class="copy">b</div></div>
                      <div class="card muted"><div class="mark">c</div><div class="copy">d</div></div>
                      <div class="card muted"><div class="mark">e</div><div class="copy">f</div></div>
                      <div class="card"><div class="mark">g</div><div class="copy">h</div></div>
                    </div>
                  </body>
                </html>
                """);

        Document document = new Document(path, false);
        document.refresh();
        assertFilterStackBalanced(document.getPaintList());

        // 热重载：模板内容变化后重新刷新同一个 Document 实例。
        HTML.invalidatePreparedTemplates(List.of(path));
        HTML.putTemple(path, """
                <html>
                  <head><meta name="aui-viewport" content="mode=browser"></head>
                  <body>
                    <style>
                      .grid { display: grid; }
                      .card { display: flex; border: 1px solid #d6c8b1; }
                      .muted { opacity: 0.58; }
                    </style>
                    <div class="grid">
                      <div class="card muted"><div class="mark">a</div><div class="copy">b</div></div>
                      <div class="card muted"><div class="mark">c</div><div class="copy">d</div></div>
                      <div class="card muted"><div class="mark">e</div><div class="copy">f</div></div>
                      <div class="card"><div class="mark">g</div><div class="copy">h</div></div>
                    </div>
                  </body>
                </html>
                """);
        document.refresh();

        List<RenderNode> paintList = document.getPaintList();
        assertFilterStackBalanced(paintList);

        for (Element card : document.getElementsByClassName("muted")) {
            assertCardWrappedByFilter(paintList, card);
            assertEquals(0.58f, Filter.getFilterOf(card).opacity(), 0.0001f,
                    "post-reload composited opacity must match the recomputed style");
        }
        Element plain = document.querySelector(".card:not(.muted)");
        assertTrue(plain != null, "plain card must exist after reload");
        assertEquals(0, countNodes(paintList, plain, true),
                "non-opacity card must stay out of the compositing path after reload");
    }
}
