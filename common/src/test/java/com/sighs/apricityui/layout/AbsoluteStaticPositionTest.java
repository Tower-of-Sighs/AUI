package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbsoluteStaticPositionTest {
    @Test
    void blockTooltipWithoutInsetsUsesLineAfterPrecedingInline() {
        var document = TestDocumentFactory.createDocument();
        Element parent = document.createElement("div");
        parent.setAttribute("style", "position:relative;width:200px;padding:10px;line-height:20px;");
        document.body.appendChild(parent);
        Element button = document.createElement("button");
        button.setAttribute("style", "display:inline-block;width:80px;height:30px;");
        parent.appendChild(button);
        Element tooltip = document.createElement("span");
        tooltip.setAttribute("style", "position:absolute;display:block;width:160px;height:40px;");
        parent.appendChild(tooltip);
        document.commitRenderState();
        Position buttonPosition = Position.getOffset(button);
        Position tooltipPosition = Position.getOffset(tooltip);
        assertEquals(10, tooltipPosition.x, 0.01);
        assertTrue(tooltipPosition.y >= buttonPosition.y + Size.of(button).height());
        assertTrue(Size.of(parent).height() < tooltipPosition.y + Size.of(tooltip).height());
    }
}
