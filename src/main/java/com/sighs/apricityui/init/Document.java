package com.sighs.apricityui.init;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.render.RenderNode;
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
    private UUID uuid = UUID.randomUUID();
    public final boolean inWorld;

    public Document(String path, boolean inWorld) {
        this.path = path;
        this.inWorld = inWorld;
//        refresh();
    }

    public UUID getUuid() {
        return uuid;
    }

    public void refresh() {
        CSSCache.clear();
        elements.clear();
        Element bodyElement = HTML.create(this, path);
        try {
            if (body != null) bodyElement.EventListener = body.EventListener;
            body = (Body) Element.init(bodyElement);
            elements.add(0, body);
            body.updateCSS();
            elements.forEach(Element::clearDirtyFlags);
            dirtyElements.clear();
            paintList = Drawer.createPaintList(body);
            elements.forEach(Element::updateCSS);
            prefetchImages();

            for (String js : JSCache) {
                String head = "let document = ApricityUI.getDocumentByUUID(\"" + uuid + "\");\n";
                head += "let window = ApricityUI.getWindow();";
                ApricityJS.eval(head + js);
            }
            for (Event eventListener : body.EventListener) {
                if (eventListener.type.equals("load")) body.triggerEvent(eventListener.listener);
            }
        } catch (Exception ignored) {}
    }

    private void prefetchImages() {
        Set<String> paths = new HashSet<>();
        for (Element element : elements) {
            String src = element.getAttribute("src");
            if (!src.isEmpty() && "IMG".equals(element.tagName)) {
                String resolved = Loader.resolve(path, src);
                if (isImagePathValid(resolved)) {
                    paths.add(resolved);
                }
            }

            Style style = element.getRawComputedStyle();
            if (style == null) continue;

            String backgroundPath = resolveCssUrl(path, style.backgroundImage);
            if (isImagePathValid(backgroundPath)) {
                paths.add(backgroundPath);
            }

            String borderImageSource = resolveFirstNonUnset(style.borderImageSource, style.borderImage);
            String borderImagePath = resolveCssUrl(path, borderImageSource);
            if (isImagePathValid(borderImagePath)) {
                paths.add(borderImagePath);
            }
        }
        ImageAsyncHandler.INSTANCE.prefetch(paths);
    }

    private static boolean isImagePathValid(String path) {
        return path != null && !path.isBlank() && !"unset".equals(path);
    }

    private static String resolveFirstNonUnset(String primary, String fallback) {
        if (primary != null && !primary.isBlank() && !"unset".equals(primary)) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank() && !"unset".equals(fallback)) {
            return fallback;
        }
        return null;
    }

    private static String resolveCssUrl(String contextPath, String cssValue) {
        if (cssValue == null || cssValue.isBlank() || "unset".equals(cssValue)) return null;
        int start = cssValue.indexOf("url(");
        if (start < 0) return null;
        int end = cssValue.indexOf(')', start + 4);
        if (end < 0) return null;
        String raw = cssValue.substring(start + 4, end).replace("\"", "").replace("'", "").trim();
        if (raw.isEmpty()) return null;
        return Loader.resolve(contextPath, raw);
    }

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

    public String getPath() { return path; }

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
            parent.children.add(0, child);
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
        documents.forEach(Document::refresh);
    }

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
        element.document.markDirty(element.parentElement, Drawer.RELAYOUT);
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
            focusedElement.setFocus(false);
            for (Event event : focusedElement.EventListener) {
                if (event.type.equals("blur")) focusedElement.triggerEvent(event.listener);
            }
        }

        focusedElement = element;

        if (element != null) {
            element.setFocus(true);
            for (Event event : element.EventListener) {
                if (event.type.equals("focus")) element.triggerEvent(event.listener);
            }
        }
    }

    // 全局清理焦点 (当点击了其他 Document 时可能需要调用)
    public void clearFocus() {
        setFocusedElement(null);
    }
}
