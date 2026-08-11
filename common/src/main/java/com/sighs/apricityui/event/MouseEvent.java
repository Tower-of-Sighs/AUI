package com.sighs.apricityui.event;

import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.init.*;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.render.GeometryQueryScope;
import com.sighs.apricityui.style.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Interaction;

// 鼠标事件，现在还没有做得很完善
public class MouseEvent extends Event implements Cloneable {
    public static final int DOM_DELTA_PIXEL = 0;
    public static final int PRIMARY_POINTER_ID = 1;
    private static final long DOUBLE_CLICK_WINDOW_NS = 500_000_000L;
    private NativeDispatchState nativeDispatchState = new NativeDispatchState();
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

    /** 浏览器名称别名：JS 里 e.ctrlKey 访问 controlKey。 */
    public boolean getCtrlKey() {
        return controlKey;
    }
    public double deltaX = 0;
    public double deltaY = 0;
    public int deltaMode = DOM_DELTA_PIXEL;
    public double scrollDelta = 0;
    public int button = -1;
    public int buttons = 0;
    public int pointerId = PRIMARY_POINTER_ID;
    public String pointerType = "mouse";
    public boolean isPrimary = true;
    public int clickCount = 0;
    /** 由 triggerResolvedEvent 把 mousemove/mouseup 重派发到按下元素（指针已移到其他元素）时置位。 */
    public boolean activeElementRedirect = false;

    public MouseEvent(String type, Position mousePosition) {
        this(type, mousePosition, -1);
    }

    public MouseEvent(String type, Position mousePosition, int button) {
        this(type, mousePosition, button, true);
    }

    public MouseEvent(String type, Position mousePosition, int button, boolean readEnvironmentState) {
        super(null, type, true);
        if (mousePosition == null) {
            mousePosition = Position.ZERO;
        }
        clientX = mousePosition.x;
        clientY = mousePosition.y;
        pageX = clientX;
        pageY = clientY;
        if (readEnvironmentState) {
            altKey = isModifierPressed("key.keyboard.left.alt") || isModifierPressed("key.keyboard.right.alt");
            shiftKey = isModifierPressed("key.keyboard.left.shift") || isModifierPressed("key.keyboard.right.shift");
            controlKey = isModifierPressed("key.keyboard.left.control") || isModifierPressed("key.keyboard.right.control");
            this.buttons = resolveButtons();
        } else {
            altKey = false;
            shiftKey = false;
            controlKey = false;
            this.buttons = 0;
        }
        this.button = button;
    }

    /** Prevents the originating Minecraft input event after AUI dispatch completes. */
    public void consumeNative() {
        nativeDispatchState.consumed = true;
    }

    public boolean isNativeConsumed() {
        return nativeDispatchState.consumed;
    }

    public static boolean tiggerEvent(MouseEvent event) {
        try (GeometryQueryScope geometryScope = GeometryQueryScope.open()) {
            Cursor.refreshFromDocuments(new Position(event.clientX, event.clientY));
            List<Document> docs = DocumentLayerOrder.frontToBack(Document.getAll());
            if (docs == null || docs.isEmpty()) return false;

            for (Document document : docs) {
                if (document == null || document.inWorld || document.isManuallyRendered()) continue;
                boolean passThroughWheel = "wheel".equals(event.type) && !document.interceptsMouseEvents();
                MouseEvent documentEvent = passThroughWheel ? event.clone() : event;
                boolean consumed = tiggerEvent(documentEvent, document);
                if (documentEvent.isNativeConsumed()) {
                    return true;
                }
                if (consumed && !passThroughWheel) {
                    return true;
                }
                if (document.interceptsMouseEventsAt(new Position(event.clientX, event.clientY))) {
                    return true;
                }
            }
            return false;
        }
    }

    // 触发鼠标事件的主体
    public static boolean tiggerEvent(MouseEvent event, Document document) {
        try (GeometryQueryScope geometryScope = GeometryQueryScope.open()) {
            try (Document.ContextScope ignored = Document.withContext(document)) {
            double originalClientX = event == null ? 0 : event.clientX;
            double originalClientY = event == null ? 0 : event.clientY;
            event = adaptToDocumentViewport(event, document);
            Element activeElement = document.getPressedElement();
            Position detectionPos = new Position(event.clientX, event.clientY);
            Element target = document.hitTest(detectionPos);
            boolean consumed = triggerResolvedEvent(event, document, target, activeElement, true);
            if (document.interceptsMouseEventsAt(new Position(originalClientX, originalClientY))) {
                event.consumeNative();
            }
            return consumed;
            }
        }
    }

    private static MouseEvent adaptToDocumentViewport(MouseEvent event, Document document) {
        if (event == null || document == null) return event;
        if (Math.abs(document.getViewportScaleX() - 1.0d) < 0.000001d
                && Math.abs(document.getViewportScaleY() - 1.0d) < 0.000001d) {
            return event;
        }

        MouseEvent adapted = event.clone();
        Position documentPosition = document.screenToDocumentPosition(new Position(event.clientX, event.clientY));
        adapted.clientX = documentPosition.x;
        adapted.clientY = documentPosition.y;
        adapted.pageX = documentPosition.x;
        adapted.pageY = documentPosition.y;
        adapted.movementX = event.movementX / document.getViewportScaleX();
        adapted.movementY = event.movementY / document.getViewportScaleY();
        adapted.deltaX = event.deltaX / document.getViewportScaleX();
        adapted.deltaY = event.deltaY / document.getViewportScaleY();
        adapted.scrollDelta = event.scrollDelta / document.getViewportScaleY();
        return adapted;
    }

    public static boolean dispatchToTarget(MouseEvent event, Document document, Element target) {
        try (GeometryQueryScope geometryScope = GeometryQueryScope.open()) {
            try (Document.ContextScope ignored = Document.withContext(document)) {
            return triggerResolvedEvent(event, document, target, document == null ? null : document.getPressedElement(), false);
            }
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
        // 其他文档的选择全部清除；当前文档的选择由文本选择监听器按命中规则处理
        // （可选中 → 折叠/扩展，不可选或输入控件 → 清空）。未命中任何元素时直接清空。
        for (Document doc : Document.getAll()) {
            if (doc == null || doc == activeDoc) continue;
            doc.clearAllTextSelections();
        }
        if (clickedTarget == null && activeDoc != null) {
            activeDoc.clearAllTextSelections();
        }
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
                dispatchPointerCompatEvent(out, element, true);

                MouseEvent leave = originalEvent.clone();
                leave.type = "mouseleave";
                leave.target = element;
                Event.triggerSingle(leave);
                dispatchPointerCompatEvent(leave, element, true);
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
                dispatchPointerCompatEvent(over, element, true);

                MouseEvent enter = originalEvent.clone();
                enter.type = "mouseenter";
                enter.target = element;
                Event.triggerSingle(enter);
                dispatchPointerCompatEvent(enter, element, true);
            }
        }
        document.setPreviousCursorElement(newTarget);
    }

    // 触发滚动，印象中是有个单独事件的，但是目前也并在鼠标事件里，以后要单独做出来。
    private static void scroll(MouseEvent event) {
        Element target = resolveScrollTarget(event);
        if (target == null) return;

        if (event.shiftKey) {
            if (target.canScrollHorizontally()) {
                target.setScrollLeft(target.getTargetScrollLeft() + event.scrollDelta);
            } else {
                target.setScrollTop(target.getTargetScrollTop() + event.scrollDelta);
            }
        } else {
            target.setScrollTop(target.getTargetScrollTop() + event.scrollDelta);
        }
        if (target.document != null) {
            // 滚动不改变层叠/节点关系，仅触发重绘。
            target.document.markDirty(target, Drawer.REPAINT);
        }
    }

    private static Element resolveScrollTarget(MouseEvent event) {
        if (event == null || !(event.target instanceof Element targetElement)) return null;
        ArrayList<Element> route = targetElement.getRoute();
        if (event.shiftKey) {
            Element horizontalEligible = null;
            Element verticalFallback = null;
            for (Element element : route) {
                if (element.hasHorizontalScrollRange()) return element;
                if (horizontalEligible == null && element.canScrollHorizontally()) {
                    horizontalEligible = element;
                }
                if (verticalFallback == null && element.hasVerticalScrollRange()) {
                    verticalFallback = element;
                }
            }
            if (horizontalEligible != null) return horizontalEligible;
            return verticalFallback;
        }

        Element eligible = null;
        for (Element element : route) {
            if (element.hasVerticalScrollRange()) return element;
            if (eligible == null && element.canScrollVertically()) {
                eligible = element;
            }
        }
        return eligible;
    }

    private static boolean applyScrollDefault(MouseEvent event) {
        Element target = resolveScrollTarget(event);
        if (target == null) return false;
        double beforeLeft = target.getTargetScrollLeft();
        double beforeTop = target.getTargetScrollTop();
        scroll(event);
        boolean changed = Double.compare(beforeLeft, target.getTargetScrollLeft()) != 0
                || Double.compare(beforeTop, target.getTargetScrollTop()) != 0;
        target.dispatchScrollEventIfChanged(beforeLeft, beforeTop);
        return changed;
    }

    private static boolean triggerResolvedEvent(MouseEvent event, Document document, Element target, Element activeElement, boolean resolveGeometry) {
        boolean consumed = false;

        if (resolveGeometry && target != null) {
            Position targetPosition = resolveHitBoxPosition(target);
            event.offsetX = event.clientX - targetPosition.x;
            event.offsetY = event.clientY - targetPosition.y;
        }

        event.target = target;

        if (event.type.equals("mousemove")) handleHoverChange(event, target, document);
        if (event.type.equals("mousedown") && target != null && target.handleScrollbarMouseDown(event)) {
            document.setPressedElement(target);
            return true;
        }
        if (event.type.equals("mousemove") && activeElement != null
                && activeElement.isScrollbarInteractionActive()
                && activeElement.handleScrollbarMouseMove(event)) {
            return true;
        }
        if (event.type.equals("mouseup") && activeElement != null
                && activeElement.isScrollbarInteractionActive()
                && activeElement.handleScrollbarMouseUp(event)) {
            document.setPressedElement(null);
            return true;
        }
        if (event.type.equals("mousedown")) {
            clearGlobalSelectionsOnMouseDown(document, target);
            event.clickCount = document.advanceClickSequence(target, event.button,
                    event.clientX, event.clientY, System.nanoTime(), DOUBLE_CLICK_WINDOW_NS);
            Event.runWithEventTrust(event, () -> {
                if (target != null) {
                    document.setPressedElement(target);
                    if (target.canFocus()) {
                        clearGlobalFocusExcept(document);
                        document.setFocusedElement(target);
                    } else {
                        document.setFocusedElement(null);
                    }
                }
            });
        }

        if (target != null) {
            consumed |= Event.tiggerEvent(event);
            consumed |= dispatchPointerCompatEvent(event, target, false);
        }

        if (target != null && event.type.equals("wheel") && !event.defaultPrevented) {
            AtomicBoolean scrollConsumed = new AtomicBoolean(false);
            Event.runWithEventTrust(event, () -> scrollConsumed.set(applyScrollDefault(event)));
            consumed |= scrollConsumed.get();
        }

        if ((event.type.equals("mousemove") || event.type.equals("mouseup")) && activeElement != null && activeElement != target) {
            MouseEvent activeEvent = event.clone();
            activeEvent.target = activeElement;
            activeEvent.activeElementRedirect = true;
            if (resolveGeometry) {
                Position activePosition = resolveHitBoxPosition(activeElement);
                activeEvent.offsetX = activeEvent.clientX - activePosition.x;
                activeEvent.offsetY = activeEvent.clientY - activePosition.y;
            }
            consumed |= Event.triggerSingle(activeEvent);
        }

        if (event.type.equals("mouseup")) {
            AtomicBoolean followupConsumed = new AtomicBoolean(false);
            Event.runWithEventTrust(event, () -> {
                followupConsumed.set(dispatchMouseUpFollowupEvents(document, event, target, activeElement));
                document.setPressedElement(null);
            });
            consumed |= followupConsumed.get();
        }

        return consumed;
    }

    private static boolean dispatchMouseUpFollowupEvents(Document document, MouseEvent originalEvent, Element target, Element activeElement) {
        if (document == null || target == null || activeElement == null) return false;
        Element activationTarget = nearestCommonInclusiveAncestor(activeElement, target);
        if (activationTarget == null
                || activeElement.isDisabled()
                || target.isDisabled()
                || activationTarget.isDisabled()) return false;

        boolean consumed = false;
        if (originalEvent.button == 0) {
            MouseEvent click = originalEvent.clone();
            click.type = "click";
            click.clickCount = document.getClickCount();
            click.target = activationTarget;
            click.cancelable = true;
            consumed |= Event.tiggerEvent(click);
            if (!click.defaultPrevented) {
                Element defaultActionTarget = activationTarget.resolveClickActivationTarget();
                if (defaultActionTarget != null && !defaultActionTarget.isDisabled()) {
                    defaultActionTarget.handleClickDefault();
                    consumed = true;
                }
            }

            if (document.registerClickAndCheckDoubleClick(activationTarget, originalEvent.button, System.nanoTime(), DOUBLE_CLICK_WINDOW_NS)) {
                MouseEvent dblclick = originalEvent.clone();
                dblclick.type = "dblclick";
                dblclick.clickCount = 2;
                dblclick.target = activationTarget;
                dblclick.cancelable = true;
                consumed |= Event.tiggerEvent(dblclick);
            }
        } else if (originalEvent.button == 1) {
            MouseEvent contextmenu = originalEvent.clone();
            contextmenu.type = "contextmenu";
            contextmenu.target = activationTarget;
            contextmenu.cancelable = true;
            consumed |= Event.tiggerEvent(contextmenu);
        }
        return consumed;
    }

    /**
     * UI Events defines click activation against the nearest common inclusive
     * ancestor of the press and release targets. A layout/paint update may make
     * the exact hit node change from a control to one of its descendants (or
     * between sibling descendants) without the pointer ever leaving the control.
     */
    private static Element nearestCommonInclusiveAncestor(Element first, Element second) {
        if (first == null || second == null) return null;
        for (Element candidate = first; candidate != null; candidate = candidate.parentElement) {
            if (candidate.contains(second)) return candidate;
        }
        return null;
    }

    private static boolean dispatchPointerCompatEvent(MouseEvent source, Element target, boolean singleTargetOnly) {
        if (source == null || target == null) return false;
        String compatType = switch (source.type) {
            case "mousedown" -> "pointerdown";
            case "mouseup" -> "pointerup";
            case "mousemove" -> "pointermove";
            case "mouseover" -> "pointerover";
            case "mouseout" -> "pointerout";
            case "mouseenter" -> "pointerenter";
            case "mouseleave" -> "pointerleave";
            default -> null;
        };
        if (compatType == null) return false;

        MouseEvent pointerEvent = source.clone();
        pointerEvent.type = compatType;
        pointerEvent.target = target;
        if ("pointerenter".equals(compatType) || "pointerleave".equals(compatType)) {
            pointerEvent.bubbles = false;
            singleTargetOnly = true;
        }
        return singleTargetOnly ? Event.triggerSingle(pointerEvent) : Event.tiggerEvent(pointerEvent);
    }

    // 肥简单的范围检查，看鼠标位置是否在某元素的范围内。
    public static boolean checkCursor(Element element, Position mousePos) {
        try (GeometryQueryScope geometryScope = GeometryQueryScope.open()) {
            return checkCursorInScope(element, mousePos);
        }
    }

    private static boolean checkCursorInScope(Element element, Position mousePos) {
        if (mousePos == null) return false;
        Position hitBoxPosition = resolveHitBoxPosition(element);
        Size hitBoxSize = resolveHitBoxSize(element);
        return (mousePos.x >= hitBoxPosition.x && mousePos.x <= hitBoxPosition.x + hitBoxSize.width()) &&
                (mousePos.y >= hitBoxPosition.y && mousePos.y <= hitBoxPosition.y + hitBoxSize.height());
    }

    private static Position resolveHitBoxPosition(Element element) {
        if (element == null) return Position.ZERO;
        Rect committed = element.getRenderer().getCommittedRect();
        if (committed != null) {
            if (usesBodyHitBox(element)) return committed.getBodyRectPosition();
            return new Position(
                    committed.position.x + committed.box.getMarginLeft(),
                    committed.position.y + committed.box.getMarginTop()
            );
        }
        if (usesBodyHitBox(element)) {
            return Rect.of(element).getBodyRectPosition();
        }

        Position position = Position.of(element);
        Box box = Box.of(element);
        return new Position(position.x + box.getMarginLeft(), position.y + box.getMarginTop());
    }

    private static Size resolveHitBoxSize(Element element) {
        if (element == null) return Size.ZERO;
        Rect committed = element.getRenderer().getCommittedRect();
        if (committed != null) {
            return usesBodyHitBox(element)
                    ? committed.getBodyRectSize()
                    : committed.getElementSize();
        }
        if (usesBodyHitBox(element)) {
            return Rect.of(element).getBodyRectSize();
        }
        return Size.of(element);
    }

    private static boolean usesBodyHitBox(Element element) {
        return element != null && "IMG".equals(element.tagName);
    }

    // 用于寻找鼠标事件的目标元素，也就是鼠标正对着的最上层元素，这块一般没啥问题。
    // 基本逻辑是把绘制队列倒序遍历，看最先命中哪个，写这么多主要是考虑到遮罩和style的影响。
    public static Element hitTest(List<RenderNode> paintOrder, Position cursorPosition) {
        try (GeometryQueryScope geometryScope = GeometryQueryScope.open()) {
            return hitTestInScope(paintOrder, cursorPosition);
        }
    }

    private static Element hitTestInScope(List<RenderNode> paintOrder, Position cursorPosition) {
        if (paintOrder == null || paintOrder.isEmpty()) return null;

        Stack<Element> clipStack = new Stack<>();

        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            RenderNode node = paintOrder.get(i);

            if (node instanceof RenderNode.MaskPopNode popNode) {
                clipStack.push(popNode.target());
            } else if (node instanceof RenderNode.MaskPushNode pushNode) {
                if (!clipStack.isEmpty() && clipStack.peek() == pushNode.target()) {
                    clipStack.pop();
                }
            } else if (node instanceof RenderNode.ElementPhaseNode phaseNode) {
                Element element = phaseNode.target();

                if (!Interaction.isDisplayed(element) || !element.isVisible || !element.isPointerEnabled) continue;

                if (checkCursorInScope(element, cursorPosition)) {
                    boolean isClipped = false;
                    for (Element mask : clipStack) {
                        if (!checkCursorInScope(mask, cursorPosition)) {
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
        MouseEvent copy = new MouseEvent(type, new Position(clientX, clientY), button, false);
        copyTo(copy);
        copy.clientX = clientX;
        copy.clientY = clientY;
        copy.pageX = pageX;
        copy.pageY = pageY;
        copy.offsetX = offsetX;
        copy.offsetY = offsetY;
        copy.movementX = movementX;
        copy.movementY = movementY;
        copy.altKey = altKey;
        copy.shiftKey = shiftKey;
        copy.controlKey = controlKey;
        copy.deltaX = deltaX;
        copy.deltaY = deltaY;
        copy.deltaMode = deltaMode;
        copy.scrollDelta = scrollDelta;
        copy.button = button;
        copy.buttons = buttons;
        copy.pointerId = pointerId;
        copy.pointerType = pointerType;
        copy.isPrimary = isPrimary;
        copy.clickCount = clickCount;
        copy.activeElementRedirect = activeElementRedirect;
        copy.nativeDispatchState = nativeDispatchState;
        return copy;
    }

    private static final class NativeDispatchState {
        private boolean consumed;
    }

    private static boolean isModifierPressed(String key) {
        try {
            return Operation.isKeyPressed(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int resolveButtons() {
        try {
            return Operation.getMouseButtons();
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
