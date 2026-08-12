package com.sighs.apricityui.dom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.spi.AuiServices;

public final class ElementTree {
    private final Document owner;
    private final ArrayList<Node> nodes = new ArrayList<>();
    private final ArrayList<Element> elements = new ArrayList<>();
    private final HashMap<String, Element> idMap = new HashMap<>();

    public ElementTree(Document owner) {
        this.owner = owner;
    }

    public ArrayList<Element> getElements() {
        return elements;
    }

    public ArrayList<Node> getNodes() {
        return nodes;
    }

    public void clear() {
        nodes.clear();
        elements.clear();
        idMap.clear();
    }

    public void rebuildFromRoot(Element root) {
        nodes.clear();
        elements.clear();
        idMap.clear();
        if (root == null) return;

        root.parentNode = null;
        root.parentElement = null;
        root.depth = 0;

        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(root);

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

    public void updateElement(Element element) {
        int index = -1;
        for (Element e : elements) {
            if (e.uuid.equals(element.uuid)) index = elements.indexOf(e);
        }
        if (index == -1) return;
        elements.set(index, element);
    }

    public void createRelation(Node child, Node parent, boolean head) {
        if (child == null || parent == null) return;
        moveSubtree(child, parent, head ? 0 : parent.childNodes.size());
    }

    public void insertBefore(Node newChild, Node parent, Node referenceChild) {
        if (newChild == null || parent == null) return;
        int index = referenceChild == null ? parent.childNodes.size() : parent.childNodes.indexOf(referenceChild);
        if (index < 0) index = parent.childNodes.size();
        moveSubtree(newChild, parent, index);
    }

    public Node insertFragment(DocumentFragment fragment, Node parent, Node referenceChild) {
        if (fragment == null || parent == null || fragment.childNodes.isEmpty()) return null;
        int index = referenceChild == null ? parent.childNodes.size() : parent.childNodes.indexOf(referenceChild);
        if (index < 0) index = parent.childNodes.size();
        return moveFragmentChildren(fragment, parent, index);
    }

    public Node insertBeforeAndReturn(Node newChild, Node parent, Node referenceChild) {
        insertBefore(newChild, parent, referenceChild);
        return newChild;
    }

    public void replaceChild(Node parent, Node newChild, Node oldChild) {
        if (parent == null || newChild == null || oldChild == null) return;
        int index = parent.childNodes.indexOf(oldChild);
        if (index < 0) return;
        removeNode(oldChild);
        moveSubtree(newChild, parent, Math.min(index, parent.childNodes.size()));
    }

    public void removeNode(Node node) {
        if (node == null) return;
        detachSubtree(node);
    }

    public void clearChildren(Node parent) {
        if (parent == null || parent.childNodes.isEmpty()) return;
        // 整批清空子节点改变单元的渲染子节点集合，选择缓存随之失效
        if (owner != null) owner.bumpSelectionCache();
        if (owner != null) owner.markHitTestDirtyAll();
        ArrayList<Node> removedRoots = new ArrayList<>(parent.childNodes);
        Node previousSibling = null;
        Node nextSibling = null;
        parent.childNodes.clear();
        syncElementChildView(parent);

        ArrayList<Node> removedNodes = new ArrayList<>();
        ArrayList<Element> removedElements = new ArrayList<>();
        for (Node child : removedRoots) {
            List<Node> subtree = flattenSubtree(child);
            removedNodes.addAll(subtree);
            removedElements.addAll(flattenElements(subtree));
            child.parentNode = null;
            if (child instanceof Element element) {
                element.parentElement = null;
            }
        }

        nodes.removeAll(removedNodes);
        elements.removeAll(removedElements);
        for (Element element : removedElements) {
            element.onDisconnectedFromDocument();
            if (element.id != null && !element.id.isBlank()) {
                removeId(element.id, element);
            }
            element.getRenderer().route.clear();
        }
        clearRemovedFocusState(removedElements);

        if (parent instanceof Element parentElement) {
            clearTextCaches(parentElement);
            clearLayoutChain(parentElement);
            owner.markDirty(parentElement, Drawer.RELAYOUT | Drawer.REORDER);
        }
        AuiServices.expander().restoreRequiredContent(owner, parent);
        owner.queueMutation(Document.MutationRecord.childList(parent, List.of(), removedRoots, previousSibling, nextSibling));
    }

    public void removeId(String id, Element element) {
        if (id == null || id.isBlank()) return;
        Element current = idMap.get(id);
        if (current == element) {
            idMap.remove(id);
        }
    }

    public void recordId(Element element) {
        if (element == null || element.id == null || element.id.isBlank()) return;
        idMap.put(element.id, element);
    }

    public Element getElementById(String id) {
        return idMap.get(id);
    }

    private void moveSubtree(Node child, Node parent, int childIndex) {
        // 子树挂载改变单元的渲染子节点集合，选择缓存随之失效
        if (owner != null) owner.bumpSelectionCache();
        AuiServices.expander().validateRuntimeInsertion(owner, parent, child);
        detachSubtree(child);
        // Moving the only required Item within its existing Slot/Ingredient can
        // restore a placeholder during detach; validate once more before attach
        // so the moved node remains the sole direct content element.
        AuiServices.expander().validateRuntimeInsertion(owner, parent, child);

        int safeIndex = Math.max(0, Math.min(childIndex, parent.childNodes.size()));
        parent.childNodes.add(safeIndex, child);
        syncElementChildView(parent);
        Node previousSibling = safeIndex > 0 ? parent.childNodes.get(safeIndex - 1) : null;
        Node nextSibling = safeIndex + 1 < parent.childNodes.size() ? parent.childNodes.get(safeIndex + 1) : null;

        updateSubtree(child, parent, parent.depth + 1, owner);
        if (child instanceof Element childElement) {
            childElement.syncDomStateAfterAttach();
            childElement.invalidateSubtreeAfterAttach();
            childElement.invalidateStyle();
            owner.markDirty(childElement, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        }

        int insertIndex = safeIndex == 0 ? nodes.indexOf(parent) + 1 : findSubtreeEndExclusive(parent.childNodes.get(safeIndex - 1));
        List<Node> subtreeNodes = flattenSubtree(child);
        nodes.addAll(insertIndex, subtreeNodes);
        elements.addAll(resolveInsertIndexForElements(insertIndex), flattenElements(subtreeNodes));

        if (parent instanceof Element parentElement) {
            clearTextCaches(parentElement);
            clearLayoutChain(parentElement);
            owner.markDirty(parentElement, Drawer.RELAYOUT | Drawer.REORDER);
        }
        owner.queueMutation(Document.MutationRecord.childList(parent, List.of(child), List.of(), previousSibling, nextSibling));
    }

    private Node moveFragmentChildren(DocumentFragment fragment, Node parent, int childIndex) {
        ArrayList<Node> roots = new ArrayList<>(fragment.childNodes);
        if (roots.isEmpty()) return null;
        // 片段挂载改变单元的渲染子节点集合，选择缓存随之失效
        if (owner != null) owner.bumpSelectionCache();
        for (Node child : roots) {
            AuiServices.expander().validateRuntimeInsertion(owner, parent, child);
        }

        int safeIndex = Math.max(0, Math.min(childIndex, parent.childNodes.size()));
        Node previousSibling = safeIndex > 0 ? parent.childNodes.get(safeIndex - 1) : null;
        Node nextSibling = safeIndex < parent.childNodes.size() ? parent.childNodes.get(safeIndex) : null;
        fragment.childNodes.clear();

        parent.childNodes.addAll(safeIndex, roots);
        syncElementChildView(parent);
        if (owner != null) owner.markHitTestDirtyAll();

        ArrayList<Node> insertedNodes = new ArrayList<>();
        ArrayList<Element> insertedElements = new ArrayList<>();
        for (Node child : roots) {
            updateSubtree(child, parent, parent.depth + 1, owner);
            if (child instanceof Element childElement) {
                childElement.syncDomStateAfterAttach();
                childElement.invalidateSubtreeAfterAttach();
                childElement.invalidateStyle();
                owner.markDirty(childElement, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
            }
            List<Node> subtreeNodes = flattenSubtree(child);
            insertedNodes.addAll(subtreeNodes);
            insertedElements.addAll(flattenElements(subtreeNodes));
        }

        int insertIndex = safeIndex == 0 ? nodes.indexOf(parent) + 1 : findSubtreeEndExclusive(parent.childNodes.get(safeIndex - 1));
        nodes.addAll(insertIndex, insertedNodes);
        elements.addAll(resolveInsertIndexForElements(insertIndex), insertedElements);

        if (parent instanceof Element parentElement) {
            clearTextCaches(parentElement);
            clearLayoutChain(parentElement);
            owner.markDirty(parentElement, Drawer.RELAYOUT | Drawer.REORDER);
        }
        AuiServices.expander().normalizeRuntimeChildren(owner, parent);
        owner.queueMutation(Document.MutationRecord.childList(parent, roots, List.of(), previousSibling, nextSibling));
        return roots.get(roots.size() - 1);
    }

    private void detachSubtree(Node node) {
        if (owner != null) owner.bumpSelectionCache();
        Node oldParent = node.parentNode;
        if (oldParent != null) {
            int index = oldParent.childNodes.indexOf(node);
            Node previousSibling = index > 0 ? oldParent.childNodes.get(index - 1) : null;
            Node nextSibling = index >= 0 && index + 1 < oldParent.childNodes.size() ? oldParent.childNodes.get(index + 1) : null;
            oldParent.childNodes.removeIf(candidate -> node.uuid.equals(candidate.uuid));
            syncElementChildView(oldParent);
            if (oldParent instanceof Element oldParentElement) {
                clearTextCaches(oldParentElement);
                clearLayoutChain(oldParentElement);
                owner.markDirty(oldParentElement, Drawer.RELAYOUT | Drawer.REORDER);
            }
            owner.queueMutation(Document.MutationRecord.childList(oldParent, List.of(), List.of(node), previousSibling, nextSibling));
        }

        List<Node> subtree = flattenSubtree(node);
        nodes.removeAll(subtree);
        List<Element> subtreeElements = flattenElements(subtree);
        elements.removeAll(subtreeElements);
        for (Element element : subtreeElements) {
            element.onDisconnectedFromDocument();
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
        if (oldParent != null) {
            AuiServices.expander().restoreRequiredContent(owner, oldParent);
        }
    }

    private static void clearTextCaches(Element element) {
        if (element == null) return;
        element.getRenderer().text.clear();
        element.getRenderer().wrappedText.clear();
        element.getRenderer().size.clear();
    }

    private static void clearLayoutChain(Element element) {
        Element current = element;
        while (current != null) {
            current.getRenderer().size.clear();
            current.getRenderer().box.clear();
            current.getRenderer().position.clear();
            current = current.parentElement;
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
        ArrayList<Element> elementChildren = new ArrayList<>();
        for (Node child : element.childNodes) {
            if (child instanceof Element childElement) {
                childElement.parentElement = element;
                elementChildren.add(childElement);
            }
        }
        element.children = elementChildren;
        element.syncSelectStateAfterChildrenChanged();
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
