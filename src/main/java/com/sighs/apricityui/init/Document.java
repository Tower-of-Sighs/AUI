package com.sighs.apricityui.init;

import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.instance.dom.DocumentExpander;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.script.ApricityJS;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Document {
    private static final List<Document> documents = new CopyOnWriteArrayList<>();
    private final ArrayList<Element> elements = new ArrayList<>();
    private final Set<Element> dirtyElements = ConcurrentHashMap.newKeySet();
    private ArrayList<RenderNode> paintList = new ArrayList<>();
    private final HashMap<String, Element> IDMap = new HashMap<>();

    private Element previousCursorElement = null;
    private Element activeElement = null;
    private Element focusedElement = null;

    private final String path;
    public final Map<String, Map<String, String>> CSSCache = new HashMap<>();
    public final List<String> JSCache = new ArrayList<>();
    public Body body;
    private final UUID uuid = UUID.randomUUID();
    public final boolean inWorld;
    private volatile boolean reloadPersistent = false;

    public Document(String path, boolean inWorld) {
        this.path = path;
        this.inWorld = inWorld;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void refresh() {
        CSSCache.clear();
        JSCache.clear();
        IDMap.clear();
        elements.clear();
        Element bodyElement = HTML.create(this, path);
        try {
            if (bodyElement == null) return;
            if (body != null) bodyElement.EventListener = body.EventListener;
            body = (Body) Element.init(bodyElement);
            rebuildElementIndexFromBody();

            // First pass: ensure computed styles exist for DOM expanders.
            body.updateCSS();
            DocumentExpander.apply(this);

            // Final pass: apply styles once after expansion.
            body.updateCSS();
            elements.forEach(Element::clearDirtyFlags);
            dirtyElements.clear();
            paintList = Drawer.createPaintList(body);
            ImageAsyncHandler.prefetchImages(this);

            for (String js : JSCache) {
                String head = "let document = ApricityUI.getDocumentByUUID(\"" + uuid + "\");\n";
                head += "let window = ApricityUI.getWindow();";
                ApricityJS.eval(head + js);
            }
            for (Event eventListener : body.EventListener) {
                if (eventListener.type.equals("load")) body.triggerEvent(eventListener.listener);
            }
        } catch (Exception ignored) {
        }
    }

    private void rebuildElementIndexFromBody() {
        elements.clear();
        IDMap.clear();
        if (body == null) return;

        body.parentElement = null;
        body.depth = 0;

        Deque<Element> stack = new ArrayDeque<>();
        stack.push(body);

        while (!stack.isEmpty()) {
            Element current = stack.pop();
            elements.add(current);

            current.runInitFromDomOnce(current);
            if (current.id != null && !current.id.isBlank()) {
                IDMap.put(current.id, current);
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


    // 绘制队列，详见Drawer类
    public ArrayList<RenderNode> getPaintList() {
        return paintList;
    }

    // 用来将某个元素更新成另一个元素，比如创建的时候用转换成对应类的元素替换掉原来通用的
    public void updateElement(Element element) {
        int index = -1;
        for (Element e : elements) {
            if (e.uuid.equals(element.uuid)) index = elements.indexOf(e);
        }
        if (index == -1) return;
        elements.set(index, element);
    }

    public Set<Element> getDirtyElements() {
        return dirtyElements;
    }

    public void markDirty(int mask) {
        elements.forEach(element -> element.addDirtyFlags(mask));
        dirtyElements.addAll(elements);
    }

    public void markDirty(Element element, int mask) {
        if (element == null) return;
        element.addDirtyFlags(mask);
        dirtyElements.add(element);
    }

    public void reapplyStylesFromCache() {
        if (body == null) return;
        elements.forEach(Element::updateCSS);
        markDirty(body, Drawer.RELAYOUT | Drawer.REPAINT);
    }

    public boolean is(String path) {
        return this.path.equals(path);
    }

    public boolean is(UUID uuid) {
        return this.uuid.equals(uuid);
    }

    public String getPath() {
        return path;
    }

    public boolean isReloadPersistent() {
        return reloadPersistent;
    }

    public void setReloadPersistent(boolean reloadPersistent) {
        this.reloadPersistent = reloadPersistent;
    }

    public Element createHTML(String html) {
        return HTML.createElement(this, html);
    }

    public Element createElement(String tagName) {
        return new Element(this, tagName);
    }

    public void createRelation(Element child, Element parent, boolean head) {
        if (child.parentElement != null) child.parentElement.children.remove(child);
        child.parentElement = parent;
        if (parent.children.isEmpty() || !head) {
            elements.add(child);
            parent.children.add(child);
        } else {
            int maxIndex = elements.size() - 1;
            for (int i = 0; i <= maxIndex; i++) {
                Element parentElement = elements.get(i).parentElement;
                if (parentElement != null && parentElement.uuid.equals(parent.uuid)) {
                    elements.add(i, child);
                    break;
                }
            }
            parent.children.addFirst(child);
        }
        child.depth = parent.getDepth() + 1;
        child.updateCSS();
        child.getRenderer().size.clear();

        // 需要判断一下是否为影响布局的属性，待补充
        markDirty(parent, Drawer.RELAYOUT);
    }

    public List<Element> querySelectorAll(String selector) {
        return Selector.querySelectorAll(body, selector);
    }

    public Element querySelector(String selector) {
        return Selector.querySelector(body, selector);
    }

    public void recordID(Element element) {
        IDMap.put(element.id, element);
    }

    public Element getElementById(String id) {
        return IDMap.get(id);
    }

    public static void refreshAll() {
        for (Document document : documents) {
            if (document == null || document.isReloadPersistent()) continue;
            document.refresh();
        }
    }

    // 这俩是创建UI用的，如果refresh放在构造函数里，那创建时就不会执行内嵌js，所以挪到了这里。
    public static Document create(String path) {
        if (HTML.getTemple(path) == null) return null;
        Document document = new Document(path, false);
        documents.add(document);
        document.refresh();
        return document;
    }

    public static Document createInWorld(String path) {
        if (HTML.getTemple(path) == null) return null;
        Document document = new Document(path, true);
        documents.add(document);
        document.refresh();
        return document;
    }

    public static ArrayList<Document> get(String path) {
        ArrayList<Document> result = new ArrayList<>();
        for (Document document : documents) {
            if (document.getPath().equals(path)) result.add(document);
        }
        return result;
    }

    public static Document getByUUID(String uuid) {
        for (Document document : documents) {
            if (document.uuid.toString().equals(uuid)) return document;
        }
        return null;
    }

    public static List<Document> getAll() {
        return documents;
    }

    public ArrayList<Element> getElements() {
        return elements;
    }

    public static void remove(String path) {
        documents.removeIf(document -> document.is(path));
    }

    public static void remove(UUID uuid) {
        documents.removeIf(document -> document.is(uuid));
    }

    public void remove() {
        Document.remove(uuid);
    }

    public void removeElement(Element element) {
        element.parentElement.children.removeIf(e -> element.uuid.equals(e.uuid));
        element.document.markDirty(element.parentElement, Drawer.REORDER);
        elements.removeIf(e -> element.uuid.equals(e.uuid));
    }

    public Element getPreviousCursorElement() {
        return previousCursorElement;
    }

    public void setPreviousCursorElement(Element element) {
        this.previousCursorElement = element;
    }

    public Element getActiveElement() {
        return activeElement;
    }

    public void setActiveElement(Element element) {
        List<Element> oldChain = activeElement != null ? activeElement.getRoute() : Collections.emptyList();
        List<Element> newChain = element != null ? element.getRoute() : Collections.emptyList();

        for (Element e : oldChain) {
            if (!newChain.contains(e)) {
                e.setActive(false);
            }
        }
        for (Element e : newChain) {
            e.setActive(true);
        }

        this.activeElement = element;
    }

    public Element getFocusedElement() {
        return focusedElement;
    }

    public void setFocusedElement(Element element) {
        if (focusedElement != null && focusedElement != element) {
            if (focusedElement instanceof AbstractText textElement) {
                textElement.clearSelection();
            } else {
                focusedElement.clearTextSelection();
            }
            focusedElement.setFocus(false);
            ArrayList<Event> blurSnapshot = new ArrayList<>(focusedElement.EventListener);
            for (Event event : blurSnapshot) {
                if (!"blur".equals(event.type) || event.listener == null) continue;
                event.listener.accept(event);
            }
        }

        focusedElement = element;

        if (element != null) {
            element.setFocus(true);
            ArrayList<Event> focusSnapshot = new ArrayList<>(element.EventListener);
            for (Event event : focusSnapshot) {
                if (!"focus".equals(event.type) || event.listener == null) continue;
                event.listener.accept(event);
            }
        }
    }


    public boolean hasAnyTextSelection() {
        for (Element element : elements) {
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
        for (Element element : elements) {
            if (element == keep) continue;
            if (element instanceof AbstractText textElement) {
                if (textElement.hasSelection()) textElement.clearSelection();
                continue;
            }
            if (element.hasInnerTextSelection()) element.clearTextSelection();
        }
    }

    // 全局清理焦点 (当点击了其他 Document 时可能需要调用)
    public void clearFocus() {
        setFocusedElement(null);
    }
}

