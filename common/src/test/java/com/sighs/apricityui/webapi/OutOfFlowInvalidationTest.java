package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutOfFlowInvalidationTest {
    @Test
    void absoluteContentUpdatesPreserveSiblingLayoutAndRefreshScrollExtent() {
        Size.setViewportOverride(800, 600);
        String path = "test://out-of-flow-invalidation";
        HTML.putTemple(path, "<html><body style='margin:0'>"
                + "<div id='scroll' style='position:relative;width:240px;height:100px;overflow:auto;scrollbar-width:none'>"
                + "<div id='absolute' style='position:absolute;left:0;top:0;width:50%'>"
                + "<div id='text' style='line-height:20px'>short</div></div>"
                + "<div id='sibling' style='height:24px'>sibling</div></div></body></html>");
        Document document = Document.create(path);
        try {
            commit(document);
            Element scroller = document.querySelector("#scroll");
            Element absolute = document.querySelector("#absolute");
            Element text = document.querySelector("#text");
            Element sibling = document.querySelector("#sibling");
            double siblingY = sibling.getBoundingClientRect().y;
            long siblingLayout = sibling.getRenderer().layoutDependency();

            text.setTextContent("long content ".repeat(50));
            commit(document);
            assertEquals(siblingLayout, sibling.getRenderer().layoutDependency());
            assertEquals(siblingY, sibling.getBoundingClientRect().y, 0.1);
            assertTrue(absolute.getBoundingClientRect().height > 100);
            scroller.commitScrollMetricsFromLayout();
            assertTrue(scroller.scrollHeight > 100, "Overflow must still be recomputed");

            double oldHeight = absolute.getBoundingClientRect().height;
            Element extra = document.createElement("div");
            extra.setAttribute("style", "height:30px");
            absolute.appendChild(extra);
            commit(document);
            assertEquals(oldHeight + 30, absolute.getBoundingClientRect().height, 0.1);
            assertEquals(siblingLayout, sibling.getRenderer().layoutDependency());
            extra.remove();
            commit(document);
            assertEquals(oldHeight, absolute.getBoundingClientRect().height, 0.1);

            absolute.setAttribute("style", "position:static;width:50%");
            commit(document);
            assertTrue(sibling.getBoundingClientRect().y > siblingY + 100,
                    "Returning to normal flow must move following siblings");
            absolute.setAttribute("style", "position:absolute;left:0;top:0;width:50%");
            commit(document);
            assertEquals(siblingY, sibling.getBoundingClientRect().y, 0.1);

            scroller.setAttribute("style", "position:relative;width:300px;height:100px;overflow:auto;scrollbar-width:none");
            commit(document);
            assertEquals(150, absolute.getBoundingClientRect().width, 0.1,
                    "Containing-block resize must still invalidate absolute descendants");
        } finally {
            document.remove();
            Size.clearViewportOverride();
        }
    }

    private static void commit(Document document) {
        document.commitStyleRecalc();
        document.commitRenderState();
    }
}
