package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;

@ElementRegister(Span.TAG_NAME)
public class Span extends Element {
    public static final String TAG_NAME = "SPAN";

    public Span(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + innerText + ")";
    }
}
