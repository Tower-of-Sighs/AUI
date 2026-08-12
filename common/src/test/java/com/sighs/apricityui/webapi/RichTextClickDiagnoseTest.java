package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.dom.DocumentFragment;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
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

    /**
     * 决定性验证：页面 JS 的渲染方式(fragment + replaceChildren 重建 DOM)后,
     * 块的冒泡链是否包含 RichText(决定 mousedown 能否到达 RichText 内部监听器)。
     */
    @Test
    void pageRenderFragmentStructureKeepsRouteToRichText() {
        Document document = TestDocumentFactory.createDocument();
        Element root = document.createHTML(
                "<div contenteditable style=\"width: 320px; height: 140px;\"></div>");
        assertTrue(root instanceof RichText);
        RichText rich = (RichText) root;
        document.body.appendChild(rich);

        // 页面 renderBlock: 创建块 + dataset.block + 文本节点
        Element p1 = document.createElement("h1");
        p1.getDataset().set("block", "0");
        p1.appendChild(document.createTextNode("数据驱动编辑器"));
        Element p2 = document.createElement("p");
        p2.getDataset().set("block", "1");
        p2.appendChild(document.createTextNode("hello world"));

        // 页面 render: frag.appendChild + root.replaceChildren(frag)
        DocumentFragment frag = document.createDocumentFragment();
        frag.appendChild(p1);
        frag.appendChild(p2);
        rich.replaceChildren(frag);

        // 1) 块的父链必须通向 RichText
        Node current = p2;
        boolean reachedRich = false;
        while (current != null) {
            if (current == rich) { reachedRich = true; break; }
            current = current.getParentNode();
        }
        assertTrue(reachedRich, "p must be under rich, parent chain must contain rich");
        assertEquals(rich, p2.getParentNode(), "fragment children are spliced into rich");

        // 2) 冒泡链含 rich -> mousedown 事件能到达 RichText 内部监听器
        assertTrue(p2.getRouteNodes().contains(rich), "event route must include rich");

        // 3) dataset.block 写入 + 读取
        assertEquals("1", p2.getDataset().get("block"));

        // 4) 派发 mousedown 到 p:RichText 内部监听器应执行(selection 设置)
        Element.DOMStringMap ds = p2.getDataset();
        ds.set("block", "1");
        Position pp = Rect.of(p2).getContentPosition();
        double y = pp.y + Text.of(p2).lineHeight / 2.0;
        double x = pp.x + Text.measureLine(Text.of(p2), "hello") + 1;
        com.sighs.apricityui.event.MouseEvent.dispatchToTarget(
                new com.sighs.apricityui.event.MouseEvent("mousedown", new Position(x, y), 0), document, p2);

        RichTextSelection selection = document.getRichTextSelection();
        assertTrue(selection != null && selection.hasAnchor(), "mousedown on p must set selection");
        assertEquals(rich, document.getFocusedElement(), "rich must take focus on mousedown");
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

        // 点击设置的单元必须在 RichText 树内(否则光标绘制条件 rootOf(unit)==this 不满足)
        Element au = selection.getAnchorUnit();
        Element cur = au;
        boolean inRich = false;
        while (cur != null) {
            if (cur == rich) { inRich = true; break; }
            cur = cur.getParentNode() instanceof Element pe ? pe : null;
        }
        System.out.println("[probe] click unit=" + (au == null ? "null" : au.tagName)
                + " inRich=" + inRich + " off=" + selection.getAnchorOffset());
        assertTrue(inRich, "click anchor unit must be inside the RichText tree");

        // 字符输入：直接变换（真实键盘路径经 Operation.onCharTyped,依赖全局注册）
        boolean inserted = com.sighs.apricityui.behavior.richtext.RichTextEditing.insertText(rich, "x");
        assertTrue(inserted, "insertText must succeed");
        assertTrue(rich.getTextContent().contains("hellox"), "typed char inserted, content=" + rich.getTextContent());
    }

}
