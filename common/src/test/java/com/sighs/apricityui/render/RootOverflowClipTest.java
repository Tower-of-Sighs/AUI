package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.style.Interaction;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The root/body overflow clip contract (CSS 2.1 §11.1.1).
 *
 * <p>{@code html} owns the viewport's {@code overflow}, and the body's is propagated to
 * the viewport while {@code html} stays {@code visible} — the same rule
 * {@code ScrollModel} uses to pick the viewport scroller, whose scrollport is the CSS
 * viewport minus the scrollbar gutters. The root boxes are content-sized, so clipping
 * the viewport's overflow to them collapses the clip to nothing whenever the page has
 * no in-flow content, which used to paint the whole page away with no error at all.</p>
 */
class RootOverflowClipTest {
    private static final String OUT_OF_FLOW_APP =
            "<div id='app' style='position:fixed;top:0;left:0;right:0;bottom:0'>"
                    + "<div id='inner' style='width:100px;height:50px'></div></div>";
    private static final String TALL_CONTENT =
            "<div id='content' style='width:100px;height:4000px'></div>";
    private static final String NESTED_BOX =
            "<div id='box' style='width:200px;height:100px;overflow:hidden;padding:5px'>"
                    + "<div id='inner' style='width:50px;height:50px'></div></div>";

    @Test
    void outOfFlowOnlyPageSurvivesRootOverflow() {
        withDocument("out-of-flow", "html,body{overflow:hidden}", OUT_OF_FLOW_APP, document -> {
            // 内容全脱离文档流，根盒子被内容撑到 0 高——正是报告里的页面。
            assertEquals(0.0d, document.body.getBoundingClientRect().height, 0.01d);

            AABB clip = rootClip(document);
            assertNotNull(clip);
            assertTrue(clip.isValid(), "root overflow must not collapse the clip to nothing");
            assertEquals(0.0f, clip.x(), 0.01f);
            assertEquals(0.0f, clip.y(), 0.01f);

            Element app = document.getElementById("app");
            assertNotNull(app);
            assertFalse(RenderNode.isFullyCulled(Rect.of(app), clip),
                    "an out-of-flow app shell must not be culled by the root overflow");
        });
    }

    @Test
    void rootOverflowClipsAtTheViewportScrollport() {
        withDocument("root-overflow", "html,body{overflow:auto}", TALL_CONTENT, document -> {
            Size viewport = document.getViewportSize();
            Element html = document.documentElement;
            AABB htmlClip = RenderNode.overflowClipBox(Rect.of(html), html);
            AABB bodyClip = RenderNode.overflowClipBox(Rect.of(document.body), document.body);
            assertNotNull(htmlClip);
            assertNotNull(bodyClip);

            double width = Math.max(0.0d, viewport.width() - html.getVerticalScrollbarGutter());
            double height = Math.max(0.0d, viewport.height() - html.getHorizontalScrollbarGutter());
            assertEquals(width, htmlClip.width(), 0.5d);
            assertEquals(height, htmlClip.height(), 0.5d);

            // 内容比视口高，html 的裁剪框不能跟着根盒子的内容高度走。
            assertTrue(htmlClip.height() < document.body.getBoundingClientRect().height);

            // html 的 overflow 不是 visible 时，body 的 overflow 留在自己盒子上。
            Rect bodyRect = Rect.of(document.body);
            assertEquals(bodyRect.getBodyRectSize().width(), bodyClip.width(), 0.01d);
            assertEquals(bodyRect.getBodyRectSize().height(), bodyClip.height(), 0.01d);
        });
    }

    @Test
    void bodyOverflowIsPropagatedToTheViewportWhileHtmlStaysVisible() {
        withDocument("body-only", "body{overflow:auto}", TALL_CONTENT, document -> {
            Size viewport = document.getViewportSize();
            Element body = document.body;
            AABB bodyClip = RenderNode.overflowClipBox(Rect.of(body), body);
            assertNotNull(bodyClip);
            assertEquals(Math.max(0.0d, viewport.width() - body.getVerticalScrollbarGutter()),
                    bodyClip.width(), 0.5d);
            assertEquals(Math.max(0.0d, viewport.height() - body.getHorizontalScrollbarGutter()),
                    bodyClip.height(), 0.5d);
            assertTrue(bodyClip.height() < body.getBoundingClientRect().height);
        });
    }

    @Test
    void nonRootOverflowStillClipsAtItsOwnPaddingBox() {
        withDocument("plain-element", "", NESTED_BOX, document -> {
            Element element = document.getElementById("box");
            assertNotNull(element);
            Rect rect = Rect.of(element);
            AABB clip = RenderNode.overflowClipBox(rect, element);
            assertNotNull(clip);
            assertFalse(RenderNode.clipsAtViewport(rect, element),
                    "a plain element keeps its overflow on its own box");
            assertEquals(rect.getBodyRectSize().width(), clip.width(), 0.01d);
            assertEquals(rect.getBodyRectSize().height(), clip.height(), 0.01d);
        });
    }

    /** The clip the two root masks leave behind, as {@code Mask} intersects them. */
    private static AABB rootClip(Document document) {
        AABB clip = null;
        for (Element element : new Element[]{document.documentElement, document.body}) {
            if (element == null || !Interaction.clipsOverflow(element.getComputedStyle())) continue;
            AABB box = RenderNode.overflowClipBox(Rect.of(element), element);
            if (box == null) continue;
            clip = clip == null ? box : clip.intersection(box);
        }
        return clip;
    }

    private static void withDocument(String name, String css, String body, Consumer<Document> assertions) {
        String path = "probe://root-overflow/" + name;
        HTML.putTemple(path, "<html><head><style>" + css + "</style></head><body>" + body + "</body></html>");
        Document document = new Document(path, false);
        try {
            document.refresh();
            assertions.accept(document);
        } finally {
            document.remove();
        }
    }
}
