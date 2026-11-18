package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.canvas.CanvasRenderingContext2D;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.ui.Tooltip;

import java.awt.geom.Path2D;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class DevToolsDepthView {
    private static final String[][] FACE_COLORS = {
            {"#5231d9", "#6949e5"}, {"#7851e8", "#946df1"},
            {"#a772ef", "#bf90f5"}, {"#ce87e3", "#e5a0e9"},
            {"#ed79ba", "#f698cc"}, {"#ed5597", "#fb7cb0"}
    };
    private static final String[][] SIDE_COLORS = {
            {"#3520a2", "#4831b6"}, {"#5032ae", "#694dc0"},
            {"#7044b4", "#8659c4"}, {"#9b52a4", "#b46ab4"},
            {"#b04480", "#c85d98"}, {"#b42c6e", "#cd4888"}
    };
    private final DevToolsController controller;
    private final List<Layer> layers = new ArrayList<>();
    private final List<SnapshotLayer> snapshot = new ArrayList<>();
    private Size viewport = Size.ZERO;
    private boolean snapshotPending = true;
    private boolean dirty = true;
    private int renderedWidth;
    private int renderedHeight;
    private double yaw = -0.65;
    private double pitch = 0.15;
    private double zoom = 1;
    private double panX;
    private double panY;
    private double lastX;
    private double lastY;
    private int dragButton = -1;
    private boolean moved;
    private Element hovered;

    DevToolsDepthView(DevToolsController controller) {
        this.controller = controller;
    }

    void bind() {
        Document tool = controller.toolDocument();
        if (tool == null) return;
        Element canvas = tool.querySelector("#depthCanvas");
        if (canvas == null || "1".equals(canvas.getAttribute("data-depth-bound"))) return;
        dragButton = -1;
        canvas.setAttribute("data-depth-bound", "1");
        canvas.addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouse) || (mouse.button != 0 && mouse.button != 1) || dragButton != -1) return;
            dragButton = mouse.button;
            moved = false;
            lastX = mouse.clientX;
            lastY = mouse.clientY;
            event.preventDefault();
        });
        canvas.addEventListener("mousemove", event -> {
            if (dragButton != -1 || !(event instanceof MouseEvent mouse)) return;
            Element element = layerAt(canvas, mouse);
            if (element != hovered) {
                clearHover();
                hovered = element;
                if (hovered != null) controller.hoverFromView(hovered);
                dirty = true;
            }
        });
        canvas.addEventListener("mouseleave", event -> clearHover());
        canvas.addEventListener("contextmenu", event -> event.preventDefault());
        tool.body.addEventListener("mousemove", event -> {
            if (dragButton == -1 || !(event instanceof MouseEvent mouse)) return;
            double deltaX = mouse.clientX - lastX;
            double deltaY = mouse.clientY - lastY;
            if (Math.abs(deltaX) + Math.abs(deltaY) > 1) moved = true;
            if (moved) clearHover();
            if (dragButton == 0) {
                yaw += deltaX * 0.01;
                pitch += deltaY * 0.01;
            } else if (canvas instanceof Canvas surface) {
                Element.DOMRect rect = canvas.getBoundingClientRect();
                if (rect.width > 0 && rect.height > 0) {
                    panX += deltaX * surface.getWidth() / rect.width;
                    panY += deltaY * surface.getHeight() / rect.height;
                }
            }
            lastX = mouse.clientX;
            lastY = mouse.clientY;
            dirty = true;
        });
        tool.body.addEventListener("mouseup", event -> {
            if (dragButton == -1 || !(event instanceof MouseEvent mouse) || mouse.button != dragButton) return;
            dragButton = -1;
            if (moved || mouse.button != 0) return;
            controller.selectFromView(layerAt(canvas, mouse), true);
        });
        canvas.addEventListener("wheel", event -> {
            if (!(event instanceof MouseEvent mouse)) return;
            zoom = Math.max(0.25, Math.min(4, zoom * (mouse.deltaY < 0 ? 1.2 : 1 / 1.2)));
            clearHover();
            dirty = true;
            event.preventDefault();
        });
        Element reset = tool.querySelector("#depthReset");
        if (reset != null) {
            reset.addEventListener("click", event -> {
                clearHover();
                yaw = -0.65;
                pitch = 0.15;
                zoom = 1;
                panX = panY = 0;
                dirty = true;
            });
            Tooltip.bindTranslation(reset, "devtools.apricityui.depth_reset");
        }
    }

    private Element layerAt(Element canvas, MouseEvent mouse) {
        Element.DOMRect rect = canvas.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0 || !(canvas instanceof Canvas surface)
                || mouse.clientX < rect.x || mouse.clientX >= rect.x + rect.width
                || mouse.clientY < rect.y || mouse.clientY >= rect.y + rect.height) return null;
        double x = (mouse.clientX - rect.x) * surface.getWidth() / rect.width;
        double y = (mouse.clientY - rect.y) * surface.getHeight() / rect.height;
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            if (layer.path.contains(x, y)) return layer.element;
        }
        return null;
    }

    void clearHover() {
        if (hovered == null) return;
        controller.clearHoverFromView(hovered);
        hovered = null;
        dirty = true;
    }

    boolean prepareRender() {
        Document tool = controller.toolDocument();
        if (tool == null || !(tool.querySelector("#depthCanvas") instanceof Canvas canvas)) return false;
        if (!dirty && renderedWidth == canvas.getWidth() && renderedHeight == canvas.getHeight()) return false;
        if (snapshotPending) capture(controller.targetDocument());
        render(canvas);
        renderedWidth = canvas.getWidth();
        renderedHeight = canvas.getHeight();
        dirty = false;
        return true;
    }

    void invalidate() {
        dirty = true;
    }

    void refreshSnapshot() {
        clearHover();
        dragButton = -1;
        snapshot.clear();
        layers.clear();
        viewport = Size.ZERO;
        snapshotPending = true;
        dirty = true;
    }

    private void capture(Document target) {
        snapshotPending = false;
        if (target == null) return;
        target.commitStyleRecalc();
        target.commitRenderState();
        viewport = target.getViewportSize();
        LinkedHashSet<Element> elements = new LinkedHashSet<>();
        for (RenderNode node : target.getPaintList()) {
            Element element = null;
            if (node instanceof RenderNode.ElementPhaseNode phase && phase.phase() == Base.RenderPhase.BODY) element = phase.target();
            else if (node instanceof RenderNode.ElementBackgroundNode background) element = background.target();
            if (element != null && !element.isPseudoElement() && !RenderNode.shouldSkip(element)) elements.add(element);
        }
        for (Element element : elements) {
            snapshot.add(new SnapshotLayer(element, element.getBoundingClientRect(), element.getRawComputedStyle().zIndex));
        }
    }

    private void render(Canvas canvas) {
        CanvasRenderingContext2D context = canvas.getContext("2d");
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        context.clearRect(0, 0, width, height);
        layers.clear();
        Element highlighted = hovered;
        Element label = controller.toolDocument().querySelector("#depthSelection");
        if (label != null) {
            SnapshotLayer selection = snapshot.stream().filter(layer -> layer.element == highlighted).findFirst().orElse(null);
            label.setTextContent(selection == null ? ""
                    : highlighted.tagName.toLowerCase(Locale.ROOT) + (highlighted.id == null || highlighted.id.isBlank() ? "" : "#" + highlighted.id)
                    + "  |  z-index: " + selection.zIndex);
        }
        if (snapshot.isEmpty()) return;
        double vw = Math.max(1, viewport.width());
        double vh = Math.max(1, viewport.height());
        double baseScale = Math.min(width / (vw + vh), height / (vw + vh));
        double scale = zoom * baseScale;
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        double spacing = Math.max(vh * 0.45 / Math.max(1, snapshot.size() - 1), 3.5 / baseScale);
        double thickness = Math.min(vh * 0.03, spacing * 0.4);
        boolean reverse = cosYaw * cosPitch < 0;
        for (int order = 0; order < snapshot.size(); order++) {
            int index = reverse ? snapshot.size() - 1 - order : order;
            SnapshotLayer captured = snapshot.get(index);
            Element element = captured.element;
            Element.DOMRect rect = captured.bounds;
            if (!(rect.width > 0 && rect.height > 0)) continue;
            double depth = (index - (snapshot.size() - 1) / 2.0) * spacing;
            int color = index * (FACE_COLORS.length - 1) / Math.max(1, snapshot.size() - 1);
            double[][] points = {{rect.x, rect.y}, {rect.x + rect.width, rect.y},
                    {rect.x + rect.width, rect.y + rect.height}, {rect.x, rect.y + rect.height}};
            double[][][] projected = new double[2][4][2];
            for (int side = 0; side < 2; side++) {
                double z = depth + (side == 0 ? 1 : -1) * (reverse ? -1 : 1) * thickness / 2;
                for (int i = 0; i < points.length; i++) {
                    double x = points[i][0] - vw / 2;
                    double y = points[i][1] - vh / 2;
                    projected[side][i][0] = width / 2.0 + panX + (x * cosYaw + z * sinYaw) * scale;
                    double rotatedDepth = -x * sinYaw + z * cosYaw;
                    projected[side][i][1] = height / 2.0 + panY + (y * cosPitch - rotatedDepth * sinPitch) * scale;
                }
            }
            Area hit = new Area();
            int[] edges = {-sinYaw * cosPitch >= 0 ? 1 : 3, sinPitch >= 0 ? 2 : 0};
            context.setFillStyle(element == highlighted ? "#c6782c" : SIDE_COLORS[color][index % 2]);
            for (int edge : edges) {
                int next = (edge + 1) % 4;
                hit.add(new Area(polygon(context, new double[][] {
                        projected[0][edge], projected[0][next], projected[1][next], projected[1][edge]})));
                context.fill();
            }
            Path2D.Double path = polygon(context, projected[0]);
            hit.add(new Area(path));
            context.setFillStyle(element == highlighted ? "#ffae46" : FACE_COLORS[color][index % 2]);
            context.fill();
            context.setStrokeStyle(element == highlighted ? "#b65300" : SIDE_COLORS[color][index % 2]);
            context.setLineWidth(element == highlighted ? 2 : 1);
            context.stroke();
            layers.add(new Layer(element, captured.zIndex, hit, projected[0][0][0], projected[0][0][1]));
        }
        List<Rectangle2D> labels = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = layers.size() - 1; i >= 0; i--) {
                Layer layer = layers.get(i);
                if ((layer.element == highlighted) != (pass == 0)) continue;
                String zIndex = layer.zIndex;
                if (zIndex == null || "auto".equalsIgnoreCase(zIndex)) continue;
                String text = "z-index: " + zIndex;
                Rectangle2D bounds = new Rectangle2D.Double(layer.labelX, layer.labelY - 11,
                        context.measureText(text).width + 6, 14);
                if (bounds.getMinX() < 0 || bounds.getMinY() < 0
                        || bounds.getMaxX() > width || bounds.getMaxY() > height
                        || labels.stream().anyMatch(bounds::intersects)) continue;
                labels.add(bounds);
                context.setFillStyle("rgba(255,255,255,0.9)");
                context.fillRect(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
                context.setFillStyle("#1e2933");
                context.fillText(text, bounds.getX() + 3, bounds.getY() + 11);
            }
        }
    }

    private static Path2D.Double polygon(CanvasRenderingContext2D context, double[][] points) {
        Path2D.Double path = new Path2D.Double();
        context.beginPath();
        for (int i = 0; i < points.length; i++) {
            if (i == 0) { context.moveTo(points[i][0], points[i][1]); path.moveTo(points[i][0], points[i][1]); }
            else { context.lineTo(points[i][0], points[i][1]); path.lineTo(points[i][0], points[i][1]); }
        }
        context.closePath();
        path.closePath();
        return path;
    }

    private record SnapshotLayer(Element element, Element.DOMRect bounds, String zIndex) {}

    private record Layer(Element element, String zIndex, Area path, double labelX, double labelY) {}

}
