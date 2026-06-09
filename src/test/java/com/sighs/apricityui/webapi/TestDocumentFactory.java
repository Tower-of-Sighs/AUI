package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.init.Document;

public final class TestDocumentFactory {
    private TestDocumentFactory() {
    }

    public static Document createDocument() {
        Document document = new Document("test://doc", false);
        document.body = new Body(document);
        return document;
    }
}
