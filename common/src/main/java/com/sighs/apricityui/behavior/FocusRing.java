package com.sighs.apricityui.behavior;

import com.sighs.apricityui.element.AbstractText;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.behavior.DocumentSelection;
import com.sighs.apricityui.behavior.SelectionUnits;

public final class FocusRing {
    private final Document owner;
    private Element previousCursorElement = null;
    private Element activeElement = null;
    private Element focusedElement = null;

    public FocusRing(Document owner) {
        this.owner = owner;
    }

    public Element getPreviousCursorElement() {
        return previousCursorElement;
    }

    public void setPreviousCursorElement(Element element) {
        previousCursorElement = element;
    }

    public Element getPressedElement() {
        return activeElement;
    }

    public void setPressedElement(Element element) {
        if (activeElement == element) return;

        List<Element> oldChain = activeElement != null ? activeElement.getRoute() : Collections.emptyList();
        List<Element> newChain = element != null ? element.getRoute() : Collections.emptyList();

        Set<Element> oldSet = Collections.newSetFromMap(new IdentityHashMap<>());
        oldSet.addAll(oldChain);

        Set<Element> newSet = Collections.newSetFromMap(new IdentityHashMap<>());
        newSet.addAll(newChain);

        for (Element e : oldChain) {
            if (!newSet.contains(e)) {
                e.setActive(false);
            }
        }

        for (Element e : newChain) {
            if (!oldSet.contains(e)) {
                e.setActive(true);
            }
        }

        activeElement = element;
    }

    public Element getFocusedElement() {
        return focusedElement;
    }

    public void setFocusedElement(Element element) {
        if (focusedElement != null && focusedElement != element) {
            Element previous = focusedElement;
            // 失焦不再清空文本选择：非可编辑文本的选择已统一为文档级单选区，
            // 由鼠标按下/键盘快捷键自行管理（Esc 仍可清空）。
            previous.setFocus(false);
            dispatchFocusEvent(previous, "blur");
        }

        focusedElement = element;

        if (element != null) {
            element.setFocus(true);
            dispatchFocusEvent(element, "focus");
        }
    }

    private static void dispatchFocusEvent(Element element, String type) {
        if (element == null || type == null || type.isBlank()) return;
        Event event = new Event(element, type, null, false);
        event.bubbles = false;
        Event.markTrustedFromCurrentDispatch(event);
        Event.triggerSingle(event);
    }

    public boolean hasAnyTextSelection() {
        if (owner.getDocumentSelection().isActive()) return true;
        for (Element element : owner.getElements()) {
            if (element instanceof AbstractText textElement && textElement.hasSelection()) return true;
        }
        return false;
    }

    public void clearAllTextSelections() {
        clearAllTextSelectionsExcept(null);
    }

    public void clearAllTextSelectionsExcept(Element keep) {
        DocumentSelection selection = owner.getDocumentSelection();
        Element keepUnit = keep == null ? null : SelectionUnits.resolveUnit(keep);
        // 文档级单选区：仅当整个选区都落在 keep 所在单元内时保留
        if (keepUnit == null
                || selection.getAnchorUnit() != keepUnit
                || selection.getEndUnit() != keepUnit) {
            selection.clear();
        }
        com.sighs.apricityui.behavior.richtext.RichTextSelection rich = owner.getRichTextSelection();
        if (!rich.isActive() || keepUnit == null || !rich.coversUnit(keepUnit)) {
            rich.clear();
        }
        for (Element element : owner.getElements()) {
            if (element == keep) continue;
            if (element instanceof AbstractText textElement && textElement.hasSelection()) {
                textElement.clearSelection();
            }
        }
    }

    public void clearFocus() {
        setFocusedElement(null);
    }
}
