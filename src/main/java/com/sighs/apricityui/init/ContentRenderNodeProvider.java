package com.sighs.apricityui.init;

import com.sighs.apricityui.render.RenderNode;

import java.util.List;

/**
 * 为元素在自身边框之后、子元素之前插入自定义内容渲染节点。
 */
public interface ContentRenderNodeProvider {
    List<RenderNode> createContentRenderNodes();
}
