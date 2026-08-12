package com.sighs.apricityui.init;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.EventRegistry;
import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.dom.DocumentFragment;
import com.sighs.apricityui.dom.TextNode;

public abstract class Node {
    public static final short ELEMENT_NODE = 1;
    public static final short TEXT_NODE = 3;
    public static final short COMMENT_NODE = 8;
    public static final short DOCUMENT_FRAGMENT_NODE = 11;

    public UUID uuid = UUID.randomUUID();
    public Document document;
    public Node parentNode = null;
    public final ArrayList<Node> childNodes = new ArrayList<>();
    public int depth = 0;
    private long subtreeMutationVersion = 1L;

    private final EventRegistry events = new EventRegistry(this);
    public CopyOnWriteArrayList<Event.ListenerRecord> EventListener = events.listeners();

    protected Node(Document document) {
        this.document = document;
    }

    public Document getOwnerDocument() {
        return document;
    }

    public Node getParentNode() {
        return parentNode;
    }

    /** JS 侧 parentElement 属性的 JavaBean 映射:父元素(无父或父非元素时 null)。 */
    public Element getParentElement() {
        return parentNode instanceof Element parent ? parent : null;
    }

    public List<Node> getChildNodes() {
        return Collections.unmodifiableList(childNodes);
    }

    public long getSubtreeMutationVersion() {
        return subtreeMutationVersion;
    }

    public void invalidateSubtreeMutationVersion() {
        for (Node current = this; current != null; current = current.parentNode) {
            current.subtreeMutationVersion++;
        }
    }

    public Node getFirstChild() {
        return childNodes.isEmpty() ? null : childNodes.get(0);
    }

    public Node getLastChild() {
        return childNodes.isEmpty() ? null : childNodes.get(childNodes.size() - 1);
    }

    public boolean hasChildNodes() {
        return !childNodes.isEmpty();
    }

    public Node getNextSibling() {
        if (parentNode == null) return null;
        int index = parentNode.childNodes.indexOf(this);
        if (index < 0 || index + 1 >= parentNode.childNodes.size()) return null;
        return parentNode.childNodes.get(index + 1);
    }

    public Node getPreviousSibling() {
        if (parentNode == null) return null;
        int index = parentNode.childNodes.indexOf(this);
        if (index <= 0) return null;
        return parentNode.childNodes.get(index - 1);
    }

    public ArrayList<Node> getRouteNodes() {
        ArrayList<Node> result = new ArrayList<>();
        Node current = this;
        while (current != null) {
            result.add(current);
            current = current.parentNode;
        }
        return result;
    }

    public boolean contains(Node node) {
        if (node == null) return false;
        Node current = node;
        while (current != null) {
            if (current == this) return true;
            current = current.parentNode;
        }
        return false;
    }

    public boolean isConnected() {
        Node current = this;
        while (current != null) {
            if (current.parentNode == null) {
                if (!(current instanceof Element root) || root.document == null) return false;
                return root.document.documentElement == root || root.document.body == root;
            }
            current = current.parentNode;
        }
        return false;
    }

    public Node appendChild(Node node) {
        if (document == null || node == null) return null;
        if (node instanceof DocumentFragment fragment) {
            if (isConnected()) {
                return document.getTree().insertFragment(fragment, this, null);
            }
            Node last = null;
            ArrayList<Node> snapshot = new ArrayList<>(fragment.childNodes);
            for (Node child : snapshot) {
                last = appendSingleChild(prepareForInsertion(child));
            }
            return last;
        }
        return appendSingleChild(prepareForInsertion(node));
    }

    public Node removeChild(Node node) {
        if (document == null || node == null || node.parentNode != this) return null;
        document.removeNode(node);
        return node;
    }

    public void clearChildren() {
        if (document == null || childNodes.isEmpty()) return;
        if (isConnected()) {
            document.getTree().clearChildren(this);
            return;
        }
        ArrayList<Node> snapshot = new ArrayList<>(childNodes);
        for (Node child : snapshot) {
            detachLocalChild(child);
        }
    }

    public Node insertBefore(Node newNode, Node referenceNode) {
        if (document == null || newNode == null) return null;
        if (newNode instanceof DocumentFragment fragment) {
            if (isConnected()) {
                return document.getTree().insertFragment(fragment, this, referenceNode);
            }
            Node last = null;
            ArrayList<Node> snapshot = new ArrayList<>(fragment.childNodes);
            for (Node child : snapshot) {
                last = insertSingleChildBefore(prepareForInsertion(child), referenceNode);
            }
            return last;
        }
        return insertSingleChildBefore(prepareForInsertion(newNode), referenceNode);
    }

    public Node replaceChild(Node newNode, Node oldNode) {
        if (document == null || newNode == null || oldNode == null || oldNode.parentNode != this) return null;
        if (newNode instanceof DocumentFragment fragment) {
            Node nextSibling = oldNode.getNextSibling();
            document.removeNode(oldNode);
            if (isConnected()) {
                document.getTree().insertFragment(fragment, this, nextSibling);
                return oldNode;
            }
            ArrayList<Node> snapshot = new ArrayList<>(fragment.childNodes);
            for (Node child : snapshot) {
                insertSingleChildBefore(prepareForInsertion(child), nextSibling);
            }
            return oldNode;
        }
        Node inserted = prepareForInsertion(newNode);
        if (isConnected()) {
            document.getTree().replaceChild(this, inserted, oldNode);
        } else {
            int index = childNodes.indexOf(oldNode);
            if (index < 0) return null;
            detachLocalChild(oldNode);
            attachLocalChild(inserted, index);
        }
        return oldNode;
    }

    private Node appendSingleChild(Node node) {
        if (node == null) return null;
        if (isConnected()) {
            document.createRelation(node, this, false);
        } else {
            attachLocalChild(node, childNodes.size());
        }
        return node;
    }

    private Node insertSingleChildBefore(Node node, Node referenceNode) {
        if (node == null) return null;
        if (isConnected()) {
            document.getTree().insertBefore(node, this, referenceNode);
        } else {
            int index = referenceNode == null ? childNodes.size() : childNodes.indexOf(referenceNode);
            if (index < 0) index = childNodes.size();
            attachLocalChild(node, index);
        }
        return node;
    }

    private void attachLocalChild(Node node, int index) {
        if (node == null) return;
        // 断开状态下插入也会改变单元的渲染子节点集合，选择缓存随之失效
        if (document != null) document.bumpSelectionCache();
        detachLocalChild(node);
        int safeIndex = Math.max(0, Math.min(index, childNodes.size()));
        childNodes.add(safeIndex, node);
        node.parentNode = this;
        node.document = document;
        node.depth = depth + 1;
        if (this instanceof Element parentElement) {
            if (node instanceof Element childElement) {
                childElement.parentElement = parentElement;
                childElement.syncDomStateAfterAttach();
            }
            parentElement.refreshElementChildrenFromChildNodes();
        } else if (node instanceof Element childElement) {
            childElement.parentElement = null;
            childElement.syncDomStateAfterAttach();
        }
    }

    private static void detachLocalChild(Node node) {
        if (node == null) return;
        Node oldParent = node.parentNode;
        if (oldParent == null) return;
        // 断开状态下移除也会改变单元的渲染子节点集合，选择缓存随之失效
        if (oldParent.document != null) oldParent.document.bumpSelectionCache();
        oldParent.childNodes.remove(node);
        if (oldParent instanceof Element oldParentElement) {
            oldParentElement.refreshElementChildrenFromChildNodes();
        }
        node.parentNode = null;
        if (node instanceof Element childElement) {
            childElement.parentElement = null;
        }
    }

    private Node prepareForInsertion(Node node) {
        if (node instanceof Element element) {
            return Element.init(element);
        }
        return node;
    }

    public Node cloneNode() {
        return cloneNode(false);
    }

    public Node cloneNode(boolean deep) {
        if (this instanceof TextNode textNode) {
            return new TextNode(document, textNode.getTextContent());
        }
        if (this instanceof CommentNode commentNode) {
            return new CommentNode(document, commentNode.getTextContent());
        }
        if (this instanceof DocumentFragment fragment) {
            DocumentFragment copy = new DocumentFragment(document);
            if (deep) {
                for (Node child : fragment.childNodes) {
                    copy.appendChild(child.cloneNode(true));
                }
            }
            return copy;
        }
        if (this instanceof Element element) {
            return element.cloneNode(deep);
        }
        return null;
    }

    public void before(Node node) {
        if (parentNode == null || node == null) return;
        parentNode.insertBefore(node, this);
    }

    public void after(Node node) {
        if (parentNode == null || node == null) return;
        parentNode.insertBefore(node, getNextSibling());
    }

    public void replaceWith(Node node) {
        if (parentNode == null || node == null) return;
        parentNode.replaceChild(node, this);
    }

    public void remove() {
        if (document == null) return;
        document.removeNode(this);
    }

    public boolean dispatchEvent(Object event) {
        if (!(event instanceof Event targetEvent)) return false;
        if (targetEvent.target == null) targetEvent.target = this;
        if (targetEvent.currentTarget == null) targetEvent.currentTarget = this;
        Event.tiggerEvent(targetEvent);
        return !targetEvent.defaultPrevented;
    }

    public void addEventListener(String type, Consumer<Event> listener) {
        events.addEventListener(type, listener);
    }

    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        events.addEventListener(type, listener, useCapture);
    }

    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture, boolean once) {
        events.addEventListener(type, listener, useCapture, once);
    }

    protected void addInternalEventListener(String type, Consumer<Event> listener) {
        events.addInternalEventListener(type, listener);
    }

    protected void addInternalEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        events.addInternalEventListener(type, listener, useCapture);
    }

    public void removeEventListener(String type, Consumer<Event> listener) {
        removeEventListener(type, listener, false);
    }

    public void removeEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        events.removeEventListener(type, listener, useCapture);
    }

    public void triggerEvent(Consumer<Event.ListenerRecord> handler) {
        events.triggerEvent(handler);
    }

    public void setEventListeners(CopyOnWriteArrayList<Event.ListenerRecord> listeners) {
        events.setListeners(listeners);
        EventListener = events.listeners();
    }

    public abstract short getNodeType();

    public abstract String getNodeName();

    public String getNodeValue() {
        return null;
    }

    public abstract String getTextContent();

    public abstract void setTextContent(String value);
}
