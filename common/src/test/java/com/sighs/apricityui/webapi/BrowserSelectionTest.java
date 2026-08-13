package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.richtext.RangeBridge;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.behavior.richtext.SelectionBridge;
import com.sighs.apricityui.behavior.richtext.TreeWalkerBridge;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.render.ClipboardDataBridge;
import com.sighs.apricityui.render.Operation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 浏览器标准 JS API 桥(Selection/Range/TreeWalker/clipboardData)的换算与事件桥测试,
 * 供浏览器写法编辑器(contenteditable 输入漏斗)在 AUI 中运行。
 */
class BrowserSelectionTest {

    private static Document document() {
        return TestDocumentFactory.createDocument();
    }

    private static RichText parsed(Document document, String body) {
        Element element = document.createHTML(
                "<div contenteditable style=\"width: 320px; height: 120px;\">" + body + "</div>");
        assertTrue(element instanceof RichText);
        return (RichText) element;
    }

    // ------------------------------------------------------------------
    // SelectionBridge: setBaseAndExtent 换算
    // ------------------------------------------------------------------

    @Test
    void setBaseAndExtentMapsToRichTextSelection() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        TextNode tn = (TextNode) p.getChildNodes().get(0);

        SelectionBridge sel = new SelectionBridge(document);
        sel.setBaseAndExtent(tn, 1, tn, 3);

        RichTextSelection selection = document.getRichTextSelection();
        assertEquals(p, selection.getAnchorUnit());
        assertEquals(1, selection.getAnchorOffset());
        assertEquals(3, selection.getEndOffset());

        // 往返:anchorNode/anchorOffset 还原
        assertEquals(tn, sel.getAnchorNode());
        assertEquals(1, sel.getAnchorOffset());
        assertEquals(tn, sel.getFocusNode());
        assertEquals(3, sel.getFocusOffset());
        assertEquals(1, sel.getRangeCount());
    }

    @Test
    void collapseAndRemoveAllRanges() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        TextNode tn = (TextNode) p.getChildNodes().get(0);

        SelectionBridge sel = new SelectionBridge(document);
        sel.setBaseAndExtent(tn, 0, tn, 5);
        assertTrue(document.getRichTextSelection().isActive());

        sel.removeAllRanges();
        assertNull(document.getRichTextSelection().getAnchorUnit(), "removed ranges clear the selection");

        sel.collapse(tn, 2);
        RichTextSelection selection = document.getRichTextSelection();
        assertTrue(selection.collapsed());
        assertEquals(2, selection.getAnchorOffset());
    }

    // ------------------------------------------------------------------
    // RangeBridge: setStart/setEnd/toString
    // ------------------------------------------------------------------

    @Test
    void rangeSetStartEndAndToString() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello world</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        TextNode tn = (TextNode) p.getChildNodes().get(0);

        RangeBridge range = new RangeBridge();
        range.setStart(tn, 0);
        range.setEnd(tn, 5);
        assertEquals("hello", range.toString());
        assertEquals(tn, range.getStartContainer());
        assertEquals(5, range.getEndOffset());

        range.collapse(false);
        assertTrue(range.getCollapsed());
    }

    @Test
    void crossBlockSelectionViaBridges() {
        Document document = document();
        RichText rich = parsed(document, "<p>ab</p><p>cd</p>");
        Element first = (Element) rich.getChildNodes().get(0);
        TextNode firstText = (TextNode) first.getChildNodes().get(0);

        SelectionBridge sel = new SelectionBridge(document);
        sel.setBaseAndExtent(firstText, 1, firstText, 1);
        RichTextSelection selection = document.getRichTextSelection();
        assertEquals(first, selection.getAnchorUnit());
        assertEquals(1, selection.getAnchorOffset());
    }

    // ------------------------------------------------------------------
    // TreeWalker / replaceChildren
    // ------------------------------------------------------------------

    @Test
    void treeWalkerWalksTextNodes() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello <b>world</b></p>");
        Element p = (Element) rich.getChildNodes().get(0);

        TreeWalkerBridge walker = new TreeWalkerBridge(p, TreeWalkerBridge.SHOW_TEXT);
        Node first = walker.nextNode();
        assertNotNull(first);
        assertEquals("hello ", first.getNodeValue());
        Node second = walker.nextNode();
        assertNotNull(second);
        assertEquals("world", second.getNodeValue());
        assertNull(walker.nextNode());
    }

    @Test
    void replaceChildrenSwapsContent() {
        Document document = document();
        RichText rich = parsed(document, "<p>old</p>");
        Element p = (Element) rich.getChildNodes().get(0);

        p.replaceChildren(document.createTextNode("new"));
        assertEquals("new", p.getTextContent());
        assertEquals(1, p.getChildNodes().size());
    }

    // ------------------------------------------------------------------
    // 事件桥:clipboardData / ctrlKey
    // ------------------------------------------------------------------

    @Test
    void clipboardDataBridgeReadsAndWrites() {
        Operation.setInternalClipboardHtml("<b>html</b>");
        ClipboardDataBridge clipboard = new ClipboardDataBridge();
        // text/html 走 AUI 内存剪贴板,任意环境可测
        assertEquals("<b>html</b>", clipboard.getData("text/html"));
        clipboard.setData("text/html", "<i>written</i>");
        assertEquals("<i>written</i>", Operation.getInternalClipboardHtml());

        // text/plain 对接系统剪贴板:无 Minecraft 客户端时为 ""(Base.getClipboardText 契约),
        // 桥的职责只是透传,不与 Operation 的结果脱节。
        assertEquals(Operation.getClipboardText(), clipboard.getData("text/plain"));
        clipboard.setData("text/plain", "written"); // 无客户端时 no-op,不应抛异常

        // 未知 MIME 类型忽略
        clipboard.setData("unknown/type", "x");
        assertNull(clipboard.getData("unknown/type"));
    }

    @Test
    void keyEventCtrlKeyAlias() {
        KeyEvent event = new KeyEvent(null, "keydown", 0, 0, 0, false, null);
        event.controlKey = true;
        assertTrue(event.getCtrlKey(), "getCtrlKey() aliases controlKey for JS e.ctrlKey");
    }

    // ------------------------------------------------------------------
    // 折叠光标也是 range:浏览器语义 rangeCount=1(页面 readSelection 依赖)
    // ------------------------------------------------------------------

    @Test
    void collapsedCaretStillCountsAsRange() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello world</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        TextNode tn = (TextNode) p.getChildNodes().get(0);

        SelectionBridge sel = new SelectionBridge(document);
        sel.collapse(tn, 5); // 折叠光标在 hello 后
        assertEquals(1, sel.getRangeCount(), "collapsed caret must count as a range");
        assertNotNull(sel.getAnchorNode(), "collapsed caret must resolve an anchor node");
    }

    // ------------------------------------------------------------------
    // anchorOffset 是相对 anchorNode 的容器内偏移(浏览器语义)
    // ------------------------------------------------------------------

    @Test
    void anchorOffsetIsContainerLocal() {
        Document document = document();
        RichText rich = parsed(document, "<p>ab<strong>cd</strong>ef</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        TextNode first = (TextNode) p.getChildNodes().get(0);      // "ab"
        Element strong = (Element) p.getChildNodes().get(1);
        TextNode strongText = (TextNode) strong.getChildNodes().get(0); // "cd"

        SelectionBridge sel = new SelectionBridge(document);
        // 归一化偏移 3 = strong 文本 "cd" 的第 1 个字符
        sel.setBaseAndExtent(strongText, 1, strongText, 1);
        assertEquals(strongText, sel.getAnchorNode(), "anchor node is the strong text");
        assertEquals(1, sel.getAnchorOffset(), "anchorOffset is container-local, not normalized");
    }

    // ------------------------------------------------------------------
    // dataset:DOMStringMap 的 Map 语义(Rhino 对 Java Map 支持任意键读写,
    // 浏览器写法的 el.dataset.x = v 才不抛 "no public instance field named x")
    // ------------------------------------------------------------------

    @Test
    void datasetArbitraryKeyAssignmentThroughRhino() throws Exception {
        Document document = document();
        Element el = document.createElement("div");
        Element.DOMStringMap dataset = el.getDataset();

        dev.latvian.mods.rhino.Context cx = RhinoTestSupport.enterContext();
        try {
            dev.latvian.mods.rhino.Scriptable scope = cx.initStandardObjects();
            scope.put(cx, "ds", scope, RhinoTestSupport.wrap(cx, scope, dataset));
            Object result = cx.evaluateString(scope,
                    "ds.block = 5;"
                            + "ds['x.y'] = 'v';"
                            + "ds.action = 'mark';"
                            + "var out = ds.block + '|' + ds.get('block') + '|' + ds['x.y'] + '|' + ds.action;"
                            + "out;",
                    "dataset", 1, null);
            assertEquals("5|5|v|mark", result);
            // Java 侧同步到 data-* 属性
            assertEquals("5", el.getAttribute("data-block"));
            assertEquals("mark", el.getAttribute("data-action"));
            assertEquals("v", el.getAttribute("data-x.y"));
        } finally {
            // fork 无 Context.exit()
        }
    }
    // ------------------------------------------------------------------
    // readSelection 等价:anchorOffset 修复后 o = 点击处(不是块末尾)
    // ------------------------------------------------------------------

    @Test
    void readSelectionEquivalentOffsetMatchesClick() {
        Document document = document();
        RichText rich = parsed(document, "<p>所有输入都被 <strong>beforeinput</strong> 拦截</p>");
        Element p = (Element) rich.getChildNodes().get(0);

        SelectionBridge sel = new SelectionBridge(document);
        // 点击第二处(strong 文本 "beforeinput" 内 offset 3)
        Element strong = (Element) p.getChildNodes().get(1);
        com.sighs.apricityui.dom.TextNode strongText = (com.sighs.apricityui.dom.TextNode) strong.getChildNodes().get(0);
        sel.setBaseAndExtent(strongText, 3, strongText, 3);

        // toPoint 等价:setStart(blockEl, 0); setEnd(anchorNode, anchorOffset); o = toString().length
        com.sighs.apricityui.behavior.richtext.RangeBridge r = new com.sighs.apricityui.behavior.richtext.RangeBridge();
        r.setStart(p, 0);
        r.setEnd(sel.getAnchorNode(), sel.getAnchorOffset());
        int o = r.toString().length();
        // 期望:点击在归一化偏移 7+3=10 处(而非块末尾)
        System.out.println("[probe] o=" + o + " (expected 10) anchorNode="
                + sel.getAnchorNode().getClass().getSimpleName() + " anchorOffset=" + sel.getAnchorOffset());
        assertEquals(10, o, "readSelection o must equal the click point, not block end");
    }
    // ------------------------------------------------------------------
    // 行内标记(strong/u)内的文本:单元必须是块,不是行内元素
    // ------------------------------------------------------------------

    @Test
    void anchorUnitIsBlockNotInlineMarkup() {
        Document document = document();
        RichText rich = parsed(document, "<p>ab<strong>cd</strong><u>ef</u>gh</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        Element strong = (Element) p.getChildNodes().get(1);
        com.sighs.apricityui.dom.TextNode strongText = (com.sighs.apricityui.dom.TextNode) strong.getChildNodes().get(0);

        SelectionBridge sel = new SelectionBridge(document);
        sel.setBaseAndExtent(strongText, 1, strongText, 1);
        assertEquals(p, document.getRichTextSelection().getAnchorUnit(),
                "selection unit must be the block P, not the inline strong");
    }
}
