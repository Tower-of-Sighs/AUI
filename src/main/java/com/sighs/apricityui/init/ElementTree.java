package com.sighs.apricityui.init;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

final class ElementTree {
    private final Document owner;
    private final ArrayList<Element> elements = new ArrayList<>();
    private final HashMap<String, Element> idMap = new HashMap<>();

    ElementTree(Document owner) {
        this.owner = owner;
    }

    ArrayList<Element> getElements() {
        return elements;
    }

    void clear() {
        elements.clear();
        idMap.clear();
    }

    void rebuildFromBody() {
        elements.clear();
        idMap.clear();
        if (owner.body == null) return;

        owner.body.parentElement = null;
        owner.body.depth = 0;

        ArrayDeque<Element> stack = new ArrayDeque<>();
        stack.push(owner.body);

        while (!stack.isEmpty()) {
            Element current = stack.pop();
            elements.add(current);

            current.runInitFromDomOnce(current);
            if (current.id != null && !current.id.isBlank()) {
                idMap.put(current.id, current);
            }

            List<Element> children = current.children;
            for (int i = children.size() - 1; i >= 0; i--) {
                Element child = children.get(i);
                if (child == null) continue;
                child.parentElement = current;
                child.depth = current.depth + 1;
                stack.push(child);
            }
        }
    }

    void updateElement(Element element) {
        int index = -1;
        for (Element e : elements) {
            if (e.uuid.equals(element.uuid)) index = elements.indexOf(e);
        }
        if (index == -1) return;
        elements.set(index, element);
    }

    void createRelation(Element child, Element parent, boolean head) {
        if (child == null || parent == null) return;
        moveSubtree(child, parent, head ? 0 : parent.children.size());
    }

    void insertBefore(Element newChild, Element parent, Element referenceChild) {
        if (newChild == null || parent == null) return;
        int index = referenceChild == null ? parent.children.size() : parent.children.indexOf(referenceChild);
        if (index < 0) index = parent.children.size();
        moveSubtree(newChild, parent, index);
    }

    void replaceChild(Element parent, Element newChild, Element oldChild) {
        if (parent == null || newChild == null || oldChild == null) return;
        int index = parent.children.indexOf(oldChild);
        if (index < 0) return;
        removeElement(oldChild);
        moveSubtree(newChild, parent, Math.min(index, parent.children.size()));
    }

    void removeElement(Element element) {
        if (element == null) return;
        detachSubtree(element);
    }

    void removeId(String id, Element element) {
        if (id == null || id.isBlank()) return;
        Element current = idMap.get(id);
        if (current == element) {
            idMap.remove(id);
        }
    }

    void recordId(Element element) {
        if (element == null || element.id == null || element.id.isBlank()) return;
        idMap.put(element.id, element);
    }

    Element getElementById(String id) {
        return idMap.get(id);
    }

    private void moveSubtree(Element child, Element parent, int childIndex) {
        detachSubtree(child);

        int safeIndex = Math.max(0, Math.min(childIndex, parent.children.size()));
        parent.children.add(safeIndex, child);

        updateSubtree(child, parent, parent.getDepth() + 1, owner);
        child.syncDomStateAfterAttach();

        int insertIndex = safeIndex == 0 ? elements.indexOf(parent) + 1 : findSubtreeEndExclusive(parent.children.get(safeIndex - 1));
        List<Element> subtree = flattenSubtree(child);
        elements.addAll(insertIndex, subtree);

        child.invalidateStyle();
        child.getRenderer().size.clear();
        owner.markDirty(parent, Drawer.RELAYOUT | Drawer.REORDER);
    }

    private void detachSubtree(Element element) {
        Element oldParent = element.parentElement;
        if (oldParent != null) {
            oldParent.children.removeIf(e -> element.uuid.equals(e.uuid));
            owner.markDirty(oldParent, Drawer.RELAYOUT | Drawer.REORDER);
        }

        List<Element> subtree = flattenSubtree(element);
        elements.removeAll(subtree);
        for (Element node : subtree) {
            if (node.id != null && !node.id.isBlank()) {
                removeId(node.id, node);
            }
            node.getRenderer().route.clear();
        }
        clearRemovedFocusState(subtree);
        element.parentElement = null;
    }

    private void updateSubtree(Element root, Element parent, int depth, Document document) {
        root.parentElement = parent;
        root.depth = depth;
        root.document = document;
        root.getRenderer().route.clear();
        if (root.id != null && !root.id.isBlank()) {
            idMap.put(root.id, root);
        }
        for (Element child : root.children) {
            updateSubtree(child, root, depth + 1, document);
        }
    }

    private List<Element> flattenSubtree(Element root) {
        ArrayList<Element> subtree = new ArrayList<>();
        ArrayDeque<Element> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Element current = stack.pop();
            subtree.add(current);
            List<Element> children = current.children;
            for (int i = children.size() - 1; i >= 0; i--) {
                Element child = children.get(i);
                if (child != null) stack.push(child);
            }
        }
        return subtree;
    }

    private int findSubtreeEndExclusive(Element element) {
        int start = elements.indexOf(element);
        if (start < 0) return elements.size();
        int end = start + 1;
        while (end < elements.size() && elements.get(end).depth > element.depth) {
            end++;
        }
        return end;
    }

    private void clearRemovedFocusState(List<Element> subtree) {
        Element focused = owner.getFocusedElement();
        if (focused != null && subtree.contains(focused)) {
            owner.clearFocus();
        }
        Element active = owner.getPressedElement();
        if (active != null && subtree.contains(active)) {
            owner.setPressedElement(null);
        }
        Element previousCursor = owner.getPreviousCursorElement();
        if (previousCursor != null && subtree.contains(previousCursor)) {
            owner.setPreviousCursorElement(null);
        }
    }
}
