package com.sighs.apricityui.dom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Ingredient;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.element.Stack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

import java.util.ArrayList;

/** Central DOM invariants for Slot, Stack and Ingredient. */
public final class SlotContentRules {
    private static boolean restoring;

    private SlotContentRules() {
    }

    public static void validateRuntimeInsertion(Node parent, Node child) {
        if (parent instanceof Slot slot) {
            if (!(child instanceof Stack) && !(child instanceof Ingredient)) throw hierarchy(parent, child);
            replaceSlotContent(slot, child);
            return;
        }
        if (parent instanceof Stack) {
            if (!(child instanceof TextNode)) throw hierarchy(parent, child);
            return;
        }
        if (parent instanceof Ingredient ingredient) {
            if (!(child instanceof Stack) && !(child instanceof TextNode)) throw hierarchy(parent, child);
            if (child instanceof Stack) replaceControlledStack(ingredient, child);
        }
    }

    public static void normalizeTemplate(Document document) {
        if (document == null) return;
        for (Element element : new ArrayList<>(document.getElements())) {
            if (element instanceof Slot slot) normalizeSlot(slot, document);
            else if (element instanceof Ingredient ingredient) normalizeIngredient(ingredient, document);
            else if (element instanceof Stack stack) normalizeStack(stack, document);
        }
    }

    public static Stack ensureDirectStack(Slot slot) {
        if (slot == null) return null;
        for (Node child : slot.childNodes) {
            if (child instanceof Stack stack) return stack;
        }
        Stack stack = new Stack(slot.document);
        stack.setTextContent("minecraft:air");
        slot.appendChild(stack);
        return stack;
    }

    /** Compatibility helper for callers that explicitly require an Item view. */
    public static Item ensureDirectItem(Slot slot) {
        if (slot == null) return null;
        for (Node child : slot.childNodes) {
            if (child instanceof Item item) return item;
        }
        Item item = new Item(slot.document);
        item.setTextContent("minecraft:air");
        slot.appendChild(item);
        return item;
    }

    public static Stack ensureControlledStack(Ingredient ingredient) {
        if (ingredient == null) return null;
        Stack existing = findControlledStack(ingredient);
        if (existing != null) return existing;
        Stack stack = new Stack(ingredient.document);
        stack.setTextContent("minecraft:air");
        ingredient.appendChild(stack);
        return stack;
    }

    public static Element getSlotContent(Slot slot) {
        if (slot == null) return null;
        for (Node child : slot.childNodes) {
            if (child instanceof Stack || child instanceof Ingredient) return (Element) child;
        }
        return null;
    }

    public static Stack getDisplayStack(Slot slot) {
        Element content = getSlotContent(slot);
        if (content instanceof Stack stack) return stack;
        if (content instanceof Ingredient ingredient) return findControlledStack(ingredient);
        return null;
    }

    public static Item getDisplayItem(Slot slot) {
        Stack stack = getDisplayStack(slot);
        return stack instanceof Item item ? item : null;
    }

    public static void normalizeRuntimeChildren(Node parent) {
        if (parent instanceof Slot slot) normalizeSlot(slot, slot.document);
        else if (parent instanceof Ingredient ingredient) normalizeIngredient(ingredient, ingredient.document);
        else if (parent instanceof Stack stack) normalizeStack(stack, stack.document);
    }

    public static void restoreRequiredContent(Node parent) {
        if (restoring) return;
        try {
            restoring = true;
            if (parent instanceof Slot slot && getSlotContent(slot) == null) {
                ensureDirectStack(slot);
            } else if (parent instanceof Ingredient ingredient && findControlledStack(ingredient) == null) {
                ensureControlledStack(ingredient);
            }
        } finally {
            restoring = false;
        }
    }

    private static void normalizeSlot(Slot slot, Document document) {
        Element keep = getSlotContent(slot);
        for (Node child : new ArrayList<>(slot.childNodes)) {
            if (child == keep) continue;
            warn(document, slot, child);
            child.remove();
        }
        if (keep == null) ensureDirectStack(slot);
    }

    private static void normalizeIngredient(Ingredient ingredient, Document document) {
        Stack keep = findControlledStack(ingredient);
        if (keep == null) keep = ensureControlledStack(ingredient);
        boolean sourceTextPresent = false;
        for (Node child : new ArrayList<>(ingredient.childNodes)) {
            if (child == keep) continue;
            if (child instanceof TextNode textNode && !sourceTextPresent && !textNode.getTextContent().isBlank()) {
                sourceTextPresent = true;
                continue;
            }
            warn(document, ingredient, child);
            child.remove();
        }
        if (!sourceTextPresent && !keep.getTextContent().isBlank()
                && !"minecraft:air".equals(keep.getTextContent().trim())) {
            ingredient.innerText = keep.getTextContent();
            keep.setTextContent("minecraft:air");
        }
    }

    private static void normalizeStack(Stack stack, Document document) {
        boolean textFound = false;
        for (Node child : new ArrayList<>(stack.childNodes)) {
            if (child instanceof TextNode && !textFound) {
                textFound = true;
                continue;
            }
            warn(document, stack, child);
            child.remove();
        }
    }

    private static Stack findControlledStack(Ingredient ingredient) {
        for (Node child : ingredient.childNodes) {
            if (child instanceof Stack stack) return stack;
        }
        return null;
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

    private static void replaceControlledStack(Ingredient ingredient, Node incoming) {
        boolean previous = restoring;
        restoring = true;
        try {
            for (Node child : new ArrayList<>(ingredient.childNodes)) {
                if (child instanceof Stack && child != incoming) child.remove();
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
        ApricityUI.LOGGER.warn("Discarded invalid Slot/Stack/Ingredient template child, template={}, parent={}, child={}",
                document == null ? "" : document.getPath(), parent.tagName,
                child == null ? "null" : child.getNodeName());
    }
}
