package com.sighs.apricityui.init;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public abstract class Node {
    public static final short ELEMENT_NODE = 1;
    public static final short TEXT_NODE = 3;
    public static final short COMMENT_NODE = 8;
    public static final short DOCUMENT_FRAGMENT_NODE = 11;

    public UUID uuid = UUID.randomUUID();
    public Document document;
    public Node parentNode = null;
    public final CopyOnWriteArrayList<Node> childNodes = new CopyOnWriteArrayList<>();
    public int depth = 0;

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

    public List<Node> getChildNodes() {
        return Collections.unmodifiableList(childNodes);
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
            Node last = null;
            ArrayList<Node> snapshot = new ArrayList<>(fragment.childNodes);
            for (Node child : snapshot) {
                last = document.createRelationAndReturn(prepareForInsertion(child), this, false);
            }
            return last;
        }
        Node inserted = prepareForInsertion(node);
        document.createRelation(inserted, this, false);
        return inserted;
    }

    public Node removeChild(Node node) {
        if (document == null || node == null || node.parentNode != this) return null;
        document.removeNode(node);
        return node;
    }

    public Node insertBefore(Node newNode, Node referenceNode) {
        if (document == null || newNode == null) return null;
        if (newNode instanceof DocumentFragment fragment) {
            Node last = null;
            ArrayList<Node> snapshot = new ArrayList<>(fragment.childNodes);
            for (Node child : snapshot) {
                last = document.getTree().insertBeforeAndReturn(prepareForInsertion(child), this, referenceNode);
            }
            return last;
        }
        Node inserted = prepareForInsertion(newNode);
        document.getTree().insertBefore(inserted, this, referenceNode);
        return inserted;
    }

    public Node replaceChild(Node newNode, Node oldNode) {
        if (document == null || newNode == null || oldNode == null || oldNode.parentNode != this) return null;
        if (newNode instanceof DocumentFragment fragment) {
            Node nextSibling = oldNode.getNextSibling();
            document.removeNode(oldNode);
            ArrayList<Node> snapshot = new ArrayList<>(fragment.childNodes);
            for (Node child : snapshot) {
                document.getTree().insertBefore(prepareForInsertion(child), this, nextSibling);
            }
            return oldNode;
        }
        document.getTree().replaceChild(this, prepareForInsertion(newNode), oldNode);
        return oldNode;
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
