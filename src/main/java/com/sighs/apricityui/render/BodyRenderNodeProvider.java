package com.sighs.apricityui.render;

import java.util.List;

/**
 * Optional extension point for elements that need custom BODY render nodes.
 * <p>
 * Returning an empty list means fallback to the default BODY phase node.
 */
public interface BodyRenderNodeProvider {
    List<RenderNode> createBodyRenderNodes();
}

