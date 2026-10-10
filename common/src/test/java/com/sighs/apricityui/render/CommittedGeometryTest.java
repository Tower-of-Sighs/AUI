package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window.IntersectionRect;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommittedGeometryTest {
    @Test
    void observerUsesViewportClipForPropagatedBodyOverflow() {
        String path = "probe://intersection-observer/viewport-clip";
        HTML.putTemple(path, "<html><head><style>body{overflow:hidden}</style></head>"
                + "<body><div style='width:100px;height:4000px'></div></body></html>");
        Document document = new Document(path, false);
        try {
            document.refresh();
            document.commitRenderState();
            LayoutCommit.commit(document);
            Element body = document.body;
            IntersectionRect clip = CommittedGeometry.overflowClip(body);
            assertNotNull(clip);
            assertEquals(0.0, clip.x());
            assertEquals(0.0, clip.y());
            assertEquals(document.getViewportSize().height() - body.getHorizontalScrollbarGutter(),
                    clip.height(), 0.5);
            assertTrue(clip.height() < CommittedGeometry.borderBox(body).height());
        } finally {
            document.remove();
        }
    }
}
