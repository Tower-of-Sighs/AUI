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
        apply(document, ExpandContext.from(document));
    }

    public static void apply(Document document, ExpandContext context) {
        if (document == null) return;
        ExpandContext safeContext = context == null ? ExpandContext.from(document) : context;
        try {
            ContainerExpander.expand(document, safeContext);
        } catch (Exception e) {
            ApricityUI.LOGGER.warn("ContainerExpander failed, template={}", safeContext.templatePath(), e);
        }
        try {
            RecipeExpander.expand(document, safeContext);
        } catch (Exception e) {
            ApricityUI.LOGGER.warn("RecipeExpander failed, template={}", safeContext.templatePath(), e);
        }
    }
}
