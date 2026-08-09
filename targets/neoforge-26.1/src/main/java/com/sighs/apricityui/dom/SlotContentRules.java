package com.sighs.apricityui.dom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Ingredient;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

import java.util.ArrayList;

/**
 * Slot、Item 与 Ingredient 的集中结构不变量。
 */
public final class SlotContentRules {
    private static boolean restoring;

    private SlotContentRules() {
    }

    /**
     * 连接到文档树的运行时插入前校验层级，并按需替换旧内容。
     */
    public static void validateRuntimeInsertion(Node parent, Node child) {
        if (parent instanceof Slot slot) {
            if (!(child instanceof Item) && !(child instanceof Ingredient)) throw hierarchy(parent, child);
            replaceSlotContent(slot, child);
            return;
        }
        if (parent instanceof Item) {
            if (!(child instanceof TextNode)) throw hierarchy(parent, child);
            return;
        }
        if (parent instanceof Ingredient ingredient) {
            if (!(child instanceof Item) && !(child instanceof TextNode)) throw hierarchy(parent, child);
            if (child instanceof Item) replaceControlledItem(ingredient, child);
        }
    }

    /**
     * 在模板刷新后清理非法内容并补齐必需节点。
     */
    public static void normalizeTemplate(Document document) {
        if (document == null) return;
        for (Element element : new ArrayList<>(document.getElements())) {
            if (element instanceof Slot slot) normalizeSlot(slot, document);
            else if (element instanceof Ingredient ingredient) normalizeIngredient(ingredient, document);
            else if (element instanceof Item item) normalizeItem(item, document);
        }
    }

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

    public static Item ensureControlledItem(Ingredient ingredient) {
        if (ingredient == null) return null;
        Item existing = findControlledItem(ingredient);
        if (existing != null) return existing;

        Item item = new Item(ingredient.document);
        item.setTextContent("minecraft:air");
        ingredient.appendChild(item);
        return item;
    }

    public static Element getSlotContent(Slot slot) {
        if (slot == null) return null;
        for (Node child : slot.childNodes) {
            if (child instanceof Item || child instanceof Ingredient) return (Element) child;
        }
        return null;
    }

    public static Item getDisplayItem(Slot slot) {
        Element content = getSlotContent(slot);
        if (content instanceof Item item) return item;
        if (content instanceof Ingredient ingredient) return findControlledItem(ingredient);
        return null;
    }

    /**
     * 片段插入会一次性附加多个节点；完成后收敛为允许的直接子节点集合。
     */
    public static void normalizeRuntimeChildren(Node parent) {
        if (parent instanceof Slot slot) normalizeSlot(slot, slot.document);
        else if (parent instanceof Ingredient ingredient) normalizeIngredient(ingredient, ingredient.document);
        else if (parent instanceof Item item) normalizeItem(item, item.document);
    }

    public static void restoreRequiredContent(Node parent) {
        if (restoring) return;
        try {
            restoring = true;
            if (parent instanceof Slot slot && getSlotContent(slot) == null) {
                ensureDirectItem(slot);
            } else if (parent instanceof Ingredient ingredient && findControlledItem(ingredient) == null) {
                ensureControlledItem(ingredient);
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
        if (keep == null) ensureDirectItem(slot);
    }

    private static void normalizeIngredient(Ingredient ingredient, Document document) {
        Item keep = findControlledItem(ingredient);
        if (keep == null) keep = ensureControlledItem(ingredient);

        boolean sourceTextPresent = false;
        for (Node child : new ArrayList<>(ingredient.childNodes)) {
            if (child == keep) continue;
            if (child instanceof TextNode textNode
                    && !sourceTextPresent
                    && !textNode.getTextContent().isBlank()) {
                sourceTextPresent = true;
                continue;
            }
            warn(document, ingredient, child);
            child.remove();
        }

        if (!sourceTextPresent
                && !keep.getTextContent().isBlank()
                && !"minecraft:air".equals(keep.getTextContent().trim())) {
            ingredient.innerText = keep.getTextContent();
            keep.setTextContent("minecraft:air");
        }
    }

    private static void normalizeItem(Item item, Document document) {
        boolean textFound = false;
        for (Node child : new ArrayList<>(item.childNodes)) {
            if (child instanceof TextNode && !textFound) {
                textFound = true;
                continue;
            }
            warn(document, item, child);
            child.remove();
        }
    }

    private static Item findControlledItem(Ingredient ingredient) {
        for (Node child : ingredient.childNodes) {
            if (child instanceof Item item) return item;
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

    private static void replaceControlledItem(Ingredient ingredient, Node incoming) {
        boolean previous = restoring;
        restoring = true;
        try {
            for (Node child : new ArrayList<>(ingredient.childNodes)) {
                if (child instanceof Item && child != incoming) child.remove();
            }
        } finally {
            restoring = previous;
        }
    }

    private static IllegalArgumentException hierarchy(Node parent, Node child) {
        String parentName = parent instanceof Element element ? element.tagName : parent.getNodeName();
        String childName = child instanceof Element element
                ? element.tagName
                : child == null ? "null" : child.getNodeName();
        return new IllegalArgumentException("HierarchyRequestError: " + parentName + " cannot contain " + childName);
    }

    private static void warn(Document document, Element parent, Node child) {
        ApricityUI.LOGGER.warn(
                "Discarded invalid Slot/Item/Ingredient template child, template={}, parent={}, child={}",
                document == null ? "" : document.getPath(),
                parent.tagName,
                child == null ? "null" : child.getNodeName()
        );
    }
}
