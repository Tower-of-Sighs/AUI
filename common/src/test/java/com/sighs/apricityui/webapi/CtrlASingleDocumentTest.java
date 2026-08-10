package com.sighs.apricityui.webapi;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.render.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ctrl+A 单文档回归测试：快捷键全选只能命中“一个”目标文档，且选区按文档隔离。
 * <p>
 * 目标文档由 {@link Operation#resolveSelectionTargetDocument()} 解析（公开静态方法，
 * 优先级：持有当前选区的文档 → 鼠标指针命中的文档 → 上下文文档）。测试只通过公开
 * API 驱动：文档级选区用 {@link Document#selectAllDocumentText()} /
 * {@link Element#selectAllInnerText()}，指针用 {@link Operation#cachedMousePosition}，
 * 鼠标事件经 {@link MouseEvent#dispatchToTarget} 派发。
 * <p>
 * 解析器依赖 {@link Document#getAll()} 注册表与“指针命中”两步：指针命中要求文档
 * 声明 {@code aui-mouse-events} meta（{@link Document#interceptsMouseEventsAt}），
 * 因此涉及指针的用例用 {@link HTML#putTemple} + {@link Document#create} 建真实文档
 * （与 RootScrollTest 同套路），创建后 {@link Document#remove()} 清理；纯选区/点击
 * 用例用 {@link TestDocumentFactory#createDocument()} 并显式注册进注册表。
 */
class CtrlASingleDocumentTest {

    @Test
    void documentHoldingSelectionWinsOverPointerAndContext() {
        // 前置文档在指针位置（z 更高，指针步骤会先命中它），被选中文档的单元在别处。
        Document pointerDoc = registeredDocument("test://ctrl-a-selection-ptr-doc", 300, true,
                "<div id=\"pointer\" style=\"position:fixed;left:0;top:0;width:200px;height:120px\"></div>");
        Document selectedDoc = registeredDocument("test://ctrl-a-selection-doc", 100, true,
                "<div id=\"selected\" style=\"position:fixed;left:300px;top:0;width:200px;height:120px\">hello world</div>");
        try {
            Element selected = selectedDoc.querySelector("#selected");
            selected.selectAllInnerText();
            assertTrue(selectedDoc.hasDocumentSelection());
            assertEquals("hello world", selected.getSelectedInnerText());
            assertFalse(pointerDoc.hasDocumentSelection());

            // 指针位于前置文档（pointerDoc）的元素上、不在 selectedDoc 上：若解析器忽略
            // 选区，指针步骤会返回 pointerDoc（或回退到上下文文档）。
            Position pointer = new Position(100, 60);
            assertTrue(pointerDoc.interceptsMouseEventsAt(pointer),
                    "pointer must hit the front document for this scenario to be meaningful");
            assertFalse(selectedDoc.interceptsMouseEventsAt(pointer));

            Position previousPointer = Operation.cachedMousePosition;
            Operation.cachedMousePosition = pointer;
            try (Document.ContextScope ignored = Document.withContext(pointerDoc)) {
                // 选区优先：即使指针与上下文都指向 pointerDoc，也要返回 selectedDoc。
                assertSame(selectedDoc, Operation.resolveSelectionTargetDocument());
            } finally {
                Operation.cachedMousePosition = previousPointer;
            }
        } finally {
            pointerDoc.remove();
            selectedDoc.remove();
        }
    }

    @Test
    void pointerWinsOverContextWhenNoSelectionExists() {
        // 无选区时指针命中优先于上下文文档：指针在前置文档（z 更高）的元素上。
        Document contextDoc = registeredDocument("test://ctrl-a-pointer-context-doc", 100, false,
                "<div id=\"context\" style=\"position:fixed;left:300px;top:0;width:200px;height:120px\"></div>");
        Document pointerDoc = registeredDocument("test://ctrl-a-pointer-doc", 200, true,
                "<div id=\"pointer\" style=\"position:fixed;left:0;top:0;width:200px;height:120px\"></div>");
        try {
            assertFalse(contextDoc.hasDocumentSelection());
            assertFalse(pointerDoc.hasDocumentSelection());

            Position pointer = new Position(100, 60);
            assertTrue(pointerDoc.interceptsMouseEventsAt(pointer));
            assertFalse(contextDoc.interceptsMouseEventsAt(pointer));

            Position previousPointer = Operation.cachedMousePosition;
            Operation.cachedMousePosition = pointer;
            try (Document.ContextScope ignored = Document.withContext(contextDoc)) {
                assertSame(pointerDoc, Operation.resolveSelectionTargetDocument());
            } finally {
                Operation.cachedMousePosition = previousPointer;
            }
        } finally {
            contextDoc.remove();
            pointerDoc.remove();
        }
    }

    @Test
    void fallbackReturnsContextDocumentOrNull() {
        // 无选区、无指针：解析器回退到上下文文档（未设置上下文时为 null）。
        Document contextDoc = TestDocumentFactory.createDocument();
        Position previousPointer = Operation.cachedMousePosition;
        Operation.cachedMousePosition = null;
        try {
            try (Document.ContextScope ignored = Document.withContext(contextDoc)) {
                assertSame(contextDoc, Operation.resolveSelectionTargetDocument());
            }
            try (Document.ContextScope ignored = Document.withContext(null)) {
                assertNull(Operation.resolveSelectionTargetDocument());
            }
        } finally {
            Operation.cachedMousePosition = previousPointer;
        }
    }

    @Test
    void selectAllDocumentTextStaysWithinItsOwnDocument() {
        // 文档级全选按文档隔离：对 doc B 全选不会波及 doc A，清掉 doc A 也不影响 doc B。
        Document first = TestDocumentFactory.createDocument();
        Document second = TestDocumentFactory.createDocument();
        Element divA = selectableUnit(first, "alpha");
        Element divB = selectableUnit(second, "beta");

        assertTrue(second.selectAllDocumentText());
        assertTrue(second.hasDocumentSelection());
        assertEquals("beta", second.getDocumentSelectedText());
        assertTrue(divB.hasInnerTextSelection());

        assertFalse(first.hasDocumentSelection());
        assertFalse(divA.hasInnerTextSelection());
        assertEquals("", divA.getSelectedInnerText());

        first.clearDocumentSelection();
        assertTrue(second.hasDocumentSelection());
        assertEquals("beta", second.getDocumentSelectedText());
        assertTrue(divB.hasInnerTextSelection());
    }

    @Test
    void mouseDownInOtherDocumentClearsTheDocumentSelection() {
        // 跨文档 mousedown：点击其他文档仍清掉本文档的选区，被点击文档状态自洽。
        Document docA = TestDocumentFactory.createDocument();
        Document docB = TestDocumentFactory.createDocument();
        // 解析器/事件清选走注册表（clearGlobalSelectionsOnMouseDown 遍历 Document.getAll()），
        // 工厂文档默认不在注册表，这里按 WindowApiTest 的写法显式注册。
        Document.getAll().add(docA);
        Document.getAll().add(docB);
        try {
            Element divA = selectableUnit(docA, "alpha text");
            Element neutralB = new Element(docB, "div");
            neutralB.setAttribute("style", "width: 200px; height: 40px;");
            docB.body.appendChild(neutralB);
            docA.tickFrame();
            docB.tickFrame();

            divA.selectAllInnerText();
            assertTrue(docA.hasDocumentSelection());
            assertEquals("alpha text", divA.getSelectedInnerText());
            assertFalse(docB.hasDocumentSelection());

            Position body = Rect.of(neutralB).getBodyRectPosition();
            mouse(neutralB, "mousedown", 0, 1, body.x + 10, body.y + 10);

            assertFalse(docA.hasDocumentSelection());
            assertFalse(divA.hasInnerTextSelection());
            assertFalse(docB.hasDocumentSelection());
        } finally {
            docA.remove();
            docB.remove();
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 带真实模板的注册文档：meta 控制是否拦截鼠标（interceptsMouseEventsAt 的前提）。 */
    private static Document registeredDocument(String path, int zIndex, boolean intercept, String bodyHtml) {
        String meta = intercept ? "<meta name=\"aui-mouse-events\" content=\"intercept\">" : "";
        HTML.putTemple(path, "<html style=\"transform:translateZ(" + zIndex + "px)\"><head>"
                + meta + "</head><body>" + bodyHtml + "</body></html>");
        Document document = Document.create(path);
        document.tickFrame();
        return document;
    }

    /** 一个带几何的普通流选择单元（div + 文本节点，200x40 内容盒）。 */
    private static Element selectableUnit(Document document, String text) {
        Element div = new Element(document, "div");
        div.setAttribute("style", "width: 200px; height: 40px;");
        div.appendChild(document.createTextNode(text));
        document.body.appendChild(div);
        return div;
    }

    /** 派发鼠标事件到指定元素：与 TextDragAndPasteTest 同一约定（dispatchToTarget 不做几何解析）。 */
    private static void mouse(Element target, String type, int button, int buttons, double x, double y) {
        MouseEvent event = new MouseEvent(type, new Position(x, y), button, false);
        event.buttons = buttons;
        Position body = Rect.of(target).getBodyRectPosition();
        event.offsetX = x - body.x;
        event.offsetY = y - body.y;
        MouseEvent.dispatchToTarget(event, target.document, target);
    }
}
