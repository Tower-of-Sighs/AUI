package com.sighs.apricityui.style;

import com.sighs.apricityui.layout.Grid;
import com.sighs.apricityui.layout.Layout;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * display / overflow / visibility 归一化枚举化 + Style memo 与旧字符串 API 的等价性回归。
 *
 * <p>这一阶段刻意保留了各调用点**不同**的归一化程度，本测试逐条锁住：</p>
 * <ul>
 *   <li>{@code Style.isFlexDisplay()/isGridDisplay()} 复刻 {@code Layout.isFlexDisplay(String)}，
 *       要 trim + lowercase；</li>
 *   <li>{@code Style.isDisplayNone()/isInFlow()} 复刻裸比较（不 trim、不 lowercase）；</li>
 *   <li>{@code Style.overflowX()/overflowY()} 复刻 {@code Interaction.resolveOverflowX/Y(Style)}，
 *       其中 {@code "unset"} 是大小写敏感精确比较；</li>
 *   <li>{@code Style.visibility()} 只归一化本 Style 自己的字段（祖先链逻辑仍在
 *       {@code Interaction.getVisibility(Element)}）。</li>
 * </ul>
 */
class StyleNormalizationEquivalenceTest {

    private static final String[] VALUES = {
            null, "", " ", "flex", " FLEX ", "inline-flex", "grid", "INLINE-GRID",
            "none", "NONE", "block", "unset", "absolute", "ABSOLUTE", "fixed", "static",
            "visible", "HIDDEN", "scroll", "clip", "bogus"
    };

    @Test
    void displayMemoMatchesLegacyStringApis() {
        for (String value : VALUES) {
            Style style = new Style();
            style.display = value;
            assertEquals(Layout.isFlexDisplay(value), style.isFlexDisplay(),
                    "isFlexDisplay mismatch for display=" + value);
            assertEquals(Layout.isGridDisplay(value), style.isGridDisplay(),
                    "isGridDisplay mismatch for display=" + value);
            // isInFlow 用的是裸比较，与上面两个归一化判定语义不同，必须单独对拍
            assertEquals(Layout.isInFlow(style), style.isInFlow(),
                    "isInFlow mismatch for display=" + value);
            assertEquals("none".equals(value), style.isDisplayNone(),
                    "isDisplayNone mismatch for display=" + value);
        }
    }

    @Test
    void inFlowMemoMatchesLegacyAcrossPositions() {
        String[] positions = {
                null, "", " ", "static", "absolute", "ABSOLUTE", "fixed", "FIXED", "relative", "bogus"
        };
        for (String display : new String[]{"block", "none", "NONE", "flex"}) {
            for (String position : positions) {
                Style style = new Style();
                style.display = display;
                style.position = position;
                assertEquals(Layout.isInFlow(style), style.isInFlow(),
                        "isInFlow mismatch display=" + display + " position=" + position);
            }
        }
    }

    /** 裸比较 vs 归一化：同一 Style 上两条判定刻意不同。 */
    @Test
    void bareDisplaySemanticsDifferFromNormalized() {
        Style style = new Style();

        style.display = " FLEX ";
        assertTrue(style.isFlexDisplay(), "归一化判定要认 ' FLEX '");
        assertFalse(style.isDisplayNone());
        assertTrue(style.isInFlow(), "裸比较：' FLEX ' 不是精确 none，仍在流");

        style.display = "NONE";
        style.resetMetrics(); // 直接赋值不失效，显式清 memo
        assertFalse(style.isFlexDisplay());
        assertFalse(style.isDisplayNone(), "裸比较不认大写 NONE");
        assertTrue(style.isInFlow(), "裸比较不认大写 NONE，position:static 时仍在流");

        style.display = "none";
        style.resetMetrics();
        assertTrue(style.isDisplayNone());
        assertFalse(style.isInFlow());
    }

    @Test
    void overflowMemoMatchesLegacyStringApis() {
        for (String value : VALUES) {
            Style style = new Style();
            style.overflow = value;
            assertEquals(Interaction.resolveOverflowX(style), style.overflowX().cssValue(),
                    "overflowX mismatch for overflow=" + value);
            assertEquals(Interaction.resolveOverflowY(style), style.overflowY().cssValue(),
                    "overflowY mismatch for overflow=" + value);
            assertEquals(Interaction.clipsOverflow(style), style.clipsOverflow(),
                    "clipsOverflow mismatch for overflow=" + value);
            assertEquals(Interaction.normalizeOverflow(value), Interaction.Overflow.parse(value).cssValue(),
                    "normalizeOverflow mismatch for overflow=" + value);
        }
    }

    /** {@code overflowX/overflowY} 的 {@code "unset"} 是精确比较（大小写敏感），空白/非法值要回退。 */
    @Test
    void overflowAxisUnsetSemanticsArePreserved() {
        String[][] cases = {
                // overflowX, overflowY, overflow
                {"unset", "unset", "hidden"},
                {"UNSET", "UNSET", "hidden"},   // "UNSET" != "unset" -> 用它自己 -> visible
                {" HIDDEN ", "unset", "visible"},
                {"", "unset", "scroll"},
                {" ", "clip", "visible"},
                {"bogus", "unset", "auto"},
                {"unset", "scroll", "hidden"},
        };
        for (String[] c : cases) {
            Style style = new Style();
            style.overflowX = c[0];
            style.overflowY = c[1];
            style.overflow = c[2];
            assertEquals(Interaction.resolveOverflowX(style), style.overflowX().cssValue(),
                    "overflowX mismatch case=" + String.join(",", c));
            assertEquals(Interaction.resolveOverflowY(style), style.overflowY().cssValue(),
                    "overflowY mismatch case=" + String.join(",", c));
            assertEquals(Interaction.clipsOverflow(style), style.clipsOverflow(),
                    "clipsOverflow mismatch case=" + String.join(",", c));
        }
    }

    @Test
    void visibilityMemoMatchesLegacyNormalization() {
        for (String value : VALUES) {
            Style style = new Style();
            style.visibility = value;
            assertEquals(Interaction.normalizeVisibility(value), style.visibility().cssValue(),
                    "visibility mismatch for visibility=" + value);
            assertEquals(Interaction.normalizeVisibility(value), Interaction.Visibility.parse(value).cssValue(),
                    "Visibility.parse mismatch for visibility=" + value);
        }
    }

    /** 锁住旧 switch 关键字表：只认列出的关键字，非法/空白/null 回退 visible。 */
    @Test
    void enumParseMatchesOriginalKeywordTables() {
        assertEquals("visible", Interaction.normalizeOverflow(null));
        assertEquals("visible", Interaction.normalizeOverflow("  "));
        assertEquals("hidden", Interaction.normalizeOverflow("HIDDEN"));
        assertEquals("scroll", Interaction.normalizeOverflow(" Scroll "));
        assertEquals("auto", Interaction.normalizeOverflow("AUTO"));
        assertEquals("clip", Interaction.normalizeOverflow("clip"));
        assertEquals("visible", Interaction.normalizeOverflow("bogus"));
        assertEquals("visible", Interaction.normalizeOverflow("unset"));

        assertEquals("visible", Interaction.normalizeVisibility(null));
        assertEquals("visible", Interaction.normalizeVisibility(""));
        assertEquals("hidden", Interaction.normalizeVisibility(" HIDDEN "));
        assertEquals("collapse", Interaction.normalizeVisibility("COLLAPSE"));
        assertEquals("visible", Interaction.normalizeVisibility("bogus"));
        assertEquals("visible", Interaction.normalizeVisibility("unset"));
    }

    /** memo 失效走真实写入路径 {@code setFieldValue}（内部 resetMetrics）。 */
    @Test
    void memoInvalidatedByFieldMutation() {
        Style style = new Style();
        style.display = "block";
        style.overflow = "visible";
        style.overflowX = "unset";
        style.overflowY = "unset";
        style.visibility = "visible";
        style.position = "static";

        assertFalse(style.isFlexDisplay());
        assertFalse(style.isGridDisplay());
        assertFalse(style.isDisplayNone());
        assertTrue(style.isInFlow());
        assertSame(Interaction.Overflow.VISIBLE, style.overflowX());
        assertFalse(style.clipsOverflow());
        assertSame(Interaction.Visibility.VISIBLE, style.visibility());

        style.setFieldValue("display", "flex");
        assertTrue(style.isFlexDisplay());
        style.setFieldValue("display", "grid");
        assertTrue(style.isGridDisplay());
        style.setFieldValue("display", "none");
        assertTrue(style.isDisplayNone());
        assertFalse(style.isInFlow());

        style.setFieldValue("display", "block");
        style.setFieldValue("position", "fixed");
        assertFalse(style.isInFlow());
        style.setFieldValue("position", "static");
        assertTrue(style.isInFlow());

        style.setFieldValue("overflowX", "hidden");
        assertSame(Interaction.Overflow.HIDDEN, style.overflowX());
        assertTrue(style.clipsOverflow());
        style.setFieldValue("overflowX", "unset");
        style.setFieldValue("overflow", "scroll");
        assertSame(Interaction.Overflow.SCROLL, style.overflowX());
        assertTrue(style.clipsOverflow());
        style.setFieldValue("overflow", "visible");
        assertFalse(style.clipsOverflow());

        style.setFieldValue("visibility", "HIDDEN");
        assertSame(Interaction.Visibility.HIDDEN, style.visibility());
    }

    /** 直接赋值不自动失效，必须显式 resetMetrics()——锁住 memo 契约。 */
    @Test
    void memoInvalidatedByResetMetricsAfterDirectAssignment() {
        Style style = new Style();
        style.display = "block";
        assertFalse(style.isFlexDisplay());

        style.display = "flex";
        assertFalse(style.isFlexDisplay(), "memo 未清时应读到旧值");

        style.resetMetrics();
        assertTrue(style.isFlexDisplay());
    }

    // ---------------------------------------------------------------
    // Grid.parseSpanSpec 等价性：golden 常量取自改动前的旧实现（反射探针实测输出）。
    // ---------------------------------------------------------------
    private static final Map<String, int[]> SPAN_GOLDEN = new LinkedHashMap<>();

    static {
        // input -> {start, span}
        SPAN_GOLDEN.put("auto", new int[]{-1, 1});
        SPAN_GOLDEN.put("span 2", new int[]{-1, 2});
        SPAN_GOLDEN.put("3", new int[]{2, 1});
        SPAN_GOLDEN.put("2", new int[]{1, 1});
        SPAN_GOLDEN.put("4", new int[]{3, 1});
        SPAN_GOLDEN.put("  SPAN 3 ", new int[]{-1, 3});
        SPAN_GOLDEN.put("unset", new int[]{-1, 1});
        SPAN_GOLDEN.put("", new int[]{-1, 1});
        SPAN_GOLDEN.put(null, new int[]{-1, 1});
        SPAN_GOLDEN.put("bogus", new int[]{-1, 1});
        SPAN_GOLDEN.put("2 / 4", new int[]{1, 2});
        SPAN_GOLDEN.put("span 0", new int[]{-1, 1});
        SPAN_GOLDEN.put("span", new int[]{-1, 1});
        SPAN_GOLDEN.put(" 2 ", new int[]{1, 1});
        SPAN_GOLDEN.put("SPAN", new int[]{-1, 1});
        SPAN_GOLDEN.put("span 12", new int[]{-1, 12});
        SPAN_GOLDEN.put("0", new int[]{0, 1});
        SPAN_GOLDEN.put("1", new int[]{0, 1});
        SPAN_GOLDEN.put("1/1", new int[]{0, 1});
    }

    @Test
    void parseSpanSpecMatchesLegacyGolden() throws Exception {
        Method parse = Grid.class.getDeclaredMethod("parseSpanSpec", String.class);
        parse.setAccessible(true);
        for (Map.Entry<String, int[]> entry : SPAN_GOLDEN.entrySet()) {
            Object spec = parse.invoke(null, entry.getKey());
            assertNotNull(spec, "null spec for input=" + entry.getKey());
            Method start = spec.getClass().getDeclaredMethod("start");
            Method span = spec.getClass().getDeclaredMethod("span");
            start.setAccessible(true);
            span.setAccessible(true);
            assertEquals(entry.getValue()[0], (int) start.invoke(spec),
                    "start mismatch for input=" + entry.getKey());
            assertEquals(entry.getValue()[1], (int) span.invoke(spec),
                    "span mismatch for input=" + entry.getKey());
        }
    }

    /** 缓存按输入（含 null / 空串）返回同一不可变实例。 */
    @Test
    void parseSpanSpecCacheReturnsSameInstance() throws Exception {
        Method parse = Grid.class.getDeclaredMethod("parseSpanSpec", String.class);
        parse.setAccessible(true);
        assertSame(parse.invoke(null, (Object) null), parse.invoke(null, (Object) null));
        assertSame(parse.invoke(null, "span 3"), parse.invoke(null, "span 3"));
        assertSame(parse.invoke(null, ""), parse.invoke(null, ""));
    }
}
