package com.sighs.apricityui.dom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.dom.expander.ContainerExpander;
import com.sighs.apricityui.dom.expander.RecipeExpander;

/**
 * 文档刷新后的一次性扩展入口（Forge 实现）。
 * 容器展开为纯 DOM 逻辑，配方展开依赖 Minecraft 配方管理器。
 */
public final class ForgeDocumentExpander implements DocumentExpander {
    @Override
    public void apply(Document document) {
        if (document == null) return;
        String templatePath = document.getPath();
        try {
            ContainerExpander.expand(document);
        } catch (Exception e) {
            ApricityUI.LOGGER.warn("ContainerExpander failed, template={}", templatePath, e);
        }
        try {
            RecipeExpander.expand(document);
        } catch (Exception e) {
            ApricityUI.LOGGER.warn("RecipeExpander failed, template={}", templatePath, e);
        }
    }
}
