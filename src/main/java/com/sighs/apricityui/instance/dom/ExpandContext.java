package com.sighs.apricityui.instance.dom;

import com.sighs.apricityui.init.Document;

/**
 * Document 扩展阶段上下文。
 */
public record ExpandContext(String templatePath, boolean inWorld) {
    public static ExpandContext from(Document document) {
        if (document == null) return new ExpandContext("", false);
        return new ExpandContext(document.getPath(), document.inWorld);
    }
}
