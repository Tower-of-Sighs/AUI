package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 与真实 Chromium 对差后修好的布局行为，逐条钉住。
 *
 * <p>标记来自 {@link LayoutParityProbeTest}（同一份源），期望值是同标记、同视口下
 * Chromium 的实测值。改这些用例前先按 {@code tools/page-diff/README.md} 的流程重跑一遍对差，
 * 不要只改数字。</p>
 */
class BrowserParityLayoutTest {
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

    /** 换行容器的行是独立的格式化上下文：剩余空间只在自己那一行里分配。 */
    @Test
    void wrappedRowDistributesMainSizePerLine() {
        Document document = layout("flex-row-wrap-order");

        // 卡片内容宽 310：第一行 head(基础 28) + 8 间隔 + clock(70)，剩余 204 全给 head。
        assertEquals(232.0, width(document, "#head"), 0.01, "第一行的剩余空间必须给本行的 flex:1 1 auto 项");
        assertEquals(70.0, width(document, "#clock"), 0.01);
        assertEquals(18.0, height(document, "#head"), 0.01, "交叉轴拉伸取本行行高，不是容器内容高");
        assertEquals(310.0, width(document, "#meta"), 0.01, "flex:1 0 100% 独占一行，宽度是容器内容宽");
        assertEquals(36.0, y(document, "#meta"), 0.01);
        assertEquals(63.0, height(document, "#card"), 0.01, "10 + 18 + 5 + 14 + 10 + 6 边框");
    }

    /** flex-basis 的百分比基准是 flex 容器的内容盒，不是容器的包含块（外层 700）。 */
    @Test
    void flexBasisPercentResolvesAgainstFlexContainer() {
        Document document = layout("flex-basis-percent");

        assertEquals(300.0, width(document, "#b"), 0.01, "100% 按容器内容宽 300，不是外层 700");
        assertEquals(0.0, x(document, "#b"), 0.01, "40 + 300 超过行宽，独占一行");
        assertEquals(x(document, "#b"), x(document, "#c"), 0.01, "两项各占一行，行首 x 相同");
        assertEquals(16.0, y(document, "#b"), 0.01);
        assertEquals(32.0, y(document, "#c"), 0.01);
        assertEquals(48.0, height(document, "body > div.outer > div.box"), 0.01);
    }

    /** 换行判定按外层 hypothetical main size（flex base 经 min/max 钳制），不是天然尺寸。 */
    @Test
    void autoWidthRowContainerAssignsChildMainSize() {
        Document document = layout("nest-flex-auto-width-row");

        assertEquals(232.0, width(document, "#row"), 0.01);
        assertEquals(204.0, width(document, "#c"), 0.01, "容器宽度定型后子项必须拿到分配值");
        assertEquals(0.0, x(document, "#c") - 28, 0.01);
    }

    /** CSSOM View §7.1/§7.2 的盒度量：client 是 padding box，offset 是 border box。 */
    @Test
    void cssomBoxMetricsMatchBrowser() {
        Document document = layout("box-metrics");
        Element pad = document.querySelector("#pad");

        assertEquals(200.0, pad.getOffsetWidth(), 0.01);
        assertEquals(100.0, pad.getOffsetHeight(), 0.01);
        assertEquals(194.0, pad.getClientWidth(), 0.01, "200 - 2*3 边框");
        assertEquals(94.0, pad.getClientHeight(), 0.01);
    }

    /** CSSOM View §7.1.1：getBoundingClientRect 返回变换后的视觉盒。 */
    @Test
    void boundingClientRectIncludesAncestorTransform() {
        Document document = layout("transform-scale-centering");
        Element world = document.querySelector("#world");
        Element canvas = document.querySelector("#canvas");

        // 画布内容盒原点 + translate(20,20)；800x400 被 scale(0.5) 缩到 400x200。
        assertEquals(canvas.getBoundingClientRect().x + 3 + 20, world.getBoundingClientRect().x, 0.01);
        assertEquals(canvas.getBoundingClientRect().y + 3 + 20, world.getBoundingClientRect().y, 0.01);
        assertEquals(400.0, world.getBoundingClientRect().width, 0.01);
        assertEquals(200.0, world.getBoundingClientRect().height, 0.01);
        assertEquals(800.0, world.getOffsetWidth(), 0.01, "offsetWidth 是布局尺寸，不受 transform 影响");
    }

    /** 两行容器里项拉伸到自己那一行的交叉轴尺寸，不是容器内容高。 */
    @Test
    void wrappedItemStretchesToItsOwnLine() {
        Document document = layout("pseudo-tree-lines");

        // 根节点：卡片行 24 高，子分支另起一行。
        Element root = document.querySelector(".tree-chart > .tree-branch > .tree-node");
        Element leaf = root.querySelector(".leaf");
        assertEquals(24.0, height(document, ".tree-chart > .tree-branch > .tree-node > .leaf"), 0.01);
        assertEquals(24.0, leaf.getOffsetHeight(), 0.01, "根节点行的项不该被撑到整树高");
    }

    private static Document layout(String caseName) {
        String source = LayoutParityProbeTest.source(caseName);
        String path = "test://browser-parity/" + caseName;
        HTML.putTemple(path, source);
        Document document = new Document(path, false);
        document.refresh();
        return document;
    }

    private static double width(Document document, String selector) {
        return document.querySelector(selector).getBoundingClientRect().width;
    }

    private static double height(Document document, String selector) {
        return document.querySelector(selector).getBoundingClientRect().height;
    }

    private static double x(Document document, String selector) {
        return document.querySelector(selector).getBoundingClientRect().x;
    }

    private static double y(Document document, String selector) {
        return document.querySelector(selector).getBoundingClientRect().y;
    }
}
