package com.sighs.apricityui.dom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.GenericStackElement;
import com.sighs.apricityui.element.Ingredient;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemStackType;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

import java.util.ArrayList;

public final class SlotContentRules {
    private static boolean restoring;

    private SlotContentRules() {
    }

    public static void validateRuntimeInsertion(Node parent, Node child) {
        if (parent instanceof Slot slot) {
            if (!(child instanceof GenericStackElement)) throw hierarchy(parent, child);
            replaceSlotContent(slot, child);
        } else if (parent instanceof GenericStackElement && !(child instanceof TextNode)) {
            throw hierarchy(parent, child);
        }
    }

    public static void normalizeTemplate(Document document) {
        if (document == null) return;
        for (Element element : new ArrayList<>(document.getElements())) normalizeRuntimeChildren(element);
    }

    public static Item ensureDirectItem(Slot slot) {
        if (slot == null) return null;
        for (Node child : slot.childNodes) {
            if (child instanceof Item item) return item;
        }
        Item item = new Item(slot.document, GenericStackTypes.require(ItemStackType.class));
        item.setTextContent("minecraft:air");
        slot.appendChild(item);
        return item;
    }

    public static Element getSlotContent(Slot slot) {
        if (slot == null) return null;
        for (Node child : slot.childNodes) {
            if (child instanceof GenericStackElement) return (Element) child;
        }
        return null;
    }

    public static MinecraftElement getDisplayElement(Slot slot) {
        Element content = getSlotContent(slot);
        return content instanceof MinecraftElement resource ? resource : null;
    }

    public static void normalizeRuntimeChildren(Node parent) {
        if (parent instanceof Slot slot) {
            Element keep = getSlotContent(slot);
            boolean previous = restoring;
            restoring = true;
            try {
                for (Node child : new ArrayList<>(slot.childNodes)) {
                    if (child == keep) continue;
                    warn(slot.document, slot, child);
                    child.remove();
                }
                if (keep == null) ensureDirectItem(slot);
            } finally {
                restoring = previous;
            }
        } else if (parent instanceof GenericStackElement resource) {
            for (Node child : new ArrayList<>(resource.childNodes)) {
                if (child instanceof TextNode) continue;
                warn(resource.document, resource, child);
                child.remove();
            }
        }
    }

    public static void restoreRequiredContent(Node parent) {
        if (restoring) return;
        try {
            restoring = true;
            if (parent instanceof Slot slot && getSlotContent(slot) == null) ensureDirectItem(slot);
        } finally {
            restoring = false;
        }
    }

    private static void replaceSlotContent(Slot slot, Node incoming) {
        boolean previous = restoring;
        restoring = true;
        try {
            for (Node child : new ArrayList<>(slot.childNodes)) {
                if (child != incoming) child.remove();
            }
        } finally {
            restoring = previous;
        }
    }

    private static IllegalArgumentException hierarchy(Node parent, Node child) {
        String parentName = parent instanceof Element element ? element.tagName : parent.getNodeName();
        String childName = child instanceof Element element
                ? element.tagName : child == null ? "null" : child.getNodeName();
        return new IllegalArgumentException("HierarchyRequestError: " + parentName + " cannot contain " + childName);
    }

    private static void warn(Document document, Element parent, Node child) {
        ApricityUI.LOGGER.warn("Discarded invalid resource template child, template={}, parent={}, child={}",
                document == null ? "" : document.getPath(), parent.tagName,
                child == null ? "null" : child.getNodeName());
    }
}
