package com.sighs.apricityui.instance.dom.expander;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.dom.ExpandContext;
import com.sighs.apricityui.instance.element.Recipe;

import java.util.ArrayList;

/**
 * 在文档刷新阶段触发 recipe DOM 预览槽位生成。
 */
public final class RecipeExpander {
    public static void expand(Document document, ExpandContext context) {
        if (document == null) return;
        ArrayList<Element> snapshot = new ArrayList<>(document.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Recipe recipe)) continue;
            recipe.ensurePreviewSlots();
        }
    }
}
