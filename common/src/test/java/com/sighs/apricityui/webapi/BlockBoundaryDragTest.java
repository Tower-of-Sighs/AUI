package com.sighs.apricityui.webapi;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockBoundaryDragTest {

    private static void mouse(Element target, String type, int button, int clickCount, double x, double y) {
        MouseEvent event = new MouseEvent(type, new Position(x, y), button, false);
        event.clickCount = clickCount;
        MouseEvent.dispatchToTarget(event, target.document, target);
    }

    /**
     * 跨块级边界拖拽（A4 测试页场景）：从第一个 div 拖到第二个 div，复制文本在
     * 两个单元之间应出现换行符。曾失效：triggerResolvedEvent 把 mousemove 重派发到
     * 按下元素，其 handler 用自身几何算出错误偏移，覆盖掉悬停元素已扩展的正确终点。
     */
    @Test
    void blockBoundaryNewline() {
        Document document = TestDocumentFactory.createDocument();
        Element a4a = new Element(document, "div");
        a4a.appendChild(document.createTextNode("alpha line"));
        document.body.appendChild(a4a);
        Element a4b = new Element(document, "div");
        a4b.appendChild(document.createTextNode("beta line"));
        document.body.appendChild(a4b);

        Position pa = Rect.of(a4a).getContentPosition();
        Position pb = Rect.of(a4b).getContentPosition();
        double ya = pa.y + Text.of(a4a).lineHeight / 2.0;
        double yb = pb.y + Text.of(a4b).lineHeight / 2.0;
        double mx = pb.x + Text.measureLine(Text.of(a4b), "beta line") - 1;

        // 从 a4a 起点拖到 a4b 末尾：跨块级边界，复制文本应在两单元之间出现换行符
        mouse(a4a, "mousedown", 0, 1, pa.x, ya);
        mouse(a4b, "mousemove", 0, 0, mx, yb);
        mouse(a4b, "mouseup", 0, 0, mx, yb);

        assertEquals("alpha line\nbeta line", document.getDocumentSelectedText());
        assertEquals("alpha line", a4a.getSelectedInnerText());
        assertEquals("beta line", a4b.getSelectedInnerText());
    }
}
