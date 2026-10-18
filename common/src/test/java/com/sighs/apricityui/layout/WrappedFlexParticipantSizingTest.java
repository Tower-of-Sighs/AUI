package com.sighs.apricityui.layout;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WrappedFlexParticipantSizingTest {
    private Element row() {
        var document = TestDocumentFactory.createDocument();
        Element row = document.createElement("div");
        row.setAttribute("style", "display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;width:500px;gap:18px;");
        document.body.appendChild(row);
        return row;
    }

    private Element control(Element row) {
        Element control = row.document.createElement("div");
        control.setAttribute("style", "width:200px;min-width:132px;height:14px;flex:1 1 0;");
        row.appendChild(control);
        return control;
    }

    @Test
    void grownItemDoesNotReceiveJustificationSpaceAgain() {
        Element row = row();
        Element label = row.document.createElement("span");
        label.setAttribute("style", "width:50px;height:20px;");
        row.appendChild(label);
        Element control = control(row);
        row.document.commitRenderState();
        assertEquals(432, Size.of(control).width(), 0.01);
        assertEquals(68, Position.getOffset(control).x, 0.01);
        assertEquals(500, Position.getOffset(control).x + Size.of(control).width(), 0.01);
    }

    @Test
    void anonymousLabelConsumesMainSpaceBeforeGrow() {
        Element row = row();
        row.appendChild(new TextNode(row.document, "Brightness"));
        Element control = control(row);
        row.document.commitRenderState();
        var text = Flex.computeDirectTextLayouts(row).get(0);
        double textWidth = text.text().size.width();
        assertEquals(500 - 18 - textWidth, Size.of(control).width(), 0.01);
        assertEquals(textWidth + 18, Position.getOffset(control).x, 0.01);
        assertEquals(500, Position.getOffset(control).x + Size.of(control).width(), 0.01);
    }

    @Test
    void anonymousItemParticipatesInLineBreaking() {
        Element row = row();
        row.setAttribute("style", "display:flex;flex-wrap:wrap;align-items:center;width:160px;gap:10px;");
        row.appendChild(new TextNode(row.document, "Brightness"));
        Element control = control(row);
        row.document.commitRenderState();
        var text = Flex.computeDirectTextLayouts(row).get(0);
        assertEquals(0, text.position().y, 0.01);
        assertTrue(Position.getOffset(control).y >= text.text().lineHeight + 9.99);
        assertEquals(0, Position.getOffset(control).x, 0.01);
        assertEquals(160, Size.of(control).width(), 0.01);
    }
}
