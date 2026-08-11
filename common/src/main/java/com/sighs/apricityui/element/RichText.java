package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.behavior.TextSelection;
import com.sighs.apricityui.behavior.richtext.RichTextEditing;
import com.sighs.apricityui.behavior.richtext.RichTextNavigation;
import com.sighs.apricityui.behavior.richtext.RichTextOperation;
import com.sighs.apricityui.behavior.richtext.RichTextRange;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.ui.ContextMenu;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 富文本可编辑元素（{@code <richtext>}）：内容保留为子节点树（TextNode + 行内元素），
 * 不扁平化。提供鼠标点击定位、拖拽选区、聚焦光标渲染；编辑选区状态由
 * {@link RichTextSelection} 维护，高亮由 Element 的 run 绘制路径（经 Document
 * 的选区来源解析）渲染。
 * <p>
 * 编辑操作（输入/删除/Enter/粘贴/撤销重做）经统一变换层
 * {@link RichTextEditing} 执行（beforeinput 可取消 → 变换 → normalize →
 * 光标修复 → input 事件），撤销/重做为操作日志（{@link RichTextOperation}，
 * 正/逆操作重放 + 连续输入合并）。
 */
public class RichText extends Element {
    private static final int MAX_UNDO_STACK = 128;
    private static final Set<String> REGISTERED_CONTENT_STYLE_DOCUMENTS = ConcurrentHashMap.newKeySet();

    /** UA 级 content 排版样式：选择器限定 [contenteditable] 内，order 用负值保证最低优先级。 */
    private static final String CONTENT_CSS = String.join("",
            "[contenteditable] h1{font-size:2em;font-weight:700;margin:0.67em 0;}",
            "[contenteditable] h2{font-size:1.5em;font-weight:700;margin:0.83em 0;}",
            "[contenteditable] h3{font-size:1.17em;font-weight:700;margin:1em 0;}",
            "[contenteditable] h4{font-size:1em;font-weight:700;margin:1.33em 0;}",
            "[contenteditable] h5{font-size:0.83em;font-weight:700;margin:1.67em 0;}",
            "[contenteditable] h6{font-size:0.67em;font-weight:700;margin:2.33em 0;}",
            "[contenteditable] p{margin:1em 0;}",
            "[contenteditable] blockquote{margin:1em 40px;}",
            "[contenteditable] ul,[contenteditable] ol{margin:1em 0;padding-left:40px;}",
            "[contenteditable] hr{border:none;border-top:1px solid #000000;margin:0.5em 0;}",
            "[contenteditable] a{color:#0000EE;text-decoration:underline;}",
            "[contenteditable] code,[contenteditable] pre{font-family:monospace;}",
            "[contenteditable] i,[contenteditable] em{font-style:oblique;}",
            "[contenteditable] img{max-width:100%;}");
    private long lastBlinkTime;
    private String focusValueSnapshot = "";
    private final Deque<RichTextOperation> undoStack = new ArrayDeque<>();
    private final Deque<RichTextOperation> redoStack = new ArrayDeque<>();
    private Element draggingObject = null;
    private int dragTargetOffset = -1;

    public RichText(Document document, String tagName) {
        super(document, tagName);
        ensureContentStyles(document);
        lastBlinkTime = System.currentTimeMillis();
        addInternalEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouse) || document == null) return;
            if (mouse.button != 0 && mouse.button != -1) return;
            document.clearAllTextSelectionsExcept(this);
            RichTextSelection selection = document.getRichTextSelection();
            Element hit = event.target instanceof Element targetElement ? targetElement : this;
            Element objectHit = hitAtomicObject(hit, mouse.clientX, mouse.clientY);
            if (objectHit != null) {
                // 原子对象：单击选中（选区恰好覆盖对象）+ 进入拖拽态
                Element objectBlock = blockOf(objectHit);
                int objectStart = SelectionUnits.baseOffsetOfDescendant(objectBlock, objectHit);
                selection.setRange(objectBlock, objectStart, objectBlock, objectStart + 1);
                draggingObject = objectHit;
                dragTargetOffset = -1;
            } else {
                selection.setFromPoint(hit, mouse, mouse.shiftKey);
                draggingObject = null;
                dragTargetOffset = -1;
            }
            document.setFocusedElement(this);
            event.preventDefault();
        });
        addInternalEventListener("mousemove", event -> {
            if (!(event instanceof MouseEvent mouse) || document == null) return;
            RichTextSelection selection = document.getRichTextSelection();
            if (draggingObject != null) {
                // 对象拖拽：更新目标光标位置
                Element hit = event.target instanceof Element targetElement ? targetElement : this;
                SelectionUnits.UnitOffset target = TextSelection.resolveUnitOffset(hit, mouse.clientX, mouse.clientY);
                if (target != null && target.unit() != null) {
                    dragTargetOffset = target.offset();
                    selection.setCollapsed(target.unit(), target.offset());
                }
                return;
            }
            if (!selection.isSelecting() || document.getPressedElement() == null) return;
            Element hit = event.target instanceof Element targetElement ? targetElement : this;
            SelectionUnits.UnitOffset target = TextSelection.resolveUnitOffset(hit, mouse.clientX, mouse.clientY);
            if (target != null) {
                selection.extendTo(target.unit(), target.offset());
            }
        });
        addInternalEventListener("mouseup", event -> {
            if (document == null) return;
            RichTextSelection selection = document.getRichTextSelection();
            if (draggingObject != null) {
                Element object = draggingObject;
                int target = dragTargetOffset;
                draggingObject = null;
                dragTargetOffset = -1;
                if (target >= 0) {
                    RichTextEditing.moveObject(this, object, target);
                }
                selection.setSelecting(false);
                return;
            }
            selection.setSelecting(false);
        });
        addInternalEventListener("blur", event -> {
            if (document == null) return;
            document.getRichTextSelection().setSelecting(false);
            draggingObject = null;
            dragTargetOffset = -1;
            if (!Objects.equals(focusValueSnapshot, getInnerHTML())) {
                dispatchChangeEvent();
                focusValueSnapshot = getInnerHTML();
            }
        });
        addInternalEventListener("contextmenu", event -> {
            if (!(event instanceof MouseEvent mouse) || document == null) return;
            // 右键定位：点击位置不在当前选区时，光标移到右键处（浏览器语义）
            Element hit = event.target instanceof Element targetElement ? targetElement : this;
            SelectionUnits.UnitOffset target = TextSelection.resolveUnitOffset(hit, mouse.clientX, mouse.clientY);
            if (target != null) {
                RichTextSelection selection = document.getRichTextSelection();
                int[] range = selection.localRangeForUnit(target.unit());
                boolean inside = range != null && target.offset() >= range[0] && target.offset() <= range[1];
                if (!inside) {
                    selection.setCollapsed(target.unit(), target.offset());
                }
            }
            showContextMenu(mouse);
            event.preventDefault();
        });
        addInternalEventListener("focus", event -> focusValueSnapshot = getInnerHTML());
    }

    // ------------------------------------------------------------------
    // 右键上下文菜单（复制/剪切/粘贴/全选）
    // ------------------------------------------------------------------

    private void showContextMenu(MouseEvent mouse) {
        if (document == null) return;
        RichTextSelection selection = document.getRichTextSelection();
        boolean hasSelection = selection != null && selection.isActive();
        boolean hasClipboard = Operation.getInternalClipboardHtml() != null
                || (Operation.getClipboardText() != null && !Operation.getClipboardText().isEmpty());
        java.util.List<ContextMenu.Item> items = new ArrayList<>();
        items.add(ContextMenu.Item.header("TEXT"));
        ContextMenu.Item cut = ContextMenu.Item.action("Cut", ContextMenu.Icons.DELETE, "Ctrl+X", this::cutSelection);
        ContextMenu.Item copy = ContextMenu.Item.action("Copy", ContextMenu.Icons.COPY, "Ctrl+C", this::copySelection);
        ContextMenu.Item paste = ContextMenu.Item.action("Paste", ContextMenu.Icons.REFRESH, "Ctrl+V", this::pasteClipboard);
        if (!hasSelection) {
            cut = cut.disabled();
            copy = copy.disabled();
        }
        if (!hasClipboard) {
            paste = paste.disabled();
        }
        items.add(cut);
        items.add(copy);
        items.add(paste);
        items.add(ContextMenu.Item.separator());
        items.add(ContextMenu.Item.action("Select All", ContextMenu.Icons.EDIT, "Ctrl+A", this::selectAllContent));
        ContextMenu.show(document, new Position(mouse.clientX, mouse.clientY), items);
    }

    private void cutSelection() {
        copySelection();
        RichTextEditing.deleteSelection(this);
    }

    private void copySelection() {
        if (document == null) return;
        RichTextSelection selection = document.getRichTextSelection();
        if (selection == null || !selection.isActive()) return;
        RichTextRange range = selection.toRange();
        Operation.setClipboardText(selection.getSelectedText());
        Operation.setInternalClipboardHtml(range == null ? null : range.toHtml());
    }

    private void pasteClipboard() {
        if (document == null) return;
        String html = Operation.getInternalClipboardHtml();
        if (html != null) {
            RichTextEditing.pasteHtml(this, html);
        } else {
            RichTextEditing.pasteText(this, Operation.getClipboardText());
        }
    }

    private void selectAllContent() {
        if (document == null) return;
        document.getRichTextSelection().selectAllInRoot();
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    /** 是否可编辑：无 readonly 属性时允许编辑（readonly 下仍可选择/聚焦）。 */
    public boolean canEditText() {
        String ce = getAttribute("contenteditable");
        if (ce != null && "false".equalsIgnoreCase(ce.trim())) return false;
        return !hasAttribute("readonly");
    }

    // ------------------------------------------------------------------
    // 历史（操作日志）
    // ------------------------------------------------------------------

    /** 变换完成后记录操作（连续 insertText 合并为一条 undo 记录；新编辑清空 redo 栈）。 */
    public void pushUndo(RichTextOperation operation) {
        if (operation == null) return;
        RichTextOperation top = undoStack.peek();
        if (top != null && operation.mergeableWith(top)) {
            undoStack.push(operation.merge(top));
        } else {
            undoStack.push(operation);
        }
        while (undoStack.size() > MAX_UNDO_STACK) undoStack.removeLast();
        redoStack.clear();
    }

    /** 撤销：弹栈顶操作并应用其逆操作（光标由 applyOperation 恢复）。 */
    public boolean undoInternal() {
        if (undoStack.isEmpty()) return false;
        RichTextOperation operation = undoStack.pop();
        redoStack.push(operation);
        RichTextEditing.applyOperation(this, operation.inverse());
        RichTextRange.normalize(this);
        markHistoryDirty();
        return true;
    }

    /** 重做：弹 redo 栈顶并重放。 */
    public boolean redoInternal() {
        if (redoStack.isEmpty()) return false;
        RichTextOperation operation = redoStack.pop();
        undoStack.push(operation);
        RichTextEditing.applyOperation(this, operation);
        RichTextRange.normalize(this);
        markHistoryDirty();
        return true;
    }

    private void markHistoryDirty() {
        getRenderer().text.clear();
        if (document != null) {
            document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT);
        }
    }

    /** 首次创建时向文档注册 UA 级 content 排版样式（每文档一次，幂等）。 */
    private static void ensureContentStyles(Document document) {
        if (document == null) return;
        if (!REGISTERED_CONTENT_STYLE_DOCUMENTS.add(document.getUuid().toString())) return;
        document.registerUaStylesheet(CONTENT_CSS, "<richtext-content>");
    }

    private void dispatchChangeEvent() {
        if (document == null) return;
        Event event = new Event(this, "change", true);
        Event.markTrustedFromCurrentDispatch(event);
        Event.tiggerEvent(event);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        super.drawPhase(poseStack, phase);
        if (phase != Base.RenderPhase.BODY || document == null) return;
        RichTextSelection selection = document.getRichTextSelection();
        if (selection == null) return;

        // 原子对象选中框（选区覆盖对象的哨兵位置）
        if (selection.isActive() && selection.getAnchorUnit() != null
                && rootOf(selection.getAnchorUnit()) == this) {
            drawObjectSelectionFrames(poseStack, selection);
        }
        // 文本光标（对象选中态或非聚焦时不画）
        if (!Element.isElementFocusing(this)) return;
        if (!selection.hasAnchor() || !selection.collapsed()) return;
        Element unit = selection.getAnchorUnit();
        if (unit == null || rootOf(unit) != this) return;

        RichTextNavigation.Caret caret = RichTextNavigation.caretPosition(unit, selection.getAnchorOffset());
        Text text = Text.of(unit == this ? this : unit);
        Graph.drawCursor(poseStack.last().pose(), (float) caret.x(), (float) caret.y(),
                (float) Math.max(caret.lineHeight(), Size.DEFAULT_LINE_HEIGHT),
                Text.getFontColor(unit), lastBlinkTime);
    }

    /** 指针是否落在某个原子对象（img/hr）的盒子内；命中返回对象元素，否则 null。 */
    private static Element hitAtomicObject(Element current, double x, double y) {
        if (current == null) return null;
        for (Node child : current.getRenderChildNodes()) {
            if (!(child instanceof Element childElement)) continue;
            if (SelectionUnits.isAtomicObject(childElement)) {
                Element.DOMRect rect = childElement.getBoundingClientRect();
                if (rect != null && rect.width > 0 && rect.height > 0
                        && x >= rect.x && x <= rect.x + rect.width
                        && y >= rect.y && y <= rect.y + rect.height) {
                    return childElement;
                }
                continue;
            }
            if (childElement instanceof com.sighs.apricityui.element.AbstractText) continue;
            Element hit = hitAtomicObject(childElement, x, y);
            if (hit != null) return hit;
        }
        return null;
    }

    /** 收集单元内的全部原子对象（DOM 序）。 */
    private static List<Element> collectAtomicObjects(Element current) {
        List<Element> objects = new ArrayList<>();
        collectAtomicObjectsRecursive(current, objects);
        return objects;
    }

    private static void collectAtomicObjectsRecursive(Element current, List<Element> out) {
        for (Node child : current.getRenderChildNodes()) {
            if (!(child instanceof Element childElement)) continue;
            if (SelectionUnits.isAtomicObject(childElement)) {
                out.add(childElement);
                continue;
            }
            if (childElement instanceof com.sighs.apricityui.element.AbstractText) continue;
            collectAtomicObjectsRecursive(childElement, out);
        }
    }

    private void drawObjectSelectionFrames(PoseStack poseStack, RichTextSelection selection) {
        int[] range = selection.localRangeForUnit(this);
        if (range == null) return;
        for (Element object : collectAtomicObjects(this)) {
            Element block = blockOf(object);
            int objectStart = SelectionUnits.baseOffsetOfDescendant(block, object);
            if (objectStart < range[0] || objectStart >= range[1]) continue;
            Element.DOMRect rect = object.getBoundingClientRect();
            if (rect == null || rect.width <= 0 || rect.height <= 0) continue;
            drawSelectionFrame(poseStack, rect);
        }
    }

    /** 原子对象所在的块单元（对象父链中最近的块级元素；无块时为 [contenteditable] 自身）。 */
    private static Element blockOf(Element object) {
        Element root = rootOf(object);
        for (Element e = object.parentElement; e != null; e = e.parentElement) {
            if (e == root) break;
            if (SelectionUnits.isSelectionUnit(e) && e != root) return e;
        }
        return root;
    }

    private static Element rootOf(Element element) {
        for (Element e = element; e != null; e = e.parentElement) {
            if (e instanceof RichText) return e;
        }
        return null;
    }

    /** 画对象选中框：蓝色细边框 + 四角手柄。 */
    private void drawSelectionFrame(PoseStack poseStack, Element.DOMRect rect) {
        int color = 0xFF1E90FF;
        float x0 = (float) rect.x - 1;
        float y0 = (float) rect.y - 1;
        float x1 = (float) (rect.x + rect.width) + 1;
        float y1 = (float) (rect.y + rect.height) + 1;
        Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y0 + 1, color);
        Graph.drawFillRect(poseStack.last().pose(), x0, y1 - 1, x1, y1, color);
        Graph.drawFillRect(poseStack.last().pose(), x0, y0, x0 + 1, y1, color);
        Graph.drawFillRect(poseStack.last().pose(), x1 - 1, y0, x1, y1, color);
        float handle = 3f;
        float[][] corners = {{x0, y0}, {x1 - handle, y0}, {x0, y1 - handle}, {x1 - handle, y1 - handle}};
        for (float[] corner : corners) {
            Graph.drawFillRect(poseStack.last().pose(), corner[0], corner[1], corner[0] + handle, corner[1] + handle, color);
        }
    }
}
