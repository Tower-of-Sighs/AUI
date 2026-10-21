package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.RenderNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TableSpanPaintTest {
    @Test void rowBackgroundsStayBehindSpanningCells() {
        for (String groupStyle : List.of("", "opacity:.5", "overflow:hidden")) {
            String path = "test://table-span-paint";
            HTML.putTemple(path, """
                <table><tbody style="%s">
                <tr><td id="a" rowspan="2" style="width:50px;height:60px;background:green"></td><td style="width:50px;height:20px"></td></tr>
                <tr id="r2" style="background:red"><td style="height:20px"></td></tr>
                </tbody></table>
                """.formatted(groupStyle));
            Document doc = new Document(path, false);
            try {
                doc.refresh(); doc.commitRenderState();
                Element a = doc.querySelector("#a"), row = doc.querySelector("#r2");
                List<RenderNode> nodes = Drawer.createPaintList(doc.body);
                int background = -1, cell = -1, backgrounds = 0;
                for (int i = 0; i < nodes.size(); i++) {
                    if (nodes.get(i) instanceof RenderNode.ElementBackgroundNode n && n.target() == row) {
                        background = i; backgrounds++;
                    }
                    if (nodes.get(i) instanceof RenderNode.ElementPhaseNode n && n.phase() == Base.RenderPhase.BODY) {
                        assertNotSame(row, n.target(), "row background must not be painted twice");
                        if (n.target() == a) cell = i;
                    }
                }
                assertEquals(1, backgrounds, groupStyle);
                assertTrue(background < cell, groupStyle);
            } finally { doc.remove(); }
        }
    }
}
