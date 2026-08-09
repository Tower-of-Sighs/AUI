package com.sighs.apricityui.render;

import java.util.List;

/**
 * Optional extension point for elements that need render nodes above their
 * children while still participating in the element's overflow scope.
 */
public interface ForegroundRenderNodeProvider {
    List<RenderNode> createForegroundRenderNodes();
}
