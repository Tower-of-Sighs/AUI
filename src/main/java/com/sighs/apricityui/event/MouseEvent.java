package com.sighs.apricityui.event;

import com.sighs.apricityui.init.*;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

// 鼠标事件，现在还没有做得很完善
public class MouseEvent extends Event implements Cloneable {
    public double clientX;
    public double clientY;
    public double pageX = 0;
    public double pageY = 0;
    public double offsetX = 0;
    public double offsetY = 0;
    public double movementX = 0;
    public double movementY = 0;
    public boolean altKey;
    public boolean shiftKey;
    public boolean controlKey;
    public double scrollDelta = 0;
    public int button;

    public MouseEvent(String type, Position mousePosition) {
        this(type, mousePosition, -1);
    }

    public MouseEvent(String type, Position mousePosition, int button) {
        super(null, type, null, true);
        clientX = mousePosition.x;
        clientY = mousePosition.y;
        altKey = Operation.isKeyPressed("key.keyboard.left.alt") || Operation.isKeyPressed("key.keyboard.right.alt");
        shiftKey = Operation.isKeyPressed("key.keyboard.left.shift") || Operation.isKeyPressed("key.keyboard.right.shift");
        controlKey = Operation.isKeyPressed("key.keyboard.left.control") || Operation.isKeyPressed("key.keyboard.right.control");
        this.button = button;
    }

    public static boolean tiggerEvent(MouseEvent event) {
        StyleFrameCache.begin();
        try {
            boolean consumed = false;
            applyCursorForTopMostDocument(event);
            List<Document> docs = Document.getAll();
            if (docs.isEmpty()) return false;

            for (int i = docs.size() - 1; i >= 0; i--) {
                Document document = docs.get(i);
                if (document == null || document.inWorld) continue;
                Element target = hitTest(document.getPaintList(), new Position(event.clientX, event.clientY));
                if (target != null) {
                    consumed |= tiggerEvent(event, document);
                    if (target != document.body) {
                        return consumed;
                    }
                    continue;
                }
                consumed |= tiggerEvent(event, document);
            }
            return consumed;
        } finally {
            StyleFrameCache.end();
        }
    }

    private static void applyCursorForTopMostDocument(MouseEvent event) {
        List<Document> docs = Document.getAll();
        if (docs.isEmpty()) {
            Cursor.resetToDefault();
            return;
        }

        Position detectionPos = new Position(event.clientX, event.clientY);

        for (int i = docs.size() - 1; i >= 0; i--) {
            Document document = docs.get(i);
            if (document == null || document.inWorld) continue;

            Element target = hitTest(document.getPaintList(), detectionPos);
            if (target == null) continue;
            String cursor = resolveCursor(target);
            if (target == document.body && isDefaultCursor(cursor)) continue;

            Cursor.applyCssCursor(document.getPath(), cursor);
            return;
        }

        Cursor.resetToDefault();
    }

    private static boolean isDefaultCursor(String cursor) {
        if (cursor == null) return true;
        String value = cursor.trim();
        return value.isEmpty()
                || value.equalsIgnoreCase("auto")
                || value.equalsIgnoreCase("default")
                || value.equalsIgnoreCase("unset");
    }

    private static String resolveCursor(Element target) {
        if (target == null) return "default";

        String cache = target.getRenderer().cursor.get();
        if (cache != null) return cache;

        Element e = target;
        while (e != null) {
            String c = e.getComputedStyle().cursor;
            if (c != null) {
                c = c.trim();
                if (!c.isEmpty() && !c.equalsIgnoreCase("unset") && !c.equalsIgnoreCase("auto")) {
                    target.getRenderer().cursor.set(c);
                    return c;
                }
            }
            e = e.parentElement;
        }
        target.getRenderer().cursor.set("default");
        return "default";
    }

    // 触发鼠标事件的主体
    public static boolean tiggerEvent(MouseEvent event, Document document) {
        StyleFrameCache.begin();
        try {
            boolean consumed = false;
            List<RenderNode> paintList = document.getPaintList();
            Element activeElement = document.getActiveElement();
            Position detectionPos = new Position(event.clientX, event.clientY);
            Element target = hitTest(paintList, detectionPos);

            if (target != null) {
                Position targetPosition = Position.of(target);
                event.offsetX = event.clientX - targetPosition.x;
                event.offsetY = event.clientY - targetPosition.y;
            }

            event.target = target;

            if (event.type.equals("mousemove")) handleHoverChange(event, target, document);
            if (event.type.equals("mousedown")) {
                clearGlobalSelectionsOnMouseDown(document, target);
                if (target != null) {
                    document.setActiveElement(target);
                    if (target.canFocus()) {
                        clearGlobalFocusExcept(document);
                        document.setFocusedElement(target);
                    } else {
                        document.setFocusedElement(null);
                    }
                }
            }

            if (target != null && event.type.equals("scroll")) scroll(event);
            if (target != null) {
                consumed |= Event.tiggerEvent(event);
            }

            if ((event.type.equals("mousemove") || event.type.equals("mouseup")) && activeElement != null && activeElement != target) {
                MouseEvent activeEvent = event.clone();
                activeEvent.target = activeElement;
                Position activePosition = Position.of(activeElement);
                activeEvent.offsetX = activeEvent.clientX - activePosition.x;
                activeEvent.offsetY = activeEvent.clientY - activePosition.y;
                consumed |= Event.triggerSingle(activeEvent);
            }

            if (event.type.equals("mouseup")) {
                document.setActiveElement(null);
            }

            return consumed;
        } finally {
            StyleFrameCache.end();
        }
    }

    private static void clearGlobalFocusExcept(Document keepFocusDoc) {
        Document.getAll().forEach(doc -> {
            if (doc != keepFocusDoc) {
                doc.clearFocus();
            }
        });
    }

    // 其实是专门为hover写了这个部分，所以函数名就叫hover，实际上是处理各类鼠标事件的，这边是根据路径去做处理，不知道性能上能不能优化。
    private static void clearGlobalSelectionsOnMouseDown(Document activeDoc, Element clickedTarget) {
        Document.getAll().forEach(doc -> {
            if (doc == null) return;
            if (doc == activeDoc) {
                doc.clearAllTextSelectionsExcept(clickedTarget);
                return;
            }
            doc.clearAllTextSelections();
        });
    }

    private static void handleHoverChange(MouseEvent originalEvent, Element newTarget, Document document) {
        Element previousCursorElement = document.getPreviousCursorElement();
        if (previousCursorElement == newTarget) return;
        List<Element> oldChain = previousCursorElement != null ? previousCursorElement.getRoute() : Collections.emptyList();
        List<Element> newChain = newTarget != null ? newTarget.getRoute() : Collections.emptyList();

        for (Element element : oldChain) {
            if (!newChain.contains(element)) {
                element.setHover(false);

                MouseEvent out = originalEvent.clone();
                out.type = "mouseout";
                out.target = element;
                Event.triggerSingle(out);

                MouseEvent leave = originalEvent.clone();
                leave.type = "mouseleave";
                leave.target = element;
                Event.triggerSingle(leave);
            }
        }

        for (int i = newChain.size() - 1; i >= 0; i--) {
            Element element = newChain.get(i);
            element.setHover(true);

            if (!oldChain.contains(element)) {
                // 只有新进入的元素才触发事件
                MouseEvent over = originalEvent.clone();
                over.type = "mouseover";
                over.target = element;
                Event.triggerSingle(over);

                MouseEvent enter = originalEvent.clone();
                enter.type = "mouseenter";
                enter.target = element;
                Event.triggerSingle(enter);
            }
        }
        document.setPreviousCursorElement(newTarget);
    }

    // 触发滚动，印象中是有个单独事件的，但是目前也并在鼠标事件里，以后要单独做出来。
    private static void scroll(MouseEvent event) {
        Element target = null;
        ArrayList<Element> route = event.target.getRoute();
        for (Element element : route) {
            if (element.canScroll()) {
                target = element;
                break;
            }
        }
        if (target != null) {
            if (event.shiftKey && target.canScrollHorizontally()) {
                target.setScrollLeft(target.getTargetScrollLeft() + event.scrollDelta);
            } else {
                target.setScrollTop(target.getTargetScrollTop() + event.scrollDelta);
            }
            if (target.document != null) {
                // 滚动不改变层叠/节点关系，仅触发重绘。
                target.document.markDirty(target, Drawer.REPAINT);
            }
        }
    }

    // 肥简单的范围检查，看鼠标位置是否在某元素的范围内。
    public static boolean checkCursor(Element element, Position mousePos) {
        if (mousePos == null) return false;
        double absX = 0;
        double absY = 0;
        boolean first = true;
        Element current = element;
        while (current != null) {
            Position offset = Position.getOffset(current);
            absX += offset.x;
            absY += offset.y;
            if (!first) {
                absX -= current.getScrollLeft();
                absY -= current.getScrollTop();
            }
            if ("fixed".equals(current.getComputedStyle().position)) break;
            current = current.parentElement;
            first = false;
        }

        Size size = Size.of(element);
        Box box = Box.of(element);
        double elementX = absX + box.getMarginLeft();
        double elementY = absY + box.getMarginTop();
        return (mousePos.x >= elementX && mousePos.x <= elementX + size.width()) &&
                (mousePos.y >= elementY && mousePos.y <= elementY + size.height());
    }

    // 用于寻找鼠标事件的目标元素，也就是鼠标正对着的最上层元素，这块一般没啥问题。
    // 基本逻辑是把绘制队列倒序遍历，看最先命中哪个，写这么多主要是考虑到遮罩和style的影响。
    public static Element hitTest(List<RenderNode> paintOrder, Position cursorPosition) {
        if (paintOrder == null || paintOrder.isEmpty()) return null;

        Stack<Element> clipStack = new Stack<>();

        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            RenderNode node = paintOrder.get(i);

            if (node instanceof RenderNode.MaskPopNode(Element target1)) {
                clipStack.push(target1);
            } else if (node instanceof RenderNode.MaskPushNode(Element target1)) {
                if (!clipStack.isEmpty() && clipStack.peek() == target1) {
                    clipStack.pop();
                }
            } else if (node instanceof RenderNode.ElementPhaseNode phaseNode) {
                Element element = phaseNode.target();

                if (!element.isVisible || !element.isPointerEnabled) continue;

                if (checkCursor(element, cursorPosition)) {
                    boolean isClipped = false;
                    for (Element mask : clipStack) {
                        if (!checkCursor(mask, cursorPosition)) {
                            isClipped = true;
                            break;
                        }
                    }
                    if (!isClipped) {
                        return element;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public MouseEvent clone() {
        try {
            return (MouseEvent) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
