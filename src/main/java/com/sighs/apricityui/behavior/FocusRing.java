package com.sighs.apricityui.behavior;

import com.sighs.apricityui.element.AbstractText;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

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
            if (focusedElement instanceof AbstractText textElement) {
                textElement.clearSelection();
            } else {
                focusedElement.clearTextSelection();
            }
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
        for (Element element : owner.getElements()) {
            if (element instanceof AbstractText textElement) {
                if (textElement.hasSelection()) return true;
                continue;
            }
            if (element.hasInnerTextSelection()) return true;
        }
        return false;
    }

    public void clearAllTextSelections() {
        clearAllTextSelectionsExcept(null);
    }

    public void clearAllTextSelectionsExcept(Element keep) {
        for (Element element : owner.getElements()) {
            if (element == keep) continue;
            if (element instanceof AbstractText textElement) {
                if (textElement.hasSelection()) textElement.clearSelection();
                continue;
            }
            if (element.hasInnerTextSelection()) element.clearTextSelection();
        }
    }

    public void clearFocus() {
        setFocusedElement(null);
    }
}
