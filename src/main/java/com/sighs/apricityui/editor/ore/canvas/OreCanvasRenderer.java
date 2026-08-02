package com.sighs.apricityui.editor.ore.canvas;

import com.sighs.apricityui.editor.ore.OreEditorDom;
import com.sighs.apricityui.editor.ore.model.OreCanvasNode;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.MouseEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/** Projects canvas data into DOM while keeping editor-only decoration outside canvas nodes. */
public final class OreCanvasRenderer {
    private final Document document;
    private final Element canvas;
    private final Consumer<UUID> selectionConsumer;
    private final BiConsumer<UUID, MouseEvent> dragStartConsumer;
    private final BiConsumer<UUID, MouseEvent> resizeStartConsumer;
    private final Map<UUID, Element> elements = new HashMap<>();
    private final Map<UUID, Element> emptyHints = new HashMap<>();
    private Element selectionOverlay;
    private Element hoverOverlay;
    private Element insertionOverlay;
    private Element resizeHandle;
    private Element flexOverlay;

    public OreCanvasRenderer(Document document, Element canvas, Consumer<UUID> selectionConsumer) {
        this(document, canvas, selectionConsumer, null);
    }

    public OreCanvasRenderer(Document document, Element canvas, Consumer<UUID> selectionConsumer,
                             BiConsumer<UUID, MouseEvent> dragStartConsumer) {
        this(document, canvas, selectionConsumer, dragStartConsumer, null);
    }

    public OreCanvasRenderer(Document document, Element canvas, Consumer<UUID> selectionConsumer,
                             BiConsumer<UUID, MouseEvent> dragStartConsumer,
                             BiConsumer<UUID, MouseEvent> resizeStartConsumer) {
        this.document = document;
        this.canvas = canvas;
        this.selectionConsumer = selectionConsumer == null ? ignored -> { } : selectionConsumer;
        this.dragStartConsumer = dragStartConsumer == null ? (ignored, event) -> { } : dragStartConsumer;
        this.resizeStartConsumer = resizeStartConsumer == null ? (ignored, event) -> { } : resizeStartConsumer;
    }

    public Element elementFor(UUID id) { return elements.get(id); }
    public Map<UUID, Element> elements() { return Map.copyOf(elements); }

    public void renderInsertion(OreFlexInsertionResolver.Insertion insertion) {
        if (insertion == null) {
            if (insertionOverlay != null) insertionOverlay.remove();
            insertionOverlay = null;
            return;
        }
        if (insertionOverlay == null) {
            insertionOverlay = Element.init(document.createElement("DIV"));
            insertionOverlay.setAttribute("class", "editor-insertion-overlay");
            insertionOverlay.setAttribute("data-ore-editor-ui", "insertion");
        }
        Element.DOMRect canvasRect = canvas.getBoundingClientRect();
        String style = insertion.row()
                ? "left:" + (insertion.coordinate() - canvasRect.x - 1) + "px;top:" + (insertion.crossStart() - canvasRect.y)
                + "px;width:3px;height:" + Math.max(16, insertion.crossSize()) + "px;"
                : "left:" + (insertion.crossStart() - canvasRect.x) + "px;top:" + (insertion.coordinate() - canvasRect.y - 1)
                + "px;width:" + Math.max(16, insertion.crossSize()) + "px;height:3px;";
        insertionOverlay.setAttribute("style", style);
        canvas.appendChild(insertionOverlay);
        document.markDirty(insertionOverlay, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.HITTEST);
    }

    public void render(OreEditorProject project, UUID selected) {
        render(project, selected, null);
    }

    public void render(OreEditorProject project, UUID selected, UUID hovered) {
        if (project == null || canvas == null) return;
        canvas.setAttribute("style", project.theme().toCss());
        Set<UUID> seen = new HashSet<>();
        Element root = renderNode(project.root(), seen);
        if (root != null) canvas.appendChild(root);
        for (UUID id : new ArrayList<>(elements.keySet())) {
            if (!seen.contains(id)) {
                Element removed = elements.remove(id);
                if (removed != null) removed.remove();
                Element hint = emptyHints.remove(id);
                if (hint != null) hint.remove();
            }
        }
        try {
            renderOverlay(selected, "editor-selection-overlay", true);
            renderOverlay(hovered != null && hovered.equals(selected) ? null : hovered, "editor-hover-overlay", false);
            renderFlexOverlay(project, selected);
            renderResizeHandle(project, selected);
        } catch (LinkageError ignored) {
            clearEditorOverlays();
        }
        document.markDirty(canvas, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
    }

    private Element renderNode(OreCanvasNode node, Set<UUID> seen) {
        seen.add(node.id());
        Element element = elements.computeIfAbsent(node.id(), ignored -> createElement(node));
        element.setAttribute("data-ore-node-id", node.id().toString());
        element.setAttribute("data-ore-node-type", node instanceof OreContainerNode ? "container" : "component");
        element.setAttribute("style", styleFor(node, element));
        if (node instanceof OreContainerNode container) {
            if (container.children().isEmpty()) {
                Element hint = emptyHints.computeIfAbsent(container.id(), ignored -> emptyHint());
                element.appendChild(hint);
            } else {
                Element hint = emptyHints.remove(container.id());
                if (hint != null) hint.remove();
                for (OreCanvasNode child : container.children()) element.appendChild(renderNode(child, seen));
            }
        } else if (node instanceof OreComponentNode component) {
            element.setTextContent(component.content());
        }
        return element;
    }

    private Element emptyHint() {
        Element hint = OreEditorDom.translation(document, "ore_editor.apricityui.empty.container", "editor-empty-container");
        hint.setAttribute("data-ore-editor-ui", "empty-container");
        return hint;
    }

    private Element createElement(OreCanvasNode node) {
        String tag = node instanceof OreComponentNode component ? component.type() : ((OreContainerNode) node).tag();
        Element element = Element.init(document.createElement(tag));
        node.attributes().forEach(element::setAttribute);
        String editorClass = node instanceof OreContainerNode ? "ore-editor-container" : componentClass((OreComponentNode) node);
        String sourceClass = element.getAttribute("class").trim();
        element.setAttribute("class", sourceClass.isEmpty() ? editorClass : sourceClass + " " + editorClass);
        element.addEventListener("click", event -> {
            event.preventDefault();
            event.stopPropagation();
            selectionConsumer.accept(node.id());
        });
        element.addEventListener("mousedown", event -> {
            if (event instanceof MouseEvent mouseEvent) dragStartConsumer.accept(node.id(), mouseEvent);
        });
        if (node instanceof OreComponentNode component) bindStateRefresh(element, component);
        return element;
    }

    private void bindStateRefresh(Element element, OreComponentNode component) {
        for (String eventName : new String[]{"mouseover", "mouseout", "mousedown", "mouseup", "focus", "blur"}) {
            element.addEventListener(eventName, event -> refreshComponentStyle(element, component));
        }
    }

    private void refreshComponentStyle(Element element, OreComponentNode component) {
        element.setAttribute("style", styleFor(component, element));
        document.markDirty(element, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.HITTEST);
    }

    private String componentClass(OreComponentNode component) {
        return "button".equals(component.type()) ? "button button-normal ore-editor-component" : "ore-editor-component";
    }

    private String styleFor(OreCanvasNode node, Element element) {
        StringBuilder style = new StringBuilder();
        if (node instanceof OreContainerNode container) {
            style.append("display:flex;position:relative;")
                    .append("flex-direction:").append(container.flex().direction()).append(';')
                    .append("flex-wrap:").append(container.flex().wrap()).append(';')
                    .append("justify-content:").append(container.flex().justifyContent()).append(';')
                    .append("align-items:").append(container.flex().alignItems()).append(';')
                    .append("align-content:").append(container.flex().alignContent()).append(';')
                    .append("gap:").append(container.flex().gap()).append(';')
                    .append("row-gap:").append(container.flex().rowGap()).append(';')
                    .append("column-gap:").append(container.flex().columnGap()).append(';');
            if (container.isRoot()) style.append("min-height:100%;width:100%;");
        }
        node.style().properties().forEach((key, value) -> style.append(key).append(':').append(value).append(';'));
        if (node instanceof OreComponentNode component) {
            OreComponentNode.VisualState state = stateFor(element);
            component.stateStyle(state).properties().forEach((key, value) -> style.append(key).append(':').append(value).append(';'));
        }
        return style.toString();
    }

    private OreComponentNode.VisualState stateFor(Element element) {
        if (element.isDisabled()) return OreComponentNode.VisualState.DISABLED;
        if (element.isActive) return OreComponentNode.VisualState.ACTIVE;
        if (element.isFocus) return OreComponentNode.VisualState.FOCUS;
        if (element.isHover) return OreComponentNode.VisualState.HOVER;
        return OreComponentNode.VisualState.DEFAULT;
    }

    private void renderOverlay(UUID id, String className, boolean selection) {
        Element overlay = selection ? selectionOverlay : hoverOverlay;
        if (id == null || !elements.containsKey(id)) {
            if (overlay != null) overlay.remove();
            if (selection) selectionOverlay = null;
            else hoverOverlay = null;
            return;
        }
        if (overlay == null) {
            overlay = Element.init(document.createElement("DIV"));
            overlay.setAttribute("class", className);
            if (selection) selectionOverlay = overlay;
            else hoverOverlay = overlay;
        }
        Element target = elements.get(id);
        Element.DOMRect targetRect = target.getBoundingClientRect();
        Element.DOMRect canvasRect = canvas.getBoundingClientRect();
        overlay.setAttribute("style", "left:" + (targetRect.x - canvasRect.x) + "px;top:"
                + (targetRect.y - canvasRect.y) + "px;width:" + targetRect.width + "px;height:" + targetRect.height + "px;");
        canvas.appendChild(overlay);
    }

    private void renderResizeHandle(OreEditorProject project, UUID selected) {
        OreCanvasNode node = project == null || selected == null ? null : project.find(selected);
        if (!(node instanceof OreComponentNode component) || !component.absolute() || !elements.containsKey(selected)) {
            if (resizeHandle != null) resizeHandle.remove();
            resizeHandle = null;
            return;
        }
        if (resizeHandle == null) {
            resizeHandle = Element.init(document.createElement("DIV"));
            resizeHandle.setAttribute("class", "editor-absolute-resize-handle");
            resizeHandle.setAttribute("data-ore-editor-ui", "absolute-resize");
            resizeHandle.addEventListener("mousedown", event -> {
                String id = resizeHandle.getAttribute("data-ore-node-id");
                if (event instanceof MouseEvent mouseEvent && id != null) {
                    resizeStartConsumer.accept(UUID.fromString(id), mouseEvent);
                }
            });
        }
        Element.DOMRect targetRect = elements.get(selected).getBoundingClientRect();
        Element.DOMRect canvasRect = canvas.getBoundingClientRect();
        resizeHandle.setAttribute("style", "left:" + (targetRect.x - canvasRect.x + targetRect.width - 5)
                + "px;top:" + (targetRect.y - canvasRect.y + targetRect.height - 5) + "px;");
        resizeHandle.setAttribute("data-ore-node-id", selected.toString());
        canvas.appendChild(resizeHandle);
    }

    private void clearEditorOverlays() {
        if (selectionOverlay != null) selectionOverlay.remove();
        if (hoverOverlay != null) hoverOverlay.remove();
        if (insertionOverlay != null) insertionOverlay.remove();
        if (resizeHandle != null) resizeHandle.remove();
        if (flexOverlay != null) flexOverlay.remove();
        selectionOverlay = hoverOverlay = insertionOverlay = resizeHandle = flexOverlay = null;
    }

    private void renderFlexOverlay(OreEditorProject project, UUID selected) {
        if (flexOverlay != null) flexOverlay.remove();
        flexOverlay = null;
        OreCanvasNode node = project == null || selected == null ? null : project.find(selected);
        if (!(node instanceof OreContainerNode container) || !elements.containsKey(selected)) return;

        Element target = elements.get(selected);
        Element.DOMRect targetRect = target.getBoundingClientRect();
        Element.DOMRect canvasRect = canvas.getBoundingClientRect();
        boolean row = !container.flex().direction().startsWith("column");
        flexOverlay = Element.init(document.createElement("DIV"));
        flexOverlay.setAttribute("class", "editor-flex-overlay");
        flexOverlay.setAttribute("data-ore-editor-ui", "flex-overlay");
        addOverlayPart(row ? "editor-flex-main-axis horizontal" : "editor-flex-main-axis vertical",
                row ? targetRect.left - canvasRect.x : targetRect.left - canvasRect.x + 4,
                row ? targetRect.top - canvasRect.y + 4 : targetRect.top - canvasRect.y,
                row ? targetRect.width : 1, row ? 1 : targetRect.height);
        addOverlayPart(row ? "editor-flex-cross-axis vertical" : "editor-flex-cross-axis horizontal",
                row ? targetRect.left - canvasRect.x + 4 : targetRect.left - canvasRect.x,
                row ? targetRect.top - canvasRect.y : targetRect.top - canvasRect.y + 4,
                row ? 1 : targetRect.width, row ? targetRect.height : 1);

        List<Element> children = new ArrayList<>();
        for (OreCanvasNode child : container.children()) {
            Element element = elements.get(child.id());
            if (element != null && !"absolute".equals(element.getComputedStyle().position)) children.add(element);
        }
        for (List<Element> line : flexLines(children, row)) {
            renderFlexLine(line, row, canvasRect);
            renderFlexGaps(line, row, canvasRect);
        }
        canvas.appendChild(flexOverlay);
    }

    private List<List<Element>> flexLines(List<Element> children, boolean row) {
        List<Element> ordered = new ArrayList<>(children);
        ordered.sort(Comparator.comparingDouble(element -> crossCenter(element.getBoundingClientRect(), row)));
        List<List<Element>> lines = new ArrayList<>();
        for (Element child : ordered) {
            if (lines.isEmpty() || Math.abs(lineCrossCenter(lines.get(lines.size() - 1), row)
                    - crossCenter(child.getBoundingClientRect(), row)) > 2) {
                lines.add(new ArrayList<>());
            }
            lines.get(lines.size() - 1).add(child);
        }
        return lines;
    }

    private void renderFlexLine(List<Element> line, boolean row, Element.DOMRect canvasRect) {
        if (line.isEmpty()) return;
        double left = line.stream().map(Element::getBoundingClientRect).mapToDouble(rect -> rect.left).min().orElse(0);
        double top = line.stream().map(Element::getBoundingClientRect).mapToDouble(rect -> rect.top).min().orElse(0);
        double right = line.stream().map(Element::getBoundingClientRect).mapToDouble(rect -> rect.right).max().orElse(left);
        double bottom = line.stream().map(Element::getBoundingClientRect).mapToDouble(rect -> rect.bottom).max().orElse(top);
        addOverlayPart("editor-flex-line-overlay", left - canvasRect.x, top - canvasRect.y,
                Math.max(1, right - left), Math.max(1, bottom - top));
    }

    private void renderFlexGaps(List<Element> line, boolean row, Element.DOMRect canvasRect) {
        List<Element> ordered = new ArrayList<>(line);
        ordered.sort(Comparator.comparingDouble(element -> mainStart(element.getBoundingClientRect(), row)));
        for (int index = 1; index < ordered.size(); index++) {
            Element.DOMRect previous = ordered.get(index - 1).getBoundingClientRect();
            Element.DOMRect next = ordered.get(index).getBoundingClientRect();
            double gap = mainStart(next, row) - mainEnd(previous, row);
            if (gap <= 1) continue;
            if (row) addOverlayPart("editor-flex-gap-overlay", previous.right - canvasRect.x,
                    Math.min(previous.top, next.top) - canvasRect.y, gap,
                    Math.max(previous.bottom, next.bottom) - Math.min(previous.top, next.top));
            else addOverlayPart("editor-flex-gap-overlay", Math.min(previous.left, next.left) - canvasRect.x,
                    previous.bottom - canvasRect.y,
                    Math.max(previous.right, next.right) - Math.min(previous.left, next.left), gap);
        }
    }

    private void addOverlayPart(String className, double left, double top, double width, double height) {
        Element part = Element.init(document.createElement("DIV"));
        part.setAttribute("class", className);
        part.setAttribute("style", "left:" + left + "px;top:" + top + "px;width:" + width + "px;height:" + height + "px;");
        flexOverlay.appendChild(part);
    }

    private static double mainStart(Element.DOMRect rect, boolean row) { return row ? rect.left : rect.top; }
    private static double mainEnd(Element.DOMRect rect, boolean row) { return row ? rect.right : rect.bottom; }
    private static double crossCenter(Element.DOMRect rect, boolean row) { return row ? rect.top + rect.height / 2 : rect.left + rect.width / 2; }
    private static double lineCrossCenter(List<Element> line, boolean row) {
        return line.stream().map(Element::getBoundingClientRect).mapToDouble(rect -> crossCenter(rect, row)).average().orElse(0);
    }
}
