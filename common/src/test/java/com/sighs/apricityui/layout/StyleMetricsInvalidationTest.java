package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Style 上的类型化几何 memo 失效回归。
 *
 * <p>memo 的正确性完全取决于"字段一变就清"：动画/过渡复用同一个 Style 缓冲逐帧
 * {@code copyFrom(base)} 后原地改写字段，viewport 变化不清 computedStyle。这里逐条锁住。</p>
 */
class StyleMetricsInvalidationTest {
    @Test
    void styleMutationRecomputesGeometry() {
        Size.setViewportOverride(1000, 800);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "margin:0;padding:0;");

            Element element = document.createElement("div");
            element.setAttribute("style", "width:100px;height:20px;");
            document.body.appendChild(element);
            assertEquals(100.0, Size.of(element).width(), 1e-6);

            element.setAttribute("style", "width:250px;height:20px;");
            document.flushPendingStyleUpdates();
            assertEquals(250.0, Size.of(element).width(), 1e-6);
        } finally {
            Size.clearViewportOverride();
        }
    }

    /**
     * vw 必须实时求值：viewport 变化不会清 computedStyle，若把 vw 烘成 px 存进 memo，
     * 同一 Style 会一直吐旧值。
     */
    @Test
    void viewportUnitsStayLiveAcrossViewportChanges() {
        Size.setViewportOverride(1000, 800);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "margin:0;padding:0;");

            Element element = document.createElement("div");
            element.setAttribute("style", "width:10vw;height:10px;");
            document.body.appendChild(element);

            Style computed = element.getComputedStyle();
            assertEquals(100.0, Size.of(element).width(), 1e-6);

            // 只改视口，不动样式：computedStyle 是同一个对象，memo 未被清。
            Size.setViewportOverride(500, 800);
            assertSame(computed, element.getComputedStyle());
            element.getRenderer().size.clear();

            assertEquals(50.0, Size.of(element).width(), 1e-6);
        } finally {
            Size.clearViewportOverride();
        }
    }

    /** clone() 是独立快照：改克隆的字段必须让克隆重新编译，且不影响源对象。 */
    @Test
    void cloneResetsMetricMemo() {
        Style original = new Style();
        original.width = "100px";
        original.height = "50%";
        original.boxSizing = "border-box";
        assertEquals(100.0, original.widthLength().resolve(0), 1e-9);
        assertTrue(original.isBorderBox());

        Style copy = original.clone();
        copy.width = "321px";
        copy.height = "20%";
        copy.boxSizing = "content-box";

        assertEquals(321.0, copy.widthLength().resolve(0), 1e-9);
        assertEquals(40.0, copy.heightLength().resolve(200), 1e-9);
        assertFalse(copy.isBorderBox());

        // 源对象不受克隆改写影响
        assertEquals(100.0, original.widthLength().resolve(0), 1e-9);
        assertEquals(100.0, original.heightLength().resolve(200), 1e-9);
        assertTrue(original.isBorderBox());
    }

    /**
     * 动画/过渡的原地改写路径：同一个 Style 缓冲 {@code copyFrom(base)} 后再写字段，
     * copyFrom 必须清 memo，否则动画元素会读到上一帧几何。
     */
    @Test
    void copyFromResetsMetricMemoForAnimationBuffers() {
        Style buffer = new Style();
        Style base = new Style();
        base.width = "100px";
        base.boxSizing = "border-box";
        buffer.copyFrom(base);
        assertEquals(100.0, buffer.widthLength().resolve(0), 1e-9);
        assertTrue(buffer.isBorderBox());

        // 下一帧：同一缓冲 copyFrom 新 base
        base.width = "250px";
        base.boxSizing = "content-box";
        buffer.copyFrom(base);
        assertEquals(250.0, buffer.widthLength().resolve(0), 1e-9);
        assertFalse(buffer.isBorderBox());

        // 过渡原地改写走 Style.update()
        buffer.update("width", "77px");
        assertEquals(77.0, buffer.widthLength().resolve(0), 1e-9);
    }

    /** 布局几何必须跟随 StyleFrameCache 里的克隆帧样式（motion 路径）。 */
    @Test
    void layoutGeometryFollowsClonedFrameStyle() {
        Size.setViewportOverride(1000, 800);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "margin:0;padding:0;");

            Element element = document.createElement("div");
            element.setAttribute("style", "width:100px;height:20px;");
            document.body.appendChild(element);
            assertEquals(100.0, Size.of(element).width(), 1e-6);

            Style animated = element.getComputedStyle().clone();
            animated.width = "321px";

            StyleFrameCache.begin();
            try {
                StyleFrameCache.put(element, animated);
                element.getRenderer().size.clear();
                assertEquals(321.0, Size.of(element).width(), 1e-6);
            } finally {
                StyleFrameCache.end();
            }
        } finally {
            Size.clearViewportOverride();
        }
    }
}
