package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A grid inside a scroll container must be laid out against the width the container
 * actually has, scrollbar gutter included.
 *
 * <p>{@code overflow-y: auto} turns the vertical scrollbar into a layout input the moment
 * the content stops fitting, and {@code ::-webkit-scrollbar { width: 12px }} makes it a
 * wide one. {@link Box#innerSize()} is the contract every grid/flex track calculation
 * reads, so tracks sized against the width from before the scrollbar appeared leave the
 * items laid out past the container's right edge.</p>
 */
class ScrollbarGridTrackTest {
    private static final double EPSILON = 0.01;
    private static final int CARDS = 8;
    /** {@code width} in the case under test is the content box (no border, padding-right only). */
    private static final double AREA_BORDER_BOX_WIDTH = 326 + 4;
    private static final double PADDING_RIGHT = 4;
    /** {@code ::-webkit-scrollbar { width }}. */
    private static final double SCROLLBAR_WIDTH = 12;
    /** Gutter a declared 12px scrollbar reserves: 12px track + two 2px insets. */
    private static final double GUTTER = SCROLLBAR_WIDTH * 4 / 3;

    private static final String PAGE = """
            <html><head><style>
            html,body{margin:0;padding:0}
            .scroll-area{width:326px;max-height:300px;overflow-y:auto;padding-right:4px}
            .scroll-area::-webkit-scrollbar{width:12px}
            .slot-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(216px,1fr));gap:12px}
            .slot-card{height:200px}
            </style></head><body>
            <div class="scroll-area" id="area"><div class="slot-grid" id="grid">
            %s
            </div></div>
            </body></html>
            """.formatted(cards());

    @Test
    void gridUsesTheScrollportWidthInTheVeryFrameTheScrollbarAppears() {
        Document document = document("test://scrollbar-grid/first-frame");
        try {
            Element area = document.getElementById("area");
            Element grid = document.getElementById("grid");

            // 一帧：与运行时 20Hz tick 相同的一轮（样式提交 → 元素 tick → 几何提交）。
            document.tickFrame();

            assertEquals(GUTTER, area.getVerticalScrollbarGutter(), EPSILON,
                    "内容比 max-height 高：这一帧竖直滚动条就必须已经预留 gutter");

            double scrollportWidth = Box.of(area).innerSize().width();
            assertEquals(AREA_BORDER_BOX_WIDTH - PADDING_RIGHT - GUTTER, scrollportWidth, EPSILON,
                    "scrollport 宽度 = 内容盒 - gutter");
            assertEquals(scrollportWidth, grid.getBoundingClientRect().width, EPSILON,
                    "网格容器的已用宽度必须等于扣掉 gutter 后的 scrollport 宽度");

            assertCardsAgreeWithScrollport(document, area, grid);
        } finally {
            document.remove();
        }
    }

    /** 内容变矮、滚动条收回后，网格同样不能残留旧轨道宽度。 */
    @Test
    void gridReturnsToTheRawWidthWhenTheScrollbarDisappears() {
        Document document = document("test://scrollbar-grid/scrollbar-gone");
        try {
            Element area = document.getElementById("area");
            Element grid = document.getElementById("grid");
            document.tickFrame();
            assertEquals(GUTTER, area.getVerticalScrollbarGutter(), EPSILON, "前置条件：滚动条已出现");

            // 只留一张 200px 高的卡：内容不再溢出，滚动条应当收回。
            for (Element card : List.copyOf(document.querySelectorAll(".slot-card")).subList(1, CARDS)) {
                card.remove();
            }
            document.tickFrame();

            assertEquals(0.0d, area.getVerticalScrollbarGutter(), EPSILON,
                    "内容不再溢出时 gutter 必须归零");
            double scrollportWidth = Box.of(area).innerSize().width();
            assertEquals(AREA_BORDER_BOX_WIDTH - PADDING_RIGHT, scrollportWidth, EPSILON);
            assertEquals(scrollportWidth, grid.getBoundingClientRect().width, EPSILON,
                    "滚动条消失后网格必须回到不扣 gutter 的宽度");
            assertCardsAgreeWithScrollport(document, area, grid);
        } finally {
            document.remove();
        }
    }

    /**
     * 所有卡片宽度一致、且都落在容器的 padding box 之内。
     * 混用两套轨道宽度时，先布局的卡片会按旧宽度排到容器右边界之外。
     */
    private static void assertCardsAgreeWithScrollport(Document document, Element area, Element grid) {
        Element.DOMRect areaRect = area.getBoundingClientRect();
        double paddingBoxRight = areaRect.right - PADDING_RIGHT;
        List<Element> cards = document.querySelectorAll(".slot-card");
        assertTrue(!cards.isEmpty(), "网格里至少要有一张卡");

        double width = cards.get(0).getBoundingClientRect().width;
        assertTrue(width > 0, "卡片宽度必须已经布局出来");
        StringBuilder seen = new StringBuilder();
        for (Element card : cards) {
            Element.DOMRect rect = card.getBoundingClientRect();
            seen.append(id(card)).append('=').append(String.format("%.2f", rect.width)).append(' ');
            assertEquals(width, rect.width, EPSILON,
                    "同一次提交里所有网格项必须用同一套轨道宽度，实测：" + seen);
            assertTrue(rect.right <= paddingBoxRight + EPSILON,
                    "网格项不得越过滚动容器的 padding box 右边界（" + id(card)
                            + " right=" + String.format("%.2f", rect.right)
                            + " > " + String.format("%.2f", paddingBoxRight) + "）");
            assertTrue(rect.left >= areaRect.left - EPSILON,
                    "网格项不得越过滚动容器左边界（" + id(card) + "）");
        }
        assertTrue(grid.getBoundingClientRect().right <= paddingBoxRight + EPSILON,
                "网格容器自身也不得越过滚动容器的 padding box");
    }

    private static String id(Element card) {
        String id = card.getAttribute("id");
        return id == null || id.isBlank() ? card.getAttribute("class") : id;
    }

    private static String cards() {
        StringBuilder cards = new StringBuilder();
        for (int i = 0; i < CARDS; i++) {
            cards.append("<div class='slot-card' id='c").append(i).append("'></div>\n");
        }
        return cards.toString();
    }

    private static Document document(String path) {
        HTML.putTemple(path, PAGE);
        Document document = new Document(path, false);
        document.refresh();
        return document;
    }
}
