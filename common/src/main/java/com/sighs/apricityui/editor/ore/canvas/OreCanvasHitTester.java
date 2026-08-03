package com.sighs.apricityui.editor.ore.canvas;

import com.sighs.apricityui.init.Element;

import java.util.Map;
import java.util.UUID;

/** Resolves the smallest rendered canvas node containing the document-space point. */
public final class OreCanvasHitTester {
    public UUID hit(Map<UUID, Element> elements, double x, double y) {
        UUID hit = null;
        double hitArea = Double.POSITIVE_INFINITY;
        for (Map.Entry<UUID, Element> entry : elements.entrySet()) {
            Element.DOMRect rect = entry.getValue().getBoundingClientRect();
            if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) continue;
            double area = rect.width * rect.height;
            if (area <= hitArea) {
                hit = entry.getKey();
                hitArea = area;
            }
        }
        return hit;
    }
}
