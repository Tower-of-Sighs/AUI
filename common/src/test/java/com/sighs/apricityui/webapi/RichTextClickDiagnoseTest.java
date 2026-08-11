package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 诊断：页面场景（contenteditable + 页面 JS 重渲染）下点击定位光标与字符输入链路。
 */
class RichTextClickDiagnoseTest {

    private static void mouse(Element target, String type, int button, double x, double y) {
        MouseEvent event = new MouseEvent(type, new Position(x, y), button, false);
        MouseEvent.dispatchToTarget(event, target.document, target);
    }

    @Test
    void clickLocatesCaretAndTypingInserts() {
        Document document = TestDocumentFactory.createDocument();
        Element root = document.createHTML(
                "<div contenteditable style=\"width: 320px; height: 140px;\">"
                        + "<h1>数据驱动编辑器</h1><p>hello world</p></div>");
        assertTrue(root instanceof RichText, "contenteditable upgraded to RichText");
        RichText rich = (RichText) root;
        document.body.appendChild(rich);

        Element p = (Element) rich.getChildNodes().get(1);
        Position pp = Rect.of(p).getContentPosition();
        double y = pp.y + Text.of(p).lineHeight / 2.0;
        double x = pp.x + Text.measureLine(Text.of(p), "hello") + 1;

        // 点击 p 的 hello 之后
        mouse(p, "mousedown", 0, x, y);
        mouse(p, "mouseup", 0, x, y);

        RichTextSelection selection = document.getRichTextSelection();
        assertNotNull(selection);
        assertTrue(selection.hasAnchor(), "click must set an anchor");
        assertTrue(selection.collapsed(), "plain click must collapse the selection");
        assertNotNull(selection.getAnchorUnit(), "click must resolve an anchor unit");
        assertEquals(rich, document.getFocusedElement(), "click must focus the editor");

        // 字符输入：直接变换（真实键盘路径经 Operation.onCharTyped,依赖全局注册）
        boolean inserted = com.sighs.apricityui.behavior.richtext.RichTextEditing.insertText(rich, "x");
        assertTrue(inserted, "insertText must succeed");
        assertTrue(rich.getTextContent().contains("hellox"), "typed char inserted, content=" + rich.getTextContent());
    }
}
