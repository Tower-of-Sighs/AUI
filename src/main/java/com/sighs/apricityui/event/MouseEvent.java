package com.sighs.apricityui.event;

import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.init.*;
import com.sighs.apricityui.init.Operation;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class MouseEvent extends Event implements Cloneable {
    public double clientX = 0;
    public double clientY = 0;
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

    public MouseEvent(String type, Position mousePosition) {
        super(null, type, null, true);
        clientX = mousePosition.x;
        clientY = mousePosition.y;
        altKey = Operation.isKeyPressed("key.keyboard.left.alt") || Operation.isKeyPressed("key.keyboard.right.alt");
        shiftKey = Operation.isKeyPressed("key.keyboard.left.shift") || Operation.isKeyPressed("key.keyboard.right.shift");
        controlKey = Operation.isKeyPressed("key.keyboard.left.control") || Operation.isKeyPressed("key.keyboard.right.control");
    }

    public static void tiggerEvent(MouseEvent event) {
        Document.getAll().forEach(document -> {
            if (!document.inWorld) tiggerEvent(event, document);
        });
    }

    public static void tiggerEvent(MouseEvent event, Document document) {
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

        if (event.type.equals("mousemove")) {
            handleHoverChange(event, target, document);
        }
        if (event.type.equals("mousedown")) {
            if (target != null) {
                document.setActiveElement(target);
                if (target.canFocus()) {
                    clearGlobalFocusExcept(document);
                    document.setFocusedElement(target);
                } else {
                    // 点击了不能 Focus 的地方（比如背景），通常是清除当前 Document 的焦点
                    document.setFocusedElement(null);
                }
            }
        }

        if (target != null && event.type.equals("scroll")) {
            scroll(event);
        }
        if (target != null) {
            Event.tiggerEvent(event);
        }

        if ((event.type.equals("mousemove") || event.type.equals("mouseup")) && activeElement != null && activeElement != target) {
            MouseEvent activeEvent = event.clone();
            activeEvent.target = activeElement;
            Position activePosition = Position.of(activeElement);
            activeEvent.offsetX = activeEvent.clientX - activePosition.x;
            activeEvent.offsetY = activeEvent.clientY - activePosition.y;
            Event.triggerSingle(activeEvent);
        }

        if (event.type.equals("mouseup")) {
            document.setActiveElement(null);
        }
    }

    private static void clearGlobalFocusExcept(Document keepFocusDoc) {
        Document.getAll().forEach(doc -> {
            if (doc != keepFocusDoc) {
                doc.clearFocus();
            }
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
            if (event.shiftKey) target.setScrollLeft(target.scrollLeft + event.scrollDelta);
            else target.setScrollTop(target.scrollTop + event.scrollDelta);
            if(target.children != null) {
                target.children.forEach(e -> e.getRenderer().position.clear());
            }
        }
    }

    public static boolean checkCursor(Element element, Position mousePos) {
        if (mousePos == null) return false;
        Position elementPos = Position.of(element);
        Size size = Size.of(element);
        Box box = Box.of(element);
        double elementX = elementPos.x + box.getMarginLeft();
        double elementY = elementPos.y + box.getMarginTop();
        return (mousePos.x >= elementX && mousePos.x <= elementX + size.width()) &&
                (mousePos.y >= elementY && mousePos.y <= elementY + size.height());
    }

    public static Element hitTest(List<RenderNode> paintOrder, Position cursorPosition) {
        if (paintOrder == null || paintOrder.isEmpty()) return null;

        Stack<Element> clipStack = new Stack<>();

        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            RenderNode node = paintOrder.get(i);

            if (node instanceof RenderNode.MaskPopNode popNode) {
                clipStack.push(popNode.target());
            }
            else if (node instanceof RenderNode.MaskPushNode pushNode) {
                if (!clipStack.isEmpty() && clipStack.peek() == pushNode.target()) {
                    clipStack.pop();
                }
            }
            else if (node instanceof RenderNode.ElementPhaseNode phaseNode) {
                Element element = phaseNode.target();
                boolean slotNode = element instanceof Slot;
                if (phaseNode.phase() != Base.RenderPhase.BODY && !slotNode) continue;

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
