package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;

@ElementRegister(Body.TAG_NAME)
public class Body extends Div {
    public static final String TAG_NAME = "BODY";

    public Body(Document document) {
        super(document);
        this.tagName = TAG_NAME;
    }
}
