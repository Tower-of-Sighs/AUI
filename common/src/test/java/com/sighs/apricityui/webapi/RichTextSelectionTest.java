package com.sighs.apricityui.webapi;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.NormalFlow;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 富文本（混合直接文本与内联子元素）的鼠标选择回归测试。
 * <p>
 * 曾出现的缺陷：locateRuns 对同一视觉行的多个 run 距离相等时“最后一个 run 恒胜”，
 * 导致任何点击位置都映射到行尾 —— 拖拽选择完全没效果、双击只能选中最后一个词。
 * 本类用真实的鼠标事件流（mousedown/mousemove/mouseup）锁定修复后的行为。
 * <p>
 * 鼠标坐标取自布局自身的 run 几何（NormalFlow.computeTextRuns），避免测试侧
 * 用 Text.measureLine 单独测量与布局存在 1-2px 舍入差导致边界落点偏一个字符。
 */
class RichTextSelectionTest {

    /** 构造单行富文本：direct text + 内联 b 子元素。返回目标 div。 */
    private static Element richUnit(Document document, String before, String inline, String after) {
        Element div = new Element(document, "div");
        div.setAttribute("style", "width: 400px; user-select: text;");
        document.body.appendChild(div);
        div.appendChild(document.createTextNode(before));
        Element b = new Element(document, "b");
        b.appendChild(document.createTextNode(inline));
        div.appendChild(b);
        div.appendChild(document.createTextNode(after));
        return div;
    }

    /** 在布局 run 列表里找内容为指定文本的 run。 */
    private static NormalFlow.TextRunLayout findRun(List<NormalFlow.TextRunLayout> runs, String content) {
        for (NormalFlow.TextRunLayout run : runs) {
            if (run.text() != null && content.equals(run.text().content)) return run;
        }
        throw new IllegalStateException("run not found: " + content);
    }

    /** run 行的绝对 X 起点（单行、无对齐、无滚动时的内容盒坐标系）。 */
    private static double runX(Position contentPos, NormalFlow.TextRunLayout run) {
        return contentPos.x + run.x();
    }

    /** run 行的垂直中心（首行，run.y 已含基线对齐位移）。 */
    private static double runCenterY(Position contentPos, NormalFlow.TextRunLayout run) {
        return contentPos.y + run.y() + run.text().lineHeight / 2.0;
    }

    /** 派发鼠标事件到指定元素：clientX/clientY 为文档坐标。 */
    private static void mouse(Element target, String type, int button, int clickCount, double x, double y) {
        MouseEvent event = new MouseEvent(type, new Position(x, y), button, false);
        event.clickCount = clickCount;
        MouseEvent.dispatchToTarget(event, target.document, target);
    }

    @Test
    void dragInsideRichTextSelectsTheInlineWordOnly() {
        Document document = TestDocumentFactory.createDocument();
        Element div = richUnit(document, "aaaa ", "bbbb", " cccc");
        Element b = (Element) div.childNodes.get(1);

        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(div);
        NormalFlow.TextRunLayout inlineRun = findRun(runs, "bbbb");
        Position contentPos = Rect.of(div).getContentPosition();
        double inlineWidth = Text.measureLine(inlineRun.text(), "bbbb");
        double x0 = runX(contentPos, inlineRun);
        double y = runCenterY(contentPos, inlineRun);

        // 从 "bbbb" 起点附近拖到终点附近：选中恰好是内联词 "bbbb"
        mouse(b, "mousedown", 0, 1, x0 + 1, y);
        mouse(b, "mousemove", 0, 0, x0 + inlineWidth - 1, y);
        mouse(b, "mouseup", 0, 0, x0 + inlineWidth - 1, y);

        assertEquals("bbbb", div.getSelectedInnerText(),
                "富文本内拖拽应选中指针覆盖的精确范围，而不是整体映射到行尾");
    }

    @Test
    void doubleClickInsideRichTextSelectsTheClickedWord() {
        Document document = TestDocumentFactory.createDocument();
        Element div = richUnit(document, "aaaa ", "bbbb", " cccc");
        Element b = (Element) div.childNodes.get(1);

        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(div);
        NormalFlow.TextRunLayout inlineRun = findRun(runs, "bbbb");
        Position contentPos = Rect.of(div).getContentPosition();
        double inlineWidth = Text.measureLine(inlineRun.text(), "bbbb");
        double cx = runX(contentPos, inlineRun) + inlineWidth / 2.0;
        double y = runCenterY(contentPos, inlineRun);

        // 同一位置连续两次 mousedown/mouseup：文档点击序列计数为 2 → 触发选词
        // （clickCount 由 triggerResolvedEvent 用 advanceClickSequence 覆盖，不能手动设）
        mouse(b, "mousedown", 0, 0, cx, y);
        mouse(b, "mouseup", 0, 0, cx, y);
        mouse(b, "mousedown", 0, 0, cx, y);
        mouse(b, "mouseup", 0, 0, cx, y);

        assertEquals("bbbb", div.getSelectedInnerText(),
                "双击富文本内联词应选中该词，而不是行尾的最后一个词");
    }

    @Test
    void dragAcrossMultipleRunsSelectsTheWholeSpan() {
        Document document = TestDocumentFactory.createDocument();
        Element div = richUnit(document, "aaaa ", "bbbb", " cccc");
        Element b = (Element) div.childNodes.get(1);

        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(div);
        NormalFlow.TextRunLayout firstRun = findRun(runs, "aaaa ");
        NormalFlow.TextRunLayout inlineRun = findRun(runs, "bbbb");
        Position contentPos = Rect.of(div).getContentPosition();
        double inlineWidth = Text.measureLine(inlineRun.text(), "bbbb");
        double y = runCenterY(contentPos, inlineRun);

        // 从首 run 左缘（offset 0）拖到 "bbbb" 末尾：跨两个 run 的连续选区
        mouse(div, "mousedown", 0, 1, runX(contentPos, firstRun), y);
        mouse(b, "mousemove", 0, 0, runX(contentPos, inlineRun) + inlineWidth - 1, y);
        mouse(b, "mouseup", 0, 0, runX(contentPos, inlineRun) + inlineWidth - 1, y);

        assertEquals("aaaa bbbb", div.getSelectedInnerText(),
                "跨多个 run 的拖拽应产出连续的文本区间");
    }
}
