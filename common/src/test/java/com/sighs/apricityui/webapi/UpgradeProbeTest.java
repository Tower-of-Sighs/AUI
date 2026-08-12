package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;






import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeProbeTest {

    private static final String HTML_PATH = "D:/work/AUI/targets/forge-1.20.1/run/apricity/overlays/Qwen_html.html";
    private static final String DOC_PATH = "file:/D:/work/AUI/targets/forge-1.20.1/run/apricity/overlays/Qwen_html.html";

    @Test
    void nestedContenteditableUpgrades() {
        Document document = TestDocumentFactory.createDocument();
        Element root = document.createHTML(
                "<div class=\"wrap\"><div class=\"toolbar\"></div>"
                        + "<div id=\"editor\" contenteditable=\"true\" spellcheck=\"false\"></div></div>");
        Element editor = document.getElementById("editor");
        assertTrue(editor instanceof com.sighs.apricityui.element.RichText,
                "nested contenteditable div must upgrade to RichText, got "
                        + (editor == null ? "null" : editor.getClass().getSimpleName()));
    }

    /** 页面结构(多文本节点+strong)下 Range 偏移换算:readSelection 的 o 与末尾定位。 */
    @Test
    void rangeOffsetsOnMultiNodeBlock() {
        Document document = TestDocumentFactory.createDocument();
        Element root = document.createHTML(
                "<div contenteditable style=\"width: 320px; height: 140px;\">"
                        + "<h1>数据驱动编辑器</h1>"
                        + "<p>所有输入都被 <strong>beforeinput</strong> 拦截</p></div>");
        assertTrue(root instanceof com.sighs.apricityui.element.RichText);
        Element p = (Element) root.getChildNodes().get(1);

        // p 的文本节点:["所有输入都被 ", "beforeinput", " 拦截"]
        java.util.List<com.sighs.apricityui.init.Node> kids = p.getChildNodes();
        System.out.println("[probe] p children: " + kids.size() + " tags=" + kids);
        Object text0 = kids.get(0); // TextNode "所有输入都被 "
        Object strong = kids.get(1);
        java.util.List<com.sighs.apricityui.init.Node> strongKids = ((Element) strong).getChildNodes();
        Object text1 = strongKids.get(0); // "beforeinput"

        // readSelection 场景:点击在 "beforeinput" 中段
        // r.setStart(p, 0); r.setEnd(text1, 5) -> toString 应为 "所有输入都被 before" (11 字符)
        com.sighs.apricityui.behavior.richtext.RangeBridge range = new com.sighs.apricityui.behavior.richtext.RangeBridge();
        range.setStart(p, 0);
        range.setEnd((com.sighs.apricityui.init.Node) text1, 5);
        String s = range.toString();
        System.out.println("[probe] toString for offset 5 in beforeinput = " + s + " len=" + s.length());

        // writeSelection 场景:选区在文本节点中段 -> anchorNode/anchorOffset 往返
        com.sighs.apricityui.behavior.richtext.SelectionBridge sel =
                new com.sighs.apricityui.behavior.richtext.SelectionBridge(document);
        sel.setBaseAndExtent((com.sighs.apricityui.init.Node) text1, 3,
                (com.sighs.apricityui.init.Node) text1, 3);
        com.sighs.apricityui.init.Node anchorNode = sel.getAnchorNode();
        System.out.println("[probe] anchor after setBaseAndExtent(text1,3) = "
                + (anchorNode == null ? "null" : anchorNode.getClass().getSimpleName())
                + " off=" + sel.getAnchorOffset());
    }

    /** Rhino 直接读未装饰节点的 parentElement/nodeType(页面 readSelection 依赖)。 */
    @Test
    void rhinoReadsRawNodeProperties() {
        Document document = TestDocumentFactory.createDocument();
        Element p = document.createElement("p");
        com.sighs.apricityui.dom.TextNode tn = document.createTextNode("hello");
        p.appendChild(tn);
        document.body.appendChild(p);

        dev.latvian.mods.rhino.Context cx = dev.latvian.mods.rhino.Context.enter();
        try {
            dev.latvian.mods.rhino.Scriptable scope = cx.initStandardObjects();
            scope.put(cx, "TN", scope, tn);
            scope.put(cx, "P", scope, p);
            Object r = cx.evaluateString(scope,
                    "var host = TN.nodeType === 1 ? TN : TN.parentElement;"
                    + " host ? (host.tagName + '|' + TN.nodeType) : 'no-host';",
                    "raw", 1, null);
            System.out.println("[probe] raw node props: " + r);
            assertTrue(("P|3").equals(r), "raw node parentElement/nodeType must resolve, got " + r);
        } finally {
        }
    }

}
