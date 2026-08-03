package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;

@ElementRegister(Option.TAG_NAME)
public class Option extends Element {
    public static final String TAG_NAME = "OPTION";

    public Option(Document document) {
        super(document, TAG_NAME);
    }
}
