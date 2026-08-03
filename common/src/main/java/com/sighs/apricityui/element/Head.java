package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;

@ElementRegister(Head.TAG_NAME)
public class Head extends Div {
    public static final String TAG_NAME = "HEAD";

    public Head(Document document) {
        super(document);
        tagName = TAG_NAME;
        setAttribute("style", "display:none;");
    }
}
