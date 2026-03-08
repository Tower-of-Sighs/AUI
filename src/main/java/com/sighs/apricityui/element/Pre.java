package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;

@ElementRegister(Pre.TAG_NAME)
public class Pre extends Element {
    public static final String TAG_NAME = "PRE";

    public Pre(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    protected void onInitFromDom(Element origin) {
        // Keep line breaks for preformatted text.
        this.innerText = origin.innerText;
    }
}
