package com.sighs.apricityui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CssLength 与旧 Size API 的等价性回归。
 *
 * <p>类型化的唯一目的就是"布局阶段不再解析字符串"，前提是解析/求值语义逐字节等价。
 * 这里对同一批字符串 × 多个 basis（含 0/负值）断言
 * {@code Size.tryResolveLength(s, b[, em])} 与 {@code CssLength.parse(s).resolve(b[, em])}
 * 的结果 {@link Double#compare} 完全一致（null 也要一致）。</p>
 */
class CssLengthEquivalenceTest {
    private static final String[] VALUES = {
            // 不可解析 / 关键字：必须与 0 区分
            null, "", "   ", "auto", "auto ", "unset", "UNSET", "min-content", "fit-content",
            "garbage", "none", "50%foo",
            // 单值
            "0", "120px", "-5px", "+3px", ".5em", "1.5em", "2rem", "10vw", "10vh",
            "100%", "50 %", "10foo", "1e3", "10vw ", "120PX", "1.5EM",
            // calc
            "calc(100% - 20px)", "calc(10px + 5px)", "calc(10px +)", "calc()", "calc(  )",
            "calc(100% - 20px + 5em)", "calc(-10px + 2vw)", "calc(1px + foo)",
            // min/max/clamp
            "min(10px,20%)", "max(10px,20%)", "clamp(1px,2%,3px)", "clamp(1px,2px)",
            "min()", "min(10px,foo)", "max(1px,2px,3px)", "MIN(10px, 20px)",
            "min(calc(10px + 5px), 30px)", "max(1px, min(2px, 3px))",
    };

    private static final double[] BASES = {0, 100, 800, -1, 1920.5};

    private static final double[] EM_BASES = {0, 10, 16, 32, -4};

    @Test
    void resolvesIdenticallyToLegacySizeApi() {
        for (String value : VALUES) {
            for (double basis : BASES) {
                Double expected = Size.tryResolveLength(value, basis);
                Double actual = CssLength.parse(value).resolve(basis);
                assertEquivalent(expected, actual, value, basis, null);

                for (double em : EM_BASES) {
                    Double expectedEm = Size.tryResolveLength(value, basis, em);
                    Double actualEm = CssLength.parse(value).resolve(basis, em);
                    assertEquivalent(expectedEm, actualEm, value, basis, em);
                }
            }
        }
    }

    @Test
    void resolveOrMatchesLegacyResolveLength() {
        for (String value : VALUES) {
            for (double basis : BASES) {
                double fallback = -12345.5;
                double expected = Size.resolveLength(value, basis, fallback);
                double actual = CssLength.parse(value).resolveOr(fallback, basis);
                assertEquals(0, Double.compare(expected, actual),
                        "resolveOr mismatch for value=" + value + " basis=" + basis);
            }
        }
    }

    /** 调用点用 hasNumber()/isPercent() 取代 Size.parseNumber/Size.isPercent，必须同口径。 */
    @Test
    void numberAndPercentFlagsMatchLegacyHelpers() {
        for (String value : VALUES) {
            CssLength parsed = CssLength.parse(value);
            assertEquals(Size.parseNumber(value) != null, parsed.hasNumber(),
                    "hasNumber mismatch for value=" + value);
            assertEquals(Size.parseNumber(value), parsed.numberValue(),
                    "numberValue mismatch for value=" + value);
            assertEquals(Size.isPercent(value), parsed.isPercent(),
                    "isPercent mismatch for value=" + value);
        }
    }

    /** parseNumber 是前导数字扫描，不是整串 parseDouble；锁住几个关键语义。 */
    @Test
    void leadingNumberScanSemantics() {
        assertEquals(10.0, Size.parseNumber("10foo"));
        assertEquals(1.0, Size.parseNumber("1e3"));
        assertEquals(50.0, Size.parseNumber("50%"));
        assertEquals(3.0, Size.parseNumber("+3px"));
        assertEquals(0.5, Size.parseNumber(".5em"));
        assertEquals(2.0, Size.parseNumber("  2rem"));
        assertNull(Size.parseNumber("foo"));
        assertNull(Size.parseNumber(""));
        assertNull(Size.parseNumber(null));
    }

    /** auto/空 不可解析必须与 0 区分开。 */
    @Test
    void unresolvableIsNotNullNotZero() {
        for (String value : new String[]{null, "", "auto", "unset", "garbage"}) {
            assertNull(Size.tryResolveLength(value, 100), "expected null for " + value);
            assertNull(CssLength.parse(value).resolve(100), "expected null for " + value);
        }
        assertEquals(0.0, Size.tryResolveLength("0", 100));
    }

    /** 显式 em 基准必须逐调用点传入：同一串在不同 emBasis 下结果不同。 */
    @Test
    void explicitEmBasisIsHonoured() {
        Double small = CssLength.parse("1.5em").resolve(0, 10);
        Double large = CssLength.parse("1.5em").resolve(0, 32);
        assertNotNull(small);
        assertNotNull(large);
        assertEquals(15.0, small, 1e-9);
        assertEquals(48.0, large, 1e-9);
        assertFalse(Double.compare(small, large) == 0);
    }

    /** rem 实时读根字号；vw/vh 实时读视口——memo 只存 token，不烘成 px。 */
    @Test
    void viewportUnitsAreResolvedLive() {
        Size.setViewportOverride(1000, 800);
        try {
            assertEquals(100.0, CssLength.parse("10vw").resolve(0), 1e-9);
            assertEquals(80.0, CssLength.parse("10vh").resolve(0), 1e-9);
            assertEquals(100.0, Size.tryResolveLength("10vw", 0), 1e-9);
        } finally {
            Size.clearViewportOverride();
        }
        Size.setViewportOverride(500, 400);
        try {
            assertEquals(50.0, CssLength.parse("10vw").resolve(0), 1e-9);
            assertEquals(40.0, CssLength.parse("10vh").resolve(0), 1e-9);
        } finally {
            Size.clearViewportOverride();
        }
    }

    /** aspect-ratio 解析也搬到 CssLength；委托后语义不变。 */
    @Test
    void aspectRatioDelegationMatchesLegacy() {
        assertNull(Size.parseAspectRatio(null));
        assertNull(Size.parseAspectRatio("auto"));
        assertNull(Size.parseAspectRatio("none"));
        assertNull(Size.parseAspectRatio("0"));
        assertNull(Size.parseAspectRatio("16/0"));
        assertEquals(16.0 / 9.0, Size.parseAspectRatio("16 / 9"), 1e-9);
        assertEquals(1.5, Size.parseAspectRatio("1.5"), 1e-9);
        assertNull(Size.parseAspectRatio("foo"));
        assertTrue(Size.parseAspectRatio("16/9") > 0);
    }

    private static void assertEquivalent(Double expected, Double actual, String value, double basis, Double emBasis) {
        String message = "value=" + value + " basis=" + basis + " em=" + emBasis;
        if (expected == null) {
            assertNull(actual, "expected null: " + message);
            return;
        }
        assertNotNull(actual, "expected non-null: " + message);
        assertEquals(0, Double.compare(expected, actual), message);
    }
}
