package com.sighs.apricityui.init;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class ElementTree {
    private final Document owner;
    private final ArrayList<Node> nodes = new ArrayList<>();
    private final ArrayList<Element> elements = new ArrayList<>();
    private final HashMap<String, Element> idMap = new HashMap<>();

    ElementTree(Document owner) {
        this.owner = owner;
    }

    ArrayList<Element> getElements() {
        return elements;
    }

    ArrayList<Node> getNodes() {
        return nodes;
    }

    void clear() {
        nodes.clear();
        elements.clear();
        idMap.clear();
    }

    void rebuildFromBody() {
        nodes.clear();
        elements.clear();
        idMap.clear();
        if (owner.body == null) return;

        owner.body.parentNode = null;
        owner.body.parentElement = null;
        owner.body.depth = 0;

        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(owner.body);

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            nodes.add(current);
            if (current instanceof Element element) {
                syncElementChildView(element);
                elements.add(element);
                element.runInitFromDomOnce(element);
                if (element.id != null && !element.id.isBlank()) {
                    idMap.put(element.id, element);
                }
            }

            List<Node> children = current.childNodes;
            for (int i = children.size() - 1; i >= 0; i--) {
                Node child = children.get(i);
                if (child == null) continue;
                child.parentNode = current;
                child.depth = current.depth + 1;
                if (child instanceof Element childElement) {
                    childElement.parentElement = current instanceof Element parentElement ? parentElement : null;
                }
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

    void createRelation(Node child, Node parent, boolean head) {
        if (child == null || parent == null) return;
        moveSubtree(child, parent, head ? 0 : parent.childNodes.size());
    }

    void insertBefore(Node newChild, Node parent, Node referenceChild) {
        if (newChild == null || parent == null) return;
        int index = referenceChild == null ? parent.childNodes.size() : parent.childNodes.indexOf(referenceChild);
        if (index < 0) index = parent.childNodes.size();
        moveSubtree(newChild, parent, index);
    }

    void replaceChild(Node parent, Node newChild, Node oldChild) {
        if (parent == null || newChild == null || oldChild == null) return;
        int index = parent.childNodes.indexOf(oldChild);
        if (index < 0) return;
        removeNode(oldChild);
        moveSubtree(newChild, parent, Math.min(index, parent.childNodes.size()));
    }

    void removeNode(Node node) {
        if (node == null) return;
        detachSubtree(node);
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

    private void moveSubtree(Node child, Node parent, int childIndex) {
        detachSubtree(child);

        int safeIndex = Math.max(0, Math.min(childIndex, parent.childNodes.size()));
        parent.childNodes.add(safeIndex, child);
        syncElementChildView(parent);
        Node previousSibling = safeIndex > 0 ? parent.childNodes.get(safeIndex - 1) : null;
        Node nextSibling = safeIndex + 1 < parent.childNodes.size() ? parent.childNodes.get(safeIndex + 1) : null;

        updateSubtree(child, parent, parent.depth + 1, owner);
        if (child instanceof Element childElement) {
            childElement.syncDomStateAfterAttach();
        }

        int insertIndex = safeIndex == 0 ? nodes.indexOf(parent) + 1 : findSubtreeEndExclusive(parent.childNodes.get(safeIndex - 1));
        List<Node> subtreeNodes = flattenSubtree(child);
        nodes.addAll(insertIndex, subtreeNodes);
        elements.addAll(resolveInsertIndexForElements(insertIndex), flattenElements(subtreeNodes));

        if (child instanceof Element childElement) {
            childElement.invalidateStyle();
            childElement.getRenderer().size.clear();
        }
        if (parent instanceof Element parentElement) {
            owner.markDirty(parentElement, Drawer.RELAYOUT | Drawer.REORDER);
        }
        owner.queueMutation(Document.MutationRecord.childList(parent, List.of(child), List.of(), previousSibling, nextSibling));
    }

    private void detachSubtree(Node node) {
        Node oldParent = node.parentNode;
        if (oldParent != null) {
            int index = oldParent.childNodes.indexOf(node);
            Node previousSibling = index > 0 ? oldParent.childNodes.get(index - 1) : null;
            Node nextSibling = index >= 0 && index + 1 < oldParent.childNodes.size() ? oldParent.childNodes.get(index + 1) : null;
            oldParent.childNodes.removeIf(candidate -> node.uuid.equals(candidate.uuid));
            syncElementChildView(oldParent);
            if (oldParent instanceof Element oldParentElement) {
                owner.markDirty(oldParentElement, Drawer.RELAYOUT | Drawer.REORDER);
            }
            owner.queueMutation(Document.MutationRecord.childList(oldParent, List.of(), List.of(node), previousSibling, nextSibling));
        }

        List<Node> subtree = flattenSubtree(node);
        nodes.removeAll(subtree);
        List<Element> subtreeElements = flattenElements(subtree);
        elements.removeAll(subtreeElements);
        for (Element element : subtreeElements) {
            if (element.id != null && !element.id.isBlank()) {
                removeId(element.id, element);
            }
            element.getRenderer().route.clear();
        }
        clearRemovedFocusState(subtreeElements);
        node.parentNode = null;
        if (node instanceof Element element) {
            element.parentElement = null;
        }
    }

    private void updateSubtree(Node root, Node parent, int depth, Document document) {
        root.parentNode = parent;
        root.depth = depth;
        root.document = document;
        if (root instanceof Element element) {
            element.parentElement = parent instanceof Element parentElement ? parentElement : null;
            syncElementChildView(element);
            element.getRenderer().route.clear();
            if (element.id != null && !element.id.isBlank()) {
                idMap.put(element.id, element);
            }
        }
        for (Node child : root.childNodes) {
            updateSubtree(child, root, depth + 1, document);
        }
    }

    private List<Node> flattenSubtree(Node root) {
        ArrayList<Node> subtree = new ArrayList<>();
        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node current = stack.pop();
            subtree.add(current);
            List<Node> children = current.childNodes;
            for (int i = children.size() - 1; i >= 0; i--) {
                Node child = children.get(i);
                if (child != null) stack.push(child);
            }
        }
        return subtree;
    }

    private int findSubtreeEndExclusive(Node node) {
        int start = nodes.indexOf(node);
        if (start < 0) return nodes.size();
        int end = start + 1;
        while (end < nodes.size() && nodes.get(end).depth > node.depth) {
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

    private void syncElementChildView(Node node) {
        if (!(node instanceof Element element)) return;
        CopyOnWriteArrayList<Element> elementChildren = new CopyOnWriteArrayList<>();
        for (Node child : element.childNodes) {
            if (child instanceof Element childElement) {
                childElement.parentElement = element;
                elementChildren.add(childElement);
            }
        }
        element.children = elementChildren;
    }

    private List<Element> flattenElements(List<Node> source) {
        ArrayList<Element> out = new ArrayList<>();
        for (Node node : source) {
            if (node instanceof Element element) out.add(element);
        }
        return out;
    }

    private int resolveInsertIndexForElements(int nodeInsertIndex) {
        int elementIndex = 0;
        for (int i = 0; i < Math.min(nodeInsertIndex, nodes.size()); i++) {
            if (nodes.get(i) instanceof Element) elementIndex++;
        }
        return elementIndex;
    }
}
