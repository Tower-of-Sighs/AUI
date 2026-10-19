package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LayoutPositionDependencyTest {
    @Test
    void ancestorInvalidationRefreshesFollowingSiblingDescendants() {
        Size.setViewportOverride(800, 600);
        String path = "test://position-dependency";
        HTML.putTemple(path, "<html><body style='margin:0'><div id='first' style='height:20px'></div>"
                + "<div id='second'><div id='nested' style='height:10px'>text</div></div></body></html>");
        Document document = Document.create(path);
        try {
            document.commitRenderState();
            var nested = document.getElementById("nested");
            double before = nested.getBoundingClientRect().y;
            document.getElementById("first").setAttribute("style", "height:80px");
            document.commitStyleRecalc();
            document.commitRenderState();
            assertEquals(before + 60, nested.getBoundingClientRect().y, 0.01);
            document.getElementById("first").setAttribute("style", "height:30px");
            document.commitStyleRecalc();
            document.commitRenderState();
            assertEquals(before + 10, nested.getBoundingClientRect().y, 0.01);
        } finally {
            document.remove();
            Size.clearViewportOverride();
        }
    }
}
