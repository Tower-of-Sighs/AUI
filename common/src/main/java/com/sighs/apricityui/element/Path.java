package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.registry.annotation.ElementRegister;

@ElementRegister(Path.TAG_NAME)
public class Path extends Div {
    public static final String TAG_NAME = "PATH";

    public Path(Document document) {
        super(document);
        this.tagName = TAG_NAME;
    }
}
