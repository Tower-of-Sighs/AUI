package com.sighs.apricityui.instance.dom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.dom.expander.ContainerExpander;
import com.sighs.apricityui.instance.dom.expander.RecipeExpander;

/**
 * 文档刷新后的一次性扩展入口。
 */
public final class DocumentExpander {
    public static void apply(Document document) {
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
