package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.parser.HTML;

@ElementRegister(Html.TAG_NAME)
public class Html extends Div {
    public static final String TAG_NAME = "HTML";

    public Html(Document document) {
        super(document);
        tagName = TAG_NAME;
    }
}
